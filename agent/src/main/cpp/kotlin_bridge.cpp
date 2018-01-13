#include <iostream>
#include <jvmti.h>

#include "agent_api.h"

JNIEXPORT extern "C" jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    return agent_symbols()->kotlin.root.jwp.agent.agentOnLoad(
        (uintptr_t) vm,
        (uintptr_t) options,
        (uintptr_t) reserved);
}

JNIEXPORT extern "C" void JNICALL Agent_OnUnload(JavaVM *vm) {
    agent_symbols()->kotlin.root.jwp.agent.agentOnUnload((uintptr_t) vm);
}

JNIEXPORT extern "C" void JNICALL Java_jwp_fuzz_JvmtiTracer_internalStartTrace(JNIEnv *env, jobject obj, jthread thread) {
    agent_symbols()->kotlin.root.jwp.agent.startTrace(
        (uintptr_t) env,
        (uintptr_t) obj,
        (uintptr_t) thread);
}

JNIEXPORT extern "C" jlongArray JNICALL Java_jwp_fuzz_JvmtiTracer_internalStopTrace(JNIEnv *env, jobject obj, jthread thread) {
    return (jlongArray) (uintptr_t) agent_symbols()->kotlin.root.jwp.agent.stopTrace(
        (uintptr_t) env,
        (uintptr_t) obj,
        (uintptr_t) thread);
}

JNIEXPORT extern "C" jstring JNICALL Java_jwp_fuzz_JvmtiTracer_internalMethodName(JNIEnv *env, jobject obj, jlong methodIdPtr) {
    return (jstring) (uintptr_t) agent_symbols()->kotlin.root.jwp.agent.methodName((uintptr_t) env, methodIdPtr);
}

JNIEXPORT extern "C" jclass JNICALL Java_jwp_fuzz_JvmtiTracer_internalDeclaringClass(JNIEnv *env, jobject obj, jlong methodIdPtr) {
    return (jclass) (uintptr_t) agent_symbols()->kotlin.root.jwp.agent.declaringClass((uintptr_t) env, methodIdPtr);
}