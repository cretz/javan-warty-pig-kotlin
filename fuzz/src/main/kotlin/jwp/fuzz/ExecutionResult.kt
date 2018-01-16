package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.*

data class ExecutionResult(
    val mh: MethodHandle,
    val params: List<*>,
    val traceResult: TraceResult,
    val invokeResult: InvokeResult,
    val nanoTime: Long
) {
    sealed class InvokeResult {
        data class Ok(val value: Any?) : InvokeResult()
        data class Failure(val ex: Throwable) : InvokeResult()
    }
}