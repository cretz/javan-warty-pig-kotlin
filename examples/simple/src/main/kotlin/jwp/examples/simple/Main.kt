package jwp.examples.simple

import jwp.fuzz.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*
import java.util.concurrent.*
import java.util.function.Function

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        println("Creating fuzzer")
        // Create the fuzzer
        val fuzzer = Fuzzer(Fuzzer.Config(
            // Give the method handle to our static parser below
            mh = MethodHandles.lookup().findStatic(
                Main::class.java,
                "parseNumber",
                MethodType.methodType(Num::class.java, String::class.java)
            ),
            // Set the conf to pass in an initial value
            paramGenConf = Function {
                ParamGen.Config(
                    byteArrayConfig = ByteArrayParamGen.Config(initialValues = listOf("+1.2".toByteArray()))
                )
            },
            // The handler where we'll print out what we found
            postSubmissionHandler = object : Fuzzer.PostSubmissionHandler {
                val uniqueBranchHashes = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
                val printMutex = Object()
                override fun postSubmission(
                    conf: Fuzzer.Config,
                    future: CompletableFuture<ExecutionResult>
                ) = future.thenApply { res ->
                    if (uniqueBranchHashes.add(res.traceResult.stableBranchesHash)) synchronized(printMutex) {
                        println("Found new branch for parameter '${res.rawParam(0)}', result: ${res.invokeResult}")
                    }
                    res
                }
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