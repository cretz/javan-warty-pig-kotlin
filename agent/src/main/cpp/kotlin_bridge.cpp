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

JNIEXPORT extern "C" void JNICALL Java_jwp_fuzz_JvmtiTracer_startTrace(JNIEnv *env, jobject obj, jthread thread) {
    agent_symbols()->kotlin.root.jwp.agent.startTrace(
        (uintptr_t) env,
        (uintptr_t) obj,
        (uintptr_t) thread);
}

JNIEXPORT extern "C" void JNICALL Java_jwp_fuzz_JvmtiTracer_stopTrace(JNIEnv *env, jobject obj, jthread thread) {
    agent_symbols()->kotlin.root.jwp.agent.stopTrace(
        (uintptr_t) env,
        (uintptr_t) obj,
        (uintptr_t) thread);
}