package jwp.examples.simple

import jwp.fuzz.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        val includeHitCounts = System.getProperty("noHitCounts") == null
        println("Creating fuzzer")
        // Create the fuzzer
        val fuzzer = Fuzzer(Fuzzer.Config(
            // Static method for our parser
            method = Main::class.java.getDeclaredMethod("parseNumber", String::class.java),
            // Set the conf to pass in an initial value
            paramGenConf = Function {
                ParamGen.Config(
                    byteArrayConfig = ByteArrayParamGen.Config(initialValues = listOf("+1.2".toByteArray()))
                )
            },
            // The handler where we'll print out what we found
            postSubmissionHandler = object : Fuzzer.PostSubmissionHandler.TrackUniqueBranches(includeHitCounts) {
                @Synchronized
                override fun onUnique(result: ExecutionResult) =
                    println("New path for param '${result.rawParam(0)}', result: ${result.invokeResult}")
            },
            // Multithreaded instead of default?
            invoker = TracingMethodInvoker.ExecutorServiceInvoker(
                ThreadPoolExecutor(5, 10, 30, TimeUnit.SECONDS,
                    ArrayBlockingQueue(500), ThreadPoolExecutor.CallerRunsPolicy())
            )
        ))
        // Run the fuzzer
        println("Beginning fuzz")
        fuzzer.fuzz()
    }

    data class Num(val neg: Boolean, val num: String, val frac: String)

    @JvmStatic
    fun parseNumber(str: String): Num {
        var index = 0
        var neg = false
        when (str[index]) {
            '+' -> index++
            '-' -> { neg = true; index++ }
        }
        var num = ""
        while (index < str.length && str[index].isDigit()) {
            num += str[index]
            index++
        }
        if (num.isEmpty()) throw NumberFormatException("No leading number(s)")
        var frac = ""
        if (index < str.length && str[index] == '.') {
            index++
            while (index < str.length && str[index].isDigit()) {
                frac += str[index]
                index++
            }
            if (frac.isEmpty()) throw NumberFormatException("Decimal without trailing number(s)")
        }
        if (index != str.length) throw NumberFormatException("Unknown char: " + str[index])
        return Num(neg, num, frac);
    }
}