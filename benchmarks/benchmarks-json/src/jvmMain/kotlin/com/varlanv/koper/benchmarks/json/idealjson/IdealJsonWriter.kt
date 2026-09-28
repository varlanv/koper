package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Str
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.nio.ByteOrder

class IdealJsonWriter(private val vectorized: Boolean = false) {
    private var buffer = MutBytes(512.bytes())
    private var position = 0
    private lateinit var output: OutputStream

    fun reset(output: OutputStream) {
        this.output = output
        position = 0
    }

    fun flush() {
        if (position != 0) {
            Bytes.unsafe { useInternal(buffer) { output.write(it, 0, position) } }
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
                buffer = size.bytes().allocate()
            }
        }
    }

    fun writeByte(value: Int) {
        reserve(1)
        buffer[position++] = value.toByte()
    }

    fun writeRaw(
        bytes: Bytes,
        offset: Int = 0,
        length: Int = bytes.size,
    ) {
        if (length == 0) {
            return
        }
        if (length > buffer.size - position) {
            flush()
            if (length >= buffer.size) {
                Bytes.unsafe { useInternal(bytes) { output.write(it, offset, length) } }
                return
            }
        }
        bytes.copyInto(
            destination = buffer,
            destinationOffset = position,
            startIndex = offset,
            endIndex = offset + length,
        )
        position += length
    }

    fun writeRaw(first: Int, second: Short) {
        reserve(6)
        intView.set(buffer, position, first)
        shortView.set(buffer, position + 4, second)
        position += 6
    }

    fun writeRaw(value: Long) {
        reserve(8)
        longView.set(buffer, position, value)
        position += 8
    }

    fun writeRaw(first: Long, second: Short) {
        reserve(10)
        longView.set(buffer, position, first)
        shortView.set(buffer, position + 8, second)
        position += 10
    }

    fun writeRaw(first: Long, second: Int) {
        reserve(12)
        longView.set(buffer, position, first)
        intView.set(buffer, position + 8, second)
        position += 12
    }

    fun writeRawReserved(first: Int, second: Short) {
        intView.set(buffer, position, first)
        shortView.set(buffer, position + 4, second)
        position += 6
    }

    fun writeRawReserved(value: Long) {
        longView.set(buffer, position, value)
        position += 8
    }

    fun writeRawReserved(first: Long, second: Short) {
        longView.set(buffer, position, first)
        shortView.set(buffer, position + 8, second)
        position += 10
    }

    fun writeRawReserved(first: Long, second: Int) {
        longView.set(buffer, position, first)
        intView.set(buffer, position + 8, second)
        position += 12
    }

    fun writeLong(value: Long) {
        val size = decimalSize(value)
        reserve(size)
        writeLongReserved(value = value, size = size)
    }

    fun writeLongReserved(value: Long) {
        writeLongReserved(
            value = value,
            size = decimalSize(value),
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
            shortView.set(buffer, index, digits.toShort())
            buffer[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = writeLeadingDigits(number = (-number).toInt(), end = index)
        if (value < 0) {
            buffer[index - 1] = '-'.code.toByte()
        }
        position = end
    }

    fun writeInt(value: Int) {
        val size = decimalSize(value.toLong())
        reserve(size)
        writeIntReserved(value = value, size = size)
    }

    fun writeIntReserved(value: Int) {
        writeIntReserved(
            value = value,
            size = decimalSize(value.toLong()),
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
            shortView.set(buffer, index, digits.toShort())
            buffer[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = writeLeadingDigits(number = -number, end = index)
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
            bytes = if (value) {
                trueBytes
            } else {
                falseBytes
            },
        )
    }

    fun writeUtf8(value: Str) {
        writeByte('"'.code)
        val slice = value.slice
        val bytes = slice.bytes
        val end = slice.offset + slice.len
        var index = if (vectorized) {
            VectorJsonScan.firstSpecial(bytes = bytes, start = slice.offset, end = end)
        } else {
            slice.offset
        }
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                writeRaw(bytes = bytes, offset = slice.offset, length = index - slice.offset)
                writeEscaped(bytes = bytes, start = index, end = end)
                writeByte('"'.code)
                return
            }
            index++
        }
        writeRaw(bytes = bytes, offset = slice.offset, length = slice.len)
        writeByte('"'.code)
    }

    fun writeUtf8Reserved(value: Str) {
        buffer[position++] = '"'.code.toByte()
        val slice = value.slice
        val bytes = slice.bytes
        val end = slice.offset + slice.len
        var index = if (vectorized) {
            VectorJsonScan.firstSpecial(bytes = bytes, start = slice.offset, end = end)
        } else {
            slice.offset
        }
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                bytes.copyInto(
                    destination = buffer,
                    destinationOffset = position,
                    startIndex = slice.offset,
                    endIndex = index,
                )
                position += index - slice.offset
                position = writeEscapedRange(bytes = bytes, start = index, end = end, targetStart = position)
                buffer[position++] = '"'.code.toByte()
                return
            }
            index++
        }
        bytes.copyInto(destination = buffer, destinationOffset = position, startIndex = slice.offset, endIndex = end)
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
            index = writeStringChar(value = value, index = index)
        }
        writeByte('"'.code)
    }

    fun writeStringReserved(value: String) {
        buffer[position++] = '"'.code.toByte()
        var index = 0
        while (index < value.length) index = writeStringChar(value = value, index = index)
        buffer[position++] = '"'.code.toByte()
    }

    private fun writeStringChar(value: String, index: Int): Int {
        val char = value[index].code
        if (char < 128) {
            val pair = escapePairs[char]
            if (pair.toInt() == 0) {
                buffer[position++] = char.toByte()
            } else {
                shortView.set(buffer, position, pair)
                position += 2
                if (pair == unicodePair) {
                    intView.set(buffer, position, unicodeTails[char])
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
        longView.set(buffer, position, 0x657669746361222cL)
        longView.set(
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
        bytes: Bytes,
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
            position = writeEscapedRange(bytes = bytes, start = index, end = chunkEnd, targetStart = position)
            index = chunkEnd
        }
    }

    private fun writeEscapedRange(
        bytes: Bytes,
        start: Int,
        end: Int,
        targetStart: Int,
    ): Int {
        val target = buffer
        var index = start
        var offset = targetStart
        if (vectorized) {
            val width = VectorJsonScan.laneCount
            while (end - index >= width) {
                val blockStart = index
                val blockEnd = blockStart + width
                var events = VectorJsonScan.specialMask(bytes = bytes, start = blockStart)
                while (events != 0L) {
                    val special = blockStart + java.lang.Long.numberOfTrailingZeros(events)
                    val length = special - index
                    if (length != 0) {
                        if (length <= 8 && index <= bytes.size - 8) {
                            val word = longView.get(bytes, index) as Long
                            longView.set(target, offset, word)
                        } else {
                            bytes.copyInto(
                                destination = target,
                                destinationOffset = offset,
                                startIndex = index,
                                endIndex = special,
                            )
                        }
                        offset += length
                    }
                    val b = bytes[special].toInt() and 255
                    val escaped = escapePairs[b]
                    shortView.set(target, offset, escaped)
                    offset += 2
                    if (escaped == unicodePair) {
                        intView.set(target, offset, unicodeTails[b])
                        offset += 4
                    }
                    index = special + 1
                    events = events and (events - 1)
                }
                if (index < blockEnd) {
                    bytes.copyInto(
                        destination = target,
                        destinationOffset = offset,
                        startIndex = index,
                        endIndex = blockEnd,
                    )
                    offset += blockEnd - index
                }
                index = blockEnd
            }
        }
        while (index < end) {
            val b = bytes[index++].toInt() and 0xFF
            val escaped = escapePairs[b]
            if (escaped.toInt() != 0) {
                shortView.set(target, offset, escaped)
                offset += 2
                if (escaped == unicodePair) {
                    intView.set(target, offset, unicodeTails[b])
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
        val longView = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.LITTLE_ENDIAN)
        val shortView = MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, ByteOrder.LITTLE_ENDIAN)
        val intView = MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.LITTLE_ENDIAN)
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
        val trueBytes = Bytes(MutBytes(("true".encodeToByteArray())))
        val falseBytes = Bytes(MutBytes(("false".encodeToByteArray())))
        const val hex = "0123456789abcdef"
    }
}
