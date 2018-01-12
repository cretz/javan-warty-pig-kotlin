package jwp.fuzz

interface Tracer {
    fun startTrace(thread: Thread)
    fun stopTrace(thread: Thread)
}