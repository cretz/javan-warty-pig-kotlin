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
internal fun JNIEnvVar.getArrayLength(arr: jarray) =
    functions?.GetArrayLength?.invoke(ptr, arr)
internal fun JNIEnvVar.getJavaVm() = memScoped {
    val javaVmPtr = alloc<CPointerVar<JavaVMVar>>()
    if (functions?.GetJavaVM?.invoke(ptr, javaVmPtr.ptr) != JNI_OK) return null
    javaVmPtr.pointed
}
internal fun JNIEnvVar.getLongArrayElements(arr: jlongArray) =
    functions?.GetLongArrayElements?.invoke(ptr, arr, null)
internal fun JNIEnvVar.getMethodId(cls: jclass, name: String, sig: String) = memScoped {
    functions?.GetMethodID?.invoke(ptr, cls, name.cstr.getPointer(memScope), sig.cstr.getPointer(memScope))
}
internal fun JNIEnvVar.getObjectClass(obj: jobject) = functions?.GetObjectClass?.invoke(ptr, obj)
internal fun JNIEnvVar.newLongArray(size: jsize) = functions?.NewLongArray?.invoke(ptr, size)
internal fun JNIEnvVar.newStringUtf(bytes: CPointer<ByteVar>) = functions?.NewStringUTF?.invoke(ptr, bytes)
internal fun JNIEnvVar.releaseLongArrayElements(arr: jlongArray, vals: CPointer<jlongVar>, mode: jint) =
    functions?.ReleaseLongArrayElements?.invoke(ptr, arr, vals, mode)
internal fun JNIEnvVar.setLongArrayRegion(arr: jlongArray, start: jsize, len: jsize, buf: CPointer<jlongVar>) =
    functions?.SetLongArrayRegion?.invoke(ptr, arr, start, len, buf)

internal val jvmtiEnvVar.functions: jvmtiInterface_1_? get() = value?.pointed
internal fun jvmtiEnvVar.addCapabilities(caps: CPointer<jvmtiCapabilities>) =
    functions?.AddCapabilities?.invoke(ptr, caps) ?: -1
internal fun jvmtiEnvVar.deallocate(allocPtr: CPointer<*>) =
    functions?.Deallocate?.invoke(ptr, allocPtr.reinterpret())
internal fun jvmtiEnvVar.getBytecodes(meth: jmethodID): Pair<Int, CPointer<ByteVar>>? = memScoped {
    val count = alloc<jintVar>()
    val bytecodes = alloc<CPointerVar<ByteVar>>()
    if (functions?.GetBytecodes?.invoke(ptr, meth, count.ptr, bytecodes.ptr) != JVMTI_ERROR_NONE) return null
    return Pair(count.value, bytecodes.value!!)
}
internal fun jvmtiEnvVar.getClassSignatureRaw(cls: jclass): CPointer<ByteVar>? = memScoped {
    val sig = alloc<CPointerVar<ByteVar>>()
    if (functions?.GetClassSignature?.invoke(ptr, cls, sig.ptr, null) != JVMTI_ERROR_NONE) return null
    return sig.value
}
internal fun jvmtiEnvVar.getCurrentThread(): jthread? = memScoped {
    val thread = alloc<jthreadVar>()
    if (functions?.GetCurrentThread?.invoke(ptr, thread.ptr) != JVMTI_ERROR_NONE) return null
    thread.value
}
internal fun jvmtiEnvVar.getJLocationFormat(): jvmtiJlocationFormat? = memScoped {
    val format = alloc<jvmtiJlocationFormatVar>()
    if (functions?.GetJLocationFormat?.invoke(ptr, format.ptr) != JVMTI_ERROR_NONE) return null
    format.value
}
internal fun jvmtiEnvVar.getMethodDeclaringClass(methodId: jmethodID): jclass? = memScoped {
    val cls = alloc<jclassVar>()
    if (functions?.GetMethodDeclaringClass?.invoke(ptr, methodId, cls.ptr) != JVMTI_ERROR_NONE) return null
    cls.value
}
internal fun jvmtiEnvVar.getMethodName(methodId: jmethodID): String? =
    getMethodNameRaw(methodId)?.let { namePtr ->
        namePtr.toKString().also { deallocate(namePtr) }
    }
internal fun jvmtiEnvVar.getMethodNameRaw(methodId: jmethodID): CPointer<ByteVar>? = memScoped {
    val methodName = alloc<CPointerVar<ByteVar>>()
    if (functions?.GetMethodName?.invoke(ptr, methodId, methodName.ptr, null, null) != JVMTI_ERROR_NONE) return null
    methodName.value
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

// https://stackoverflow.com/questions/38032729/is-there-a-clever-way-to-determine-the-length-of-java-bytecode-instructions
//internal fun bytecodeLength()

internal enum class Insn(val opcode: Int, val fixedSize: Int? = 1, val branches: Boolean = false) {
    NOP(0),
    ACONST_NULL(1),
    ICONST_M1(2),
    ICONST_0(3),
    ICONST_1(4),
    ICONST_2(5),
    ICONST_3(6),
    ICONST_4(7),
    ICONST_5(8),
    LCONST_0(9),
    LCONST_1(10),
    FCONST_0(11),
    FCONST_1(12),
    FCONST_2(13),
    DCONST_0(14),
    DCONST_1(15),
    BIPUSH(16, fixedSize = 2),
    SIPUSH(17, fixedSize = 3),
    LDC(18, fixedSize = 2),
    LDC_W(19, fixedSize = 3),
    LDC2_W(20, fixedSize = 3),
    ILOAD(21, fixedSize = 2),
    LLOAD(22, fixedSize = 2),
    FLOAD(23, fixedSize = 2),
    DLOAD(24, fixedSize = 2),
    ALOAD(25, fixedSize = 2),
    ILOAD_0(26),
    ILOAD_1(27),
    ILOAD_2(28),
    ILOAD_3(29),
    LLOAD_0(30),
    LLOAD_1(31),
    LLOAD_2(32),
    LLOAD_3(33),
    FLOAD_0(34),
    FLOAD_1(35),
    FLOAD_2(36),
    FLOAD_3(37),
    DLOAD_0(38),
    DLOAD_1(39),
    DLOAD_2(40),
    DLOAD_3(41),
    ALOAD_0(42),
    ALOAD_1(43),
    ALOAD_2(44),
    ALOAD_3(45),
    IALOAD(46),
    LALOAD(47),
    FALOAD(48),
    DALOAD(49),
    AALOAD(50),
    BALOAD(51),
    CALOAD(52),
    SALOAD(53),
    ISTORE(54, fixedSize = 2),
    LSTORE(55, fixedSize = 2),
    FSTORE(56, fixedSize = 2),
    DSTORE(57, fixedSize = 2),
    ASTORE(58, fixedSize = 2),
    ISTORE_0(59),
    ISTORE_1(60),
    ISTORE_2(61),
    ISTORE_3(62),
    LSTORE_0(63),
    LSTORE_1(64),
    LSTORE_2(65),
    LSTORE_3(66),
    FSTORE_0(67),
    FSTORE_1(68),
    FSTORE_2(69),
    FSTORE_3(70),
    DSTORE_0(71),
    DSTORE_1(72),
    DSTORE_2(73),
    DSTORE_3(74),
    ASTORE_0(75),
    ASTORE_1(76),
    ASTORE_2(77),
    ASTORE_3(78),
    IASTORE(79),
    LASTORE(80),
    FASTORE(81),
    DASTORE(82),
    AASTORE(83),
    BASTORE(84),
    CASTORE(85),
    SASTORE(86),
    POP(87),
    POP2(88),
    DUP(89),
    DUP_X1(90),
    DUP_X2(91),
    DUP2(92),
    DUP2_X1(93),
    DUP2_X2(94),
    SWAP(95),
    IADD(96),
    LADD(97),
    FADD(98),
    DADD(99),
    ISUB(100),
    LSUB(101),
    FSUB(102),
    DSUB(103),
    IMUL(104),
    LMUL(105),
    FMUL(106),
    DMUL(107),
    IDIV(108),
    LDIV(109),
    FDIV(110),
    DDIV(111),
    IREM(112),
    LREM(113),
    FREM(114),
    DREM(115),
    INEG(116),
    LNEG(117),
    FNEG(118),
    DNEG(119),
    ISHL(120),
    LSHL(121),
    ISHR(122),
    LSHR(123),
    IUSHR(124),
    LUSHR(125),
    IAND(126),
    LAND(127),
    IOR(128),
    LOR(129),
    IXOR(130),
    LXOR(131),
    IINC(132, fixedSize = 3),
    I2L(133),
    I2F(134),
    I2D(135),
    L2I(136),
    L2F(137),
    L2D(138),
    F2I(139),
    F2L(140),
    F2D(141),
    D2I(142),
    D2L(143),
    D2F(144),
    I2B(145),
    I2C(146),
    I2S(147),
    LCMP(148),
    FCMPL(149),
    FCMPG(150),
    DCMPL(151),
    DCMPG(152),
    IFEQ(153, fixedSize = 3, branches = true),
    IFNE(154, fixedSize = 3, branches = true),
    IFLT(155, fixedSize = 3, branches = true),
    IFGE(156, fixedSize = 3, branches = true),
    IFGT(157, fixedSize = 3, branches = true),
    IFLE(158, fixedSize = 3, branches = true),
    IF_ICMPEQ(159, fixedSize = 3, branches = true),
    IF_ICMPNE(160, fixedSize = 3, branches = true),
    IF_ICMPLT(161, fixedSize = 3, branches = true),
    IF_ICMPGE(162, fixedSize = 3, branches = true),
    IF_ICMPGT(163, fixedSize = 3, branches = true),
    IF_ICMPLE(164, fixedSize = 3, branches = true),
    IF_ACMPEQ(165, fixedSize = 3, branches = true),
    IF_ACMPNE(166, fixedSize = 3, branches = true),
    GOTO(167, fixedSize = 3, branches = true),
    JSR(168, fixedSize = 3, branches = true),
    RET(169, fixedSize = 2, branches = true),
    TABLESWITCH(170, fixedSize = null, branches = true),
    LOOKUPSWITCH(171, fixedSize = null, branches = true),
    IRETURN(172, branches = true),
    LRETURN(173, branches = true),
    FRETURN(174, branches = true),
    DRETURN(175, branches = true),
    ARETURN(176, branches = true),
    RETURN(177, branches = true),
    GETSTATIC(178, fixedSize = 3),
    PUTSTATIC(179, fixedSize = 3),
    GETFIELD(180, fixedSize = 3),
    PUTFIELD(181, fixedSize = 3),
    INVOKEVIRTUAL(182, fixedSize = 3, branches = true),
    INVOKESPECIAL(183, fixedSize = 3, branches = true),
    INVOKESTATIC(184, fixedSize = 3, branches = true),
    INVOKEINTERFACE(185, fixedSize = 5, branches = true),
    INVOKEDYNAMIC(186, fixedSize = 5, branches = true),
    NEW(187, fixedSize = 3),
    NEWARRAY(188, fixedSize = 2),
    ANEWARRAY(189, fixedSize = 3),
    ARRAYLENGTH(190),
    ATHROW(191, branches = true),
    CHECKCAST(192, fixedSize = 3),
    INSTANCEOF(193, fixedSize = 3),
    MONITORENTER(194),
    MONITOREXIT(195),
    WIDE(196, fixedSize = null),
    MULTIANEWARRAY(197, fixedSize = 4),
    IFNULL(198, fixedSize = 3, branches = true),
    IFNONNULL(199, fixedSize = 3, branches = true),
    GOTO_W(200, fixedSize = 5, branches = true),
    JSR_W(201, fixedSize = 5, branches = true);

    companion object {
        val byOpcode = enumValues<Insn>().associateBy { it.opcode }
    }
}