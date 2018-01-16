package jwp.fuzz

import java.util.function.Supplier

// A type gen is an iterable of nullable objects
object TypeGen {

    fun <T> suggested(cls: Class<T>): Iterable<T> {
        return suggestedFixedClass[cls]?.get() as? Iterable<T> ?: error("No supported type gen for $cls")
    }

    val suggestedFixedClass: Map<Class<*>, Supplier<Iterable<*>>> by lazy {
        fun pair(cls: Class<*>?, fn: () -> Iterable<*>) = cls!! to Supplier(fn)
        mapOf(
            // Primitives
            pair(Boolean::class.javaPrimitiveType, ::suggestedBoolean),
            pair(Boolean::class.javaObjectType, { suggestedBoolean cat nullGen }),
            pair(Byte::class.javaPrimitiveType, ::suggestedByte),
            pair(Byte::class.javaObjectType, { suggestedByte cat nullGen }),
            pair(Char::class.javaPrimitiveType, ::suggestedChar),
            pair(Char::class.javaObjectType, { suggestedChar cat nullGen }),
            pair(Short::class.javaPrimitiveType, ::suggestedShort),
            pair(Short::class.javaObjectType, { suggestedShort cat nullGen }),
            pair(Int::class.javaPrimitiveType, ::suggestedInt),
            pair(Int::class.javaObjectType, { suggestedInt cat nullGen }),
            pair(Long::class.javaPrimitiveType, ::suggestedLong),
            pair(Long::class.javaObjectType, { suggestedLong cat nullGen }),
            pair(Float::class.javaPrimitiveType, ::suggestedFloat),
            pair(Float::class.javaObjectType, { suggestedFloat cat nullGen }),
            pair(Double::class.javaPrimitiveType, ::suggestedDouble),
            pair(Double::class.javaObjectType, { suggestedDouble cat nullGen })
        )
    }

    fun suggestedString(): Iterable<String> = TODO()

    fun <T : Enum<T>> allEnums(cls: Class<T>): Iterable<T> = TODO()

    val nullGen by lazy { listOf(null) }

    val allBool by lazy { listOf(true, false) }
    val allByte by lazy { (-128..127).toList() }

    val interestingByte by lazy {
        listOf(-128, -1, 0, 1, 16, 32, 64, 100, 127)
    }
    val interestingShort by lazy {
        listOf(-32768, -129, 128, 255, 256, 512, 1000, 1024, 4096, 32767)
    }
    val interestingInt by lazy {
        listOf(Int.MIN_VALUE, -100663046, -32769, 32768, 65535, 65536, 100663045, Int.MAX_VALUE)
    }
    val interestingLong by lazy {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE)
    }
    val interestingFloat by lazy {
        listOf(java.lang.Float.MIN_NORMAL, Float.MIN_VALUE, Float.MAX_VALUE,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN)
    }
    val interestingDouble by lazy {
        listOf(java.lang.Double.MIN_NORMAL, Double.MIN_VALUE, Double.MAX_VALUE,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN)
    }

    val suggestedIntSimpleRange by lazy { (-35..35).toList() }
    val suggestedFloatSimpleRange by lazy { suggestedIntSimpleRange.map { it.toFloat() } }

    val suggestedBoolean by lazy { allBool }
    val suggestedChar: Iterable<Char> get() = TODO()
    val suggestedByte by lazy { suggestedIntSimpleRange cat interestingByte }
    val suggestedShort by lazy { suggestedByte cat interestingShort }
    val suggestedInt by lazy { suggestedShort cat interestingInt }
    val suggestedLong by lazy { suggestedInt.map { it.toLong() } cat interestingLong }
    val suggestedFloat by lazy { suggestedFloatSimpleRange cat interestingFloat }
    val suggestedDouble by lazy { suggestedFloat.map { it.toDouble() } cat interestingDouble }

    interface WithFeedback {
        fun onResult(result: ExecutionResult, myParamIndex: Int)
    }
    abstract class WithFeedbackIterable<T>(val iter: Iterable<T>) : Iterable<T> {
        override fun iterator() = iter.iterator()
    }

    infix fun <T> Iterable<T>.cat(other: Iterable<T>) = let { orig ->
        if (orig !is WithFeedbackIterable || other !is WithFeedbackIterable) orig + other
        else object : Iterable<T>, WithFeedback {
            var first = true

            override fun onResult(result: ExecutionResult, myParamIndex: Int) {
                if (first && orig is WithFeedback) orig.onResult(result, myParamIndex)
                else if (!first && other is WithFeedback) other.onResult(result, myParamIndex)
            }

            override fun iterator() = object : Iterator<T> {
                var currIter = orig.iterator()

                fun checkNext() {
                    if (first && !currIter.hasNext()) {
                        first = false
                        currIter = other.iterator()
                    }
                }

                override fun hasNext() = checkNext().let { currIter.hasNext() }
                override fun next() = checkNext().let { currIter.next() }
            }
        }
    }
}