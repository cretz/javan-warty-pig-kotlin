package jwp.fuzz

object TypeGen {
    val nullGen by lazy { listOf(null) }

    val allBool by lazy { listOf(true, false) }
    val allByte by lazy { (-128..127).toList() }

    val interestingByte by lazy {
        listOf(-128, -1, 0, 1, 16, 32, 64, 100, 127)
    }
    val interestingShort by lazy {
        interestingByte + listOf(-32768, -129, 128, 255, 256, 512, 1000, 1024, 4096, 32767)
    }
    val interestingInt by lazy {
        interestingShort + listOf(Int.MIN_VALUE, -100663046, -32769, 32768, 65535, 65536, 100663045, Int.MAX_VALUE)
    }
    val interestingLong by lazy {
        interestingInt.map { it.toLong() } + listOf(Long.MIN_VALUE, Long.MAX_VALUE)
    }
    val interestingFloat by lazy {
        listOf(java.lang.Float.MIN_NORMAL, Float.MIN_VALUE, Float.MAX_VALUE,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN)
    }
    val interestingDouble by lazy {
        interestingFloat.map { it.toDouble() } +
            listOf(java.lang.Double.MIN_NORMAL, Double.MIN_VALUE, Double.MAX_VALUE,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN)
    }

    val suggestedIntSimpleRange by lazy { (-35..35).toList() }
    val suggestedFloatSimpleRange by lazy { suggestedIntSimpleRange.map { it.toFloat() } }

    val suggestedByte by lazy { suggestedIntSimpleRange + interestingByte }
    val suggestedShort by lazy { suggestedIntSimpleRange + interestingShort }
    val suggestedInt by lazy { suggestedIntSimpleRange + interestingInt }
    val suggestedLong by lazy { suggestedIntSimpleRange.map { it.toLong() } + interestingLong }
    val suggestedFloat by lazy { suggestedFloatSimpleRange + interestingFloat }
    val suggestedDouble by lazy { suggestedFloatSimpleRange.map { it.toDouble() } + interestingDouble }
}