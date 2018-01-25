package jwp.examples.htmlsan

import jwp.fuzz.*
import net.openhft.chronicle.queue.ChronicleQueueBuilder
import net.openhft.chronicle.set.ChronicleSetBuilder
import org.owasp.html.HtmlPolicyBuilder
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        // Create the fuzzer
        println("Creating fuzzer")
        val folder = File(".persist").absoluteFile.also { it.mkdirs() }
        val maxBranches = 300000L
        val fuzzer = Fuzzer(Fuzzer.Config(
            method = Main::class.java.getDeclaredMethod("sanitize", String::class.java),
            paramGenConf = {
                ParamGen.Config(
                    byteArrayConfig = ByteArrayParamGen.Config(
                        initialValues = listOf("<pre>test</pre>".toByteArray()),
                        // Use a dictionary file for this one
                        dictionary = AflDictionary.read(
                            javaClass.getResource("html_tags.dict").readText().lines()
                        ).values,
                        branchesHashCacheCreator = {
                            PersistChronicle.BranchesHashCache(ChronicleSetBuilder.of(Int::class.javaObjectType).
                                entries(maxBranches).
                                createOrRecoverPersistedTo(folder.resolve("htmlsan-hashcache")))
                        },
                        byteArrayInputQueueCreator = {
                            PersistFile.ByteArrayInputQueue(folder.resolve("htmlsan-inputqueue"))
                        }
                    )
                )
            },
            // The handler where we'll print out what we found and throw on any error
            postSubmissionHandler = object : PersistChronicle.TrackUniqueBranches(
                set = ChronicleSetBuilder.of(Int::class.javaObjectType).
                    entries(maxBranches).
                    createOrRecoverPersistedTo(folder.resolve("htmlsan-uniquebranches")),
                includeHitCounts = false
            ) {
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
        fuzzer.fuzzFor(90, TimeUnit.SECONDS)
        println("Fuzz complete")
    }

    val doNothingPolicy = HtmlPolicyBuilder().toFactory()

    @JvmStatic
    fun sanitize(str: String): String = doNothingPolicy.sanitize(str)
}