package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.text.Charset

/**
 * Validates or decodes buffered string bytes using progress stored in [JsonReadScope].
 * Does not consume the source, refill or allocate buffers, or call the read protocol. The reader handles requests for input.
 */
internal object JsonStringScanner {
    /**
     * Continues a string whose opening quote has already been consumed.
     * Position identifies the next source byte; string output position identifies the next decoded byte when [decode] is true.
     * Decoding writes into the same buffer behind unread input and advances both cursors; validation-only scanning advances
     * position without changing buffer contents or string output position. Does not update the last token or result metadata.
     *
     * @return [Boolean] true with position after the closing quote, or false with position at the exhausted limit or
     * an incomplete escape. On false, the reader must preserve decoded output and unfinished input before refilling.
     * @throws IllegalArgumentException For an invalid escape, unpaired surrogate, or unescaped control byte.
     */
    context(parseScope: JsonReadScope)
    fun scan(
        decode: Boolean,
        specialScan: JsonSpecialScan = jsonScanner,
    ): Boolean {
        val bytes = parseScope.buffer
        val limit = parseScope.limit
        val start = parseScope.position
        val special = specialScan.firstSpecial(
            bytes = Bytes(bytes),
            start = start,
            end = limit,
        )
        if (decode) {
            parseScope.stringOutputPosition =
                copyRun(bytes = bytes, outputPosition = parseScope.stringOutputPosition, start = start, end = special)
        }
        parseScope.position = special
        if (special == limit) {
            return false
        }
        if (bytes[special].toInt() == 34) {
            parseScope.position = special + 1
            return true
        }
        return if (specialScan.isVector && limit - special >= specialScan.laneCount) {
            scanVector(decode = decode, specialScan = specialScan)
        } else {
            scanScalar(decode)
        }
    }

    /**
     * Scans complete lanes using special-byte masks, then handles the remaining bytes with the scalar scanner.
     * Updates the scope's read and output cursors, decoding in place only when [decode] is true; never refills input.
     *
     * @return [Boolean] true after a closing quote or false at exhausted input or an incomplete escape.
     */
    context(parseScope: JsonReadScope)
    private fun scanVector(decode: Boolean, specialScan: JsonSpecialScan): Boolean {
        val bytes = parseScope.buffer
        val limit = parseScope.limit
        var index = parseScope.position
        var output = parseScope.stringOutputPosition
        val width = specialScan.laneCount
        while (limit - index >= width) {
            val start = index
            val end = start + width
            var events = specialScan.specialMask(
                bytes = Bytes(bytes),
                start = start,
            )
            while (events != 0) {
                val special = start + events.countTrailingZeroBits()
                if (decode) {
                    output = copyRun(bytes = bytes, outputPosition = output, start = index, end = special)
                }
                index = special
                val value = bytes[index].toInt() and 255
                if (value == 34) {
                    parseScope.position = index + 1
                    parseScope.stringOutputPosition = output
                    return true
                }
                require(value == 92) { "Unescaped control character" }
                parseScope.stringOutputPosition = output
                val next = readEscape(bytes = bytes, index = index, limit = limit, decode = decode)
                if (next == index) {
                    parseScope.position = index
                    return false
                }
                index = next
                output = parseScope.stringOutputPosition
                val consumed = index - start
                if (consumed >= width) {
                    break
                }
                events = events and (-1 shl consumed)
            }
            if (index < end) {
                if (decode) {
                    output = copyRun(bytes = bytes, outputPosition = output, start = index, end = end)
                }
                index = end
            }
        }
        parseScope.position = index
        parseScope.stringOutputPosition = output
        return scanScalar(decode)
    }

    /**
     * Scans bytes individually from the current position and updates read and output cursors.
     * Writes decoded bytes in place only when [decode] is true; never refills input.
     *
     * @return [Boolean] true after a closing quote or false at exhausted input or an incomplete escape.
     */
    context(parseScope: JsonReadScope)
    private fun scanScalar(decode: Boolean): Boolean {
        val bytes = parseScope.buffer
        val limit = parseScope.limit
        var index = parseScope.position
        var output = parseScope.stringOutputPosition
        while (index < limit) {
            val value = bytes[index].toInt() and 255
            if (value == 34) {
                parseScope.position = index + 1
                parseScope.stringOutputPosition = output
                return true
            }
            require(value >= 32) { "Unescaped control character" }
            if (value == 92) {
                parseScope.stringOutputPosition = output
                val next = readEscape(bytes = bytes, index = index, limit = limit, decode = decode)
                if (next == index) {
                    parseScope.position = index
                    return false
                }
                index = next
                output = parseScope.stringOutputPosition
            } else {
                if (decode) {
                    bytes[output++] = value.toByte()
                }
                index++
            }
        }
        parseScope.position = index
        parseScope.stringOutputPosition = output
        return false
    }

    /**
     * Copies ordinary bytes from [start] until [end] to [outputPosition] in the same buffer.
     * Requires output at or behind source. A packed store may extend past the logical output run,
     * but never past [end] into unread source bytes. Does not change scope cursors.
     *
     * @return The next output offset as an [Int].
     */
    private fun copyRun(
        bytes: MutBytes,
        outputPosition: Int,
        start: Int,
        end: Int,
    ): Int {
        val size = end - start
        if (size != 0 && outputPosition != start) {
            if (size <= 4 && start <= bytes.size - 4 && outputPosition <= end - 4) {
                val word = bytes.getPackedInt(start)
                bytes.setPackedInt(idx = outputPosition, value = word)
            } else {
                bytes.copyInto(
                    destination = bytes,
                    destinationOffset = outputPosition,
                    startIndex = start,
                    endIndex = end,
                )
            }
        }
        return outputPosition + size
    }

    /**
     * Validates the escape beginning at [index], including a complete surrogate pair when required.
     * When [decode] is true, writes its UTF-8 bytes at the scope's string output position and advances that cursor.
     * Leaves scope position unchanged; incomplete escapes leave output unchanged and require more buffered input.
     *
     * @return The first source offset after the escape as an [Int], or [index] if it is incomplete.
     * @throws IllegalArgumentException For an invalid escape or surrogate pair.
     */
    context(parseScope: JsonReadScope)
    private fun readEscape(
        bytes: MutBytes,
        index: Int,
        limit: Int,
        decode: Boolean,
    ): Int {
        if (limit - index < 2) {
            return index
        }
        val escaped = bytes[index + 1].toInt() and 255
        if (escaped != 117) {
            val value: Byte = when (escaped) {
                34, 92, 47 -> escaped.toByte()
                98 -> 8
                102 -> 12
                110 -> 10
                114 -> 13
                116 -> 9
                else -> throw IllegalArgumentException("Invalid JSON escape")
            }
            var output = parseScope.stringOutputPosition
            if (decode) {
                bytes[output++] = value
            }
            parseScope.stringOutputPosition = output
            return index + 2
        }
        if (limit - index < 6) {
            return index
        }
        var codepoint = readHex(bytes = bytes, index = index + 2)
        var consumed = 6
        if (codepoint in 0xD800..0xDBFF) {
            if (limit - index < 12) {
                return index
            }
            require(bytes[index + 6].toInt() == 92 && bytes[index + 7].toInt() == 117) {
                "Expected low surrogate escape"
            }
            val low = readHex(bytes = bytes, index = index + 8)
            require(low in 0xDC00..0xDFFF) { "Invalid low surrogate" }
            codepoint = 0x10000 + ((codepoint - 0xD800) shl 10) + low - 0xDC00
            consumed = 12
        } else {
            require(codepoint !in 0xDC00..0xDFFF) { "Unpaired low surrogate" }
        }
        var output = parseScope.stringOutputPosition
        if (decode) {
            Charset.Utf8.encodeCodepointInline(codepoint) { bytes[output++] = it }
        }
        parseScope.stringOutputPosition = output
        return index + consumed
    }

    /**
     * Reads four buffered hexadecimal digits at [index] without modifying bytes or scope state.
     * Requires four available bytes.
     *
     * @return Their unsigned 16-bit value as an [Int].
     * @throws IllegalArgumentException If a digit is not hexadecimal.
     */
    private fun readHex(bytes: MutBytes, index: Int): Int {
        return (hexDigit(bytes[index].toInt() and 255) shl 12) or
            (hexDigit(bytes[index + 1].toInt() and 255) shl 8) or
            (hexDigit(bytes[index + 2].toInt() and 255) shl 4) or
            hexDigit(bytes[index + 3].toInt() and 255)
    }

    /**
     * Converts an ASCII hexadecimal byte without changing state.
     *
     * @return The digit value as an [Int] in 0..15.
     * @throws IllegalArgumentException If [value] is not a hexadecimal digit.
     */
    private fun hexDigit(value: Int): Int {
        return when (value) {
            in 48..57 -> value - 48
            in 65..70 -> value - 55
            in 97..102 -> value - 87
            else -> throw IllegalArgumentException("Invalid Unicode escape")
        }
    }
}
