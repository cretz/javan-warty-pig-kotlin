package jwp.fuzz

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.*

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

        val suggestedFixedClass: Map<Class<*>, () -> ParamGen<*>> by lazy {
            fun pair(cls: Class<*>?, fn: () -> ParamGen<*>) = cls!! to fn
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
        val suggestedConfClass: Map<Class<*>, (Config) -> ParamGen<*>> by lazy {
            fun pair(cls: Class<*>?, fn: (Config) -> ParamGen<*>) = cls!! to fn
            mapOf(
                pair(ByteArray::class.java, ::suggestedByteArray),
                pair(String::class.java, ::suggestedString)
            )
        }

        fun suggestedByteArray(conf: Config) = suggestedByteArray(conf.byteArrayConfig)
        fun suggestedByteArray(conf: ByteArrayParamGen.Config) = ByteArrayParamGen(conf)

        fun suggestedByteBuffer(conf: Config) =
            suggestedByteArray(conf).genMap { it.map { ByteBuffer.wrap(it) } }

        fun suggestedCharBuffer(conf: Config) = conf.stringCharset.newDecoder().
                onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE).let { cd ->
            suggestedByteBuffer(conf).genMapNotNull {
                try { it.map(cd::decode) } catch (e: CharacterCodingException) { null }
            }
        }

        fun suggestedString(conf: Config) = suggestedCharBuffer(conf).genMap { it.map(CharBuffer::toString) }

        @Suppress("UNCHECKED_CAST")
        fun suggested(cls: Class<*>, conf: Config = Config()): ParamGen<*> = (
            suggestedFixedClass[cls]?.invoke() ?:
                suggestedConfClass[cls]?.invoke(conf) ?:
                error("No suggested param gen found for class $cls")
        )
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

    abstract class WithFeedbackAndCloseable<T> : ParamGen<T>, WithFeedback, Closeable {
        fun <R> genMap(fn: (T) -> R): WithFeedbackAndCloseable<R> = let { self ->
            object : WithFeedbackAndCloseable<R>() {
                override fun onResult(result: ExecutionResult, myParamIndex: Int) = self.onResult(result, myParamIndex)
                override fun close() = self.close()
                override fun iterator() = self.lazyMap(fn).iterator()
            }
        }

        fun <R : Any> genMapNotNull(fn: (T) -> R?): WithFeedbackAndCloseable<R> = let { self ->
            object : WithFeedbackAndCloseable<R>() {
                override fun onResult(result: ExecutionResult, myParamIndex: Int) = self.onResult(result, myParamIndex)
                override fun close() = self.close()
                override fun iterator() = self.lazyMapNotNull(fn).iterator()
            }
        }
    }

    open class Simple<T>(list: List<T>) : ParamGen<T>, List<T> by list

    interface ParamRef<T> {
        val value: T?
        fun <R> map(fn: (T) -> R): ParamRef<R>
    }
}