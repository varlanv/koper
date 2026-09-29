package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.asReadonly
import com.varlanv.koper.lang.bin.slice
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.lang.text.allocateString

object JsonCodec {
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

    interface Read<T> {
        val hints: Hints<T>

        fun read(reader: JsonReadProtocol): T
    }

    interface Write<T> {
        val hints: Hints<T>

        fun write(writer: JsonWriteProtocol, value: T)
    }
}

sealed interface JsonValueSize<in T> {
    data class Static(val maximumBytes: Long) : JsonValueSize<Any?>

    class FromValue<T>(val maximumBytes: (T) -> Long) : JsonValueSize<T>

    data object Dynamic : JsonValueSize<Any?>
}

object IntJsonCodec : JsonCodec.Read<Int>, JsonCodec.Write<Int> {
    override val hints: JsonCodec.Hints<Int> = JsonCodec.Hints(
        size = JsonValueSize.Static(11),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    override fun write(writer: JsonWriteProtocol, value: Int) = writePrimitive(writer = writer, value = value)

    override fun read(reader: JsonReadProtocol): Int = readPrimitive(reader)

    fun readPrimitive(reader: JsonReadProtocol): Int {
        val value = LongJsonCodec.readPrimitive(reader)
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer overflow" }
        return value.toInt()
    }

    fun writePrimitive(writer: JsonWriteProtocol, value: Int) {
        val output = writer.buffer
        val end = writer.position + JsonDecimalDigits.decimalSize(value.toLong())
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
        writer.position = end
    }
}

object LongJsonCodec : JsonCodec.Read<Long>, JsonCodec.Write<Long> {
    override val hints: JsonCodec.Hints<Long> = JsonCodec.Hints(
        size = JsonValueSize.Static(20),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    override fun write(writer: JsonWriteProtocol, value: Long) = writePrimitive(writer = writer, value = value)

    override fun read(reader: JsonReadProtocol): Long = readPrimitive(reader)

    fun writePrimitive(writer: JsonWriteProtocol, value: Long) {
        val output = writer.buffer
        val end = writer.position + JsonDecimalDigits.decimalSize(value)
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
        writer.position = end
    }

    fun readPrimitive(reader: JsonReadProtocol): Long {
        val negative = reader.token == 45
        val digit = if (negative) {
            reader.take()
        } else {
            reader.token
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Long.MIN_VALUE
        } else {
            -Long.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        var result = -(digit - 48).toLong()
        val leadingZero = digit == 48
        var index = reader.parseScope.position
        val input = reader.parseScope.buffer
        while (true) {
            while (reader.parseScope.limit - index >= 8) {
                val number = readEightDigits(input = input, index = index)
                if (number < 0) {
                    break
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= -92233720368L) { "Integer overflow" }
                result *= 100000000L
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index += 8
                if (index < reader.parseScope.limit && (input[index].toInt() and 255) !in 48..57) {
                    break
                }
            }
            while (index < reader.parseScope.limit) {
                val next = input[index].toInt() and 255
                if (next !in 48..57) {
                    reader.parseScope.position = index
                    reader.requireDelimiter(next)
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
            reader.parseScope.position = index
            if (!reader.refill()) {
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            index = 0
        }
    }

    private fun readEightDigits(input: MutBytes, index: Int): Long {
        val word = input.getPackedLong(index)
        if (((word + 0x4646464646464646L) or (word - 0x3030303030303030L)) and -0x7f7f7f7f7f7f7f80L != 0L) {
            return -1L
        }
        val digits = word - 0x3030303030303030L
        val pairs = (digits * 10 + (digits ushr 8)) and 0x00ff00ff00ff00ffL
        val quads = (pairs * 100 + (pairs ushr 16)) and 0x0000ffff0000ffffL
        return (quads * 10000 + (quads ushr 32)) and 0xffffffffL
    }
}

object BooleanJsonCodec : JsonCodec.Read<Boolean>, JsonCodec.Write<Boolean> {
    override val hints: JsonCodec.Hints<Boolean> = JsonCodec.Hints(
        size = JsonValueSize.Static(5),
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )
    private val trueBytes = Bytes(MutBytes("true".encodeToByteArray()))
    private val falseBytes = Bytes(MutBytes("false".encodeToByteArray()))

    override fun write(writer: JsonWriteProtocol, value: Boolean) = writePrimitive(writer = writer, value = value)

    override fun read(reader: JsonReadProtocol): Boolean = readPrimitive(reader)

    fun writePrimitive(writer: JsonWriteProtocol, value: Boolean) {
        writer.writeRaw(
            bytes = if (value) {
                trueBytes
            } else {
                falseBytes
            },
        )
    }

    fun readPrimitive(reader: JsonReadProtocol): Boolean {
        val index = reader.parseScope.position
        val available = reader.parseScope.limit - index
        if (reader.token == 116 && available >= 4) {
            val word = reader.parseScope.buffer.getPackedInt(index)
            if (word and 0x00ffffff == 0x00657572) {
                reader.requireDelimiter(word ushr 24)
                reader.parseScope.position = index + 3
                return true
            }
            throw IllegalArgumentException("Invalid JSON literal")
        }
        if (reader.token == 102 && available >= 5) {
            if (reader.parseScope.buffer.getPackedInt(index) == 0x65736c61) {
                reader.requireDelimiter(reader.parseScope.buffer[index + 4].toInt() and 255)
                reader.parseScope.position = index + 4
                return false
            }
            throw IllegalArgumentException("Invalid JSON literal")
        }
        val value = when (reader.token) {
            116 -> {
                readTail(reader = reader, tail = "rue")
                true
            }

            102 -> {
                readTail(reader = reader, tail = "alse")
                false
            }

            else -> {
                throw IllegalArgumentException("Expected JSON boolean")
            }
        }
        reader.requireDelimiter(reader.peek())
        return value
    }

    private fun readTail(reader: JsonReadProtocol, tail: String) {
        for (char in tail) {
            require(reader.take() == char.code) { "Invalid JSON literal" }
        }
    }
}

object StringJsonCodec : JsonCodec.Read<String>, JsonCodec.Write<String> {
    override val hints: JsonCodec.Hints<String> = JsonCodec.Hints(
        size = JsonValueSize.FromValue { value -> 2L + value.length.toLong() * 6L },
        isJsonPrimitive = true,
    )

    override fun write(writer: JsonWriteProtocol, value: String) {
        val output = writer.buffer
        var position = writer.position
        output[position++] = '"'.code.toByte()
        var index = 0
        while (index < value.length) {
            val char = value[index].code
            if (char < 128) {
                val pair = JsonStringEscapes.escapePairs[char]
                if (pair.toInt() == 0) {
                    output[position++] = char.toByte()
                } else {
                    output.setPackedShort(idx = position, value = pair)
                    position += 2
                    if (pair == JsonStringEscapes.unicodePair) {
                        output.setPackedInt(idx = position, value = JsonStringEscapes.unicodeTails[char])
                        position += 4
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
                Charset.Utf8.encodeCodepointInline(codepoint) { output[position++] = it }
                index = if (codepoint > 0xFFFF) {
                    index + 2
                } else {
                    next
                }
            }
        }
        output[position++] = '"'.code.toByte()
        writer.position = position
    }

    override fun read(reader: JsonReadProtocol): String {
        val scanner = reader.parseScope.stringScanner
        scanner.read(reader)
        return Charset.Utf8.allocateString(
            bytes = Bytes(scanner.bytes),
            offset = scanner.offset,
            len = scanner.length,
        )
    }
}

object Utf8StrJsonCodec : JsonCodec.Read<Utf8Str>, JsonCodec.Write<Utf8Str> {
    override val hints: JsonCodec.Hints<Utf8Str> = JsonCodec.Hints(
        size = JsonValueSize.FromValue { value -> 2L + value.byteLen.toLong() * 6L },
        isJsonPrimitive = true,
        isBoxedByGeneric = true,
    )

    override fun write(writer: JsonWriteProtocol, value: Utf8Str) = writePrimitive(writer = writer, value = value)

    override fun read(reader: JsonReadProtocol): Utf8Str = readPrimitive(reader)

    fun writePrimitive(writer: JsonWriteProtocol, value: Utf8Str) {
        val slice = value.slice
        val input = slice.bytes
        val end = slice.offset + slice.len
        val special = writer.scan.firstSpecial(bytes = input, start = slice.offset, end = end)
        val output = writer.buffer
        var position = writer.position
        output[position++] = '"'.code.toByte()
        input.copyInto(
            destination = output,
            destinationOffset = position,
            startIndex = slice.offset,
            endIndex = special,
        )
        position += special - slice.offset
        if (special < end) {
            position =
                JsonStringEscapes.writeUtf8Escaped(
                    writer = writer,
                    bytes = input,
                    start = special,
                    end = end,
                    targetStart = position,
                )
        }
        output[position++] = '"'.code.toByte()
        writer.position = position
    }

    fun readPrimitive(reader: JsonReadProtocol): Utf8Str {
        val scanner = reader.parseScope.stringScanner
        scanner.read(reader)
        if (scanner.length == 0) {
            return Utf8Str.empty
        }
        val bytes = scanner.bytes.copyOfRange(from = scanner.offset, to = scanner.offset + scanner.length)
        return Utf8Str.unsafeWrap(bytes.asReadonly().slice(offset = 0, len = bytes.size))
    }
}
