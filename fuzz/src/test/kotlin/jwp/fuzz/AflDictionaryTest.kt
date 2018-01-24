package jwp.fuzz

import kotlin.test.Test

class AflDictionaryTest : TestBase() {
    @Test
    fun testSimpleDictionary() {
        // Minor changes to test edge cases
        val dictStr = """
            #
            # AFL dictionary for GIF images
            # -----------------------------
            #
            # Created by Michal Zalewski <lcamtuf@google.com>
            #
            header_87a="87a"
            header_89a@35="89a"
            header_gif="GIF"

            marker_2c=","
            marker_3b =  ";"

            section_2101="!\x01\x12"
            section_21f9="!\xf9\x04"
            section_21fe="!\xfe"
            section_21ff="!\xff\x11"
            """
        val dict = AflDictionary.read(dictStr.lines())
        assertEquals(9, dict.entries.size)
        assertEquals(AflDictionary.Entry("header_89a", 35, "89a".toByteArray()), dict.entries[1])
        assertEquals(AflDictionary.Entry("marker_3b", null, ";".toByteArray()), dict.entries[4])
        assertEquals(AflDictionary.Entry("section_21f9", null, byteArrayOf(33, 0xF9 - 128, 0x04 - 128)),
            dict.entries[6])
    }
}