package jwp.fuzz

import kotlin.test.Test

class ParamProviderTest : TestBase() {

    @Test
    fun testAllPermutations() {
        val iter = ParamProvider.AllPermutations(listOf(
            ParamGen.Simple(listOf(1, 2)),
            ParamGen.Simple(listOf("4")),
            ParamGen.Simple(listOf(6, 7, 8))
        ))
        assertEquals(listOf(
            listOf(1, "4", 6),
            listOf(1, "4", 7),
            listOf(1, "4", 8),
            listOf(2, "4", 6),
            listOf(2, "4", 7),
            listOf(2, "4", 8)
        ), iter.map { it.toList() }.toList())
    }
}