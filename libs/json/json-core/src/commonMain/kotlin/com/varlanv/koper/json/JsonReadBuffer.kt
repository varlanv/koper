package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource

/**
 * Buffered input operations sharing [JsonReadScope] with the protocol and string scanner.
 * Refills consume the source; string reads additionally compact or grow the scope's single buffer.
 */
internal object JsonReadBuffer {
    /**
     * Consumes one byte, refilling exhausted input if needed, and advances the scope's position on success.
     * Leaves the last protocol token unchanged; a refill can invalidate borrowed buffer bytes.
     *
     * @return An unsigned byte as an [Int] in 0..255, or -1 at end of input.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun take(): Int {
        val value = peek()
        if (value >= 0) {
            parseScope.position++
        }
        return value
    }

    /**
     * Reads the next byte without consuming it from the buffer. Exhausted input triggers a refill,
     * which consumes the source, resets buffer offsets, and can invalidate borrowed bytes.
     *
     * @return An unsigned byte as an [Int] in 0..255, or -1 at end of input.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun peek(): Int {
        if (parseScope.position == parseScope.limit && !refill()) {
            return -1
        }
        return parseScope.buffer[parseScope.position].toInt() and 255
    }

    /**
     * Discards buffered contents and reads the next chunk into the existing allocation.
     * Resets position to zero and sets limit to the bytes read, or zero at end of input.
     * Call only after consuming buffered input and releasing borrowed field or string bytes; this does not grow the buffer.
     *
     * @return [Boolean] true when bytes were read, or false at end of input.
     * @throws IllegalStateException If the source returns zero for a nonempty read.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun refill(): Boolean {
        parseScope.position = 0
        parseScope.limit = 0
        return appendInput()
    }

    /**
     * Reads a string after its opening quote has been consumed into the last protocol token.
     * Decodes escapes in place, advances position past the closing quote, and sets string offset and length.
     * May read ahead, compact or grow the buffer, and invalidate previous borrowed bytes; leaves the last token unchanged.
     *
     * @return [Unit]; decoded bytes are borrowed from the scope's buffer using its string offset and length.
     * @throws IllegalArgumentException If the token or string syntax is invalid, or the string is unterminated.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun readString() {
        scanString(true)
    }

    /**
     * Validates a string after its opening quote without retaining decoded content.
     * Advances position past the closing quote, resets string metadata, and leaves the last token unchanged.
     * May refill or compact input; incomplete escapes can require growth even though skipped content is discarded.
     *
     * @return [Unit] after the complete string has been consumed.
     * @throws IllegalArgumentException If the token or string syntax is invalid, or the string is unterminated.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun skipString() {
        scanString(false)
    }

    /**
     * Initializes string state and drives buffered scans until the closing quote is consumed.
     * Retains decoded output only when [decode] is true; refills preserve unfinished escape bytes and may grow storage.
     *
     * @return [Unit], with the scope's position and string metadata updated.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun scanString(decode: Boolean) {
        require(parseScope.last == 34) { "Expected JSON string" }
        parseScope.stringOutputPosition = parseScope.position
        parseScope.stringOffset = parseScope.position
        parseScope.stringLength = 0
        while (!JsonStringScanner.scan(decode = decode)) {
            val outputSize = if (decode) {
                parseScope.stringOutputPosition - parseScope.stringOffset
            } else {
                0
            }
            val remaining = parseScope.limit - parseScope.position
            if (decode && parseScope.stringOffset != 0) {
                parseScope.buffer.copyInto(
                    destination = parseScope.buffer,
                    destinationOffset = 0,
                    startIndex = parseScope.stringOffset,
                    endIndex = parseScope.stringOutputPosition,
                )
            }
            if (remaining > 0 && parseScope.position != outputSize) {
                parseScope.buffer.copyInto(
                    destination = parseScope.buffer,
                    destinationOffset = outputSize,
                    startIndex = parseScope.position,
                    endIndex = parseScope.limit,
                )
            }
            parseScope.stringOffset = 0
            parseScope.stringOutputPosition = outputSize
            parseScope.position = outputSize
            parseScope.limit = outputSize + remaining
            if (parseScope.limit == parseScope.buffer.size) {
                val capacity = parseScope.buffer.size
                check(capacity < Int.MAX_VALUE) { "JSON buffer capacity exhausted" }
                parseScope.buffer =
                    parseScope.buffer.copyOf((capacity.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            require(appendInput()) { "Unterminated JSON string" }
        }
        if (decode) {
            parseScope.stringLength = parseScope.stringOutputPosition - parseScope.stringOffset
        }
    }

    /**
     * Reads into free capacity after the current limit and increases limit by the count read.
     * Preserves existing bytes and position; requires at least one free byte and rejects zero-byte source reads.
     *
     * @return [Boolean] true when bytes were appended, or false at end of input.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun appendInput(): Boolean {
        val count = input.readAtMostTo(
            sink = parseScope.buffer,
            offset = parseScope.limit,
            length = parseScope.buffer.size - parseScope.limit,
        )
        if (count <= 0) {
            check(count != 0) { "ByteSource returned zero bytes for a non-empty read" }
            return false
        }
        parseScope.limit += count
        return true
    }
}
