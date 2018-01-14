package jwp.fuzz

interface Tracer {
    fun startTrace(thread: Thread)
    fun stopTrace(thread: Thread): TraceResult

    class JvmtiTracer : Tracer {
        override fun startTrace(thread: Thread) = JavaUtils.startJvmtiTrace(thread)

        override fun stopTrace(thread: Thread) = TraceResult.LongArrayBranches(JavaUtils.stopJvmtiTrace(thread))
    }
}