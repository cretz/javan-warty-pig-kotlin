package jwp.fuzz

import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.coroutines.experimental.buildSequence

// TODO: we know lots of bit twiddling math is wrong in here right now...we are just jotting down pseudocode
// before we write the test cases

open class ByteArrayProvider : TypeGen.WithFeedback, ParameterProvider.WithFuzzerConfig, Iterable<ByteArray> {

    // Sorted by shortest first
    lateinit var userDictionary: List<ByteArray> internal set
    lateinit var seenBranchesCache: Fuzzer.BranchesHashCache internal set
    lateinit var inputQueue: ByteArrayInputQueue internal set

    override fun setFuzzerConfig(fuzzerConfig: Fuzzer.Config) {
        userDictionary = fuzzerConfig.dictionary.toTypedArray().apply { sortBy { it.size } }.toList()
        seenBranchesCache = fuzzerConfig.branchesHashCacheSupplier.get()
        inputQueue = fuzzerConfig.byteArrayInputQueueSupplier.get()
    }

    override fun onResult(result: ExecutionResult, myParamIndex: Int) {
        // If it's a unique path, then our param goes to the input queue (if it's not null)
        if (seenBranchesCache.checkUniqueAndStore(result)) inputQueue.enqueue(TestCase(
            result.params[myParamIndex] as? ByteArray ?: return,
            result.traceResult.branchesWithResolvedMethods.map { it.stableHashCode },
            result.nanoTime
        ))
    }

    override fun iterator() = generateSequence { inputQueue.cullAndDequeue().bytes }.map(::stages).flatten().iterator()

    open fun stages(buf: ByteArray) = sequenceOf(
        stageFlipBits(buf, 1),
        stageFlipBits(buf, 2),
        stageFlipBits(buf, 4),
        stageFlipBytes(buf, 1),
        stageFlipBytes(buf, 2),
        stageFlipBytes(buf, 4),
        stageArith8(buf),
        stageArith16(buf),
        stageArith32(buf),
        stageInteresting8(buf),
        stageInteresting16(buf),
        stageInteresting32(buf),
        stageDictionary(buf, userDictionary)
    ).flatten().asIterable()

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
        for (index in 0 until buf.size)  TypeGen.interestingByte.forEach { byte ->
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
        fun couldBeInteresting8(orig: Short, new: Short) = TypeGen.interestingByte.any {
            (orig.byte0 == new.byte0 && it == new.byte1) || (it == new.byte0 && orig.byte1 == new.byte1)
        }
        for (index in 0 until buf.size - 1)  TypeGen.interestingShort.forEach { short ->
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
        fun couldBeInteresting8(orig: Int, new: Int) = TypeGen.interestingByte.any {
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
                if (TypeGen.interestingShort.any {
                    origArr.apply { putShortLe(i, it) }.contentEquals(newArr) ||
                        origArr.apply { putShortBe(i, it) }.contentEquals(newArr)
                }) return true
                origArr[i] = pre1
                origArr[i + 1] = pre2
            }
            return false
        }
        for (index in 0 until buf.size - 3)  TypeGen.interestingInt.forEach { int ->
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
            for (entry in sortedDictionary) {
                if (index + entry.size < buf.size) yield(buf.copyOf().apply {
                    for (i in 0 until entry.size) set(index + i, entry[i])
                })
            }
        }
    }.asIterable()

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

    // Must be thread safe in all ops
    interface ByteArrayInputQueue {
        // This should try to be unbounded, but should immediately throw if unable to enqueue
        fun enqueue(testCase: TestCase)
        // Implementations can and should time this out and throw a TimeoutException if waiting too long
        fun cullAndDequeue(): TestCase

        open class InMemory(val timeoutTime: Long, val timeoutUnit: TimeUnit) : ByteArrayInputQueue {
            protected var queue = ArrayList<TestCase>()
            protected val lock = ReentrantLock()
            protected var enqueuedSinceLastDequeued = false
            protected val notEmptyCond = lock.newCondition()

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

            override fun cullAndDequeue(): TestCase {
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
                    return queue.removeAt(0)
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    companion object {
        const val arithMax = 35
        val arithValsToAdd = (1..arithMax).flatMap { listOf(it, -it) }
    }
}