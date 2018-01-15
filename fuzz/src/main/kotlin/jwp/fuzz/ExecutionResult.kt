package jwp.fuzz

import java.lang.invoke.MethodHandle
import java.util.*

data class ExecutionResult(
    val mh: MethodHandle,
    val params: Array<*>,
    val traceResult: TraceResult,
    val invokeResult: InvokeResult
) {
    sealed class InvokeResult {
        data class Ok(val value: Any?) : InvokeResult()
        data class Failure(val ex: Throwable) : InvokeResult()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExecutionResult

        if (mh != other.mh) return false
        if (!Arrays.equals(params, other.params)) return false
        if (traceResult != other.traceResult) return false
        if (invokeResult != other.invokeResult) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mh.hashCode()
        result = 31 * result + Arrays.hashCode(params)
        result = 31 * result + traceResult.hashCode()
        result = 31 * result + invokeResult.hashCode()
        return result
    }
}