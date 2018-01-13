package jwp.fuzz

import kotlin.streams.asStream

class JvmtiTracer : Tracer {

    private var _branches: LongArray? = null

    override fun startTrace(thread: Thread) {
        synchronized(globalTracerStartStopLocker) { internalStartTrace(thread) }
    }

    override fun stopTrace(thread: Thread) {
        _branches = synchronized(globalTracerStartStopLocker) { internalStopTrace(thread) }
        println("DEBUG: Unique branches: " + _branches?.let { it.size / 5 })
        val methodNameMap = HashMap<Long, String>()
        fun cachedName(methodId: Long) = methodNameMap.getOrPut(methodId) {
            (declaringClass(methodId)?.name ?: "<unknown>") + "::" + (methodName(methodId) ?: "<unknown>")
        }
        branches().forEach { (fromMeth, fromLoc, toMeth, toLoc, hits) ->
            println("  From ${cachedName(fromMeth)}($fromLoc) to ${cachedName(toMeth)}($toLoc) - $hits hit(s)")
        }
    }

    private external fun internalStartTrace(thread: Thread)
    private external fun internalStopTrace(thread: Thread): LongArray

    override fun branches() = (_branches ?: error("Not completed")).asSequence().chunked(5) {
        (fromMeth, fromLoc, toMeth, toLoc, hits) -> Tracer.Branch(fromMeth, fromLoc, toMeth, toLoc, hits.toInt())
    }.asStream()

    override fun methodName(methodId: Long) = if (methodId < 0) null else internalMethodName(methodId)
    private external fun internalMethodName(methodId: Long): String?

    override fun declaringClass(methodId: Long) = if (methodId < 0) null else internalDeclaringClass(methodId)
    private external fun internalDeclaringClass(methodId: Long): Class<*>?

    companion object {
        private val globalTracerStartStopLocker = Any()

        // TODO: Waiting for 1.2.20 to be released, it is breaking because of https://youtrack.jetbrains.com/issue/KT-21146
        // @JvmStatic
        // private external fun internalMethodName(methodId: Long): String?
    }
}