@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.asReadonly
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
    inline fun flush(position: Int): Int {
        if (position != 0) {
            sink.writeTo(source = writeScope.buffer.asReadonly(), offset = 0, length = position)
            return 0
        }
        return position
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
    inline fun reserve(size: Int, position: Int): Int {
        require(size >= 0) { "Negative JSON reservation" }
        if (writeScope.buffer.size - position < size) {
            flush(position)
            if (writeScope.buffer.size < size) {
                writeScope.buffer = MutBytes(size.bytes())
            }
            return 0
        }
        return position
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
    inline fun writeByte(value: Int, position: Int) {
        writeScope.buffer[position] = value.toByte()
    }

    /**
     * Copies [length] bytes from [bytes] at [offset] into pending output and advances position by [length].
     * Requires reserved capacity; does not escape, validate, reserve, or flush the bytes.
     *
     * @return [Unit] after appending the requested range.
     */
    context(writeScope: JsonWriteScope)
    inline fun writeRaw(
        bytes: Bytes,
        position: Int,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ): Int {
        bytes.copyInto(
            destination = writeScope.buffer,
            destinationOffset = position,
            startIndex = offset,
            endIndex = offset + length,
        )
        return length
    }

    /**
     * Appends four bytes from [first], then two from [second], each in little-endian order.
     * Requires reserved capacity and advances position by six without reserving or flushing output.
     *
     * @return [Unit] after appending the packed bytes.
     */
    context(writeScope: JsonWriteScope)
    inline fun writeRaw(
        first: Int,
        second: Short,
        position: Int,
    ) {
        val output = writeScope.buffer
        output.setPackedInt(idx = position, value = first)
        output.setPackedShort(idx = position + 4, value = second)
        //        writeScope.position = pos + 6
    }

    /** Appends four little-endian bytes without reserving or flushing output. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Int, position: Int) {
        writeScope.buffer.setPackedInt(idx = position, value = value)
        //        writeScope.position = pos + 4
    }

    /** Appends two little-endian bytes without reserving or flushing output. */
    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Short, position: Int) {
        writeScope.buffer.setPackedShort(idx = position, value = value)
    }

    /** Appends eight little-endian bytes with the platform's packed stores. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    inline fun writeRaw(
        first: Int,
        second: Int,
        position: Int,
    ) {
        jsonWritePackedBytes(target = writeScope.buffer, offset = position, low = first, high = second)
    }

    /** Appends ten little-endian bytes. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    inline fun writeRaw(
        first: Int,
        second: Int,
        third: Short,
        position: Int,
    ) {
        val output = writeScope.buffer
        jsonWritePackedBytes(target = output, offset = position, low = first, high = second)
        output.setPackedShort(idx = position + 8, value = third)
    }

    /** Appends twelve little-endian bytes. Requires reserved capacity. */
    context(writeScope: JsonWriteScope)
    inline fun writeRaw(
        first: Int,
        second: Int,
        third: Int,
        position: Int,
    ) {
        val output = writeScope.buffer
        jsonWritePackedBytes(target = output, offset = position, low = first, high = second)
        output.setPackedInt(idx = position + 8, value = third)
    }
}
