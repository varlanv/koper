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
        if (writeScope.position != 0) {
            sink.writeTo(source = writeScope.buffer, offset = 0, length = writeScope.position)
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

    /**
     * Validates a [Long] reservation and delegates to the [Int] capacity reservation.
     * May flush pending output and replace the buffer; does not occupy the reserved space.
     *
     * @return [Unit] with at least [size] bytes available for appending.
     * @throws IllegalArgumentException If [size] is negative or exceeds Int.MAX_VALUE.
     */
    context(sink: ByteSink, writeScope: JsonWriteScope)
    fun reserve(size: Long) {
        require(size in 0L..Int.MAX_VALUE.toLong()) { "JSON reservation is too large" }
        reserve(size.toInt())
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
        bytes.copyInto(
            destination = writeScope.buffer,
            destinationOffset = writeScope.position,
            startIndex = offset,
            endIndex = offset + length,
        )
        writeScope.position += length
    }

    /**
     * Appends four bytes from [first], then two from [second], each in little-endian order.
     * Requires reserved capacity and advances position by six without reserving or flushing output.
     *
     * @return [Unit] after appending the packed bytes.
     */
    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Int, second: Short) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedShort(idx = writeScope.position + 4, value = second)
        writeScope.position += 6
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Int) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = value)
        writeScope.position += 4
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(value: Short) {
        writeScope.buffer.setPackedShort(idx = writeScope.position, value = value)
        writeScope.position += 2
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(first: Int, second: Int) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedInt(idx = writeScope.position + 4, value = second)
        writeScope.position += 8
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(
        first: Int,
        second: Int,
        third: Short,
    ) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedInt(idx = writeScope.position + 4, value = second)
        writeScope.buffer.setPackedShort(idx = writeScope.position + 8, value = third)
        writeScope.position += 10
    }

    context(writeScope: JsonWriteScope)
    fun writeRaw(
        first: Int,
        second: Int,
        third: Int,
    ) {
        writeScope.buffer.setPackedInt(idx = writeScope.position, value = first)
        writeScope.buffer.setPackedInt(idx = writeScope.position + 4, value = second)
        writeScope.buffer.setPackedInt(idx = writeScope.position + 8, value = third)
        writeScope.position += 12
    }
}
