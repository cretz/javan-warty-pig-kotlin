package jwp.fuzz

import kotlin.test.Test

class ByteArrayParamGenTest : TestBase() {

    @Test
    fun testStageFlipBits(): Unit = with(ByteArrayParamGen()) {
        // Make sure only one bit, in order, is flipped
        assertEquals(
            (0 until 32).map { "0".repeat(32).replaceRange(it, it + 1, "1") },
            stageFlipBits(ByteArray(4), 1).map { it.binaryString }.sortedDescending()
        )
        // How about every 2 and 4 bits? Just make sure we have the expected amount of unique binary strings
        assertEquals(31, stageFlipBits(ByteArray(4), 2).map { it.binaryString }.distinct().size)
        assertEquals(29, stageFlipBits(ByteArray(4), 4).map { it.binaryString }.distinct().size)
    }

    @Test
    fun testStageFlipBytes(): Unit = with(ByteArrayParamGen()) {
        // Single byte
        fun byteList(vararg b: Byte) = b.toList()
        assertEquals(
            listOf(byteList(-1, 0, 0, 0), byteList(0, -1, 0, 0), byteList(0, 0, -1, 0), byteList(0, 0, 0, -1)),
            stageFlipBytes(ByteArray(4), 1).map { it.toList() }.toList()
        )
        // Two bytes
        assertEquals(
            listOf(byteList(-1, -1, 0, 0), byteList(0, -1, -1, 0), byteList(0, 0, -1, -1)),
            stageFlipBytes(ByteArray(4), 2).map { it.toList() }.toList()
        )
        // Four bytes
        assertEquals(
            listOf(byteList(-1, -1, -1, -1, 0, 0), byteList(0, -1, -1, -1, -1, 0), byteList(0, 0, -1, -1, -1, -1)),
            stageFlipBytes(ByteArray(6), 4).map { it.toList() }.toList()
        )
    }

    @Test
    fun testStageArith8(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped ones in there
        assertEquals(false, allArith8.any { allFlipped.any(it::contentEquals) })
    }

    @Test
    fun testStageArith16(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped ones and no arith8 ones in there
        assertEquals(false, allArith16.any { allFlipped.any(it::contentEquals) })
        assertEquals(false, allArith16.any { allArith8.any(it::contentEquals) })
    }

    @Test
    fun testStageArith32(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped ones and no arith8 or arith16 ones in there
        assertEquals(false, allArith32.any { allFlipped.any(it::contentEquals) })
        assertEquals(false, allArith32.any { allArith8.any(it::contentEquals) })
        assertEquals(false, allArith32.any { allArith16.any(it::contentEquals) })
    }

    @Test
    fun testStageInteresting8(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped ones and no arith ones in there
        assertEquals(false, allInteresting8.any { allFlipped.any(it::contentEquals) })
        assertEquals(false, allInteresting8.any { allArith8.any(it::contentEquals) })
        assertEquals(false, allInteresting8.any { allArith16.any(it::contentEquals) })
        assertEquals(false, allInteresting8.any { allArith32.any(it::contentEquals) })
    }

    @Test
    fun testStageInteresting16(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped, arith, or prev interesting ones in there
        assertEquals(false, allInteresting16.any { allFlipped.any(it::contentEquals) })
        assertEquals(false, allInteresting16.any { allArith8.any(it::contentEquals) })
        assertEquals(false, allInteresting16.any { allArith16.any(it::contentEquals) })
        assertEquals(false, allInteresting16.any { allArith32.any(it::contentEquals) })
        assertEquals(false, allInteresting16.any { allInteresting8.any(it::contentEquals) })
    }

    @Test
    fun testStageInteresting32(): Unit = with(ByteArrayParamGen()) {
        // I don't care the values, I just want no flipped, arith, or prev interesting ones in there
        assertEquals(false, allInteresting32.any { allFlipped.any(it::contentEquals) })
        assertEquals(false, allInteresting32.any { allArith8.any(it::contentEquals) })
        assertEquals(false, allInteresting32.any { allArith16.any(it::contentEquals) })
        assertEquals(false, allInteresting32.any { allArith32.any(it::contentEquals) })
        assertEquals(false, allInteresting32.any { allInteresting8.any(it::contentEquals) })
        assertEquals(false, allInteresting32.any { allInteresting16.any(it::contentEquals) })
    }

    companion object {
        val bytes = ByteArray(8)
        val allFlipped = with(ByteArrayParamGen()) {
            (stageFlipBits(bytes, 1) + stageFlipBits(bytes, 2) + stageFlipBits(bytes, 4)).toList()
        }
        val allArith8 = with(ByteArrayParamGen()) { stageArith8(bytes).toList() }
        val allArith16 = with(ByteArrayParamGen()) { stageArith16(bytes).toList() }
        val allArith32 = with(ByteArrayParamGen()) { stageArith32(bytes).toList() }
        val allInteresting8 = with(ByteArrayParamGen()) { stageInteresting8(bytes).toList() }
        val allInteresting16 = with(ByteArrayParamGen()) { stageInteresting16(bytes).toList() }
        val allInteresting32 = with(ByteArrayParamGen()) { stageInteresting32(bytes).toList() }
    }
}