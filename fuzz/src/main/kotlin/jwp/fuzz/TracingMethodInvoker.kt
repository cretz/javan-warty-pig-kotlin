package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

abstract class TracingMethodInvoker {
    abstract fun invoke(
        tracer: Tracer,
        mh: MethodHandle,
        vararg params: Any
    ): CompletableFuture<ExecutionResult>

    class SingleThreadTracingMethodInvoker(val exec: Executor) : TracingMethodInvoker() {
        override fun invoke(
            tracer: Tracer,
            mh: MethodHandle,
            vararg params: Any
        ) = CompletableFuture.supplyAsync(Supplier {
            JavaUtils.invokeTraced(tracer, mh, *params).let { traceComplete ->
                val invokeResult =
                    if (traceComplete.exception == null) ExecutionResult.InvokeResult.Ok(traceComplete.result)
                    else ExecutionResult.InvokeResult.Failure(traceComplete.exception)
                ExecutionResult(mh, params, traceComplete.tracerResult, invokeResult)
            }
        }, exec)
    }
}