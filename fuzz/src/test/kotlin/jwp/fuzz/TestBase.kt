package jwp.fuzz

import kotlin.test.DefaultAsserter

abstract class TestBase {

    private val asserter = DefaultAsserter()

    val debug = false

    fun assertEquals(expected: Any?, actual: Any?) = asserter.assertEquals(null, expected, actual)
    fun assertTrue(actual: Boolean) = asserter.assertTrue(null, actual)
}