package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

abstract class TracingMethodInvoker {
    abstract fun invoke(
        mh: MethodHandle,
        params: List<*>,
        stepHandler: Natives.StepHandler
    ): CompletableFuture<Result>

    sealed class Result {
        data class Normal(val value: Any) : Result()
        data class Exception(val ex: Throwable) : Result()
    }

    class SingleThreadTracingMethodInvoker(val exec: Executor) : TracingMethodInvoker() {
        override fun invoke(
            mh: MethodHandle,
            params: List<*>,
            stepHandler: Natives.StepHandler
        ) = CompletableFuture.supplyAsync(Supplier {
            Natives.startTrace(Thread.currentThread(), stepHandler)
            try { Result.Normal(mh.invokeWithArguments(params)) }
            catch (e: Throwable) { Result.Exception(e) }
            finally { Natives.stopTrace(Thread.currentThread()) }
        }, exec)
    }
}