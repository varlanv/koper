package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource

/**
 * Token and structural operations used by readers and generated codecs.
 * Operations share mutable [JsonReadScope] state; source-reading methods may refill input and invalidate borrowed bytes.
 * Field matching helpers inspect already buffered bytes without decoding or refilling.
 */
object JsonReadProtocol {
    /**
     * Skips JSON whitespace and consumes the next byte, storing it as the scope's last token.
     * May refill the buffer and consume input from the source; does not decode the token's value.
     *
     * @return The token byte as an [Int] in 0..255, or -1 at end of input.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun nextToken(): Int {
        var value = JsonReadBuffer.take()
        while (value == 32 || value == 9 || value == 10 || value == 13) value = JsonReadBuffer.take()
        parseScope.last = value
        return value
    }

    /**
     * Consumes the colon after a field name and the first non-whitespace byte of its value.
     * Advances position and stores that byte as the last token; may refill input.
     *
     * @return [Unit], with the value's first byte already consumed.
     * @throws IllegalArgumentException If the colon or value is missing.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun nextFieldValue() {
        val index = parseScope.position
        if (parseScope.limit - index >= 2 && parseScope.buffer[index].toInt() == 58) {
            val value = parseScope.buffer[index + 1].toInt() and 255
            if (value > 32) {
                parseScope.position = index + 2
                parseScope.last = value
                return
            }
        }
        require(nextToken() == 58) { "Expected colon" }
        require(nextToken() != -1) { "Expected JSON value" }
    }

    context(parseScope: JsonReadScope)
    fun peekFieldWord(offset: Int = 0): Int {
        return if (parseScope.limit - parseScope.position - offset >= 4) {
            parseScope.buffer.getPackedInt(parseScope.position + offset)
        } else {
            0
        }
    }

    /**
     * Consumes [length] field-name bytes and their buffered closing quote after the caller has matched the name.
     * Checks only the quote at that length; does not decode or validate the name, refill input, or update the last token.
     *
     * @return [Boolean] true after advancing position, or false with state unchanged if the closing quote is unavailable.
     */
    context(parseScope: JsonReadScope)
    fun consumeField(length: Int): Boolean {
        val position = parseScope.position
        if (parseScope.limit - position <= length || parseScope.buffer[position + length].toInt() != 34) {
            return false
        }
        parseScope.position = position + length + 1
        return true
    }

    /**
     * Compares the buffered name at position with [expected] and checks its following closing quote.
     * Does not consume bytes, decode escapes, refill input, or change state.
     *
     * @return [Boolean] true for a complete byte-for-byte match; false also covers insufficient buffered input.
     */
    context(parseScope: JsonReadScope)
    fun fieldMatches(expected: ByteArray): Boolean {
        val start = parseScope.position
        if (parseScope.limit - start <= expected.size) {
            return false
        }
        for (index in expected.indices) {
            if (parseScope.buffer[start + index] != expected[index]) {
                return false
            }
        }
        return parseScope.buffer[start + expected.size].toInt() == 34
    }

    /**
     * Skips a previously matched [length]-byte name, closing quote, and colon without rechecking them.
     * Consumes the value's first non-whitespace byte into the last token and may refill input.
     *
     * @return [Unit], with position immediately after the value's first byte.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun consumeMatchedFieldColon(length: Int) {
        nextValue(parseScope.position + length + 2)
    }

    /**
     * Checks the buffered closing quote and colon after [length] previously matched name bytes.
     * On success advances to the value's first non-whitespace byte, updates the last token, and may refill input.
     *
     * @return [Boolean] true after consuming the value's first byte, or false with state unchanged if quote or colon is unavailable.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun consumeFieldColon(length: Int): Boolean {
        val limit = parseScope.limit
        val position = parseScope.position
        if (limit - position <= length + 1 ||
            parseScope.buffer[position + length].toInt() != 34 ||
            parseScope.buffer[position + length + 1].toInt() != 58
        ) {
            return false
        }
        nextValue(position + length + 2)
        return true
    }

    /**
     * Consumes a closing brace or a comma followed by the next field's opening quote, skipping whitespace as needed.
     * Advances position, updates the last token, and may refill input.
     *
     * @return An [Int] equal to 125 for a closing brace or 34 for a field opening quote.
     * @throws IllegalArgumentException If the next object separator or field opening quote is invalid.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun nextFieldOrEnd(): Int {
        val index = parseScope.position
        val limit = parseScope.limit
        if (limit - index >= 2 && parseScope.buffer[index].toInt() == 44 && parseScope.buffer[index + 1].toInt() == 34
        ) {
            parseScope.position = index + 2
            parseScope.last = 34
            return 34
        }
        if (index < limit && parseScope.buffer[index].toInt() == 125) {
            parseScope.position = index + 1
            parseScope.last = 125
            return 125
        }
        val token = nextToken()
        if (token == 125) {
            return token
        }
        require(token == 44) { "Expected comma or closing brace" }
        val field = nextToken()
        require(field == 34) { "Expected JSON field name" }
        return field
    }

    /**
     * Reads a field name after its opening quote, decoding escapes when necessary.
     * Advances position past the closing quote and sets field offset and byte length in the scope.
     * The resulting bytes are borrowed and must be compared before further reads can overwrite them.
     * May refill, compact, or grow input; leaves the last token unchanged.
     *
     * @return An [Int] hash computed from zero as hash * 31 + each unsigned decoded UTF-8 byte; collisions require [fieldEquals].
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun readField(): Int {
        require(parseScope.last == 34) { "Expected JSON field name" }
        val start = parseScope.position
        var index = start
        var hash = 0
        while (index < parseScope.limit) {
            val value = parseScope.buffer[index].toInt() and 255
            if (value == 34) {
                parseScope.fieldSize = index - start
                parseScope.fieldOffset = start
                parseScope.position = index + 1
                return hash
            }
            if (value == 92 || value < 32) {
                break
            }
            hash = 31 * hash + value
            index++
        }
        JsonReadBuffer.readString()
        parseScope.fieldSize = parseScope.stringLength
        parseScope.fieldOffset = parseScope.stringOffset
        hash = 0
        for (i in 0 until parseScope.fieldSize) {
            hash = 31 * hash + (parseScope.buffer[parseScope.fieldOffset + i].toInt() and 255)
        }
        return hash
    }

    /**
     * Compares [expected] with the decoded field bytes described by the scope's current field offset and size.
     * Requires those borrowed bytes to remain valid; does not consume input or change state.
     *
     * @return [Boolean] true if the length and every byte match.
     */
    context(parseScope: JsonReadScope)
    fun fieldEquals(expected: ByteArray): Boolean {
        if (parseScope.fieldSize != expected.size) {
            return false
        }
        for (i in expected.indices) if (parseScope.buffer[parseScope.fieldOffset + i] != expected[i]) {
            return false
        }
        return true
    }

    /**
     * Consumes and validates the value whose first byte is already stored as the last token.
     * Recurses through objects and arrays, validates string escapes without retaining their contents, and leaves the following
     * separator unread. Advances scope state and may refill, compact, or grow input; the last token is not restored.
     *
     * @return [Unit] after the value has been consumed.
     * @throws IllegalArgumentException For invalid syntax or nesting deeper than 128 recursive levels.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    fun skipValue() {
        skipValue(0)
    }

    /**
     * Skips the current value at [depth], updating scope state and consuming source bytes as required.
     *
     * @return [Unit] after the value, with its following separator left unread.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun skipValue(depth: Int) {
        require(depth <= 128) { "JSON nesting limit exceeded" }
        when (parseScope.last) {
            34 -> {
                JsonReadBuffer.skipString()
            }

            116 -> {
                readLiteral("rue")
            }

            102 -> {
                readLiteral("alse")
            }

            110 -> {
                readLiteral("ull")
            }

            123 -> {
                if (nextToken() == 125) {
                    return
                }
                while (true) {
                    require(parseScope.last == 34) { "Expected JSON field name" }
                    JsonReadBuffer.skipString()
                    require(nextToken() == 58) { "Expected colon" }
                    nextToken()
                    skipValue(depth + 1)
                    when (nextToken()) {
                        125 -> return
                        44 -> nextToken()
                        else -> throw IllegalArgumentException("Expected comma or closing brace")
                    }
                }
            }

            91 -> {
                if (nextToken() == 93) {
                    return
                }
                while (true) {
                    skipValue(depth + 1)
                    when (nextToken()) {
                        93 -> return
                        44 -> nextToken()
                        else -> throw IllegalArgumentException("Expected comma or closing bracket")
                    }
                }
            }

            45, in 48..57 -> {
                skipNumber()
            }

            else -> {
                throw IllegalArgumentException("Expected JSON value")
            }
        }
    }

    /**
     * Consumes a JSON number starting with the last token, including any fraction and exponent.
     * Advances position and may refill input; validates but does not consume the following delimiter or update the last token.
     *
     * @return [Unit] after a syntactically valid number.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun skipNumber() {
        var digit = if (parseScope.last == 45) {
            JsonReadBuffer.take()
        } else {
            parseScope.last
        }
        require(digit in 48..57) { "Expected JSON number" }

        if (digit == 48) {
            digit = JsonReadBuffer.peek()
            require(digit !in 48..57) { "Leading zero in JSON number" }
        } else {
            digit = JsonReadBuffer.peek()
            while (digit in 48..57) {
                parseScope.position++
                digit = JsonReadBuffer.peek()
            }
        }
        if (digit == 46) {
            parseScope.position++
            require(JsonReadBuffer.peek() in 48..57) { "Expected fraction digits" }
            do {
                parseScope.position++
                digit = JsonReadBuffer.peek()
            } while (digit in 48..57)
        }
        if (digit == 101 || digit == 69) {
            parseScope.position++
            digit = JsonReadBuffer.peek()
            if (digit == 43 || digit == 45) {
                parseScope.position++
            }
            require(JsonReadBuffer.peek() in 48..57) { "Expected exponent digits" }
            do {
                parseScope.position++
                digit = JsonReadBuffer.peek()
            } while (digit in 48..57)
        }
        requireDelimiter(digit)
    }

    /**
     * Consumes [tail] after a literal's first byte and validates its following delimiter without consuming it.
     * Advances position and may refill input; leaves the last token unchanged.
     *
     * @return [Unit] after a valid literal suffix.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun readLiteral(tail: String) {
        for (char in tail) require(JsonReadBuffer.take() == char.code) { "Invalid JSON literal" }
        requireDelimiter(JsonReadBuffer.peek())
    }

    /**
     * Checks that [value] is whitespace, a comma, a closing bracket or brace, or the end-of-input marker.
     * Does not consume input or change state.
     *
     * @return [Unit] when the delimiter is valid.
     * @throws IllegalArgumentException For any other byte.
     */
    internal fun requireDelimiter(value: Int) {
        require(
            value == -1 ||
                value == 32 ||
                value == 9 ||
                value == 10 ||
                value == 13 ||
                value == 44 ||
                value == 93 ||
                value == 125,
        ) { "Invalid JSON value suffix" }
    }

    /**
     * Moves position to [index] and consumes the next non-whitespace byte into the last token.
     * May refill input when the byte is unavailable in the current buffer.
     *
     * @return [Unit], with the first value byte consumed.
     * @throws IllegalArgumentException If no value byte is available.
     */
    context(input: ByteSource, parseScope: JsonReadScope)
    private fun nextValue(index: Int) {
        parseScope.position = index
        if (index < parseScope.limit) {
            val value = parseScope.buffer[index].toInt() and 255
            if (value > 32) {
                parseScope.position = index + 1
                parseScope.last = value
                return
            }
        }
        require(nextToken() != -1) { "Expected JSON value" }
    }
}
