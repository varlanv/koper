package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.asReadonly
import com.varlanv.koper.lang.bin.toSlice
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.lang.text.allocateString
import kotlin.Any
import kotlin.Boolean
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.code
import kotlin.require
import kotlin.text.encodeToByteArray
import kotlin.text.iterator

/**
 * Contracts and sizing hints for codecs operating in read or write scope contexts.
 */
object JsonCodec {
    /**
     * Metadata used to plan reservations and generated calls. Construction records hints without reading or writing bytes.
     * The size hint describes the complete JSON representation, including delimiters and escaping.
     */
    class Hints<T>(
        /**
         * Hint for what is the size of the value.
         * Used to optimize assumptions on when to grow/bound check internal buffers during serialization.
         */
        val size: JsonValueSize<T> = JsonValueSize.Dynamic,
        /**
         * Whether value is serialized to JSON primitive - string, number, boolean
         */
        val isJsonPrimitive: Boolean = false,
        /**
         * Whether value can be boxed by JVM when used inside generic, e.g. `JsonCodec.Read<Int>` or `JsonCodec.Write<Int>`,
         * or `value class` like `JsonCodec.Read<Utf8Str>` or `JsonCodec.Write<Utf8Str>`
         * When value may be boxed, it is more efficient to write separate, not override version of `read` and `write`
         * methods and let generated call those directly. For example - [com.varlanv.koper.json.IntJsonCodec.readPrimitive]
         */
        val isBoxedByGeneric: Boolean = false,
    )

    /**
     * Consumes a value using the input and read-scope context. Built-in and generated readers expect the value's first
     * byte to have been consumed into the last token by the caller before [read] is invoked.
     */
    interface Read<T> {
        val hints: Hints<T>

        /**
         * Consumes the value beginning with the scope's last token.
         * Updates parser state and may refill, compact, or grow input storage; buffering can read ahead in the source.
         * Does not require consuming the entire document.
         *
         * @return The decoded value of type [T].
         */
        context(input: ByteSource, parseScope: JsonReadScope)
        fun read(): T
    }

    /**
     * Appends a value using the sink and write-scope context; capacity must be reserved before raw buffer writes.
     */
    interface Write<T> {
        val hints: Hints<T>

        /**
         * Appends the JSON representation of [value], updating the write buffer and position.
         * Reservations may grow the buffer or flush bytes to the sink; completion does not imply pending output was flushed.
         *
         * @return [Unit] after writing the value into the output state.
         */
        context(sink: ByteSink, writeScope: JsonWriteScope)
        fun write(value: T, position: Int): Int
    }
}

/**
 * Upper-bound sizing strategies for a complete encoded JSON value, used to plan output reservations.
 */
sealed interface JsonValueSize<in T> {
    /**
     * Fixed maximum encoded byte count; construction records the bound without performing output operations.
     */
    data class Static(val maximumBytes: Int) : JsonValueSize<Any?>

    /**
     * Value-dependent maximum encoded byte count. The stored function returns an [Int] bound for its input;
     * construction stores the function without invoking it or writing bytes.
     */
    class FromValue<T>(val maximumBytes: (T) -> Int) : JsonValueSize<T>

    /**
     * No bounded size is supplied; callers cannot determine a complete reservation from this hint alone.
     */
    data object Dynamic : JsonValueSize<Any?>
}

object IntJsonCodec : JsonCodec.Read<Int>, JsonCodec.Write<Int> {
    override val hints: JsonCodec.Hints<Int> = JsonCodec.Hints(
        size = JsonValueSize.Static(11),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    context(sink: ByteSink, writeScope: JsonWriteScope)
    override fun write(value: Int, position: Int) = writePrimitive(value = value, position = position)

    context(input: ByteSource, parseScope: JsonReadScope)
    override fun read(): Int = readPrimitive()

    context(input: ByteSource, parseScope: JsonReadScope)
    fun readPrimitive(): Int {
        val negative = parseScope.last == 45
        val digit = if (negative) {
            JsonReadBuffer.take()
        } else {
            parseScope.last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Int.MIN_VALUE
        } else {
            -Int.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        val packedMultiplyMinimum = minimum / jsonPackedDigitBase
        var result = -(digit - 48)
        val leadingZero = digit == 48
        var index = parseScope.position
        val input = parseScope.buffer
        while (true) {
            while (parseScope.limit - index >= jsonPackedDigitCount) {
                val number = jsonReadPackedDigits(bytes = input, index = index)
                if (number < 0) {
                    break
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= packedMultiplyMinimum) { "Integer overflow" }
                result *= jsonPackedDigitBase
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index += jsonPackedDigitCount
            }
            while (index < parseScope.limit) {
                val next = input[index].toInt() and 255
                if (next !in 48..57) {
                    parseScope.position = index
                    JsonReadProtocol.requireDelimiter(next)
                    return if (negative) {
                        result
                    } else {
                        -result
                    }
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= multiplyMinimum) { "Integer overflow" }
                result *= 10
                val number = next - 48
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index++
            }
            parseScope.position = index
            if (!JsonReadBuffer.refill()) {
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            index = 0
        }
    }

    context(writeScope: JsonWriteScope)
    fun writePrimitive(value: Int, position: Int): Int {
        val output = writeScope.buffer
        val end = position + JsonDecimalDigits.decimalSize(value)
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = JsonDecimalDigits.triplets[quotient * 1000 - number]
            index -= 3
            output.setPackedShort(idx = index, value = digits.toShort())
            output[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = JsonDecimalDigits.writeLeading(buffer = output, number = -number, end = index)
        if (value < 0) {
            output[index - 1] = '-'.code.toByte()
        }
        return end
    }
}

object LongJsonCodec : JsonCodec.Read<Long>, JsonCodec.Write<Long> {
    override val hints: JsonCodec.Hints<Long> = JsonCodec.Hints(
        size = JsonValueSize.Static(20),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    context(sink: ByteSink, writeScope: JsonWriteScope)
    override fun write(value: Long, position: Int): Int = writePrimitive(value = value, position = position)

    context(input: ByteSource, parseScope: JsonReadScope)
    override fun read(): Long = readPrimitive()

    context(writeScope: JsonWriteScope)
    fun writePrimitive(value: Long, position: Int): Int {
        val output = writeScope.buffer
        val end = position + JsonDecimalDigits.decimalSize(value)
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = JsonDecimalDigits.triplets[(quotient * 1000 - number).toInt()]
            index -= 3
            output.setPackedShort(idx = index, value = digits.toShort())
            output[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = JsonDecimalDigits.writeLeading(buffer = output, number = (-number).toInt(), end = index)
        if (value < 0) {
            output[index - 1] = '-'.code.toByte()
        }
        return end
    }

    context(input: ByteSource, parseScope: JsonReadScope)
    fun readPrimitive(): Long {
        val negative = parseScope.last == 45
        val digit = if (negative) {
            JsonReadBuffer.take()
        } else {
            parseScope.last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Long.MIN_VALUE
        } else {
            -Long.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        val packedMultiplyMinimum = minimum / jsonPackedDigitBase
        var result = -(digit - 48).toLong()
        val leadingZero = digit == 48
        var index = parseScope.position
        val input = parseScope.buffer
        while (true) {
            while (parseScope.limit - index >= jsonPackedDigitCount) {
                val number = jsonReadPackedDigits(bytes = input, index = index)
                if (number < 0) {
                    break
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= packedMultiplyMinimum) { "Integer overflow" }
                result *= jsonPackedDigitBase
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index += jsonPackedDigitCount
                if (index < parseScope.limit && (input[index].toInt() and 255) !in 48..57) {
                    break
                }
            }
            while (index < parseScope.limit) {
                val next = input[index].toInt() and 255
                if (next !in 48..57) {
                    parseScope.position = index
                    JsonReadProtocol.requireDelimiter(next)
                    return if (negative) {
                        result
                    } else {
                        -result
                    }
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= multiplyMinimum) { "Integer overflow" }
                result *= 10
                val number = next - 48
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index++
            }
            parseScope.position = index
            if (!JsonReadBuffer.refill()) {
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            index = 0
        }
    }
}

object BooleanJsonCodec : JsonCodec.Read<Boolean>, JsonCodec.Write<Boolean> {
    override val hints: JsonCodec.Hints<Boolean> = JsonCodec.Hints(
        size = JsonValueSize.Static(5),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )
    val trueBytes = "true".encodeToByteArray().asReadonly()
    val falseBytes = "false".encodeToByteArray().asReadonly()

    context(sink: ByteSink, writeScope: JsonWriteScope)
    override fun write(value: Boolean, position: Int): Int = writePrimitive(value = value, position = position)

    context(input: ByteSource, parseScope: JsonReadScope)
    override fun read(): Boolean = readPrimitive()

    context(writeScope: JsonWriteScope)
    inline fun writePrimitive(value: Boolean, position: Int): Int {
        return position + JsonWriteProtocol.writeRaw(
            bytes = if (value) {
                trueBytes
            } else {
                falseBytes
            },
            position = position,
        )
    }

    context(input: ByteSource, parseScope: JsonReadScope)
    fun readPrimitive(): Boolean {
        val index = parseScope.position
        val available = parseScope.limit - index
        if (parseScope.last == 116 && available >= 4) {
            val word = parseScope.buffer.getPackedInt(index)
            if (word and 0x00ffffff == 0x00657572) {
                JsonReadProtocol.requireDelimiter(word ushr 24)
                parseScope.position = index + 3
                return true
            }
            throw IllegalArgumentException("Invalid JSON literal")
        }
        if (parseScope.last == 102 && available >= 5) {
            if (parseScope.buffer.getPackedInt(index) == 0x65736c61) {
                JsonReadProtocol.requireDelimiter(parseScope.buffer[index + 4].toInt() and 255)
                parseScope.position = index + 4
                return false
            }
            throw IllegalArgumentException("Invalid JSON literal")
        }
        val value = when (parseScope.last) {
            116 -> {
                readTail("rue")
                true
            }

            102 -> {
                readTail("alse")
                false
            }

            else -> {
                throw IllegalArgumentException("Expected JSON boolean")
            }
        }
        JsonReadProtocol.requireDelimiter(JsonReadBuffer.peek())
        return value
    }

    context(input: ByteSource, parseScope: JsonReadScope)
    private fun readTail(tail: String) {
        for (char in tail) {
            require(JsonReadBuffer.take() == char.code) { "Invalid JSON literal" }
        }
    }
}

object StringJsonCodec : JsonCodec.Read<String>, JsonCodec.Write<String> {
    private const val zeroShort: Short = 0

    override val hints: JsonCodec.Hints<String> = JsonCodec.Hints(
        size = JsonValueSize.FromValue { value ->
            JsonWriteProtocol.addStringSize(maximumBytes = 2, length = value.length)
        },
        isJsonPrimitive = true,
    )

    context(sink: ByteSink, writeScope: JsonWriteScope)
    override fun write(value: String, position: Int): Int {
        val output = writeScope.buffer
        var pos = position
        output[pos++] = '"'.code.toByte()
        var index = 0
        while (index <= value.length - 4) {
            val first = value[index].code
            val second = value[index + 1].code
            val third = value[index + 2].code
            val fourth = value[index + 3].code
            if ((first or second or third or fourth) >= 128 ||
                (JsonStringEscapes.escapePairs[first].toInt() or
                    JsonStringEscapes.escapePairs[second].toInt() or
                    JsonStringEscapes.escapePairs[third].toInt() or
                    JsonStringEscapes.escapePairs[fourth].toInt()) != 0
            ) {
                break
            }
            output.setPackedInt(idx = pos, value = first or (second shl 8) or (third shl 16) or (fourth shl 24))
            pos += 4
            index += 4
        }
        while (index < value.length) {
            val char = value[index].code
            if (char < 128) {
                val pair = JsonStringEscapes.escapePairs[char]
                if (pair == zeroShort) {
                    output[pos++] = char.toByte()
                } else {
                    output.setPackedShort(idx = pos, value = pair)
                    pos += 2
                    if (pair == JsonStringEscapes.unicodePair) {
                        output.setPackedInt(idx = pos, value = JsonStringEscapes.unicodeTails[char])
                        pos += 4
                    }
                }
                index++
            } else {
                val next = index + 1
                val codepoint = if (char in 0xD800..0xDBFF && next < value.length && value[next].code in 0xDC00..0xDFFF
                ) {
                    0x10000 + ((char - 0xD800) shl 10) + value[next].code - 0xDC00
                } else {
                    char
                }
                Charset.Utf8.encodeCodepointInline(codepoint) { output[pos++] = it }
                index = if (codepoint > 0xFFFF) {
                    index + 2
                } else {
                    next
                }
            }
        }
        output[pos++] = '"'.code.toByte()
        return pos
    }

    context(input: ByteSource, parseScope: JsonReadScope)
    override fun read(): String {
        JsonReadBuffer.readString()
        return Charset.Utf8.allocateString(
            bytes = parseScope.buffer.asReadonly(),
            offset = parseScope.stringOffset,
            len = parseScope.stringLength,
        )
    }
}

object Utf8StrJsonCodec : JsonCodec.Read<Utf8Str>, JsonCodec.Write<Utf8Str> {
    override val hints: JsonCodec.Hints<Utf8Str> = JsonCodec.Hints(
        size = JsonValueSize.FromValue { value ->
            JsonWriteProtocol.addStringSize(maximumBytes = 2, length = value.byteLen)
        },
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    context(sink: ByteSink, writeScope: JsonWriteScope)
    override fun write(value: Utf8Str, position: Int): Int = writePrimitive(value = value, position = position)

    context(input: ByteSource, parseScope: JsonReadScope)
    override fun read(): Utf8Str = readPrimitive()

    context(writeScope: JsonWriteScope)
    fun writePrimitive(value: Utf8Str, position: Int): Int {
        val slice = value.slice
        val input = slice.bytes
        val end = slice.offset + slice.len
        val special = jsonScanner.firstSpecial(bytes = input, start = slice.offset, end = end)
        val output = writeScope.buffer
        var pos = position
        output[pos++] = '"'.code.toByte()
        input.copyInto(destination = output, destinationOffset = pos, startIndex = slice.offset, endIndex = special)
        pos += special - slice.offset
        if (special < end) {
            pos = JsonStringEscapes.writeUtf8Escaped(bytes = input, start = special, end = end, targetStart = pos)
        }
        output[pos++] = '"'.code.toByte()
        return pos
    }

    context(input: ByteSource, parseScope: JsonReadScope)
    fun readPrimitive(): Utf8Str {
        JsonReadBuffer.readString()
        if (parseScope.stringLength == 0) {
            return Utf8Str.empty
        }
        val bytes = parseScope.buffer.copyOfRange(
            from = parseScope.stringOffset,
            to = parseScope.stringOffset + parseScope.stringLength,
        )
        return Utf8Str.unsafeWrap(bytes.asReadonly().toSlice(offset = 0, len = bytes.size))
    }
}
