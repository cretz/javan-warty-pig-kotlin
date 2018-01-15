package jwp.fuzz

import java.lang.invoke.MethodHandle

class ExecutionResult(
    val mh: MethodHandle,
    val params: Array<*>,
    val traceResult: TraceResult,
    val invokeResult: InvokeResult
) {
    sealed class InvokeResult {
        data class Ok(val value: Any?) : InvokeResult()
        data class Failure(val ex: Throwable) : InvokeResult()
    }
}