package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.concurrent.*
import java.util.function.Supplier

abstract class TracingMethodInvoker {
    // Note, some params may be ParamGen.ParamRef. They need to be deref'd to call the method,
    // but put in the execution result as they were passed in.
    abstract fun invoke(conf: Config, vararg params: Any?): CompletableFuture<ExecutionResult>

    abstract fun shutdownAndWaitUntilComplete(timeout: Long, timeUnit: TimeUnit): Boolean

    data class Config(
        val tracer: Tracer,
        val mh: MethodHandle
    )

    open class ExecutorServiceInvoker(val exec: ExecutorService) : TracingMethodInvoker() {
        override fun invoke(conf: Config, vararg params: Any?) = CompletableFuture.supplyAsync(Supplier {
            val actualParams = params.map { if (it is ParamGen.ParamRef<*>) it.value else it }.toTypedArray()
            val beginNs = System.nanoTime()
            val traceComplete = JavaUtils.invokeTraced(conf.tracer, conf.mh, *actualParams)
            val endNs = System.nanoTime()
            val invokeResult =
                if (traceComplete.exception == null) ExecutionResult.InvokeResult.Ok(traceComplete.result)
                else ExecutionResult.InvokeResult.Failure(traceComplete.exception)
            ExecutionResult(
                conf.mh,
                params.toList(),
                traceComplete.tracerResult,
                invokeResult,
                endNs - beginNs
            )
        }, exec)

        override fun shutdownAndWaitUntilComplete(timeout: Long, timeUnit: TimeUnit) =
            exec.shutdown().let { exec.awaitTermination(timeout, timeUnit) }
    }

    open class CurrentThreadExecutorService :
            ThreadPoolExecutor(0, 1, 0L, TimeUnit.SECONDS, SynchronousQueue(), ThreadPoolExecutor.CallerRunsPolicy()) {
        override fun execute(command: Runnable) = rejectedExecutionHandler.rejectedExecution(command, this)
    }
}