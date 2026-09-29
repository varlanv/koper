package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource

object JsonReadProtocol {
    //    context(parseScope: JsonParseScope)
    //    fun reset(input: ByteSource) {
    //        this.input = input
    //        parseScope.position = 0
    //        parseScope.limit = 0
    //        last = -1
    //        fieldSize = 0
    //    }

    context(input: ByteSource, parseScope: JsonParseScope)
    fun nextToken(): Int {
        var value = take()
        while (value == 32 || value == 9 || value == 10 || value == 13) value = take()
        parseScope.last = value
        return value
    }

    context(input: ByteSource, parseScope: JsonParseScope)
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

    context(parseScope: JsonParseScope)
    fun peekFieldWord(): Long {
        return if (parseScope.limit - parseScope.position >= 8) {
            parseScope.buffer.getPackedLong(parseScope.position)
        } else {
            0L
        }
    }

    context(parseScope: JsonParseScope)
    fun consumeField(length: Int): Boolean {
        val position = parseScope.position
        if (parseScope.limit - position <= length || parseScope.buffer[position + length].toInt() != 34) {
            return false
        }
        parseScope.position = position + length + 1
        return true
    }

    context(parseScope: JsonParseScope)
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

    context(input: ByteSource, parseScope: JsonParseScope)
    fun consumeMatchedFieldColon(length: Int) {
        nextValue(parseScope.position + length + 2)
    }

    context(input: ByteSource, parseScope: JsonParseScope)
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

    context(input: ByteSource, parseScope: JsonParseScope)
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

    context(input: ByteSource, parseScope: JsonParseScope)
    fun readField(): Int {
        require(parseScope.last == 34) { "Expected JSON field name" }
        val start = parseScope.position
        var index = start
        var hash = 0
        while (index < parseScope.limit) {
            val value = parseScope.buffer[index].toInt() and 255
            if (value == 34) {
                parseScope.fieldSize = index - start
                parseScope.field = parseScope.buffer
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
        JsonStringScannerV2.readToScratch()
        parseScope.fieldSize = parseScope.scratchLength
        parseScope.field = parseScope.scratchBytes
        parseScope.fieldOffset = parseScope.scratchOffset
        hash = 0
        for (i in 0 until parseScope.fieldSize) hash = 31 * hash + (parseScope.field[i].toInt() and 255)
        return hash
    }

    context(parseScope: JsonParseScope)
    fun fieldEquals(expected: ByteArray): Boolean {
        if (parseScope.fieldSize != expected.size) {
            return false
        }
        for (i in expected.indices) if (parseScope.field[parseScope.fieldOffset + i] != expected[i]) {
            return false
        }
        return true
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    fun skipValue() {
        skipValue(0)
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun skipValue(depth: Int) {
        require(depth <= 128) { "JSON nesting limit exceeded" }
        when (parseScope.last) {
            34 -> {
                JsonStringScannerV2.readToScratch()
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
                    JsonStringScannerV2.readToScratch()
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

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun skipNumber() {
        var digit = if (parseScope.last == 45) {
            take()
        } else {
            parseScope.last
        }
        require(digit in 48..57) { "Expected JSON number" }

        if (digit == 48) {
            digit = peek()
            require(digit !in 48..57) { "Leading zero in JSON number" }
        } else {
            digit = peek()
            while (digit in 48..57) {
                parseScope.position++
                digit = peek()
            }
        }
        if (digit == 46) {
            parseScope.position++
            require(peek() in 48..57) { "Expected fraction digits" }
            do {
                parseScope.position++
                digit = peek()
            } while (digit in 48..57)
        }
        if (digit == 101 || digit == 69) {
            parseScope.position++
            digit = peek()
            if (digit == 43 || digit == 45) {
                parseScope.position++
            }
            require(peek() in 48..57) { "Expected exponent digits" }
            do {
                parseScope.position++
                digit = peek()
            } while (digit in 48..57)
        }
        requireDelimiter(digit)
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun readLiteral(tail: String) {
        for (char in tail) require(take() == char.code) { "Invalid JSON literal" }
        requireDelimiter(peek())
    }

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

    context(input: ByteSource, parseScope: JsonParseScope)
    internal fun take(): Int {
        val value = peek()
        if (value >= 0) {
            parseScope.position++
        }
        return value
    }

    context(input: ByteSource, parseScope: JsonParseScope)
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

    context(input: ByteSource, parseScope: JsonParseScope)
    internal fun peek(): Int {
        if (parseScope.position == parseScope.limit && !refill()) {
            return -1
        }
        return parseScope.buffer[parseScope.position].toInt() and 255
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    internal fun refill(): Boolean {
        parseScope.position = 0
        parseScope.limit = input.readAtMostTo(sink = parseScope.buffer, offset = 0, length = parseScope.buffer.size)
        if (parseScope.limit <= 0) {
            check(parseScope.limit != 0) { "ByteSource returned zero bytes for a non-empty read" }
            parseScope.limit = 0
            return false
        }
        return true
    }
}
