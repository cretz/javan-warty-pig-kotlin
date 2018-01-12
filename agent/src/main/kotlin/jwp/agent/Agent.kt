@file:Suppress("UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
package jwp.agent

import jvmti.*
import kotlinx.cinterop.*

typealias RawVmPtr = Long
typealias RawJniEnvPtr = Long

var jvmtiEnvRef: jvmtiEnvVar? = null
var stepCount = 0

fun agentOnLoad(vmPtr: RawVmPtr, optionsPtr: Long, reservedPtr: Long): jint =
    init(vmPtr)?.let { println("Error: $it"); JNI_ERR } ?: JNI_OK

fun agentOnUnload(vmPtr: RawVmPtr) {
    jvmtiEnvRef = null
}

fun startTrace(jniEnvPtr: RawJniEnvPtr, thisPtr: Long, threadPtr: Long) {
    println("Starting trace")
    stepCount = 0
    val env = jvmtiEnvRef ?: run { println("No env"); return }
    env.setEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, threadPtr.toCPointer()).
        also { if (it != JVMTI_ERROR_NONE) println("Enable event failure code: $it") }
}

fun stopTrace(jniEnvPtr: Long, thisPtr: Long, threadPtr: Long) {
    println("Stopping trace")
    val env = jvmtiEnvRef ?: run { println("No env"); return }
    env.setEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, threadPtr.toCPointer()).
        also { if (it != JVMTI_ERROR_NONE) println("Disable event failure code: $it") }
    println("Number of insns: $stepCount")
    stepCount = 0
}

internal fun init(vmPtr: RawVmPtr): String? = memScoped {
    // Get the JVMTI environment
    val env = vmPtr.vm?.getEnv() ?: return "No environment"
    jvmtiEnvRef = env

    // Add some capabilities
    val caps = alloc<jvmtiCapabilities>()
    caps.can_generate_single_step_events = 1
    env.addCapabilities(caps.ptr).
        also { if (it != JVMTI_ERROR_NONE) return "Add caps failure code: $it" }

    // Add callback
    val callbacks = alloc<jvmtiEventCallbacks>()
    callbacks.SingleStep = staticCFunction { jvmtiEnvPtr, jniEnvPtr, thread, methodID, location ->
        stepCount++
    }
    env.setEventCallbacks(callbacks.ptr).
        also { if (it != JVMTI_ERROR_NONE) return "Set callbacks failure code: $it" }

    null
}

internal val RawVmPtr.vm: JavaVMVar? get() = toCPointer<JavaVMVar>()?.pointed
internal val JavaVMVar.functions: JNIInvokeInterface_? get() = value?.pointed
internal fun JavaVMVar.getEnv() = memScoped {
    val jvmtiEnvPtr = alloc<CPointerVar<jvmtiEnvVar>>()
    if (functions?.GetEnv?.invoke(ptr, jvmtiEnvPtr.ptr.reinterpret(), JVMTI_VERSION) != JNI_OK) return null
    jvmtiEnvPtr.pointed
}

internal val RawJniEnvPtr.jniEnv: JNIEnvVar? get() = toCPointer<JNIEnvVar>()?.pointed
internal val JNIEnvVar.functions: JNINativeInterface_? get() = value?.pointed
internal fun JNIEnvVar.getJavaVm() = memScoped {
    val javaVmPtr = alloc<CPointerVar<JavaVMVar>>()
    if (functions?.GetJavaVM?.invoke(ptr, javaVmPtr.ptr) != JNI_OK) return null
    javaVmPtr.pointed
}

internal val jvmtiEnvVar.functions: jvmtiInterface_1_? get() = value?.pointed
internal fun jvmtiEnvVar.addCapabilities(caps: CPointer<jvmtiCapabilities>) =
    functions?.AddCapabilities?.invoke(ptr, caps) ?: -1
internal fun jvmtiEnvVar.setEventCallbacks(callbacks: CPointer<jvmtiEventCallbacks>) =
    functions?.SetEventCallbacks?.invoke(ptr, callbacks, jvmtiEventCallbacks.size.toInt()) ?: -1
// XXX: Kotlin can't handle varargs functions
typealias EventNotificationModeFn =
    CPointer<CFunction<(CPointer<jvmtiEnvVar>?, jvmtiEventMode, jvmtiEvent, jthread?) -> jvmtiError>>
internal fun jvmtiEnvVar.setEventNotificationMode(
    mode: jvmtiEventMode,
    event: jvmtiEvent,
    thread: jthread?
): jvmtiError {
    val fn: EventNotificationModeFn? = functions?.SetEventNotificationMode?.reinterpret()
    return fn?.invoke(ptr, mode, event, thread) ?: -1
}