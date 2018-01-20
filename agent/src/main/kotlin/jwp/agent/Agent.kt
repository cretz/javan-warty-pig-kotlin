@file:Suppress("UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
package jwp.agent

import jvmti.*
import konan.internal.CName
import kotlinx.cinterop.*

// Need this global JNI env ref. Set once in init.
var jvmtiEnvRef: jvmtiEnvVar?
    get() = globalJvmtiEnv?.pointed
    set(value) { globalJvmtiEnv = value?.ptr }

// Initial load of the agent to initialize some things
@CName("Agent_OnLoad")
fun agentOnLoad(vmPtr: CPointer<JavaVMVar>?, optionsPtr: COpaquePointer?, reservedPtr: COpaquePointer?): jint =
    init(vmPtr)?.let { println("Error: $it"); JNI_ERR } ?: JNI_OK

@CName("Agent_OnAttach")
fun agentOnAttach(vmPtr: CPointer<JavaVMVar>?, optionsPtr: COpaquePointer?, reservedPtr: COpaquePointer): jint =
    init(vmPtr)?.let { println("Error: $it"); JNI_ERR } ?: JNI_OK

// Initialize the tracer, return error string on failure
private fun init(vmPtr: CPointer<JavaVMVar>?): String? = memScoped {
    // Get the JVMTI environment and set the global ref
    val env = vmPtr?.pointed?.getEnv() ?: return "No environment"
    jvmtiEnvRef = env

    // Add some capabilities
    val caps = alloc<jvmtiCapabilities>()
    caps.can_generate_single_step_events = 1
    caps.can_get_bytecodes = 1
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
@CName("Agent_OnUnload")
fun agentOnUnload(vmPtr: CPointer<JavaVMVar>?) {
    jvmtiEnvRef = null
}

// Called when trace is started on the thread. Expected to be globally synchronized along with stopTrace.
@CName("Java_jwp_fuzz_JavaUtils_startJvmtiTrace")
fun startTrace(jniEnvPtr: CPointer<JNIEnvVar>?, cls: jclass, thread: jthread) {
    val env = jvmtiEnvRef ?: error("No env")

    // Create the tracer state and put as a thread local
    env.setThreadLocalStorage(thread, StableRef.create(TracerState()).asCPointer()).also {
        require(it == JVMTI_ERROR_NONE) { "Set thread-local error code $it" }
    }

    // Enable stepping for this thread
    env.setEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thread).
            also { if (it != JVMTI_ERROR_NONE) println("Enable event failure code: $it") }
}

// Called when trace is stopped on the thread. Expected to be globally synchronized along with startTrace.
// This returns a JVM array of longs.
@CName("Java_jwp_fuzz_JavaUtils_stopJvmtiTrace")
fun stopTrace(jniEnvPtr: CPointer<JNIEnvVar>?, cls: jclass, thread: jthread): jlongArray = memScoped {
    val env = jvmtiEnvRef ?: error("No env")
    val jniEnv = jniEnvPtr?.pointed ?: error("No JNI env")

    // Disable stepping on this thread
    env.setEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread).
        also { if (it != JVMTI_ERROR_NONE) println("Disable event failure code: $it") }

    // Get the state
    val stateRef = env.getThreadLocalStorage(thread)?.asStableRef<TracerState>()
    val state = stateRef?.get() ?: error("No state")

    // Remove it from thread local storage
    env.setThreadLocalStorage(thread, null).also {
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
    return tuplesJniArray
}

// Take a long array, of which every 5 represents a branch, and set them as -1 if they
// are not really branches.
@CName("Java_jwp_fuzz_JavaUtils_markNonBranches")
fun markNonBranches(jniEnvPtr: CPointer<JNIEnvVar>?, cls: jclass, possible: jlongArray) {
    // Every 5 values of the long array is a branch tuple. The first and third are fromMethodId and
    // toMethodId respectively. The second and fourth are the to/from locations respectively. If the
    // method IDs are the different, we know it is a branch and move on. If they are the same, we need
    // to fetch the bytecodes of the method and see if they are really a branch. If they aren't, we
    // mark all 5 pieces as -1.
    val env = jvmtiEnvRef ?: error("No env")
    val jniEnv = jniEnvPtr?.pointed ?: error("No JNI env")

    // Grab and check length
    val len = jniEnv.getArrayLength(possible) ?: error("No length")
    if (len == 0) return
    require(len % 5 == 0) { "Unexpected array length" }

    // Since we're gonna inspect bytecodes, we need to know how the location is represented
    // for bytecodes.
    val locationFormat = env.getJLocationFormat()
    // For now we only accept the OpenJDK type, but this should be trivial to change. And
    // technically we could fall back to assuming every non-consecutive-location is a branch
    // but meh.
    require(locationFormat == JVMTI_JLOCATION_JVMBCI) { "Invalid internal location format" }

    // Cache the bytecodes when getting same method twice; we'll deallocate all at the end
    val bytecodeCache = HashMap<Long, Pair<Int, CPointer<ByteVar>>>()
    fun getBytecodes(methodId: Long) = bytecodeCache.getOrPut(methodId) {
        env.getBytecodes(methodId.toCPointer()!!) ?: error("No method")
    }

    // Grab all array pieces as native pieces
    val arrPieces = jniEnv.getLongArrayElements(possible) ?: error("No array")
    for (i in 0 until len step 5) {
        // Different from/to method? Then it must be a branch
        if (arrPieces[i] != arrPieces[i + 2]) continue
        // Same? Grab the bytecodes
        val (count, bytecodes) = getBytecodes(arrPieces[i])
        // Check whether location is within count. Technically should always be, but just being safe.
        if (arrPieces[i + 1] < count) {
            // Get the insn from the location
            val insn = Insn.byOpcode[bytecodes[arrPieces[i + 1]].toInt() and 0xFF]
            // If it's a branch...
            if (insn != null && insn.branches) {
                // If there is no fixed size (i.e. table/lookupswitch), we consider it a legit branch
                if (insn.fixedSize == null) continue
                // If the difference between this location and the next isn't the fixed byte size, it's a branch
                if (arrPieces[i + 1] + insn.fixedSize != arrPieces[i + 3]) continue
            }
        }
        // It's not a branch, empty it out
        arrPieces[i] = -1
        arrPieces[i + 1] = -1
        arrPieces[i + 2] = -1
        arrPieces[i + 3] = -1
        arrPieces[i + 4] = -1
    }
    // Dealloc all the fetched bytecodes
    bytecodeCache.forEach { (_, lenAndBytecodes) -> env.deallocate(lenAndBytecodes.second) }
    // Release the array, using param 0 to commit changes back to original array
    jniEnv.releaseLongArrayElements(possible, arrPieces, 0);
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

@CName("Java_jwp_fuzz_JavaUtils_methodName")
fun methodName(jniEnvPtr: CPointer<JNIEnvVar>?, cls: jclass, methodIdPtr: jlong): jstring? {
    val env = jvmtiEnvRef ?: error("No env")
    val jniEnv = jniEnvPtr?.pointed ?: error("No JNI env")
    return env.getMethodNameRaw(methodIdPtr.toCPointer()!!)?.let { namePtr ->
        jniEnv.newStringUtf(namePtr).also { env.deallocate(namePtr) }
    }
}

@CName("Java_jwp_fuzz_JavaUtils_declaringClass")
fun declaringClass(jniEnvPtr: CPointer<JNIEnvVar>?, cls: jclass, methodIdPtr: jlong): jclass? {
    val env = jvmtiEnvRef ?: error("No env")
    return env.getMethodDeclaringClass(methodIdPtr.toCPointer()!!)
}