package jwp.fuzz

import jwp.fuzztest.TestMethods
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class FuzzerTest : TestBase() {

    @Test
    fun testSimpleFunction() {
        // Start a fuzzer while tracking unique branches
        val uniqTracker = Fuzzer.PostSubmissionHandler.TrackUniqueBranches()
        val fuzzer = Fuzzer(Fuzzer.Config(
            // We use a separate class here to avoid the default "jwp.fuzz." branch exclusion
            mh = MethodHandles.lookup().findStatic(
                TestMethods::class.java,
                "simpleMethod",
                MethodType.methodType(String::class.java, Int::class.java, Boolean::class.java)
            ),
            // XXX: Needed because https://github.com/JetBrains/kotlin-native/issues/1234
            invoker = TracingMethodInvoker.SingleThreadTracingMethodInvoker(currentThreadExecutorService()),
            postSubmissionHandler = uniqTracker
        ))
        fuzzer.fuzz()

        // Check the branches that we expect...
        val branches = uniqTracker.results.values
        if (debug) branches.forEach { result ->
            println("Params ${result.params.toList()} ran on unique path and returned ${result.invokeResult} " +
                    "in ${result.nanoTime / 1000000}ms")
            println("Hash ${result.traceResult.stableBranchesHash}, branches:")
            result.traceResult.branchesWithResolvedMethods.forEach { println("  $it") }
        }

        // Helpers for checking the branches
        fun expectUnique(param1: (Int) -> Boolean, param2: (Boolean) -> Boolean, result: String) =
            assertTrue(branches.any { execResult ->
                (execResult.invokeResult as? ExecutionResult.InvokeResult.Ok)?.value == result &&
                    param1(execResult.params[0] as Int) && param2(execResult.params[1] as Boolean)
            })
        val whoCares = { _: Any -> true }

        // Basically just validate the conditionals. Here is what the method looks like
        //    public static String simpleMethod(int foo, boolean bar) {
        //        if (foo == 2) return "two";
        //        if (foo >= 5 && foo <= 7 && bar) return "five to seven and bar";
        //        if (foo > 20 && !bar) return "over twenty and not bar";
        //        return "something else";
        //    }
        // Simple 2 check
        expectUnique({ it == 2 }, whoCares, "two")
        // In 5 to 7 and bar true
        expectUnique({ it in 5..7 }, { it }, "five to seven and bar")
        // In 5 to 7, but not bar is technically satisfying the first part of the if, so new branch
        expectUnique({ it in 5..7 }, { !it }, "something else")
        // Over 20, but not bar
        expectUnique({ it > 20 }, { !it }, "over twenty and not bar")
        // What about over 20 and bar? That's technically a new branch...
        expectUnique({ it > 20 }, { it }, "something else")
        // How about something that doesn't hit any of the branches? We need one that hits
        // both parts of the 5 and 7 range check...
        expectUnique({ it != 2 && it < 5 && it < 20 }, whoCares, "something else")
        expectUnique({ it != 2 && it > 7 && it < 20 }, whoCares, "something else")
        // So...7 branches
        assertEquals(7, branches.size)
    }

    // XXX: Needed because https://github.com/JetBrains/kotlin-native/issues/1234
    private fun currentThreadExecutorService(): ExecutorService {
        val callerRunsPolicy = ThreadPoolExecutor.CallerRunsPolicy()
        return object : ThreadPoolExecutor(0, 1, 0L, TimeUnit.SECONDS, SynchronousQueue(), callerRunsPolicy) {
            override fun execute(command: Runnable) {
                callerRunsPolicy.rejectedExecution(command, this)
            }
        }
    }
}