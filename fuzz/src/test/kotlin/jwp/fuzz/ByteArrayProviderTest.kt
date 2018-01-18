package jwp.fuzz

import kotlin.math.min
import kotlin.test.Test

class ByteArrayProviderTest : TestBase() {

    @Test
    fun testStageFlipBits(): Unit = with(ByteArrayProvider()) {
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
    fun testStageFlipBytes(): Unit = with(ByteArrayProvider()) {
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

    object ByteArrayComparator : Comparator<ByteArray> {
        override fun compare(o1: ByteArray?, o2: ByteArray?): Int {
            if (o1 == null) { return if (o2 == null) 0 else 1 }
            if (o2 == null) return -1
            for (i in 0 until min(o1.size, o2.size)) if (o1[i] != o2[i]) return o1[i].compareTo(o2[i])
            return o1.size.compareTo(o2.size)
        }
    }
}