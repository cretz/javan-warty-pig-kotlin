package jwp.fuzz

import kotlin.test.DefaultAsserter

abstract class TestBase {

    private val asserter = DefaultAsserter()

    fun assertEquals(expected: Any?, actual: Any?) = asserter.assertEquals(null, expected, actual)
}