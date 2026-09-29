package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

class JsonWriteProtocol {
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
        bytes.copyInto(
            destination = buffer,
            destinationOffset = position,
            startIndex = offset,
            endIndex = offset + length,
        )
        position += length
    }

    fun writeRaw(first: Int, second: Short) {
        buffer.setPackedInt(idx = position, value = first)
        buffer.setPackedShort(idx = position+ 4, value = second)
        position += 6
    }

    fun writeRaw(value: Long) {
        buffer.setPackedLong(idx = position, value = value)
        position += 8
    }

    fun writeRaw(first: Long, second: Short) {
        buffer.setPackedLong(idx = position, value = first)
        buffer.setPackedShort(idx = position+ 8, value = second)
        position += 10
    }

    fun writeRaw(first: Long, second: Int) {
        buffer.setPackedLong(idx = position, value = first)
        buffer.setPackedInt(idx = position + 8, value = second)
        position += 12
    }
}
