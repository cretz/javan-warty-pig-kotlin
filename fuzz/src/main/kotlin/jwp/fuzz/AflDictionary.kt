package jwp.fuzz

import java.util.*
import kotlin.math.sign

class AflDictionary(val entries: List<Entry>) {

    val values get() = entries.map(Entry::value)

    companion object {
        fun read(lines: Iterable<String>) = AflDictionary(lines.mapIndexedNotNull { index, lineOrig ->
            // We follow AFL rules here...
            val lineNum = index + 1
            val line = lineOrig.trim()
            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith('#')) return@mapIndexedNotNull null
            // Has to end with quote
            require(line.endsWith('"')) { "Line $lineNum doesn't end with quote"}
            // Label is all alphanumeric chars and underscore
            val label = line.takeWhile { it.isLetterOrDigit() || it == '_' }.takeIf { it.isNotEmpty() }
            var currIndex = label?.length ?: 0
            // Number is all digits after at sign after that
            val level = if (label != null && line.getOrNull(currIndex) == '@') {
                currIndex++
                line.drop(currIndex).takeWhile { it.isDigit() }.let {
                    currIndex += it.length
                    it.toIntOrNull() ?: error("Line $lineNum bad level")
                }
            } else null
            // Skip whitespace and equal signs
            currIndex += line.drop(currIndex).takeWhile { it.isWhitespace() || it == '=' }.length
            // Opening quote
            require(line.getOrNull(currIndex) == '"') { "Line $lineNum doesn't have opening quote" }
            // Get the stuff before last quote
            val value = ArrayList<Byte>()
            loop@ while (true) {
                val chr = line.getOrNull(++currIndex) ?: error("Line $lineNum unexpected end")
                require(chr.toByte().toChar() == chr) { error("Line $lineNum has multibyte chars") }
                when (chr) {
                    in 1.toChar()..31.toChar(), in 128.toChar()..255.toChar() -> error("Line $lineNum invalid char")
                    '\\' -> line.getOrNull(++currIndex).also {
                        when (it) {
                            '\\', '"' -> value += it.toByte()
                            'x' -> line.substring(++currIndex, currIndex + 2).also { x ->
                                ++currIndex
                                require(x.length == 2) { "Line $lineNum invalid hex chars" }
                                value += x.toIntOrNull(16)?.let { (it - 128).toByte() } ?:
                                        error("Line $lineNum invalid hex chars")
                            }
                            else -> error("Line $lineNum unknown escape char")
                        }
                    }
                    '"' -> break@loop
                    else -> value += chr.toByte()
                }
            }
            require(value.isNotEmpty()) { "Line $lineNum value is empty" }
            Entry(label = label, level = level, value = value.toByteArray())
        })
    }

    data class Entry(val label: String?, val level: Int?, val value: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry

            if (label != other.label) return false
            if (level != other.level) return false
            if (!Arrays.equals(value, other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label?.hashCode() ?: 0
            result = 31 * result + (level ?: 0)
            result = 31 * result + Arrays.hashCode(value)
            return result
        }
    }
}