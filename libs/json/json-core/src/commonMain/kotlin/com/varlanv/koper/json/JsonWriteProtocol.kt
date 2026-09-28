package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

class JsonWriteProtocol(vectorized: Boolean = false) {
    internal val scan = jsonSpecialScan(vectorized)
    internal var buffer = MutBytes(512.bytes())
    internal var position = 0
    private lateinit var output: ByteSink

    fun reset(output: ByteSink) {
        this.output = output
        position = 0
    }

    fun flush() {
        if (position != 0) {
            output.writeTo(source = buffer, offset = 0, length = position)
            position = 0
        }
    }

    fun reserve(size: Int) {
        require(size >= 0) { "Negative JSON reservation" }
        if (buffer.size - position < size) {
            flush()
            if (buffer.size < size) {
                buffer = MutBytes(size.bytes())
            }
        }
    }

    fun reserve(size: Long) {
        require(size in 0L..Int.MAX_VALUE.toLong()) { "JSON reservation is too large" }
        reserve(size.toInt())
    }

    fun writeByte(value: Int) {
        buffer[position++] = value.toByte()
    }

    fun writeRaw(
        bytes: Bytes,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ) {
        bytes.copyInto(buffer, position, offset, offset + length)
        position += length
    }

    fun writeRaw(first: Int, second: Short) {
        buffer.setPackedInt(position, first)
        buffer.setPackedShort(position+4, second)
        position += 6
    }

    fun writeRaw(value: Long) {
        buffer.setPackedLong(position, value)
        position += 8
    }

    fun writeRaw(first: Long, second: Short) {
        buffer.setPackedLong(position, first)
        buffer.setPackedShort(position+8, second)
        position += 10
    }

    fun writeRaw(first: Long, second: Int) {
        buffer.setPackedLong(position, first)
        buffer.setPackedInt(position + 8, second)
        position += 12
    }
}
