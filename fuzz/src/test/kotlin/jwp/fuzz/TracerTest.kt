package jwp.fuzz

import kotlin.test.Test

class TracerTest : TestBase() {

    @Test
    fun testSimpleJvmtiTracer() {
        // Setup tracer and run
        val tracer = Tracer.JvmtiTracer()
        val thread = Thread.currentThread()
        tracer.startTrace(thread)
        stringLen("Trace this!")
        val result = tracer.stopTrace(thread)

        // Confirm there is (only one of and back):
        //  testSomething -> stringLen(0) and stringLen -> testSomething
        //  stringLen(x) -> String::length(0) and String::length -> stringLen(x)
        fun assertSingleBranch(fromClass: Class<*>, fromMethod: String, toClass: Class<*>, toMethod: String) =
            result.branchesWithResolvedMethods.single {
                it.fromMethodDeclaringClass == fromClass && it.fromMethodName == fromMethod &&
                    it.toMethodDeclaringClass == toClass && it.toMethodName == toMethod
            }
        assertSingleBranch(javaClass, "testSimpleJvmtiTracer", javaClass, "stringLen")
        assertSingleBranch(javaClass, "stringLen", javaClass, "testSimpleJvmtiTracer")
        assertSingleBranch(javaClass, "stringLen", String::class.java, "length")
        assertSingleBranch(String::class.java, "length", javaClass, "stringLen")
    }

    private fun stringLen(str: String) = str.length
}