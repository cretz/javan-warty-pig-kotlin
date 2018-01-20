package jwp.fuzz

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.*
import java.util.function.Function
import java.util.function.Supplier

interface ParamGen<T> : Iterable<T> {

    companion object {
        val singleNull get() = Simple(listOf(null))
        val allBool get() = Simple(listOf(true, false))
        val allByte get() = Simple(((-128)..127).toList())
        fun <T : Enum<T>> allEnums(cls: Class<T>) = Simple(cls.enumConstants.toList())

        val interestingByte get() =
            Simple(listOf<Byte>(-128, -1, 0, 1, 16, 32, 64, 100, 127))
        val interestingShort get() =
            Simple(listOf<Short>(-32768, -129, 128, 255, 256, 512, 1000, 1024, 4096, 32767))
        val interestingInt get() =
            Simple(listOf(Int.MIN_VALUE, -100663046, -32769, 32768, 65535, 65536, 100663045, Int.MAX_VALUE))
        val interestingLong get() =
            Simple(listOf(Long.MIN_VALUE, Long.MAX_VALUE))
        val interestingFloat get() =
            Simple(listOf(java.lang.Float.MIN_NORMAL, Float.MIN_VALUE, Float.MAX_VALUE,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN))
        val interestingDouble get() =
            Simple(listOf(java.lang.Double.MIN_NORMAL, Double.MIN_VALUE, Double.MAX_VALUE,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN))

        val suggestedIntSimpleRange get() = Simple((-35..35).toList())
        val suggestedFloatSimpleRange get() = Simple(suggestedIntSimpleRange.map(Int::toFloat))

        val suggestedBoolean get() = allBool
        val suggestedChar: ParamGen<Char> get() = TODO()
        val suggestedByte get() = Simple(suggestedIntSimpleRange.map(Int::toByte) + interestingByte)
        val suggestedShort get() = Simple(suggestedByte.map(Byte::toShort) + interestingShort)
        val suggestedInt get() = Simple(suggestedShort.map(Short::toInt) + interestingInt)
        val suggestedLong get() = Simple(suggestedInt.map(Int::toLong) + interestingLong)
        val suggestedFloat get() = Simple(suggestedFloatSimpleRange + interestingFloat)
        val suggestedDouble get() = Simple(suggestedFloat.map(Float::toDouble) + interestingDouble)

        val suggestedFixedClass: Map<Class<*>, Supplier<ParamGen<*>>> by lazy {
            fun pair(cls: Class<*>?, fn: () -> ParamGen<*>) = cls!! to Supplier(fn)
            mapOf(
                // Primitives
                pair(Boolean::class.javaPrimitiveType, ::suggestedBoolean),
                pair(Boolean::class.javaObjectType, { Simple(singleNull + suggestedBoolean) }),
                pair(Byte::class.javaPrimitiveType, ::suggestedByte),
                pair(Byte::class.javaObjectType, { Simple(singleNull + suggestedByte) }),
                pair(Char::class.javaPrimitiveType, ::suggestedChar),
                pair(Char::class.javaObjectType, { Simple(singleNull + suggestedChar) }),
                pair(Short::class.javaPrimitiveType, ::suggestedShort),
                pair(Short::class.javaObjectType, { Simple(singleNull + suggestedShort) }),
                pair(Int::class.javaPrimitiveType, ::suggestedInt),
                pair(Int::class.javaObjectType, { Simple(singleNull + suggestedInt) }),
                pair(Long::class.javaPrimitiveType, ::suggestedLong),
                pair(Long::class.javaObjectType, { Simple(singleNull + suggestedLong) }),
                pair(Float::class.javaPrimitiveType, ::suggestedFloat),
                pair(Float::class.javaObjectType, { Simple(singleNull + suggestedFloat) }),
                pair(Double::class.javaPrimitiveType, ::suggestedDouble),
                pair(Double::class.javaObjectType, { Simple(singleNull + suggestedDouble) })
            )
        }
        val suggestedConfClass: Map<Class<*>, Function<Config, ParamGen<*>>> by lazy {
            fun pair(cls: Class<*>?, fn: (Config) -> ParamGen<*>) = cls!! to Function(fn)
            mapOf(
                pair(ByteArray::class.java, ::suggestedByteArray),
                pair(String::class.java, ::suggestedString)
            )
        }

        fun suggestedByteArray(conf: Config) = suggestedByteArray(conf.byteArrayConfig)
        fun suggestedByteArray(conf: ByteArrayParamGen.Config) = ByteArrayParamGen(conf)

        fun suggestedByteBuffer(conf: Config) =
            suggestedByteArray(conf).lazyMapParamGen { it.map { ByteBuffer.wrap(it) } }

        fun suggestedCharBuffer(conf: Config) = conf.stringCharset.newDecoder().
                onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE).let { cd ->
            suggestedByteBuffer(conf).lazyMapNotNullParamGen {
                try { it.map(cd::decode) } catch (e: CharacterCodingException) { null }
            }
        }

        fun suggestedString(conf: Config) = suggestedCharBuffer(conf).lazyMapParamGen { it.map(CharBuffer::toString) }

        @Suppress("UNCHECKED_CAST")
        fun suggested(cls: Class<*>, conf: Config = Config()): ParamGen<*> = (
            suggestedFixedClass[cls]?.get() ?:
                suggestedConfClass[cls]?.apply(conf) ?:
                error("No suggested param gen found for class $cls")
        )

        @Suppress("UNCHECKED_CAST")
        fun <T, R> lazyMapParamGen(gen: ParamGen<T>, fn: Function<T, R>): ParamGen<R> {
            val newGen = object : ParamGen<R> { override fun iterator() = gen.lazyMap(fn::apply).iterator() }
            if (gen is WithFeedback) return object : ParamGen<R> by newGen, WithFeedback by gen { }
            return newGen
        }

        @Suppress("UNCHECKED_CAST")
        fun <T, R : Any> lazyMapNotNullParamGen(gen: ParamGen<T>, fn: Function<T, R?>): ParamGen<R> {
            val newGen = object : ParamGen<R> { override fun iterator() = gen.lazyMapNotNull(fn::apply).iterator() }
            if (gen is WithFeedback) return object : ParamGen<R> by newGen, WithFeedback by gen { }
            return newGen
        }

        private fun <T, R> ParamGen<T>.lazyMapParamGen(fn: (T) -> R) =
            lazyMapParamGen(this, Function(fn))
        private fun <T, R : Any> ParamGen<T>.lazyMapNotNullParamGen(fn: (T) -> R?) =
            lazyMapNotNullParamGen(this, Function(fn))
    }

    data class Config(
        val stringCharset: Charset = StandardCharsets.UTF_8,
        val byteArrayConfig: ByteArrayParamGen.Config = ByteArrayParamGen.Config()
    )

    interface WithFeedback {
        // Note, this isn't always called. For example, if the param is later filtered before it is
        // given to the method, then there is no result because it wasn't run and this is not called.
        fun onResult(result: ExecutionResult, myParamIndex: Int)
    }

    open class Simple<T>(list: List<T>) : ParamGen<T>, List<T> by list

    interface ParamRef<T> {
        val value: T?
        fun <R> map(fn: (T) -> R): ParamRef<R>
    }
}