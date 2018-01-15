package jwp.fuzz

import java.lang.invoke.MethodHandle

class Fuzzer {

    data class Config(
        val mh: MethodHandle,
        val params: Iterable<Array<Any?>> = ParameterProvider.suggested(
            mh.type().parameterArray().map { TypeGen.suggested(it) }.toTypedArray()
        )
    )
}