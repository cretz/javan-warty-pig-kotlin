package jwp.fuzz

import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.buildSequence
import kotlin.experimental.inv

// TODO: we know lots of bit twiddling math is wrong in here right now...we are just jotting down pseudocode
// before we write the test cases

class ByteArrayProvider : TypeGen.WithFeedback, ParameterProvider.WithFuzzerConfig, Iterable<ByteArray> {

    // Sorted by shortest first
    var dictionary = emptyArray<ByteArray>()

    override fun setFuzzerConfig(fuzzerConfig: Fuzzer.Config) {
        dictionary = fuzzerConfig.dictionary.toTypedArray().apply { sortBy { it.size } }
    }

    val seenBranchHashes = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    val queue: BlockingQueue<TestCase> = TODO()

    override fun onResult(result: ExecutionResult, myParamIndex: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    override fun iterator(): Iterator<ByteArray> = TODO()

    fun stages(buf: ByteArray) = sequenceOf(
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
        stageDictionary(buf)
    ).flatten().asIterable()

    fun stageFlipBits(buf: ByteArray, consecutiveToFlip: Int) = buildSequence {
        for (bitIndex in 0 until ((buf.size * 8) - (consecutiveToFlip - 1))) yield(buf.copyOf().apply {
            for (offset in 0 until consecutiveToFlip) {
                val byteIndex = (bitIndex + offset) / 8
                set(byteIndex, get(byteIndex).flipBit((bitIndex + offset) % 8))
            }
        })
    }

    fun stageFlipBytes(buf: ByteArray, consecutiveToFlip: Int) = buildSequence {
        for (byteIndex in 0 until (buf.size - (consecutiveToFlip - 1))) yield(buf.copyOf().apply {
            for (offset in 0 until consecutiveToFlip) {
                set(byteIndex + offset, get(byteIndex + offset).inv())
            }
        })
    }

    fun stageArith8(buf: ByteArray) = buildSequence {
        for (index in 0 until buf.size) {
            val byte = buf[index]
            for (j in 1..arithMax) {
                if (!(byte.toInt() xor (byte + j)).couldBeBitFlip())
                    yield(buf.copyOf().apply { set(index, (byte + j).toByte()) })
                if (!(byte.toInt() xor (byte - j)).couldBeBitFlip())
                    yield(buf.copyOf().apply { set(index, (byte - j).toByte()) })
            }
        }
    }

    fun stageArith16(buf: ByteArray) = buildSequence {
        for (index in 0 until buf.size - 1) {
            val origLe = buf.getShortLe(index)
            val origBe = buf.getShortBe(index)
            for (j in 1..arithMax) {
                var r = origLe.toInt() xor (origLe + j)
                if (origLe + j <= Short.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putShortLe(index, (origLe + j).toShort()) })
                r = origLe.toInt() xor (origLe - j)
                if (origLe - j >= Short.MIN_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putShortLe(index, (origLe - j).toShort()) })
                r = origLe.toInt() xor (origBe + j).toShort().swap16().toInt()
                if (origBe + j <= Short.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putShortBe(index, (origBe + j).toShort()) })
                r = origLe.toInt() xor (origBe - j).toShort().swap16().toInt()
                if (origBe - j >= Short.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putShortBe(index, (origBe - j).toShort()) })
            }
        }
    }

    fun stageArith32(buf: ByteArray) = buildSequence {
        for (index in 0 until buf.size - 3) {
            val origLe = buf.getIntLe(index)
            val origBe = buf.getIntBe(index)
            for (j in 1..arithMax) {
                var r = origLe xor (origLe + j)
                if (origLe + j.toLong() <= Int.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putIntLe(index, origLe + j) })
                r = origLe xor (origLe - j)
                if (origLe - j.toLong() >= Int.MIN_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putIntLe(index, origLe - j) })
                r = origLe xor (origBe + j).swap32()
                if (origBe + j.toLong() <= Int.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putIntBe(index, origBe + j) })
                r = origLe xor (origBe - j).swap32()
                if (origBe - j.toLong() >= Int.MAX_VALUE && !r.couldBeBitFlip())
                    yield(buf.copyOf().apply { putIntBe(index, origBe - j) })
            }
        }
    }

    fun stageInteresting8(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Byte, new: Int) = new >= orig - arithMax && new <= orig + arithMax
        for (index in 0 until buf.size)  TypeGen.interestingByte.forEach { byte ->
            val orig = buf[index]
            if (!couldBeArith(orig, byte) && !(orig.toInt() xor byte).couldBeBitFlip())
                yield(buf.copyOf().apply { set(index, byte.toByte()) })
        }
    }

    fun stageInteresting16(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Short, new: Int): Boolean {
            val origBe = orig.swap16()
            return (new >= orig - arithMax && new <= orig + arithMax) ||
                (new >= origBe - arithMax && new <= origBe + arithMax)
        }
        fun couldBeInteresting8(orig: Short, new: Int): Boolean {
            val origByte1 = orig.toByte().toInt()
            val origByte2 = orig.toByte().toInt()
            val newByte1 = new.toByte().toInt()
            val newByte2 = (new shr 8).toByte().toInt()
            return TypeGen.interestingByte.any {
                (origByte1 == newByte1 && it == newByte2) ||
                    (it == newByte1 && origByte2 == newByte2)
            }
        }
        for (index in 0 until buf.size - 1)  TypeGen.interestingShort.forEach { short ->
            val origLe = buf.getShortLe(index)
            if (!couldBeArith(origLe, short) && !couldBeInteresting8(origLe, short) &&
                    !(origLe.toInt() xor short).couldBeBitFlip()) {
                yield(buf.copyOf().apply { putShortLe(index, short.toShort()) })
            }

            val origBe = buf.getShortBe(index)
            val shortBe = short.toShort().swap16().toInt()
            if (!couldBeArith(origBe, shortBe) && !couldBeInteresting8(origBe, shortBe) &&
                    !(origLe.toInt() xor shortBe).couldBeBitFlip()) {
                yield(buf.copyOf().apply { putShortBe(index, short.toShort()) })
            }
        }
    }

    fun stageInteresting32(buf: ByteArray) = buildSequence {
        fun couldBeArith(orig: Int, new: Int): Boolean {
            val origBe = orig.swap32()
            return (new >= orig - arithMax && new <= orig + arithMax) ||
                    (new >= origBe - arithMax && new <= origBe + arithMax)
        }
        fun couldBeInteresting(orig: Int, new: Int): Boolean {
            TODO()
        }
        for (index in 0 until buf.size - 3)  TypeGen.interestingInt.forEach { int ->
            val origLe = buf.getIntLe(index)
            if (!couldBeArith(origLe, int) && !couldBeInteresting(origLe, int) &&
                    !(origLe xor int).couldBeBitFlip()) {
                yield(buf.copyOf().apply { putIntLe(index, int) })
            }

            val origBe = buf.getIntBe(index)
            val intBe = int.swap32()
            if (!couldBeArith(origBe, intBe) && !couldBeInteresting(origBe, intBe) &&
                    !(origLe xor intBe).couldBeBitFlip()) {
                yield(buf.copyOf().apply { putIntBe(index, int) })
            }
        }
    }

    fun stageDictionary(buf: ByteArray) = buildSequence {
        // To match AFL, we'll put different dictionary entries at an index before going on to the next index
        for (index in 0 until buf.size) {
            for (entry in dictionary) {
                if (index + entry.size < buf.size) yield(buf.copyOf().apply {
                    for (i in 0 until entry.size) set(index + i, entry[i])
                })
            }
        }
    }

    companion object {
        const val arithMax = 35

        @Suppress("NOTHING_TO_INLINE")
        inline fun Byte.flipBit(bit: Int) = (toInt() xor (1 shl bit)).toByte()
        @Suppress("NOTHING_TO_INLINE")
        inline infix fun Byte.xor(v: Int): Byte = (toInt() xor v).toByte()

        fun Short.swap16() = ((toInt() shl 8) or (toInt() shr 8)).toShort()
        fun ByteArray.getShortLe(index: Int) =
                ((get(index).toInt() and 0xFF) or (get(index + 1).toInt() shl 8)).toShort()
        fun ByteArray.getShortBe(index: Int) =
                ((get(index + 1).toInt() and 0xFF) or (get(index).toInt() shl 8)).toShort()
        fun ByteArray.putShortLe(index: Int, v: Short) {
            set(index, v.toByte())
            set(index + 1, (v.toInt() shr 8).toByte())
        }
        fun ByteArray.putShortBe(index: Int, v: Short) {
            set(index, (v.toInt() shr 8).toByte())
            set(index + 1, v.toByte())
        }

        @Suppress("NOTHING_TO_INLINE")
        fun Int.swap32() =
            (this shl 24) or
            (this shr 24) or
            ((this shl 8) and 0x00FF0000) or
            ((this shr 8) and 0x0000FF00)
        fun ByteArray.getIntLe(index: Int) =
            (get(index).toInt() and 0xFF) or
            ((get(index + 1).toInt() and 0xFF) shl 8) or
            ((get(index + 2).toInt() and 0xFF) shl 16) or
            (get(index + 3).toInt() shl 24)
        fun ByteArray.getIntBe(index: Int) =
            (get(index).toInt() shl 24) or
            ((get(index + 1).toInt() and 0xFF) shl 16) or
            ((get(index + 3).toInt() and 0xFF) shl 8) or
            (get(index + 3).toInt() and 0xFF)
        fun ByteArray.putIntLe(index: Int, v: Int) {
            set(index, v.toByte())
            set(index + 1, (v shr 8).toByte())
            set(index + 2, (v shr 16).toByte())
            set(index + 3, (v shr 24).toByte())
        }
        fun ByteArray.putIntBe(index: Int, v: Int) {
            set(index, (v shr 24).toByte())
            set(index + 1, (v shr 16).toByte())
            set(index + 2, (v shr 8).toByte())
            set(index + 3, v.toByte())
        }

        // TODO: this is all wrong, fix it and put tests around it
        fun Int.couldBeBitFlip(): Boolean {
            if (this == 0) return true
            var sh = 0
            var temp = this
            // Shift left until first bit set
            while (this and 1 == 0) {
                sh++
                temp = temp shr 1
            }
            // 1-, 2-, and 4-bit patterns are OK anywhere
            if (temp == 1 || temp == 3 || temp == 15) return true
            // 8-, 16-, and 32-bit patterns are OK only if shift factor is
            // divisible by 8, since that's the stepover for these ops
            if (sh and 7 != 0) return false

            if (temp == Byte.MAX_VALUE.toInt() || temp == Short.MAX_VALUE.toInt() || temp == Int.MAX_VALUE)
                return true

            return false
        }
    }

    class TestCase(
        val bytes: ByteArray
    )
}