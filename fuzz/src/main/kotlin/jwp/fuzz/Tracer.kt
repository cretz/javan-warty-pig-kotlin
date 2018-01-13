package jwp.fuzz

import java.util.stream.Stream

interface Tracer {
    fun startTrace(thread: Thread)
    fun stopTrace(thread: Thread)

    fun branches(): Stream<Branch>
    fun methodName(methodId: Long): String?
    fun declaringClass(methodId: Long): Class<*>?

    data class Branch(
        val fromMethodId: Long,
        val fromLocation: Long,
        val toMethodId: Long,
        val toLocation: Long,
        val hits: Int
    )
}