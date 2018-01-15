package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.lang.invoke.WrongMethodTypeException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class Fuzzer(val conf: Config) {

    fun fuzz() {
        // Just go over every param set, invoking...
        var first = true
        conf.params.forEach { paramSet ->
            var fut = conf.invoker.invoke(conf.tracer, conf.mh, *paramSet)
            // As a special case for the first run, we wait for completion and fail the
            // whole thing if it's the wrong method type.
            if (first) {
                first = false
                fut.get().let { execRes ->
                    if (execRes.invokeResult is ExecutionResult.InvokeResult.Failure &&
                            execRes.invokeResult.ex is WrongMethodTypeException) {
                        throw FuzzException.FirstRunFailed(execRes.invokeResult.ex)
                    }
                }
            }
            if (conf.onSubmission != null) fut = conf.onSubmission.apply(fut)
            if (conf.params is ParameterProvider.WithFeedback) fut.thenAccept(conf.params::onResult)
        }
    }

    sealed class FuzzException(msg: String, cause: Throwable) : RuntimeException(msg, cause) {
        class FirstRunFailed(cause: WrongMethodTypeException) : FuzzException("First run failed to start", cause)
    }

    data class Config(
        val mh: MethodHandle,
        val params: Iterable<Array<Any?>> = ParameterProvider.suggested(
            mh.type().parameterArray().map { TypeGen.suggested(it) }.toTypedArray()
        ),
        val onSubmission:
            java.util.function.Function<CompletableFuture<ExecutionResult>, CompletableFuture<ExecutionResult>>? = null,
        // We default to just a single-thread, single-item queue
        val invoker: TracingMethodInvoker = run {
            val exec = ThreadPoolExecutor(1, 2, 30,
                    TimeUnit.SECONDS, ArrayBlockingQueue(1), ThreadPoolExecutor.CallerRunsPolicy())
            TracingMethodInvoker.SingleThreadTracingMethodInvoker(exec)
        },
        val tracer: Tracer = Tracer.JvmtiTracer()
    )

    class TrackUniqueBranches(
        val classPrefixesToIgnore: Array<String>? = arrayOf("java.", "sun.", "jdk.internal.")
    ) : java.util.function.Function<CompletableFuture<ExecutionResult>, CompletableFuture<ExecutionResult>> {

        private val _resultMap = HashMap<Int, ExecutionResult>()
        val resultMap: Map<Int, ExecutionResult> get() = _resultMap

        override fun apply(t: CompletableFuture<ExecutionResult>) = t.thenApply { origResult ->
            var result = origResult
            // Filter out ignored branches
            if (classPrefixesToIgnore != null) {
                fun invalidClass(cls: Class<*>?) = cls != null && classPrefixesToIgnore.any { cls.name.startsWith(it) }
                result = result.copy(traceResult = result.traceResult.filtered(Predicate {
                    !invalidClass(it.fromMethodDeclaringClass) && !invalidClass(it.toMethodDeclaringClass)
                }))
            }
            _resultMap.putIfAbsent(result.traceResult.stableBranchesHash, result)
            result
        }
    }
}