package jwp.fuzz

interface Tracer {
    // Actually, this thread is not nullable but we don't want Kotlin
    // checking it at runtime.
    fun startTrace(thread: Thread?)
    // Actually, this thread is not nullable but we don't want Kotlin
    // checking it at runtime.
    fun stopTrace(thread: Thread?): TraceResult

    open class JvmtiTracer : Tracer {
        override fun startTrace(thread: Thread?) = JavaUtils.startJvmtiTrace(thread)

        override fun stopTrace(thread: Thread?): TraceResult {
            val branches = JavaUtils.stopJvmtiTrace(thread)
            // This puts -1 in the place of non-branches
            JavaUtils.markNonBranches(branches)
            return TraceResult.LongArrayBranches(branches)
        }
    }
}