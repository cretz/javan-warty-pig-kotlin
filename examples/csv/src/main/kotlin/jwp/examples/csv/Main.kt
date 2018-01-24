package jwp.examples.csv

import com.opencsv.CSVReader
import jwp.fuzz.*
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val (srcPath, className) = if (args.size >= 2) args else arrayOf(null, "jwp.examples.csv.MainTest")
        // Create a test writer
        val testWriter = TestWriter.JUnit4(TestWriter.Config(className!!))
        println("Creating fuzzer")
        // Create the fuzzer
        val fuzzer = Fuzzer(Fuzzer.Config(
            // Static method for the parser
            method = Main::class.java.getDeclaredMethod("parseCsv", String::class.java),
            // Set the conf to pass in an initial value
            paramGenConf = {
                ParamGen.Config(
                    byteArrayConfig = ByteArrayParamGen.Config(initialValues = listOf(
                        "foo,bar\nbaz,\"qux,quux\"".toByteArray()
                    ))
                )
            },
            // The handler where we'll print out what we found
            postSubmissionHandler = object : Fuzzer.PostSubmissionHandler.TrackUniqueBranches(false) {
                @Synchronized
                override fun onUnique(result: ExecutionResult) {
                    println("New path for param '${result.rawParam(0)}', result: ${result.invokeResult}")
                    try { testWriter.append(result) } catch (e: Exception) { println("  Failed to write test: $e") }
                }
            },
            // Multithreaded instead of default?
            invoker = TracingMethodInvoker.ExecutorServiceInvoker(
                ThreadPoolExecutor(5, 10, 30, TimeUnit.SECONDS,
                        ArrayBlockingQueue(500), ThreadPoolExecutor.CallerRunsPolicy())
            )
        ))
        // Stop the fuzzer after 5 minutes
        val stopper = AtomicBoolean()
        Timer().schedule(5 * 60 * 1000) { stopper.set(true) }
        // Run the fuzzer
        println("Beginning fuzz")
        fuzzer.fuzz(stopper)
        println("Fuzzer stopped")
        if (args.size < 2) println("Code: " + StringBuilder().also(testWriter::flush)) else {
            val filePath = Paths.get(srcPath, className.replace('.', '/') + ".java")
            println("Writing to $filePath")
            Files.createDirectories(filePath.parent)
            OutputStreamWriter(Files.newOutputStream(filePath), Charsets.UTF_8).use(testWriter::flush)
        }
    }

    @JvmStatic
    fun parseCsv(str: String) = CSVReader(StringReader(str)).use(CSVReader::readAll).map(Array<String>::toList)
}