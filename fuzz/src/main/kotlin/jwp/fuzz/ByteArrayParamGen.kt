package jwp.fuzz

import java.math.BigInteger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.min

open class ByteArrayParamGen(val conf: Config = Config()) :
        ParamGen<ByteArrayParamGen.ByteArrayParam>, ParamGen.WithFeedback {

    // Sorted by shortest first
    val userDictionary = conf.dictionary.sortedBy { it.size }
    val seenBranchesCache = conf.branchesHashCacheSupplier.get()
    val inputQueue = conf.byteArrayInputQueueSupplier.get()
    val arithMax = conf.arithMax
    val arithValsToAdd = (1..arithMax).flatMap { listOf(it, -it) }

    private val varMutex = Object()
    private var totalNanoTime = BigInteger.ZERO
    private var totalBranchCount = BigInteger.ZERO
    private var resultCount = BigInteger.ZERO
    private var queueCycle = BigInteger.ZERO
    private var startMs = -1L

    override fun onResult(result: ExecutionResult, myParamIndex: Int) {
        synchronized(varMutex) {
            totalNanoTime += result.nanoTime.toBigInteger()
            totalBranchCount += result.traceResult.branchesWithResolvedMethods.size.toBigInteger()
            resultCount++
        }
        (result.params[myParamIndex] as? ByteArrayParam)?.entry?.applyResult(result)
        // If it's a unique path, then our param goes to the input queue (if it's not null)
        if (seenBranchesCache.checkUniqueAndStore(result)) inputQueue.enqueue(TestCase(
            result.params[myParamIndex] as? ByteArray ?: return,
            result.traceResult.branchesWithResolvedMethods.map { it.stableHashCode },
            result.nanoTime
        ))
    }

    override fun iterator() =
        generateSequence { inputQueue.cullAndDequeue() }.map(::stages).flatten().iterator()

    open fun stages(entry: QueueEntry): Iterable<ByteArrayParam> {
        synchronized(varMutex) {
            queueCycle++
            if (startMs < 0) startMs = System.currentTimeMillis()
        }
        return sequenceOf(
            stageFlipBits(entry.bytes, 1),
            stageFlipBits(entry.bytes, 2),
            stageFlipBits(entry.bytes, 4),
            stageFlipBytes(entry.bytes, 1),
            stageFlipBytes(entry.bytes, 2),
            stageFlipBytes(entry.bytes, 4),
            stageArith8(entry.bytes),
            stageArith16(entry.bytes),
            stageArith32(entry.bytes),
            stageInteresting8(entry.bytes),
            stageInteresting16(entry.bytes),
            stageInteresting32(entry.bytes),
            stageDictionary(entry.bytes, userDictionary),
            // TODO: user extras
            stageHavoc(entry.bytes)
            // TODO: splices
        ).flatten().map { ByteArrayParam(it, entry) }.asIterable()
    }

    open fun stageFlipBits(buf: ByteArray, consecutiveToFlip: Int) = buildSequence {
        for (bitIndex in 0 until ((buf.size * 8) - (consecutiveToFlip - 1))) yield(buf.copyAnd {
            for (offset in 0 until consecutiveToFlip) flipBit(bitIndex + offset)
        })
    }.asIterable()

    open fun stageFlipBytes(buf: ByteArray, consecutiveToFlip: Int) = buildSequence {
        for (byteIndex in 0 until (buf.size - (consecutiveToFlip - 1))) yield(buf.copyAnd {
            for (offset in 0 until consecutiveToFlip) invByte(byteIndex + offset)
        })
    }.asIterable()

    open fun stageArith8(buf: ByteArray) = buildSequence {
        for (index in 0 until buf.size) {
            val oldByte = buf[index]
            arithValsToAdd.forEach {
                val newByte = (oldByte + it).toByte()
                if (!oldByte.couldHaveBitFlippedTo(newByte)) yield(buf.copyAnd { set(index, newByte) })
            }
        }
    }.asIterable()

    open fun stageArith16(buf: ByteArray) = buildSequence {
        fun affectsBothBytes(orig: Short, new: Short) =
            orig.byte0 != new.byte0 && orig.byte1 != new.byte1
        for (index in 0 until 1) {//buf.size - 1) {
            val origLe = buf.getShortLe(index)
            val origBe = buf.getShortBe(index)
            arithValsToAdd.forEach {
                val newLe = (origLe + it).toShort()
                if (affectsBothBytes(origLe, newLe) && !origLe.couldHaveBitFlippedTo(newLe))
                    yield(buf.copyAnd { putShortLe(index, newLe) })
                val newBe = (origBe + it).toShort()
                if (affectsBothBytes(origBe, newBe) && !origLe.couldHaveBitFlippedTo(newBe.endianSwapped))
                    yield(buf.copyAnd { putShortBe(index, newBe) })
            }
        }
    }.asIterable()

    open fun stageArith32(buf: ByteArray) = buildSequence {
        fun affectsMoreThanTwoBytes(orig: Int, new: Int) =
            (if (orig.byte0 != new.byte0) 1 else 0) +
                (if (orig.byte1 != new.byte1) 1 else 0) +
                (if (orig.byte2 != new.byte2) 1 else 0) +
                (if (orig.byte3 != new.byte3) 1 else 0) > 2
        for (index in 0 until buf.size - 3) {
            val origLe = buf.getIntLe(index)
            val origBe = buf.getIntBe(index)
            arithValsToAdd.forEach {
                val newLe = origLe + it
                if (affectsMoreThanTwoBytes(origLe, newLe) && !origLe.couldHaveBitFlippedTo(newLe))
                    yield(buf.copyAnd { putIntLe(index, newLe) })
                val newBe = origBe + it
                if (affectsMoreThanTwoBytes(origBe, newBe) && !origLe.couldHaveBitFlippedTo(newBe.endianSwapped))
                    yield(buf.copyAnd { putIntBe(index, newBe) })
            }
        }
    }.asIterable()

    open fun stageInteresting8(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Byte, new: Byte) =
                new >= orig - arithMax && new <= orig + arithMax
        for (index in 0 until buf.size)  ParamGen.interestingByte.forEach { byte ->
            val orig = buf[index]
            if (!couldBeArith(orig, byte) && !orig.couldHaveBitFlippedTo(byte))
                yield(buf.copyAnd { set(index, byte) })
        }
    }.asIterable()

    open fun stageInteresting16(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Short, new: Short): Boolean {
            val origBe = orig.endianSwapped
            return (new >= orig - arithMax && new <= orig + arithMax) ||
                (new >= origBe - arithMax && new <= origBe + arithMax)
        }
        fun couldBeInteresting8(orig: Short, new: Short) = ParamGen.interestingByte.any {
            (orig.byte0 == new.byte0 && it == new.byte1) || (it == new.byte0 && orig.byte1 == new.byte1)
        }
        for (index in 0 until buf.size - 1)  ParamGen.interestingShort.forEach { short ->
            val origLe = buf.getShortLe(index)
            if (!couldBeArith(origLe, short) && !couldBeInteresting8(origLe, short) &&
                    !origLe.couldHaveBitFlippedTo(short))
                yield(buf.copyAnd { putShortLe(index, short) })
            val origBe = buf.getShortBe(index)
            val shortBe = short.endianSwapped
            if (!couldBeArith(origBe, shortBe) && !couldBeInteresting8(origBe, shortBe) &&
                    !origLe.couldHaveBitFlippedTo(shortBe))
                yield(buf.copyAnd { putShortBe(index, short) })
        }
    }.asIterable()

    open fun stageInteresting32(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Int, new: Int): Boolean {
            val origArr = orig.toByteArray()
            val newArr = new.toByteArray()
            fun byteArith(index: Int) = (0 until 4).all {
                (it == index && newArr[it] - origArr[it] in (-arithMax)..arithMax) || origArr[it] == newArr[it]
            }
            fun shortArith(index: Int) = (0 until 4).all {
                it == index + 1 ||
                    (it == index && newArr.getShortLe(it) - origArr.getShortLe(it) in (-arithMax)..arithMax) ||
                    origArr[it] == newArr[it]
            }
            return (0 until 4).any { byteArith(it) || (it < 3 && shortArith(it)) }
        }
        fun couldBeInteresting8(orig: Int, new: Int) = ParamGen.interestingByte.any {
            (it == new.byte0 && orig.byte1 == new.byte1 && orig.byte2 == new.byte2 && orig.byte3 == new.byte3) ||
                (orig.byte0 == new.byte0 && it == new.byte1 && orig.byte2 == new.byte2 && orig.byte3 == new.byte3) ||
                (orig.byte0 == new.byte0 && orig.byte1 == new.byte1 && it == new.byte2 && orig.byte3 == new.byte3) ||
                (orig.byte0 == new.byte0 && orig.byte1 == new.byte1 && orig.byte2 == new.byte2 && it == new.byte3)
        }
        fun couldBeInteresting16(orig: Int, new: Int): Boolean {
            val origArr = orig.toByteArray()
            val newArr = new.toByteArray()
            for (i in 0 until 3) {
                val pre1 = origArr[i]
                val pre2 = origArr[i + 1]
                if (ParamGen.interestingShort.any {
                    origArr.apply { putShortLe(i, it) }.contentEquals(newArr) ||
                            origArr.apply { putShortBe(i, it) }.contentEquals(newArr)
                }) return true
                origArr[i] = pre1
                origArr[i + 1] = pre2
            }
            return false
        }
        for (index in 0 until buf.size - 3)  ParamGen.interestingInt.forEach { int ->
            val origLe = buf.getIntLe(index)
            if (!couldBeArith(origLe, int) && !couldBeInteresting8(origLe, int) &&
                    !couldBeInteresting16(origLe, int) && !origLe.couldHaveBitFlippedTo(int))
                yield(buf.copyAnd { putIntLe(index, int) })

            val origBe = buf.getIntBe(index)
            val intBe = int.endianSwapped
            if (!couldBeArith(origBe, intBe) && !couldBeInteresting8(origLe, intBe) &&
                    !couldBeInteresting16(origLe, intBe) && !origLe.couldHaveBitFlippedTo(intBe))
                yield(buf.copyAnd { putIntBe(index, intBe) })
        }
    }.asIterable()

    open fun stageDictionary(buf: ByteArray, sortedDictionary: List<ByteArray>) = buildSequence {
        // To match AFL, we'll put different dictionary entries at an index before going on to the next index
        if (sortedDictionary.isNotEmpty()) for (index in 0 until buf.size) {
            sortedDictionary.forEach { entry ->
                if (index + entry.size < buf.size) yield(buf.copyOf().apply {
                    for (i in 0 until entry.size) set(index + i, entry[i])
                })
            }
        }
    }.asIterable()

    open fun stageHavoc(buf: ByteArray) = buildSequence {
        // TODO: base havoc cycles on perf
        for (stageCur in 0 until conf.havocCycles) {
            var bytes = buf.copyOf()
            for (i in 0 until 2.toBigInteger().pow(1 + conf.rand.nextInt(conf.havocStackPower)).toInt()) {
                bytes = conf.havocTweaks.randItem(conf.rand).tweak(this@ByteArrayParamGen, bytes)
            }
            yield(bytes)
        }
    }.asIterable()

    open fun chooseBlockLen(limit: Int): Int {
        val rLim = synchronized(varMutex) {
            val over10Min = startMs > 0 && System.currentTimeMillis() - startMs > 10 * 60 * 1000
            if (over10Min) queueCycle.min(3.toBigInteger()).toInt() else 1
        }
        var (minValue, maxValue) = when (conf.rand.nextInt(rLim)) {
            0 -> 1 to conf.havocBlockSmall
            1 -> conf.havocBlockSmall to conf.havocBlockMedium
            else -> when (conf.rand.nextInt(10)) {
                0 -> conf.havocBlockLarge to conf.havocBlockXLarge
                else -> conf.havocBlockMedium to conf.havocBlockLarge
            }
        }
        if (minValue >= limit) minValue = 1
        return minValue + conf.rand.nextInt(min(maxValue, limit) - minValue + 1)
    }

    data class Config(
        val dictionary: List<ByteArray> = emptyList(),
        val branchesHashCacheSupplier: Supplier<BranchesHashCache> =
            Supplier { BranchesHashCache.InMemory() },
        val byteArrayInputQueueSupplier: Supplier<ByteArrayInputQueue> =
            Supplier { ByteArrayInputQueue.InMemory(1L, TimeUnit.MINUTES) },
        val havocTweaks: List<Havoc.Tweak> = Havoc.suggestedTweaks,
        val rand: Random = Random(),
        val arithMax: Int = 35,
        val havocCycles: Int = 1024,
        val havocStackPower: Int = 7,
        val havocBlockSmall: Int = 32,
        val havocBlockMedium: Int = 128,
        val havocBlockLarge: Int = 1500,
        val havocBlockXLarge: Int = 32768,
        val maxInput: Int = 1 * 1024 * 1024
    )

    class ByteArrayParam(override val value: ByteArray, val entry: QueueEntry) : ParamGen.ParamRef<ByteArray>

    class QueueEntry(
        val bytes: ByteArray,
        val dequeuedIndex: Long
    ) {
        private val varMutex = Object()
        private var totalNanoTime = 0L
        private var totalBranchCount = 0L
        private var resultCount = 0L

        fun applyResult(result: ExecutionResult) {
            synchronized(varMutex) {
                totalNanoTime += result.nanoTime
                totalBranchCount += result.traceResult.branchesWithResolvedMethods.size
                resultCount++
            }
        }

        fun calculateScore(): Double = TODO()
    }

    class TestCase(
        val bytes: ByteArray,
        val branchHashes: List<Int>?,
        val nanoTime: Long?
    ) : Comparable<TestCase> {
        val score = if (nanoTime == null) null else bytes.size * nanoTime

        override fun compareTo(other: TestCase) =
            // Null score, meaning it hasn't run, is greater than other scores
            if (score === other.score) 0
            else if (score == null) 1
            else if (other.score == null) -1
            else score.compareTo(other.score)

        companion object {
            @JvmStatic
            fun culled(cases: Iterable<TestCase>): List<TestCase> = cases.sorted().let { sorted ->
                // We needed the cases sorted by score first
                // Now, go over each, adding to favored for ones that have branches we haven't seen
                val seenBranchHashes = HashSet<Int>(sorted.size)
                val (favored, unfavored) = sorted.partition { testCase ->
                    // Must have run and have branches we haven't seen yet to be favored
                    testCase.branchHashes != null && seenBranchHashes.addAll(testCase.branchHashes)
                }
                favored + unfavored
            }
        }
    }

    @FunctionalInterface
    interface BranchesHashCache {
        // Must be thread safe. Return true if unique.
        fun checkUniqueAndStore(result: ExecutionResult): Boolean

        open class InMemory : BranchesHashCache {
            val seenBranchHashes = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
            override fun checkUniqueAndStore(result: ExecutionResult) =
                seenBranchHashes.add(result.traceResult.stableBranchesHash)
        }
    }

    // Must be thread safe in all ops
    interface ByteArrayInputQueue {
        // This should try to be unbounded, but should immediately throw if unable to enqueue
        fun enqueue(testCase: TestCase)
        // Implementations can and should time this out and throw a TimeoutException if waiting too long
        fun cullAndDequeue(): QueueEntry

        open class InMemory(val timeoutTime: Long, val timeoutUnit: TimeUnit) : ByteArrayInputQueue {
            protected var queue = ArrayList<TestCase>()
            protected val lock = ReentrantLock()
            protected var enqueuedSinceLastDequeued = false
            protected val notEmptyCond = lock.newCondition()
            protected var queueCounter = 0L

            override fun enqueue(testCase: TestCase) {
                lock.lock()
                try {
                    queue.add(testCase)
                    enqueuedSinceLastDequeued = true
                    notEmptyCond.signal()
                } finally {
                    lock.unlock()
                }
            }

            override fun cullAndDequeue(): QueueEntry {
                lock.lock()
                try {
                    // Wait for queue to not be empty
                    while (queue.isEmpty()) {
                        if (!notEmptyCond.await(timeoutTime, timeoutUnit)) throw TimeoutException()
                    }
                    // Cull if there have been some enqueued since
                    if (enqueuedSinceLastDequeued) {
                        enqueuedSinceLastDequeued = false
                        TestCase.culled(queue).let { queue.clear(); queue.addAll(it) }
                    }
                    // Take the first one
                    return QueueEntry(queue.removeAt(0).bytes, queueCounter++)
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    object Havoc {

        private fun tweakMut(fn: ByteArrayParamGen.(ByteArray) -> ByteArray) = object : Tweak {
            override fun tweak(gen: ByteArrayParamGen, bytes: ByteArray) = fn(gen, bytes)
        }

        private fun tweak(fn: ByteArrayParamGen.(ByteArray) -> Unit) = tweakMut { fn(it); it }

        private fun tweakRandomByte(fn: ByteArrayParamGen.(Byte) -> Byte) = tweak {
            val index = conf.rand.nextInt(it.size)
            it[index] = fn(this, it[index])
        }
        private fun tweakRandomShort(fn: ByteArrayParamGen.(Short) -> Short) = tweak {
            if (it.size >= 2) {
                val index = conf.rand.nextInt(it.size - 1)
                if (conf.rand.nextBoolean()) it.putShortLe(index, fn(it.getShortLe(index)))
                else it.putShortBe(index, fn(it.getShortBe(index)))
            }
        }
        private fun tweakRandomInt(fn: ByteArrayParamGen.(Int) -> Int) = tweak {
            if (it.size >= 4) {
                val index = conf.rand.nextInt(it.size - 3)
                if (conf.rand.nextBoolean()) it.putIntLe(index, fn(it.getIntLe(index)))
                else it.putIntBe(index, fn(it.getIntBe(index)))
            }
        }

        val flipSingleBit get() = tweak { it.flipBit(conf.rand.nextInt(it.size * 8)) }
        val interestingByte get() = tweakRandomByte { ParamGen.interestingByte.randItem(conf.rand) }
        val interestingShort get() = tweakRandomShort {ParamGen.interestingShort.randItem(conf.rand) }
        val interestingInt get() = tweakRandomInt { ParamGen.interestingInt.randItem(conf.rand) }
        val subtractFromByte get() = tweakRandomByte { (it - (1 + conf.rand.nextInt(arithMax))).toByte() }
        val addToByte get() = tweakRandomByte { (it + (1 + conf.rand.nextInt(arithMax))).toByte() }
        val subtractFromShort get() = tweakRandomShort { (it - (1 + conf.rand.nextInt(arithMax))).toShort() }
        val addToShort get() = tweakRandomShort { (it + (1 + conf.rand.nextInt(arithMax))).toShort() }
        val subtractFromInt get() = tweakRandomInt { it - (1 + conf.rand.nextInt(arithMax)) }
        val addToInt get() = tweakRandomInt { it + (1 + conf.rand.nextInt(arithMax)) }
        val randomByte get() = tweakRandomByte {
            while (true) {
                val b = (conf.rand.nextInt(256) - 128).toByte()
                if (it != b) return@tweakRandomByte b
            }
            // XXX: https://youtrack.jetbrains.com/issue/KT-22404
            @Suppress("UNREACHABLE_CODE")
            -1
        }
        val deleteBytes get() = tweakMut {
            if (it.size < 2) it else {
                val delLen = chooseBlockLen(it.size - 1)
                val delFrom = conf.rand.nextInt(it.size - delLen + 1)
                it.remove(delFrom, delLen)
            }
        }
        val cloneOrInsertBytes get() = tweakMut {
            if (it.size + conf.havocBlockXLarge >= conf.maxInput) it else {
                val actuallyClone = conf.rand.nextInt(4) > 0
                val (cloneLen, cloneFrom) =
                    if (actuallyClone) chooseBlockLen(it.size).let { bl -> bl to conf.rand.nextInt(it.size - bl + 1) }
                    else chooseBlockLen(conf.havocBlockXLarge) to 0
                val cloneTo = conf.rand.nextInt(it.size)
                val newArr = ByteArray(it.size + cloneLen)
                System.arraycopy(it, 0, newArr, 0, cloneTo)
                /*              memset(new_buf + clone_to,
                     UR(2) ? UR(256) : out_buf[UR(temp_len)], clone_len);*/
                if (actuallyClone) System.arraycopy(it, cloneFrom, newArr, cloneTo, cloneLen)
                else newArr.fill(
                    if (conf.rand.nextBoolean()) (conf.rand.nextInt(256) - 128).toByte()
                    else it[conf.rand.nextInt(it.size)],
                    cloneTo, cloneTo + cloneLen
                )
                System.arraycopy(it, cloneTo, newArr, cloneTo + cloneLen, it.size - cloneTo)
                newArr
            }
        }
        val overwriteRandomOrFixedBytes get() = tweak {
            if (it.size >= 2) {
                val copyLen = chooseBlockLen(it.size - 1)
                val copyFrom = conf.rand.nextInt(it.size - copyLen + 1)
                val copyTo = conf.rand.nextInt(it.size - copyLen + 1)
                if (conf.rand.nextInt(4) > 0) {
                    if (copyFrom != copyTo) System.arraycopy(it, copyFrom, it, copyTo, copyLen)
                } else it.fill(
                    if (conf.rand.nextBoolean()) (conf.rand.nextInt(256) - 128).toByte()
                    else it[conf.rand.nextInt(it.size)],
                    copyTo, copyLen
                )
            }
        }

        // TODO: dictionary-based tweaks

        val suggestedTweaks get() = listOf(
            flipSingleBit,
            interestingByte,
            interestingShort,
            interestingInt,
            subtractFromByte,
            addToByte,
            subtractFromShort,
            addToShort,
            subtractFromInt,
            addToInt,
            randomByte,
            deleteBytes,
            deleteBytes,
            cloneOrInsertBytes,
            overwriteRandomOrFixedBytes
        )

        @FunctionalInterface
        interface Tweak {
            fun tweak(gen: ByteArrayParamGen, bytes: ByteArray): ByteArray
        }
    }
}