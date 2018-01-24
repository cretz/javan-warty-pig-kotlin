package jwp.examples.htmlsan

import jwp.fuzz.*
import org.mapdb.DB
import org.mapdb.DBMaker
import org.owasp.html.HtmlPolicyBuilder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        // TODO: persistence
        // Create the fuzzer
        println("Creating fuzzer")
        val fuzzer = Fuzzer(Fuzzer.Config(
            method = Main::class.java.getDeclaredMethod("sanitize", String::class.java),
            paramGenConf = {
                ParamGen.Config(
                    byteArrayConfig = ByteArrayParamGen.Config(
                        initialValues = listOf("<pre>test</pre>".toByteArray()),
                        // Use a dictionary file for this one
                        dictionary = AflDictionary.read(
                            javaClass.getResource("html_tags.dict").readText().lines()
                        ).values
                    )
                )
            },
            // The handler where we'll print out what we found
            postSubmissionHandler = object : Fuzzer.PostSubmissionHandler.TrackUniqueBranches(false) {
                @Synchronized
                override fun onUnique(result: ExecutionResult) {
                    println("New path for param '${result.rawParam(0)}', result: ${result.invokeResult} (exec #$totalExecutions) ${backingSet.size}")
                    (result.invokeResult as? ExecutionResult.InvokeResult.Failure)?.also { throw it.ex }
                }
            },
            // Multithreaded instead of default?
            invoker = TracingMethodInvoker.ExecutorServiceInvoker(
                ThreadPoolExecutor(5, 10, 30, TimeUnit.SECONDS,
                    ArrayBlockingQueue(500), ThreadPoolExecutor.CallerRunsPolicy())
            )
        ))
        println("Beginning fuzz")
        fuzzer.fuzz()
    }

    val doNothingPolicy = HtmlPolicyBuilder().toFactory()

    @JvmStatic
    fun sanitize(str: String): String = doNothingPolicy.sanitize(str)
}