package jwp.fuzz

import java.io.Closeable
import java.lang.invoke.WrongMethodTypeException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.schedule

open class Fuzzer(val conf: Config) {

    fun fuzzFor(time: Long, unit: TimeUnit) {
        val stopper = AtomicBoolean()
        Timer().schedule(unit.toMillis(time)) { stopper.set(true) }
        fuzz(stopper)
    }

    fun fuzz(stopper: AtomicBoolean = AtomicBoolean()) {
        try {
            // Just go over every param set, invoking...
            var first = true
            val invokerConf = TracingMethodInvoker.Config(conf.tracer, conf.method)
            val stopExRef = AtomicReference<Throwable>()
            for (paramSet in conf.params) {
                if (stopper.get()) break
                stopExRef.get()?.also { throw it }
                var fut: CompletableFuture<ExecutionResult>? = conf.invoker.invoke(invokerConf, *paramSet)
                // As a special case for the first run, we wait for completion and fail the
                // whole thing if it's the wrong method type.
                if (first) {
                    first = false
                    fut!!.get().let { execRes ->
                        if (execRes.invokeResult is ExecutionResult.InvokeResult.Failure &&
                                execRes.invokeResult.ex is WrongMethodTypeException) {
                            throw FuzzException.FirstRunFailed(execRes.invokeResult.ex)
                        }
                    }
                }
                if (conf.postSubmissionHandler != null) fut = conf.postSubmissionHandler.postSubmission(conf, fut!!)
                if (conf.params is ParamProvider.WithFeedback) fut?.thenAccept(conf.params::onResult)
                if (conf.stopOnFutureFailure) fut?.whenComplete { _, ex -> ex?.also(stopExRef::set) }
            }
        } finally {
            // Shutdown and wait for a really long time...
            conf.invoker.shutdownAndWaitUntilComplete(1000, TimeUnit.DAYS)
            conf.params.close()
        }
    }

    sealed class FuzzException(msg: String, cause: Throwable) : RuntimeException(msg, cause) {
        class FirstRunFailed(cause: WrongMethodTypeException) : FuzzException("First run failed to start", cause)
    }

    data class Config(
        val method: Method,
        val paramGenConf: (Int) -> ParamGen.Config = { ParamGen.Config() },
        val params: ParamProvider = ParamProvider.Suggested(
            method.parameterTypes.mapIndexed { index, cls -> ParamGen.suggested(cls, paramGenConf(index)) }
        ),
        val postSubmissionHandler: PostSubmissionHandler? = null,
        // We default to just a single-thread, single-item queue
        val invoker: TracingMethodInvoker = TracingMethodInvoker.ExecutorServiceInvoker(
            TracingMethodInvoker.CurrentThreadExecutorService()
        ),
        val dictionary: List<ByteArray> = emptyList(),
        val tracer: Tracer = Tracer.JvmtiTracer(),
        val stopOnFutureFailure: Boolean = true
    )

    interface PostSubmissionHandler : Closeable {
        fun postSubmission(
            conf: Fuzzer.Config,
            future: CompletableFuture<ExecutionResult>
        ): CompletableFuture<ExecutionResult>?

        abstract class TrackUniqueBranches(
            val includeHitCounts: Boolean = true,
            // Must be thread safe
            val backingSet: MutableSet<Int> = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
        ) : PostSubmissionHandler {
            @Volatile
            var totalExecutions = 0L
                private set

            override fun postSubmission(conf: Config, future: CompletableFuture<ExecutionResult>) = future.thenApply {
                totalExecutions++
                it.apply {
                    val hash =
                        if (includeHitCounts) traceResult.stableBranchesHash
                        else traceResult.stableBranchesHash(false)
                    if (backingSet.add(hash)) onUnique(this)
                }
            }

            abstract fun onUnique(result: ExecutionResult)

            // Does nothing
            override fun close() { }
        }
    }
}