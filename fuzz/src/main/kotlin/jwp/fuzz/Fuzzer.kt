package jwp.fuzz

import java.lang.invoke.WrongMethodTypeException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

open class Fuzzer(val conf: Config) {

    fun fuzz(stopper: AtomicBoolean = AtomicBoolean()) {
        // Just go over every param set, invoking...
        var first = true
        val invokerConf = TracingMethodInvoker.Config(conf.tracer, conf.method)
        for (paramSet in conf.params) {
            if (stopper.get()) break
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
        }
        // Shutdown and wait for a really long time...
        conf.invoker.shutdownAndWaitUntilComplete(1000, TimeUnit.DAYS)
    }

    sealed class FuzzException(msg: String, cause: Throwable) : RuntimeException(msg, cause) {
        class FirstRunFailed(cause: WrongMethodTypeException) : FuzzException("First run failed to start", cause)
    }

    data class Config(
        val method: Method,
        val paramGenConf: Function<Int, ParamGen.Config> = Function { ParamGen.Config() },
        val params: ParamProvider = ParamProvider.Suggested(
            method.parameterTypes.mapIndexed { index, cls -> ParamGen.suggested(cls, paramGenConf.apply(index)) }
        ),
        val postSubmissionHandler: PostSubmissionHandler? = null,
        // We default to just a single-thread, single-item queue
        val invoker: TracingMethodInvoker = TracingMethodInvoker.ExecutorServiceInvoker(
            TracingMethodInvoker.CurrentThreadExecutorService()
        ),
        val dictionary: List<ByteArray> = emptyList(),
        val tracer: Tracer = Tracer.JvmtiTracer()
    )

    @FunctionalInterface
    interface PostSubmissionHandler {
        fun postSubmission(
            conf: Fuzzer.Config,
            future: CompletableFuture<ExecutionResult>
        ): CompletableFuture<ExecutionResult>?

        abstract class TrackUniqueBranches(val includeHitCounts: Boolean = true) : PostSubmissionHandler {
            private val uniqueBranchHashes = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

            @Volatile
            var totalExecutions = 0L
                private set

            override fun postSubmission(conf: Config, future: CompletableFuture<ExecutionResult>) = future.thenApply {
                totalExecutions++
                it.apply {
                    val hash =
                        if (includeHitCounts) traceResult.stableBranchesHash
                        else traceResult.stableBranchesHash(false)
                    if (uniqueBranchHashes.add(hash)) onUnique(this)
                }
            }

            abstract fun onUnique(result: ExecutionResult)
        }
    }
}