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
    ): CompletableFuture<Result>

    sealed class Result {
        data class Normal(val value: Any) : Result()
        data class Exception(val ex: Throwable) : Result()
    }

    class SingleThreadTracingMethodInvoker(val exec: Executor) : TracingMethodInvoker() {
        override fun invoke(
            tracer: Tracer,
            mh: MethodHandle,
            vararg params: Any
        ) = CompletableFuture.supplyAsync(Supplier {
            try {
                Result.Normal(JavaInvoker.invoke(tracer, mh, *params))
            } catch (e: Throwable) {
                Result.Exception(e)
            }
        }, exec)
    }
}