package com.varlanv.koper.json.tmp

import com.varlanv.koper.json.JsonOutput
import com.varlanv.koper.json.PackedJsonBytes
import com.varlanv.koper.json.jsonSpecialScan
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Utf8Str

class IdealJsonWriter(private val vectorized: Boolean = false) {
    private val scan = jsonSpecialScan(vectorized)
    private var buffer = ByteArray(512)
    private var position = 0
    private lateinit var output: JsonOutput

    fun reset(output: JsonOutput) {
        this.output = output
        position = 0
    }

    fun flush() {
        if (position != 0) {
            output.write(buffer, 0, position)
            position = 0
        }
    }

    private fun reserve(size: Int) {
        if (buffer.size - position < size) {
            flush()
        }
    }

    fun reserveObject(size: Int) {
        require(size >= 0) { "Negative JSON size" }
        if (buffer.size - position < size) {
            flush()
            if (buffer.size < size) {
                buffer = ByteArray(size)
            }
        }
    }

    fun writeByte(value: Int) {
        reserve(1)
        buffer[position++] = value.toByte()
    }

    fun writeRaw(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ) {
        if (length == 0) {
            return
        }
        if (length > buffer.size - position) {
            flush()
            if (length >= buffer.size) {
                output.write(bytes, offset, length)
                return
            }
        }
        bytes.copyInto(buffer, position, offset, offset + length)
        position += length
    }

    fun writeRaw(first: Int, second: Short) {
        reserve(6)
        PackedJsonBytes.setInt(buffer, position, first)
        PackedJsonBytes.setShort(buffer, position + 4, second)
        position += 6
    }

    fun writeRaw(value: Long) {
        reserve(8)
        PackedJsonBytes.setLong(buffer, position, value)
        position += 8
    }

    fun writeRaw(first: Long, second: Short) {
        reserve(10)
        PackedJsonBytes.setLong(buffer, position, first)
        PackedJsonBytes.setShort(buffer, position + 8, second)
        position += 10
    }

    fun writeRaw(first: Long, second: Int) {
        reserve(12)
        PackedJsonBytes.setLong(buffer, position, first)
        PackedJsonBytes.setInt(buffer, position + 8, second)
        position += 12
    }

    fun writeRawReserved(first: Int, second: Short) {
        PackedJsonBytes.setInt(buffer, position, first)
        PackedJsonBytes.setShort(buffer, position + 4, second)
        position += 6
    }

    fun writeRawReserved(value: Long) {
        PackedJsonBytes.setLong(buffer, position, value)
        position += 8
    }

    fun writeRawReserved(first: Long, second: Short) {
        PackedJsonBytes.setLong(buffer, position, first)
        PackedJsonBytes.setShort(buffer, position + 8, second)
        position += 10
    }

    fun writeRawReserved(first: Long, second: Int) {
        PackedJsonBytes.setLong(buffer, position, first)
        PackedJsonBytes.setInt(buffer, position + 8, second)
        position += 12
    }

    fun writeLong(value: Long) {
        val size = decimalSize(value)
        reserve(size)
        writeLongReserved(value, size)
    }

    fun writeLongReserved(value: Long) {
        writeLongReserved(
            value,
            decimalSize(value),
        )
    }

    private fun writeLongReserved(value: Long, size: Int) {
        val end = position + size
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = triplets[(quotient * 1000 - number).toInt()]
            index -= 3
            PackedJsonBytes.setShort(buffer, index, digits.toShort())
            buffer[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = writeLeadingDigits((-number).toInt(), index)
        if (value < 0) {
            buffer[index - 1] = '-'.code.toByte()
        }
        position = end
    }

    fun writeInt(value: Int) {
        val size = decimalSize(value.toLong())
        reserve(size)
        writeIntReserved(value, size)
    }

    fun writeIntReserved(value: Int) {
        writeIntReserved(
            value,
            decimalSize(value.toLong()),
        )
    }

    private fun writeIntReserved(value: Int, size: Int) {
        val end = position + size
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = triplets[quotient * 1000 - number]
            index -= 3
            PackedJsonBytes.setShort(buffer, index, digits.toShort())
            buffer[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = writeLeadingDigits(-number, index)
        if (value < 0) {
            buffer[index - 1] = '-'.code.toByte()
        }
        position = end
    }

    private fun writeLeadingDigits(number: Int, end: Int): Int {
        var index = end
        buffer[--index] = ones[number % 100]
        if (number >= 10) {
            buffer[--index] = tens[number % 100]
        }
        if (number >= 100) {
            buffer[--index] = ('0'.code + number / 100).toByte()
        }
        return index
    }

    private fun decimalSize(value: Long): Int {
        val number = if (value > 0) {
            -value
        } else {
            value
        }
        val digits = when {
            number > -1000000000L -> intDigits((-number).toInt())
            number > -1000000000000000000L -> 9 + intDigits(-(number / 1000000000L).toInt())
            else -> 19
        }
        return digits + if (value < 0) {
            1
        } else {
            0
        }
    }

    private fun intDigits(value: Int): Int {
        return if (value < 100000) {
            if (value < 100) {
                if (value < 10) {
                    1
                } else {
                    2
                }
            } else if (value < 1000) {
                3
            } else if (value < 10000) {
                4
            } else {
                5
            }
        } else if (value < 10000000) {
            if (value < 1000000) {
                6
            } else {
                7
            }
        } else if (value < 100000000) {
            8
        } else {
            9
        }
    }

    fun writeBoolean(value: Boolean) {
        writeRaw(
            if (value) {
                trueBytes
            } else {
                falseBytes
            },
        )
    }

    fun writeUtf8(value: Utf8Str) {
        writeByte('"'.code)
        val slice = value.bytes
        val bytes = slice.unsafeBorrowArray()
        val end = slice.offset + slice.len
        var index = if (vectorized) {
            scan.firstSpecial(bytes, slice.offset, end)
        } else {
            slice.offset
        }
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                writeRaw(bytes, slice.offset, index - slice.offset)
                writeEscaped(bytes, index, end)
                writeByte('"'.code)
                return
            }
            index++
        }
        writeRaw(bytes, slice.offset, slice.len)
        writeByte('"'.code)
    }

    fun writeUtf8Reserved(value: Utf8Str) {
        buffer[position++] = '"'.code.toByte()
        val slice = value.bytes
        val bytes = slice.unsafeBorrowArray()
        val end = slice.offset + slice.len
        var index = if (vectorized) {
            scan.firstSpecial(bytes, slice.offset, end)
        } else {
            slice.offset
        }
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                bytes.copyInto(buffer, position, slice.offset, index)
                position += index - slice.offset
                position = writeEscapedRange(bytes, index, end, position)
                buffer[position++] = '"'.code.toByte()
                return
            }
            index++
        }
        bytes.copyInto(buffer, position, slice.offset, end)
        position += slice.len
        buffer[position++] = '"'.code.toByte()
    }

    fun writeString(value: String) {
        val maximumSize = 2L + value.length.toLong() * 6L
        if (maximumSize <= buffer.size) {
            reserve(maximumSize.toInt())
            writeStringReserved(value)
            return
        }
        writeByte('"'.code)
        var index = 0
        while (index < value.length) {
            reserve(6)
            index = writeStringChar(value, index)
        }
        writeByte('"'.code)
    }

    fun writeStringReserved(value: String) {
        buffer[position++] = '"'.code.toByte()
        var index = 0
        while (index < value.length) index = writeStringChar(value, index)
        buffer[position++] = '"'.code.toByte()
    }

    private fun writeStringChar(value: String, index: Int): Int {
        val char = value[index].code
        if (char < 128) {
            val pair = escapePairs[char]
            if (pair.toInt() == 0) {
                buffer[position++] = char.toByte()
            } else {
                PackedJsonBytes.setShort(buffer, position, pair)
                position += 2
                if (pair == unicodePair) {
                    PackedJsonBytes.setInt(buffer, position, unicodeTails[char])
                    position += 4
                }
            }
            return index + 1
        }
        val next = index + 1
        val codepoint = if (char in 0xD800..0xDBFF && next < value.length && value[next].code in 0xDC00..0xDFFF) {
            0x10000 + ((char - 0xD800) shl 10) + value[next].code - 0xDC00
        } else {
            char
        }
        Charset.encodeUtf8Inline(codepoint) { buffer[position++] = it }
        return if (codepoint > 0xFFFF) {
            index + 2
        } else {
            next
        }
    }

    fun writeBooleanObjectEndReserved(value: Boolean) {
        PackedJsonBytes.setLong(buffer, position, 0x657669746361222cL)
        PackedJsonBytes.setLong(
            buffer,
            position + 8,
            if (value) {
                0x007d657572743a22L
            } else {
                0x7d65736c61663a22L
            },
        )
        position += if (value) {
            15
        } else {
            16
        }
    }

    private fun writeEscaped(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ) {
        var index = start
        while (index < end) {
            val chunkEnd = index + minOf(end - index, 4096)
            val required = (chunkEnd - index) * 6
            if (buffer.size - position < required) {
                if (buffer.size < 32768) {
                    buffer = buffer.copyOf(
                        minOf(
                            32768,
                            maxOf(
                                buffer.size * 2,
                                position + required,
                            ),
                        ),
                    )
                }
                if (buffer.size - position < required) {
                    flush()
                }
            }
            position = writeEscapedRange(bytes, index, chunkEnd, position)
            index = chunkEnd
        }
    }

    private fun writeEscapedRange(
        bytes: ByteArray,
        start: Int,
        end: Int,
        targetStart: Int,
    ): Int {
        val target = buffer
        var index = start
        var offset = targetStart
        if (vectorized) {
            val width = scan.laneCount
            while (end - index >= width) {
                val blockStart = index
                val blockEnd = blockStart + width
                var events = scan.specialMask(bytes, blockStart)
                while (events != 0L) {
                    val special = blockStart + events.countTrailingZeroBits()
                    val length = special - index
                    if (length != 0) {
                        if (length <= 8 && index <= bytes.size - 8) {
                            val word = PackedJsonBytes.getLong(bytes, index)
                            PackedJsonBytes.setLong(target, offset, word)
                        } else {
                            bytes.copyInto(target, offset, index, special)
                        }
                        offset += length
                    }
                    val b = bytes[special].toInt() and 255
                    val escaped = escapePairs[b]
                    PackedJsonBytes.setShort(target, offset, escaped)
                    offset += 2
                    if (escaped == unicodePair) {
                        PackedJsonBytes.setInt(target, offset, unicodeTails[b])
                        offset += 4
                    }
                    index = special + 1
                    events = events and (events - 1)
                }
                if (index < blockEnd) {
                    bytes.copyInto(target, offset, index, blockEnd)
                    offset += blockEnd - index
                }
                index = blockEnd
            }
        }
        while (index < end) {
            val b = bytes[index++].toInt() and 0xFF
            val escaped = escapePairs[b]
            if (escaped.toInt() != 0) {
                PackedJsonBytes.setShort(target, offset, escaped)
                offset += 2
                if (escaped == unicodePair) {
                    PackedJsonBytes.setInt(target, offset, unicodeTails[b])
                    offset += 4
                }
            } else {
                target[offset++] = b.toByte()
            }
        }
        return offset
    }

    private companion object {
        val triplets = IntArray(1000) {
            ('0'.code + it / 100) or (('0'.code + it / 10 % 10) shl 8) or (('0'.code + it % 10) shl 16)
        }



        const val unicodePair: Short = 0x755c
        val unicodeTails = IntArray(32) { value ->
            0x3030 or (hex[value ushr 4].code shl 16) or (hex[value and 15].code shl 24)
        }
        val escapePairs = ByteArray(256)
            .also { codes ->
                for (value in 0..31) codes[value] = 'u'.code.toByte()
                codes[8] = 'b'.code.toByte()
                codes[9] = 't'.code.toByte()
                codes[10] = 'n'.code.toByte()
                codes[12] = 'f'.code.toByte()
                codes[13] = 'r'.code.toByte()
                codes[34] = 34
                codes[92] = 92
            }
            .let { codes ->
                ShortArray(256) { index ->
                    if (codes[index].toInt() == 0) {
                        0
                    } else {
                        (92 or (codes[index].toInt() shl 8)).toShort()
                    }
                }
            }
        val tens = ByteArray(100) { ('0'.code + it / 10).toByte() }
        val ones = ByteArray(100) { ('0'.code + it % 10).toByte() }
        val trueBytes = "true".encodeToByteArray()
        val falseBytes = "false".encodeToByteArray()
        const val hex = "0123456789abcdef"
    }
}
