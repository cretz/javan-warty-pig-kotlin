@file:Suppress("UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
package jwp.agent

import jvmti.*
import kotlinx.cinterop.*

// Need this global JNI env ref. Set once in init.
private var jvmtiEnvRef: jvmtiEnvVar? = null

// Initial load of the agent to initialize some things
fun agentOnLoad(vmPtr: RawVmPtr, optionsPtr: Long, reservedPtr: Long): jint =
    init(vmPtr)?.let { println("Error: $it"); JNI_ERR } ?: JNI_OK

// Initialize the tracer, return error string on failure
private fun init(vmPtr: RawVmPtr): String? = memScoped {
    // Get the JVMTI environment and set the global ref
    val env = vmPtr.vm?.getEnv() ?: return "No environment"
    jvmtiEnvRef = env

    // Add some capabilities
    val caps = alloc<jvmtiCapabilities>()
    caps.can_generate_single_step_events = 1
    env.addCapabilities(caps.ptr).
            also { if (it != JVMTI_ERROR_NONE) return "Add caps failure code: $it" }

    // Add the tracing callback
    val callbacks = alloc<jvmtiEventCallbacks>()
    callbacks.SingleStep = staticCFunction(::traceStep)
    env.setEventCallbacks(callbacks.ptr).
            also { if (it != JVMTI_ERROR_NONE) return "Set callbacks failure code: $it" }

    null
}

// Final function run on this agent
fun agentOnUnload(vmPtr: RawVmPtr) {
    jvmtiEnvRef = null
}

// Called when trace is started on the thread. Expected to be globally synchronized along with stopTrace.
fun startTrace(jniEnvPtr: RawJniEnvPtr, threadPtr: Long) {
    val env = jvmtiEnvRef ?: error("No env")

    // Create the tracer state and put as a thread local
    env.setThreadLocalStorage(threadPtr.toCPointer()!!, StableRef.create(TracerState()).asCPointer()).also {
        require(it == JVMTI_ERROR_NONE) { "Set thread-local error code $it" }
    }

    // Enable stepping for this thread
    env.setEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, threadPtr.toCPointer()).
        also { if (it != JVMTI_ERROR_NONE) println("Enable event failure code: $it") }
}

// Called when trace is stopped on the thread. Expected to be globally synchronized along with startTrace.
// This returns a pointer to the JVM array of longs.
fun stopTrace(jniEnvPtr: RawJniEnvPtr, threadPtr: Long): Long = memScoped {
    val env = jvmtiEnvRef ?: error("No env")
    val jniEnv = jniEnvPtr.jniEnv ?: error("No JNI env")

    // Disable stepping on this thread
    env.setEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, threadPtr.toCPointer()).
        also { if (it != JVMTI_ERROR_NONE) println("Disable event failure code: $it") }

    // Get the state
    val stateRef = env.getThreadLocalStorage(threadPtr.toCPointer()!!)?.asStableRef<TracerState>()
    val state = stateRef?.get() ?: error("No state")

    // Remove it from thread local storage
    env.setThreadLocalStorage(threadPtr.toCPointer()!!, null).also {
        require(it == JVMTI_ERROR_NONE) { "Set thread-local error code $it" }
    }

    // Create native array w/ all tuple info
    val tuplesNativeArray = allocArray<jlongVar>(state.branchTuples.size * 5)
    var index = -1
    state.branchTuples.forEach { (branchTuple, hits) ->
        tuplesNativeArray[++index] = branchTuple.fromMethodId
        tuplesNativeArray[++index] = branchTuple.fromLocation
        tuplesNativeArray[++index] = branchTuple.toMethodId
        tuplesNativeArray[++index] = branchTuple.toLocation
        tuplesNativeArray[++index] = hits.toLong()
    }
    // Put in JVM array
    val tuplesJniArray = jniEnv.newLongArray(state.branchTuples.size * 5) ?: error("Cannot create array")
    jniEnv.setLongArrayRegion(tuplesJniArray, 0, state.branchTuples.size * 5, tuplesNativeArray)

    // Dispose the stable ref
    stateRef.dispose()

    // Return the pointer
    return tuplesJniArray.toLong()
}

// Called on each step
private fun traceStep(
    jvmtiEnvVar: CPointer<jvmtiEnvVar>?,
    jniEnvVar: CPointer<JNIEnvVar>?,
    thread: jthread?,
    methodId: jmethodID?,
    location: jlocation
) {
    val env = jvmtiEnvRef ?: error("No env")

    // Get the thread-local state and mark the step
    env.getThreadLocalStorage(thread!!)?.let { statePtr ->
        // Mark the step
        statePtr.asStableRef<TracerState>().get().step(methodId!!, location)
    }
}

fun methodName(jniEnvPtr: RawJniEnvPtr, methodIdPtr: Long): Long {
    val env = jvmtiEnvRef ?: error("No env")
    val jniEnv = jniEnvPtr.jniEnv ?: error("No JNI env")
    return env.getMethodNameRaw(methodIdPtr.toCPointer()!!)?.let { namePtr ->
        jniEnv.newStringUtf(namePtr).also { env.deallocate(namePtr) }.toLong()
    } ?: 0
}

fun declaringClass(jniEnvPtr: RawJniEnvPtr, methodIdPtr: Long): Long {
    val env = jvmtiEnvRef ?: error("No env")
    return env.getMethodDeclaringClass(methodIdPtr.toCPointer()!!).toLong()
}