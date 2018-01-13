package jwp.agent

import jvmti.*
import kotlinx.cinterop.*

typealias RawVmPtr = Long
typealias RawJniEnvPtr = Long

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
internal fun JNIEnvVar.getMethodId(cls: jclass, name: String, sig: String) = memScoped {
    functions?.GetMethodID?.invoke(ptr, cls, name.cstr.getPointer(memScope), sig.cstr.getPointer(memScope))
}
internal fun JNIEnvVar.getObjectClass(obj: jobject) = functions?.GetObjectClass?.invoke(ptr, obj)
internal fun JNIEnvVar.newLongArray(size: jsize) = functions?.NewLongArray?.invoke(ptr, size)
internal fun JNIEnvVar.setLongArrayRegion(arr: jlongArray, start: jsize, len: jsize, buf: CPointer<jlongVar>) =
    functions?.SetLongArrayRegion?.invoke(ptr, arr, start, len, buf)

internal val jvmtiEnvVar.functions: jvmtiInterface_1_? get() = value?.pointed
internal fun jvmtiEnvVar.addCapabilities(caps: CPointer<jvmtiCapabilities>) =
    functions?.AddCapabilities?.invoke(ptr, caps) ?: -1
internal fun jvmtiEnvVar.deallocate(allocPtr: CPointer<*>) =
    functions?.Deallocate?.invoke(ptr, allocPtr.reinterpret())
internal fun jvmtiEnvVar.getCurrentThread(): jthread? = memScoped {
    val thread = alloc<jthreadVar>()
    if (functions?.GetCurrentThread?.invoke(ptr, thread.ptr) != JVMTI_ERROR_NONE) return null
    thread.value
}
internal fun jvmtiEnvVar.getMethodName(methodId: jmethodID): String? = memScoped {
    val methodName = alloc<CPointerVar<ByteVar>>()
    if (functions?.GetMethodName?.invoke(ptr, methodId, methodName.ptr, null, null) != JVMTI_ERROR_NONE) return null
    methodName.value?.toKString().also { deallocate(methodName.value!!) }
}
internal fun jvmtiEnvVar.getThreadLocalStorage(thread: jthread): COpaquePointer? = memScoped {
    val pointerVar = alloc<COpaquePointerVar>()
    if (functions?.GetThreadLocalStorage?.invoke(ptr, thread, pointerVar.ptr) != JVMTI_ERROR_NONE) return null
    return pointerVar.value
}
internal fun jvmtiEnvVar.getThreadName(thread: jthread): String? = memScoped {
    val threadInfo = alloc<jvmtiThreadInfo>()
    if (functions?.GetThreadInfo?.invoke(ptr, thread, threadInfo.ptr) != JVMTI_ERROR_NONE) return null
    threadInfo.name?.toKString()?.also { deallocate(threadInfo.name!!) }
}
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
internal fun jvmtiEnvVar.setThreadLocalStorage(thread: jthread, data: COpaquePointer?) =
    functions?.SetThreadLocalStorage?.invoke(ptr, thread, data)