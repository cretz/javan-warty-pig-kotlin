package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

abstract class TracingMethodInvoker {
    abstract fun invoke(conf: Config, vararg params: Any?): CompletableFuture<ExecutionResult>

    data class Config(
        val tracer: Tracer,
        val mh: MethodHandle,
        val branchClassExcluder: Fuzzer.BranchClassExcluder?
    )

    class SingleThreadTracingMethodInvoker(val exec: Executor) : TracingMethodInvoker() {
        override fun invoke(conf: Config, vararg params: Any?) = CompletableFuture.supplyAsync(Supplier {
            val beginNs = System.nanoTime()
            val traceComplete = JavaUtils.invokeTraced(conf.tracer, conf.mh, *params)
            val endNs = System.nanoTime()
            val invokeResult =
                if (traceComplete.exception == null) ExecutionResult.InvokeResult.Ok(traceComplete.result)
                else ExecutionResult.InvokeResult.Failure(traceComplete.exception)
            ExecutionResult(
                conf.mh,
                params.toList(),
                traceComplete.tracerResult.let { tracerResult ->
                    if (conf.branchClassExcluder == null) tracerResult
                    else tracerResult.filtered(conf.branchClassExcluder)
                },
                invokeResult,
                endNs - beginNs
            )
        }, exec)
    }
}