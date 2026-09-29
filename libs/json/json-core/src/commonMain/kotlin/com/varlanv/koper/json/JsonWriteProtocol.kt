package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

object JsonWriteProtocol {

    context(sink: ByteSink, writeScope: JsonWriteScope)
    fun flush() {
        if (writeScope.position != 0) {
            sink.writeTo(source = writeScope.buffer, offset = 0, length = writeScope.position)
            writeScope.position = 0
        }
    }

    context(sink: ByteSink, writeScope: JsonWriteScope)
    fun reserve(size: Int) {
        require(size >= 0) { "Negative JSON reservation" }
        if (writeScope.buffer.size - writeScope.position < size) {
            flush()
            if (writeScope.buffer.size < size) {
                writeScope.buffer = MutBytes(size.bytes())
            }
        }
    }

    context(sink: ByteSink, writeScope: JsonWriteScope)
    fun reserve(size: Long) {
        require(size in 0L..Int.MAX_VALUE.toLong()) { "JSON reservation is too large" }
        reserve(size.toInt())
    }

    context(writeScope: JsonWriteScope)
    fun writeByte(value: Int) {
        writeScope.buffer[writeScope.position++] = value.toByte()
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(
        bytes: Bytes,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ) {
        bytes.copyInto(
            destination = writeScope.buffer,
            destinationOffset = writeScope.position,
            startIndex = offset,
            endIndex = offset + length,
        )
        writeScope.position += length
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Int, second: Short) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedShort(idx = writeScope.position+ 4, value = second)
        writeScope.position += 6
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Long) {
        writeScope.buffer.setPackedLong(idx = writeScope.position, value = value)
        writeScope.position += 8
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Long, second: Short) {
        writeScope.buffer.setPackedLong(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedShort(idx =writeScope. position+ 8, value = second)
        writeScope.position += 10
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Long, second: Int) {
        writeScope.buffer.setPackedLong(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedInt(idx = writeScope.position + 8, value = second)
        writeScope.position += 12
    }
}
