package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.lang.bin.*
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Utf8Str
import java.io.InputStream

class IdealJsonReader(
    bufferSize: DataSize = 32.kilobytes(),
    private val vectorized: Boolean = false,
) {
    private val buffer = MutBytes(bufferSize.also { require(it.bytes > 0) })
    private lateinit var input: InputStream
    private var position = 0
    private var limit = 0
    private var last = -1
    private var scratch = MutBytes(256.bytes())
    private var scratchSize = 0
    private var field = buffer
    private var fieldOffset = 0
    private var fieldSize = 0

    fun reset(input: InputStream) {
        this.input = input
        position = 0
        limit = 0
        last = -1
        fieldSize = 0
    }

    fun nextToken(): Int {
        var value = take()
        while (value == 32 || value == 9 || value == 10 || value == 13) value = take()
        last = value
        return value
    }

    fun nextFieldValue() {
        val index = position
        if (limit - index >= 2 && buffer[index].toInt() == 58) {
            val value = buffer[index + 1].toInt() and 255
            if (value > 32) {
                position = index + 2
                last = value
                return
            }
        }
        require(nextToken() == 58) { "Expected colon" }
        require(nextToken() != -1) { "Expected JSON value" }
    }

    fun readUtf8(): Utf8Str {
        require(last == 34) { "Expected JSON string" }
        val start = position
        var index = if (vectorized) {
            VectorJsonScan.firstSpecial(
                bytes = Bytes(buffer),
                start = start,
                end = limit,
            )
        } else {
            start
        }
        while (index < limit) {
            val value = buffer[index].toInt() and 255
            if (value == 34) {
                position = index + 1
                if (index == start) {
                    return Utf8Str.empty
                }
                val bytes = buffer.copyOfRange(from = start, to = index)
                return Utf8Str.unsafeWrap(bytes.asReadonly().slice(offset = 0, len = bytes.size))
            }
            if (value == 92 || value < 32) {
                break
            }
            index++
        }
        scratchSize = index - start
        ensureScratch(scratchSize)
        buffer.copyInto(destination = scratch, destinationOffset = 0, startIndex = start, endIndex = index)
        position = index
        if (index == limit || !readBufferedEscapes()) {
            readStringToScratch(false)
        }
        val bytes = scratch.copyOf(scratchSize)
        return Utf8Str.unsafeWrap(bytes.asReadonly().slice(offset = 0, len = bytes.size))
    }

    fun readString(): String {
        require(last == 34) { "Expected JSON string" }
        val start = position
        var index = if (vectorized) {
            VectorJsonScan.firstSpecial(bytes = buffer.asReadonly(), start = start, end = limit)
        } else {
            start
        }
        while (index < limit) {
            val value = buffer[index].toInt() and 255
            if (value == 34) {
                position = index + 1
                return Bytes.unsafe {
                    useInternal(buffer.asReadonly()) {
                        String(it, start, index - start, Charsets.UTF_8)
                    }
                }
            }
            if (value == 92 || value < 32) {
                break
            }
            index++
        }
        scratchSize = index - start
        ensureScratch(scratchSize)
        buffer.copyInto(destination = scratch, destinationOffset = 0, startIndex = start, endIndex = index)
        position = index
        if (index == limit || !readBufferedEscapes()) {
            readStringToScratch(false)
        }
        return Bytes.unsafe { useInternal(scratch.asReadonly()) { String(it, 0, scratchSize, Charsets.UTF_8) } }
    }

    fun peekFieldWord(): Long {
        return if (limit - position >= 8) {
            buffer.getPackedLong(position)
        } else {
            0L
        }
    }

    fun consumeField(length: Int): Boolean {
        if (limit - position <= length || buffer[position + length].toInt() != 34) {
            return false
        }
        position += length + 1
        return true
    }

    fun consumeMatchedFieldColon(length: Int) {
        nextValue(position + length + 2)
    }

    fun consumeFieldColon(length: Int): Boolean {
        if (limit - position <= length + 1 ||
            buffer[position + length].toInt() != 34 ||
            buffer[position + length + 1].toInt() != 58
        ) {
            return false
        }
        nextValue(position + length + 2)
        return true
    }

    fun nextFieldOrEnd(): Int {
        val index = position
        if (limit - index >= 2 && buffer[index].toInt() == 44 && buffer[index + 1].toInt() == 34) {
            position = index + 2
            last = 34
            return 34
        }
        if (index < limit && buffer[index].toInt() == 125) {
            position = index + 1
            last = 125
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

    fun readField(): Int {
        require(last == 34) { "Expected JSON field name" }
        val start = position
        var index = start
        var hash = 0
        while (index < limit) {
            val value = buffer[index].toInt() and 255
            if (value == 34) {
                fieldSize = index - start
                field = buffer
                fieldOffset = start
                position = index + 1
                return hash
            }
            if (value == 92 || value < 32) {
                break
            }
            hash = 31 * hash + value
            index++
        }
        readStringToScratch()
        fieldSize = scratchSize
        field = scratch
        fieldOffset = 0
        hash = 0
        for (i in 0 until fieldSize) hash = 31 * hash + (field[i].toInt() and 255)
        return hash
    }

    fun fieldEquals(expected: Bytes): Boolean {
        if (fieldSize != expected.size) {
            return false
        }
        expected.forEachIndex { i ->
            if (field[fieldOffset + i] != expected[i]) {
                return false
            }
        }
        return true
    }

    fun readLong(): Long {
        return if (swarNumbers) {
            readLongGrouped()
        } else {
            readLongScalar()
        }
    }

    fun readLongReserved(): Long {
        return if (ensureAvailable(20)) {
            readLongContiguous()
        } else {
            readLong()
        }
    }

    private fun readLongContiguous(): Long {
        val negative = last == 45
        val digit = if (negative) {
            buffer[position++].toInt() and 255
        } else {
            last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Long.MIN_VALUE
        } else {
            -Long.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        var result = -(digit - 48).toLong()
        val leadingZero = digit == 48
        var index = position
        var groups = 0
        while (groups < 2) {
            val number = readEightDigits(index)
            if (number < 0) {
                break
            }
            require(!leadingZero) { "Leading zero in JSON integer" }
            require(result >= -92233720368L) { "Integer overflow" }
            result *= 100000000L
            require(result >= minimum + number) { "Integer overflow" }
            result -= number
            index += 8
            groups++
        }
        while (true) {
            val next = buffer[index].toInt() and 255
            if (next !in 48..57) {
                position = index
                requireDelimiter(next)
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            require(!leadingZero) { "Leading zero in JSON integer" }
            require(result >= multiplyMinimum) { "Integer overflow" }
            result *= 10
            val number = next - 48
            require(result >= minimum + number) { "Integer overflow" }
            result -= number
            index++
        }
    }

    private fun readLongScalar(): Long {
        val negative = last == 45
        val digit = if (negative) {
            take()
        } else {
            last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Long.MIN_VALUE
        } else {
            -Long.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        var result = -(digit - 48).toLong()
        val leadingZero = digit == 48
        var index = position
        while (true) {
            while (index < limit) {
                val next = buffer[index].toInt() and 255
                if (next !in 48..57) {
                    position = index
                    requireDelimiter(next)
                    return if (negative) {
                        result
                    } else {
                        -result
                    }
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= multiplyMinimum) { "Integer overflow" }
                result *= 10
                val number = next - 48
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index++
            }
            position = index
            if (!refill()) {
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            index = 0
        }
    }

    private fun readLongGrouped(): Long {
        val negative = last == 45
        val digit = if (negative) {
            take()
        } else {
            last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Long.MIN_VALUE
        } else {
            -Long.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        var result = -(digit - 48).toLong()
        val leadingZero = digit == 48
        var index = position
        while (true) {
            while (limit - index >= 8) {
                val number = readEightDigits(index)
                if (number < 0) {
                    break
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= -92233720368L) { "Integer overflow" }
                result *= 100000000L
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index += 8
            }
            while (index < limit) {
                val next = buffer[index].toInt() and 255
                if (next !in 48..57) {
                    position = index
                    requireDelimiter(next)
                    return if (negative) {
                        result
                    } else {
                        -result
                    }
                }
                require(!leadingZero) { "Leading zero in JSON integer" }
                require(result >= multiplyMinimum) { "Integer overflow" }
                result *= 10
                val number = next - 48
                require(result >= minimum + number) { "Integer overflow" }
                result -= number
                index++
            }
            position = index
            if (!refill()) {
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            index = 0
        }
    }

    private fun readEightDigits(index: Int): Long {
        val word = buffer.getPackedLong(index)
        if (((word + 0x4646464646464646L) or (word - 0x3030303030303030L)) and -0x7f7f7f7f7f7f7f80L != 0L) {
            return -1L
        }
        val digits = word - 0x3030303030303030L
        val pairs = (digits * 10 + (digits ushr 8)) and 0x00ff00ff00ff00ffL
        val quads = (pairs * 100 + (pairs ushr 16)) and 0x0000ffff0000ffffL
        return (quads * 10000 + (quads ushr 32)) and 0xffffffffL
    }

    fun readInt(): Int {
        val value = readLong()
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer overflow" }
        return value.toInt()
    }

    fun readIntReserved(): Int {
        if (!ensureAvailable(11)) {
            return readInt()
        }
        val negative = last == 45
        val digit = if (negative) {
            buffer[position++].toInt() and 255
        } else {
            last
        }
        require(digit in 48..57) { "Expected JSON integer" }
        val minimum = if (negative) {
            Int.MIN_VALUE
        } else {
            -Int.MAX_VALUE
        }
        val multiplyMinimum = minimum / 10
        var result = -(digit - 48)
        val leadingZero = digit == 48
        var index = position
        while (true) {
            val next = buffer[index].toInt() and 255
            if (next !in 48..57) {
                position = index
                requireDelimiter(next)
                return if (negative) {
                    result
                } else {
                    -result
                }
            }
            require(!leadingZero) { "Leading zero in JSON integer" }
            require(result >= multiplyMinimum) { "Integer overflow" }
            result *= 10
            val number = next - 48
            require(result >= minimum + number) { "Integer overflow" }
            result -= number
            index++
        }
    }

    fun readBoolean(): Boolean {
        return when (last) {
            116 -> {
                readLiteral("rue")
                true
            }

            102 -> {
                readLiteral("alse")
                false
            }

            else -> {
                throw IllegalArgumentException("Expected JSON boolean")
            }
        }
    }

    fun readBooleanReserved(): Boolean {
        if (!ensureAvailable(
            if (last == 116) {
                4
            } else {
                5
            },
        )
        ) {
            return readBoolean()
        }
        return when (last) {
            116 -> {
                require(buffer.getPackedInt(position) and 0xffffff == 0x657572) { "Invalid JSON literal" }
                position += 3
                requireDelimiter(buffer[position].toInt() and 255)
                true
            }

            102 -> {
                require(buffer.getPackedInt(position) == 0x65736c61) { "Invalid JSON literal" }
                position += 4
                requireDelimiter(buffer[position].toInt() and 255)
                false
            }

            else -> {
                throw IllegalArgumentException("Expected JSON boolean")
            }
        }
    }

    fun skipValue() {
        skipValue(0)
    }

    private fun skipValue(depth: Int) {
        require(depth <= 128) { "JSON nesting limit exceeded" }
        when (last) {
            34 -> {
                readStringToScratch()
            }

            116, 102 -> {
                readBoolean()
            }

            110 -> {
                readLiteral("ull")
            }

            123 -> {
                if (nextToken() == 125) {
                    return
                }
                while (true) {
                    require(last == 34) { "Expected JSON field name" }
                    readStringToScratch()
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

    private fun skipNumber() {
        var digit = if (last == 45) {
            take()
        } else {
            last
        }
        require(digit in 48..57) { "Expected JSON number" }
        if (digit == 48) {
            digit = peek()
            require(digit !in 48..57) { "Leading zero in JSON number" }
        } else {
            digit = peek()
            while (digit in 48..57) {
                position++
                digit = peek()
            }
        }
        if (digit == 46) {
            position++
            require(peek() in 48..57) { "Expected fraction digits" }
            do {
                position++
                digit = peek()
            } while (digit in 48..57)
        }
        if (digit == 101 || digit == 69) {
            position++
            digit = peek()
            if (digit == 43 || digit == 45) {
                position++
            }
            require(peek() in 48..57) { "Expected exponent digits" }
            do {
                position++
                digit = peek()
            } while (digit in 48..57)
        }
        requireDelimiter(digit)
    }

    private fun readLiteral(tail: String) {
        for (char in tail) require(take() == char.code) { "Invalid JSON literal" }
        requireDelimiter(peek())
    }

    private fun requireDelimiter(value: Int) {
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

    private fun readBufferedEscapes(): Boolean {
        return if (vectorized && limit - position >= VectorJsonScan.laneCount) {
            readVectorEscapes()
        } else {
            readScalarEscapes()
        }
    }

    private fun readVectorEscapes(): Boolean {
        ensureScratch(scratchSize + limit - position)
        var index = position
        var size = scratchSize
        val output = scratch
        val width = VectorJsonScan.laneCount
        scan@ while (limit - index >= width) {
            val start = index
            val end = start + width
            var events = VectorJsonScan.specialMask(bytes = buffer.asReadonly(), start = start)
            while (events != 0L) {
                val special = start + java.lang.Long.numberOfTrailingZeros(events)
                if (special > index) {
                    copyRun(output = output, offset = size, start = index, end = special)
                    size += special - index
                }
                index = special
                val value = buffer[index].toInt() and 255
                if (value == 34) {
                    position = index + 1
                    scratchSize = size
                    return true
                }
                require(value == 92) { "Unescaped control character" }
                if (limit - index < 2) {
                    break@scan
                }
                val escaped = buffer[index + 1].toInt() and 255
                if (escaped == 117) {
                    if (limit - index < 6) {
                        break@scan
                    }
                    var codepoint = readHexAt(index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (limit - index < 12) {
                            break@scan
                        }
                        require(buffer[index + 6].toInt() == 92 && buffer[index + 7].toInt() == 117) {
                            "Expected low surrogate escape"
                        }
                        val low = readHexAt(index + 8)
                        require(low in 0xDC00..0xDFFF) { "Invalid low surrogate" }
                        codepoint = 0x10000 + ((codepoint - 0xD800) shl 10) + low - 0xDC00
                        consumed = 12
                    } else {
                        require(codepoint !in 0xDC00..0xDFFF) { "Unpaired low surrogate" }
                    }
                    Charset.Utf8.encodeCodepointInline(codepoint) { output[size++] = it }
                    index += consumed
                } else {
                    output[size++] = when (escaped) {
                        34, 92, 47 -> escaped.toByte()
                        98 -> 8
                        102 -> 12
                        110 -> 10
                        114 -> 13
                        116 -> 9
                        else -> throw IllegalArgumentException("Invalid JSON escape")
                    }
                    index += 2
                }
                val consumed = index - start
                if (consumed >= width) {
                    break
                }
                events = events and (-1L shl consumed)
            }
            if (index < end) {
                copyRun(output = output, offset = size, start = index, end = end)
                size += end - index
                index = end
            }
        }
        position = index
        scratchSize = size
        return readScalarEscapes()
    }

    private fun copyRun(
        output: MutBytes,
        offset: Int,
        start: Int,
        end: Int,
    ) {
        if (end - start <= 8 && start <= buffer.size - 8 && offset <= output.size - 8) {
            val word = buffer.getPackedLong(start)
            output.setPackedLong(idx = offset, value = word)
        } else {
            buffer.copyInto(destination = output, destinationOffset = offset, startIndex = start, endIndex = end)
        }
    }

    private fun readScalarEscapes(): Boolean {
        ensureScratch(scratchSize + limit - position)
        var index = position
        var size = scratchSize
        val output = scratch
        while (index < limit) {
            val value = buffer[index].toInt() and 255
            if (value == 34) {
                position = index + 1
                scratchSize = size
                return true
            }
            require(value >= 32) { "Unescaped control character" }
            if (value == 92) {
                if (index + 1 == limit) {
                    break
                }
                if (buffer[index + 1].toInt() == 117) {
                    if (limit - index < 6) {
                        break
                    }
                    var codepoint = readHexAt(index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (limit - index < 12) {
                            break
                        }
                        require(buffer[index + 6].toInt() == 92 && buffer[index + 7].toInt() == 117) {
                            "Expected low surrogate escape"
                        }
                        val low = readHexAt(index + 8)
                        require(low in 0xDC00..0xDFFF) { "Invalid low surrogate" }
                        codepoint = 0x10000 + ((codepoint - 0xD800) shl 10) + low - 0xDC00
                        consumed = 12
                    } else {
                        require(codepoint !in 0xDC00..0xDFFF) { "Unpaired low surrogate" }
                    }
                    Charset.Utf8.encodeCodepointInline(codepoint) { output[size++] = it }
                    index += consumed
                    continue
                }
                output[size++] = when (val escaped = buffer[index + 1].toInt() and 255) {
                    34, 92, 47 -> escaped.toByte()
                    98 -> 8
                    102 -> 12
                    110 -> 10
                    114 -> 13
                    116 -> 9
                    else -> throw IllegalArgumentException("Invalid JSON escape")
                }
                index += 2
            } else {
                output[size++] = value.toByte()
                index++
            }
        }
        position = index
        scratchSize = size
        return false
    }

    private fun readStringToScratch(clear: Boolean = true) {
        if (clear) {
            scratchSize = 0
        }
        while (true) {
            val start = position
            var end = if (vectorized) {
                VectorJsonScan.firstSpecial(bytes = buffer.asReadonly(), start = start, end = limit)
            } else {
                start
            }
            while (end < limit) {
                val value = buffer[end].toInt() and 255
                if (value == 34 || value == 92 || value < 32) {
                    break
                }
                end++
            }
            if (end > start) {
                val size = end - start
                ensureScratch(scratchSize + size)
                buffer.copyInto(
                    destination = scratch,
                    destinationOffset = scratchSize,
                    startIndex = start,
                    endIndex = end,
                )
                scratchSize += size
                position = end
            }
            val value = take()
            when {
                value == 34 -> return
                value == 92 -> readEscape()
                value < 32 -> throw IllegalArgumentException("Unterminated string or unescaped control character")
                else -> append(value.toByte())
            }
        }
    }

    private fun readEscape() {
        when (val value = take()) {
            34, 92, 47 -> {
                append(value.toByte())
            }

            98 -> {
                append(8)
            }

            102 -> {
                append(12)
            }

            110 -> {
                append(10)
            }

            114 -> {
                append(13)
            }

            116 -> {
                append(9)
            }

            117 -> {
                var codepoint = readHex()
                if (codepoint in 0xD800..0xDBFF) {
                    require(take() == 92 && take() == 117) { "Expected low surrogate escape" }
                    val low = readHex()
                    require(low in 0xDC00..0xDFFF) { "Invalid low surrogate" }
                    codepoint = 0x10000 + ((codepoint - 0xD800) shl 10) + low - 0xDC00
                } else {
                    require(codepoint !in 0xDC00..0xDFFF) { "Unpaired low surrogate" }
                }
                ensureScratch(scratchSize + 4)
                Charset.Utf8.encodeCodepointInline(codepoint) { scratch[scratchSize++] = it }
            }

            else -> {
                throw IllegalArgumentException("Invalid JSON escape")
            }
        }
    }

    private fun readHexAt(index: Int): Int {
        return (hexDigit(buffer[index].toInt() and 255) shl 12) or
            (hexDigit(buffer[index + 1].toInt() and 255) shl 8) or
            (hexDigit(buffer[index + 2].toInt() and 255) shl 4) or
            hexDigit(buffer[index + 3].toInt() and 255)
    }

    private fun hexDigit(value: Int): Int {
        return when (value) {
            in 48..57 -> value - 48
            in 65..70 -> value - 55
            in 97..102 -> value - 87
            else -> throw IllegalArgumentException("Invalid Unicode escape")
        }
    }

    private fun readHex(): Int {
        var result = 0
        repeat(4) {
            val value = take()
            val digit = when (value) {
                in 48..57 -> value - 48
                in 65..70 -> value - 55
                in 97..102 -> value - 87
                else -> throw IllegalArgumentException("Invalid Unicode escape")
            }
            result = (result shl 4) or digit
        }
        return result
    }

    private fun append(value: Byte) {
        if (scratchSize == scratch.size) {
            ensureScratch(scratchSize + 1)
        }
        scratch[scratchSize++] = value
    }

    private fun ensureScratch(size: Int) {
        if (size > scratch.size) {
            scratch = scratch.copyOf(maxOf(size, scratch.size * 2))
        }
    }

    private fun take(): Int {
        val value = peek()
        if (value >= 0) {
            position++
        }
        return value
    }

    private fun ensureAvailable(size: Int): Boolean {
        if (limit - position >= size) {
            return true
        }
        val remaining = limit - position
        if (remaining != 0) {
            buffer.copyInto(destination = buffer, destinationOffset = 0, startIndex = position, endIndex = limit)
        }
        position = 0
        limit = remaining
        while (limit < size) {
            val read = Bytes.unsafe { useInternal(buffer.asReadonly()) { input.read(it, limit, buffer.size - limit) } }
            if (read > 0) {
                limit += read
            } else if (read < 0) {
                return false
            } else {
                val value = input.read()
                if (value < 0) {
                    return false
                }
                buffer[limit++] = value.toByte()
            }
        }
        return true
    }

    private fun nextValue(index: Int) {
        position = index
        if (index < limit) {
            val value = buffer[index].toInt() and 255
            if (value > 32) {
                position = index + 1
                last = value
                return
            }
        }
        require(nextToken() != -1) { "Expected JSON value" }
    }

    private fun peek(): Int {
        if (position == limit && !refill()) {
            return -1
        }
        return buffer[position].toInt() and 255
    }

    private fun refill(): Boolean {
        position = 0
        limit = Bytes.unsafe { useInternal(buffer.asReadonly()) { input.read(it) } }
        if (limit <= 0) {
            if (limit == 0) {
                val value = input.read()
                if (value >= 0) {
                    buffer[0] = value.toByte()
                    limit = 1
                    return true
                }
            }
            limit = 0
            return false
        }
        return true
    }

    private companion object {
        val swarNumbers = System.getProperty("ideal.swar.numbers", "true").toBoolean()
    }
}
