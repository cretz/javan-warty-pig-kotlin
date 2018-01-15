package jwp.fuzz

import kotlin.test.Test

class ParameterProviderTest : TestBase() {

    @Test
    fun testParameterProviderSimple() {
        val iter = ParameterProvider.allPermutations(arrayOf(
            listOf(1, 2),
            listOf("4"),
            listOf(6, 7, 8)
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