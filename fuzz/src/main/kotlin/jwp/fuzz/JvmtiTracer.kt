package jwp.fuzz

class JvmtiTracer : Tracer {
    override external fun startTrace(thread: Thread)
    override external fun stopTrace(thread: Thread)
}