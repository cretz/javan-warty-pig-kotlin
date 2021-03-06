package jwp.fuzz

import jwp.fuzztest.TestMethods
import java.util.*
import kotlin.test.Test

class FuzzerTest : TestBase() {

    @Test
    fun testSimpleFunction() {
        // Start a fuzzer while tracking unique branches
        val branches = Collections.synchronizedList(ArrayList<ExecutionResult>())
        val fuzzer = Fuzzer(Fuzzer.Config(
            // We use a separate class here to avoid the default "jwp.fuzz." branch exclusion
            method = TestMethods::class.java.getDeclaredMethod("simpleMethod", Int::class.java, Boolean::class.java),
            postSubmissionHandler = object : Fuzzer.PostSubmissionHandler.TrackUniqueBranches() {
                override fun onUnique(result: ExecutionResult) { branches.add(result) }
            }
        ))
        fuzzer.fuzz()

        // Check the branches that we expect...
        if (debug) {
            branches.forEach { result ->
                println("Params ${result.params.toList()} ran on unique path and returned ${result.invokeResult} " +
                        "in ${result.nanoTime / 1000000}ms")
                println("Hash ${result.traceResult.stableBranchesHash}, branches:")
                result.traceResult.branchesWithResolvedMethods.forEach { println("  $it") }
            }
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
}