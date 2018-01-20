package jwp.fuzz

import java.math.BigInteger
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.inv

// By alphabetical order of type, then val/fun name

fun AtomicReference<BigInteger>.plusAssign(v: Int) = plusAssign(v.toBigInteger())
fun AtomicReference<BigInteger>.plusAssign(v: Long) = plusAssign(v.toBigInteger())
fun AtomicReference<BigInteger>.plusAssign(v: BigInteger): Unit { addAndGet(v) }
fun AtomicReference<BigInteger>.addAndGet(v: BigInteger) = accumulateAndGet(v, BigInteger::add)

val Byte.binaryString get() = "%8s".format(unsigned.toString(2)).replace(' ', '0')
fun Byte.couldHaveBitFlippedTo(vararg new: Byte): Boolean =
    new.contains(this) || byteArrayOf(this).checkConsecutiveBitsFlipped { new.contains(it[0]) }
fun Byte.flipBit(bit: Int) = (toInt() xor (1 shl bit)).toByte()
val Byte.unsigned inline get() = java.lang.Byte.toUnsignedInt(this)

val ByteArray.binaryString get() = joinToString("", transform = { it.binaryString })
fun ByteArray.copyAnd(fn: ByteArray.() -> Unit) = copyOf().apply(fn)
fun ByteArray.flipBit(bitIndex: Int) = flipBit(bitIndex / 8, bitIndex % 8)
fun ByteArray.flipBit(byteIndex: Int, bitIndex: Int) = set(byteIndex, get(byteIndex).flipBit(bitIndex))
// Actually mutates the array, only use with temporary arrays
fun ByteArray.checkConsecutiveBitsFlipped(fn: (ByteArray) -> Boolean): Boolean {
    val bitCount = size * 8
    for (i in -1 until bitCount) {
        if (i >= 0) {
            flipBit(i)
            if (fn(this)) return true
        }
        if (i < bitCount - 1) {
            flipBit(i + 1)
            if (fn(this)) return true
            if (i < bitCount - 2) {
                flipBit(i + 2)
                // Only check this 3 spot if it's exactly 3 until the end
                if (i == bitCount - 3 && fn(this)) return true
                if (i < bitCount - 3) {
                    flipBit(i + 3)
                    if (fn(this)) return true
                    flipBit(i + 3)
                }
                flipBit(i + 2)
            }
            flipBit(i + 1)
        }
        if (i >= 0) flipBit(i)
    }
    return false
}
fun ByteArray.getIntBe(index: Int) = Int.fromBytes(get(index + 3), get(index + 2), get(index + 1), get(index))
fun ByteArray.getIntLe(index: Int) = Int.fromBytes(get(index), get(index + 1), get(index + 2), get(index + 3))
fun ByteArray.getShortBe(index: Int) = Short.fromBytes(get(index + 1), get(index))
fun ByteArray.getShortLe(index: Int) = Short.fromBytes(get(index), get(index + 1))
fun ByteArray.invByte(byteIndex: Int) = set(byteIndex, get(byteIndex).inv())
fun ByteArray.putIntBe(index: Int, v: Int) {
    set(index, v.byte3)
    set(index + 1, v.byte2)
    set(index + 2, v.byte1)
    set(index + 3, v.byte0)
}
fun ByteArray.putIntLe(index: Int, v: Int) {
    set(index, v.byte0)
    set(index + 1, v.byte1)
    set(index + 2, v.byte2)
    set(index + 3, v.byte3)
}
fun ByteArray.putShortBe(index: Int, v: Short) {
    set(index, v.byte1)
    set(index + 1, v.byte0)
}
fun ByteArray.putShortLe(index: Int, v: Short) {
    set(index, v.byte0)
    set(index + 1, v.byte1)
}
fun ByteArray.remove(start: Int, amount: Int): ByteArray {
    require(amount < size)
    val newArr = ByteArray(size - amount)
    System.arraycopy(this, 0, newArr, 0, start)
    System.arraycopy(this, start + amount, newArr, start, newArr.size - start)
    return newArr
}

val Int.binaryString get() = "%32s".format(unsigned.toString(2)).replace(' ', '0')
val Int.byte0 inline get() = toByte()
val Int.byte1 inline get() = (this shr 8).toByte()
val Int.byte2 inline get() = (this shr 16).toByte()
val Int.byte3 inline get() = (this shr 24).toByte()
fun Int.couldHaveBitFlippedTo(vararg new: Int): Boolean =
    new.contains(this) || toByteArray().checkConsecutiveBitsFlipped { new.contains(it.getIntLe(0)) }
val Int.endianSwapped inline get() = Int.fromBytes(byte3, byte2, byte1, byte0)
fun Int.toByteArray() = byteArrayOf(byte0, byte1, byte2, byte3)
val Int.unsigned inline get() = java.lang.Integer.toUnsignedLong(this)
fun Int.Companion.fromBytes(byte0: Byte, byte1: Byte, byte2: Byte, byte3: Byte) =
    (byte3.toInt() shl 24) or
        ((byte2.toInt() and 0xFF) shl 16) or
        ((byte1.toInt() and 0xFF) shl 8) or
        ((byte0.toInt() and 0xFF))

fun <T, R> Iterable<T>.lazyMap(fn: (T) -> R): Iterable<R> = asSequence().map(fn).asIterable()
fun <T, R : Any> Iterable<T>.lazyMapNotNull(fn: (T) -> R?): Iterable<R> = asSequence().mapNotNull(fn).asIterable()

fun <T> List<T>.randItem(rand: Random) = get(rand.nextInt(size))

val Short.binaryString get() = "%16s".format(unsigned.toString(2)).replace(' ', '0')
val Short.byte0 inline get() = toByte()
val Short.byte1 inline get() = (toInt() shr 8).toByte()
fun Short.couldHaveBitFlippedTo(vararg new: Short): Boolean =
    new.contains(this) || toByteArray().checkConsecutiveBitsFlipped { new.contains(it.getShortLe(0)) }
val Short.endianSwapped inline get() = Short.fromBytes(byte1, byte0)
fun Short.toByteArray() = byteArrayOf(byte0, byte1)
val Short.unsigned inline get() = java.lang.Short.toUnsignedInt(this)
fun Short.Companion.fromBytes(byte0: Byte, byte1: Byte) = ((byte1.toInt() shl 8) or (byte0.toInt() and 0xFF)).toShort()
