package jwp.fuzz

import java.util.stream.Collectors
import kotlin.test.Test

class JvmtiTracerTest : TestBase() {

    @Test
    fun testSimple() {
        // Setup tracer and run
        val tracer = JvmtiTracer()
        tracer.startTrace(Thread.currentThread())
        try {
            stringLen("Trace this!")
        } finally {
            tracer.stopTrace(Thread.currentThread())
        }

        // Confirm there is (only one of and back):
        //  testSomething -> stringLen(0) and stringLen -> testSomething
        //  stringLen(x) -> String::length(0) and String::length -> stringLen(x)
        val branches = tracer.branchesWithResolvedMethods().collect(Collectors.toList())
        fun assertSingleBranch(fromClass: Class<*>, fromMethod: String, toClass: Class<*>, toMethod: String) =
            branches.single {
                it.fromMethodDeclaringClass == fromClass && it.fromMethodName == fromMethod &&
                    it.toMethodDeclaringClass == toClass && it.toMethodName == toMethod
            }
        assertSingleBranch(javaClass, "testSimple", javaClass, "stringLen")
        assertSingleBranch(javaClass, "stringLen", javaClass, "testSimple")
        assertSingleBranch(javaClass, "stringLen", String::class.java, "length")
        assertSingleBranch(String::class.java, "length", javaClass, "stringLen")
    }

    private fun stringLen(str: String) = str.length
}