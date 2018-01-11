#include <iostream>
#include <jvmti.h>

#include "agent_api.h"

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    return agent_symbols()->kotlin.root.jwp.agent.agentOnLoad(
        (uintptr_t) vm,
        (uintptr_t) options,
        (uintptr_t) reserved);
}