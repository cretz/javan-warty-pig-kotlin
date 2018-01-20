package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.lang.invoke.WrongMethodTypeException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

open class Fuzzer(val conf: Config) {

    fun fuzz() {
        // Just go over every param set, invoking...
        var first = true
        val invokerConf = TracingMethodInvoker.Config(conf.tracer, conf.mh)
        conf.params.forEach { paramSet ->
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
        val mh: MethodHandle,
        val paramGenConf: ParamGen.Config = ParamGen.Config(),
        val params: ParamProvider = ParamProvider.Suggested(
            mh.type().parameterArray().map { ParamGen.suggested(it, paramGenConf) }
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

        open class TrackUniqueBranches : PostSubmissionHandler {
            private val _uniqueBranchResults = LinkedHashMap<Int, ExecutionResult>()
            val uniqueBranchResults: Collection<ExecutionResult> get() = _uniqueBranchResults.values

            private var _totalExecutions = 0
            val totalExecutions: Int get() = _totalExecutions

            override fun postSubmission(
                conf: Config,
                future: CompletableFuture<ExecutionResult>
            ) = future.thenApply {
                _totalExecutions++
                it.apply { _uniqueBranchResults.putIfAbsent(traceResult.stableBranchesHash, this) }
            }
        }
    }
}