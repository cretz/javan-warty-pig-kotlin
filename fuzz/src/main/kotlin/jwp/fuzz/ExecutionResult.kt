package jwp.fuzz

import java.lang.reflect.Method

data class ExecutionResult(
    val method: Method,
    val params: List<*>,
    val traceResult: TraceResult,
    val invokeResult: InvokeResult,
    val nanoTime: Long
) {
    fun rawParam(index: Int): Any? {
        require(index < params.size)
        val param = params[index]
        return if (param is ParamGen.ParamRef<*>) param.value else param
    }

    sealed class InvokeResult {
        data class Ok(val value: Any?) : InvokeResult()
        data class Failure(val ex: Throwable) : InvokeResult()
    }
}