package jwp.fuzz

import kotlin.test.Test

class ByteArrayProviderTest : TestBase() {

    @Test
    fun testFlipBit() = with(ByteArrayProvider) {
        assertEquals("00000100", 0.toByte().flipBit(2).binaryString)
        assertEquals("00111000", 0.toByte().flipBit(3).flipBit(4).flipBit(5).binaryString)
        assertEquals("00000001", 0.toByte().flipBit(0).binaryString)
        assertEquals("10000000", 0.toByte().flipBit(7).binaryString)
    }

    val Byte.binaryString get() = toString(2).trimStart('-').padStart(8, '0')
}