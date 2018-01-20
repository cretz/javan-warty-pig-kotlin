package jwp.fuzz

import kotlin.test.Test

class ByteUtilTest : TestBase() {

    @Test
    fun testFlipBit() {
        assertEquals("00000100", 0.toByte().flipBit(2).binaryString)
        assertEquals("00111000", 0.toByte().flipBit(3).flipBit(4).flipBit(5).binaryString)
        assertEquals("00000001", 0.toByte().flipBit(0).binaryString)
        assertEquals("10000000", 0.toByte().flipBit(7).binaryString)
    }

    @Test
    fun testCouldHaveBitFlippedTo() {
        // Take all the unique bytes, shorts, and ints when doing flips on a 0'd byte
        // array. Then check them and +/- 1 to see if they could be flips.

        // All sets of flipped bits for 0
        val withFlippedBits = with(ByteArrayParamGen()) {
            val bytes = ByteArray(9)
            stageFlipBits(bytes, 1) + stageFlipBits(bytes, 2) + stageFlipBits(bytes, 4)
        }
        // All unique bytes
        val bytes = withFlippedBits.flatMap { it.asIterable() }.toSortedSet()
        fun testByte(byte: Byte) {
            assertEquals(byte == 0.toByte() || bytes.contains(byte), 0.toByte().couldHaveBitFlippedTo(byte))
        }
        bytes.forEach {
            if (it != Byte.MIN_VALUE) testByte((it - 1).toByte())
            testByte(it)
            if (it != Byte.MAX_VALUE) testByte((it + 1).toByte())
        }
        // All unique shorts
        val shorts = withFlippedBits.flatMap { arr ->
            (0 until (arr.size - 1)).map { arr.getShortLe(it) }
        }.toSortedSet()
        fun testShort(short: Short) {
            assertEquals(short == 0.toShort() || shorts.contains(short), 0.toShort().couldHaveBitFlippedTo(short))
        }
        shorts.forEach {
            if (it != Short.MIN_VALUE) testShort((it - 1).toShort())
            testShort(it)
            if (it != Short.MAX_VALUE) testShort((it + 1).toShort())
        }
        // All unique ints
        val ints = withFlippedBits.flatMap { arr ->
            (0 until (arr.size - 3)).map { arr.getIntLe(it) }
        }.toSortedSet()
        fun testInt(int: Int) {
            assertEquals(int == 0 || ints.contains(int), 0.couldHaveBitFlippedTo(int))
        }
        ints.forEach {
            if (it != Int.MIN_VALUE) testInt(it - 1)
            testInt(it)
            if (it != Int.MAX_VALUE) testInt(it + 1)
        }
    }
}