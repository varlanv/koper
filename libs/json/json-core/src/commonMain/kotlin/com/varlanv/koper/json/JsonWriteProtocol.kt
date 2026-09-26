package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink

class JsonWriteProtocol(vectorized: Boolean = false) {
    internal val scan = jsonSpecialScan(vectorized)
    internal var buffer = ByteArray(512)
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
                buffer = ByteArray(size)
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
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ) {
        bytes.copyInto(buffer, position, offset, offset + length)
        position += length
    }

    fun writeRaw(first: Int, second: Short) {
        PackedJsonBytes.setInt(bytes = buffer, offset = position, value = first)
        PackedJsonBytes.setShort(bytes = buffer, offset = position + 4, value = second)
        position += 6
    }

    fun writeRaw(value: Long) {
        PackedJsonBytes.setLong(bytes = buffer, offset = position, value = value)
        position += 8
    }

    fun writeRaw(first: Long, second: Short) {
        PackedJsonBytes.setLong(bytes = buffer, offset = position, value = first)
        PackedJsonBytes.setShort(bytes = buffer, offset = position + 8, value = second)
        position += 10
    }

    fun writeRaw(first: Long, second: Int) {
        PackedJsonBytes.setLong(bytes = buffer, offset = position, value = first)
        PackedJsonBytes.setInt(bytes = buffer, offset = position + 8, value = second)
        position += 12
    }
}
