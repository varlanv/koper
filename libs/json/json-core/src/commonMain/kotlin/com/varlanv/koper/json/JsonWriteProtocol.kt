package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

/**
 * Buffered output operations used by writers and generated codecs.
 * Raw append methods require sufficient capacity reserved by the caller; they return [Unit], advance position,
 * and do not flush. [reserve] may write pending bytes to the sink; [flush] writes all currently pending bytes.
 */
object JsonWriteProtocol {
    /**
     * Writes all pending bytes to the sink and resets position after a successful write.
     * Does nothing when the buffer is empty; retains the allocation and does not close or otherwise flush the sink itself.
     *
     * @return [Unit] after the pending bytes have been written.
     */
    context(sink: ByteSink, writeScope: JsonWriteScope)
    fun flush() {
        val pos = writeScope.position
        if (pos != 0) {
            sink.writeTo(source = writeScope.buffer, offset = 0, length = pos)
            writeScope.position = 0
        }
    }

    /**
     * Ensures capacity for [size] additional bytes after the current position.
     * If capacity is insufficient, flushes pending output and replaces the allocation only if [size] still does not fit.
     * Does not advance position to occupy the reserved space.
     *
     * @return [Unit] with at least [size] bytes available for appending.
     * @throws IllegalArgumentException If [size] is negative.
     */
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

    /** Adds a worst-case escaped string length to an existing bound, rejecting Int overflow. */
    fun addStringSize(maximumBytes: Int, length: Int): Int {
        require(maximumBytes >= 0 && length >= 0) { "Negative JSON size" }
        require(length <= (Int.MAX_VALUE - maximumBytes) / 6) { "JSON reservation is too large" }
        return maximumBytes + length * 6
    }

    /**
     * Appends the low eight bits of [value] as one byte and advances position by one.
     * Requires reserved capacity; does not validate JSON syntax, reserve space, or flush output.
     *
     * @return [Unit] after appending the byte.
     */
    context(writeScope: JsonWriteScope)
    fun writeByte(value: Int) {
        writeScope.buffer[writeScope.position++] = value.toByte()
    }

    /**
     * Copies [length] bytes from [bytes] at [offset] into pending output and advances position by [length].
     * Requires reserved capacity; does not escape, validate, reserve, or flush the bytes.
     *
     * @return [Unit] after appending the requested range.
     */
    context(writeScope: JsonWriteScope)
    fun writeRaw(
        bytes: Bytes,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ) {
        val pos = writeScope.position
        bytes.copyInto(
            destination = writeScope.buffer,
            destinationOffset = pos,
            startIndex = offset,
            endIndex = offset + length,
        )
        writeScope.position = pos + length
    }

    /**
     * Appends four bytes from [first], then two from [second], each in little-endian order.
     * Requires reserved capacity and advances position by six without reserving or flushing output.
     *
     * @return [Unit] after appending the packed bytes.
     */
    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Int, second: Short) {
        val pos = writeScope.position
        val output = writeScope.buffer
        output.setPackedInt(idx = pos, value = first)
        output.setPackedShort(idx = pos + 4, value = second)
        writeScope.position = pos + 6
    }

    /** Appends four little-endian bytes without reserving or flushing output. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Int) {
        val pos = writeScope.position
        writeScope.buffer.setPackedInt(idx = pos, value = value)
        writeScope.position = pos + 4
    }

    /** Appends two little-endian bytes without reserving or flushing output. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Short) {
        val pos = writeScope.position
        writeScope.buffer.setPackedShort(idx = pos, value = value)
        writeScope.position = pos + 2
    }

    /** Appends eight little-endian bytes with the platform's packed stores. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Int, second: Int) {
        val pos = writeScope.position
        jsonWritePackedBytes(target = writeScope.buffer, offset = pos, low = first, high = second)
        writeScope.position = pos + 8
    }

    /** Appends ten little-endian bytes. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(
        first: Int,
        second: Int,
        third: Short,
    ) {
        val output = writeScope.buffer
        val pos = writeScope.position
        jsonWritePackedBytes(target = output, offset = pos, low = first, high = second)
        output.setPackedShort(idx = pos + 8, value = third)
        writeScope.position = pos + 10
    }

    /** Appends twelve little-endian bytes. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(
        first: Int,
        second: Int,
        third: Int,
    ) {
        val output = writeScope.buffer
        val pos = writeScope.position
        jsonWritePackedBytes(target = output, offset = pos, low = first, high = second)
        output.setPackedInt(idx = pos + 8, value = third)
        writeScope.position = pos + 12
    }
}
