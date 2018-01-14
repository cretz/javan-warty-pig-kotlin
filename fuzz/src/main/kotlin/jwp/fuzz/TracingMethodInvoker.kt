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
    ): CompletableFuture<TraceComplete>

    class SingleThreadTracingMethodInvoker(val exec: Executor) : TracingMethodInvoker() {
        override fun invoke(
            tracer: Tracer,
            mh: MethodHandle,
            vararg params: Any
        ) = CompletableFuture.supplyAsync(Supplier {
            JavaUtils.invokeTraced(tracer, mh, *params).let { traceComplete ->
                if (traceComplete.exception == null)
                    TraceComplete.Success(traceComplete.result, traceComplete.tracerResult)
                else
                    TraceComplete.Failure(traceComplete.exception, traceComplete.tracerResult)
            }
        }, exec)
    }

    sealed class TraceComplete {
        abstract val traceResult: TraceResult
        data class Success(val methodResult: Any, override val traceResult: TraceResult) : TraceComplete()
        data class Failure(val methodException: Throwable, override val traceResult: TraceResult) : TraceComplete()
    }
}