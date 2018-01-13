package jwp.fuzz

class JvmtiTracer : Tracer {

    override fun startTrace(thread: Thread) {
        synchronized(globalTracerStartStopLocker) { internalStartTrace(thread) }
    }

    override fun stopTrace(thread: Thread) {
        val branches = synchronized(globalTracerStartStopLocker) { internalStopTrace(thread) }
        println("DEBUG: Unique branches: " + (branches.size / 5))
    }

    external fun internalStartTrace(thread: Thread)
    external fun internalStopTrace(thread: Thread): LongArray

    companion object {
        private val globalTracerStartStopLocker = Any()
    }
}