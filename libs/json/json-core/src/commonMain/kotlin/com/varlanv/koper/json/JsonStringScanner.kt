package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.text.Charset

object JsonStringScanner {
    context(input: ByteSource, parseScope: JsonParseScope)
    fun read() {
        require(parseScope.last == 34) { "Expected JSON string" }
        val start = parseScope.position
        var index = if (isVectorized) {
            jsonScanner.firstSpecial(
                bytes = Bytes(parseScope.buffer),
                start = start,
                end = parseScope.limit,
            )
        } else {
            start
        }
        while (index < parseScope.limit) {
            val value = parseScope.buffer[index].toInt() and 255
            if (value == 34) {
                parseScope.position = index + 1
                parseScope.scratchBytes = parseScope.buffer
                parseScope.scratchOffset = start
                parseScope.scratchLength = index - start
                return
            }
            if (value == 92 || value < 32) {
                break
            }
            index++
        }
        parseScope.scratchSize = index - start
        ensureScratch(parseScope.scratchSize)
        parseScope.buffer.copyInto(
            destination = parseScope.scratch,
            destinationOffset = 0,
            startIndex = start,
            endIndex = index,
        )
        parseScope.position = index
        if (index == parseScope.limit || !readBufferedEscapes()) {
            readStringToScratch(false)
        }
        parseScope.scratchBytes = parseScope.scratch
        parseScope.scratchOffset = 0
        parseScope.scratchLength = parseScope.scratchSize
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    fun readToScratch() {
        readStringToScratch()
        parseScope.scratchBytes = parseScope.scratch
        parseScope.scratchOffset = 0
        parseScope.scratchLength = parseScope.scratchSize
    }

    context(parseScope: JsonParseScope)
    private fun readBufferedEscapes(): Boolean {
        return if (isVectorized && parseScope.limit - parseScope.position >= jsonScanner.laneCount) {
            readVectorEscapes()
        } else {
            readScalarEscapes()
        }
    }

    context(parseScope: JsonParseScope)
    private fun readVectorEscapes(): Boolean {
        ensureScratch(parseScope.scratchSize + parseScope.limit - parseScope.position)
        var index = parseScope.position
        var size = parseScope.scratchSize
        val output = parseScope.scratch
        val width = jsonScanner.laneCount
        scan@ while (parseScope.limit - index >= width) {
            val start = index
            val end = start + width
            var events = jsonScanner.specialMask(
                bytes = Bytes(parseScope.buffer),
                start = start,
            )
            while (events != 0L) {
                val special = start + events.countTrailingZeroBits()
                if (special > index) {
                    copyRun(output = output, offset = size, start = index, end = special)
                    size += special - index
                }
                index = special
                val value = parseScope.buffer[index].toInt() and 255
                if (value == 34) {
                    parseScope.position = index + 1
                    parseScope.scratchSize = size
                    return true
                }
                require(value == 92) { "Unescaped control character" }
                if (parseScope.limit - index < 2) {
                    break@scan
                }
                val escaped = parseScope.buffer[index + 1].toInt() and 255
                if (escaped == 117) {
                    if (parseScope.limit - index < 6) {
                        break@scan
                    }
                    var codepoint = readHexAt(index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (parseScope.limit - index < 12) {
                            break@scan
                        }
                        require(
                            parseScope.buffer[index + 6].toInt() == 92 && parseScope.buffer[index + 7].toInt() == 117,
                        ) {
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
        parseScope.position = index
        parseScope.scratchSize = size
        return readScalarEscapes()
    }

    context(parseScope: JsonParseScope)
    private fun copyRun(
        output: MutBytes,
        offset: Int,
        start: Int,
        end: Int,
    ) {
        if (end - start <= 8 && start <= parseScope.buffer.size - 8 && offset <= output.size - 8) {
            val word = parseScope.buffer.getPackedLong(start)
            output.setPackedLong(idx = offset, value = word)
        } else {
            parseScope.buffer.copyInto(
                destination = output,
                destinationOffset = offset,
                startIndex = start,
                endIndex = end,
            )
        }
    }

    context(parseScope: JsonParseScope)
    private fun readScalarEscapes(): Boolean {
        ensureScratch(parseScope.scratchSize + parseScope.limit - parseScope.position)
        var index = parseScope.position
        var size = parseScope.scratchSize
        val output = parseScope.scratch
        while (index < parseScope.limit) {
            val value = parseScope.buffer[index].toInt() and 255
            if (value == 34) {
                parseScope.position = index + 1
                parseScope.scratchSize = size
                return true
            }
            require(value >= 32) { "Unescaped control character" }
            if (value == 92) {
                if (index + 1 == parseScope.limit) {
                    break
                }
                if (parseScope.buffer[index + 1].toInt() == 117) {
                    if (parseScope.limit - index < 6) {
                        break
                    }
                    var codepoint = readHexAt(index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (parseScope.limit - index < 12) {
                            break
                        }
                        require(
                            parseScope.buffer[index + 6].toInt() == 92 && parseScope.buffer[index + 7].toInt() == 117,
                        ) {
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
                output[size++] = when (val escaped = parseScope.buffer[index + 1].toInt() and 255) {
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
        parseScope.position = index
        parseScope.scratchSize = size
        return false
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun readStringToScratch(clear: Boolean = true) {
        if (clear) {
            parseScope.scratchSize = 0
        }
        while (true) {
            val start = parseScope.position
            var end = if (isVectorized) {
                jsonScanner.firstSpecial(
                    bytes = Bytes(parseScope.buffer),
                    start = start,
                    end = parseScope.limit,
                )
            } else {
                start
            }
            while (end < parseScope.limit) {
                val value = parseScope.buffer[end].toInt() and 255
                if (value == 34 || value == 92 || value < 32) {
                    break
                }
                end++
            }
            if (end > start) {
                val size = end - start
                ensureScratch(parseScope.scratchSize + size)
                parseScope.buffer.copyInto(
                    destination = parseScope.scratch,
                    destinationOffset = parseScope.scratchSize,
                    startIndex = start,
                    endIndex = end,
                )
                parseScope.scratchSize += size
                parseScope.position = end
            }
            val value = JsonReadProtocol.take()
            when {
                value == 34 -> return
                value == 92 -> readEscape()
                value < 32 -> throw IllegalArgumentException("Unterminated string or unescaped control character")
                else -> append(value.toByte())
            }
        }
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun readEscape() {
        when (val value = JsonReadProtocol.take()) {
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
                    require(
                        JsonReadProtocol.take() == 92 && JsonReadProtocol.take() == 117,
                    ) { "Expected low surrogate escape" }
                    val low = readHex()
                    require(low in 0xDC00..0xDFFF) { "Invalid low surrogate" }
                    codepoint = 0x10000 + ((codepoint - 0xD800) shl 10) + low - 0xDC00
                } else {
                    require(codepoint !in 0xDC00..0xDFFF) { "Unpaired low surrogate" }
                }
                ensureScratch(parseScope.scratchSize + 4)
                Charset.Utf8.encodeCodepointInline(codepoint) { parseScope.scratch[parseScope.scratchSize++] = it }
            }

            else -> {
                throw IllegalArgumentException("Invalid JSON escape")
            }
        }
    }

    context(parseScope: JsonParseScope)
    private fun readHexAt(index: Int): Int {
        return (hexDigit(parseScope.buffer[index].toInt() and 255) shl 12) or
            (hexDigit(parseScope.buffer[index + 1].toInt() and 255) shl 8) or
            (hexDigit(parseScope.buffer[index + 2].toInt() and 255) shl 4) or
            hexDigit(parseScope.buffer[index + 3].toInt() and 255)
    }

    private fun hexDigit(value: Int): Int {
        return when (value) {
            in 48..57 -> value - 48
            in 65..70 -> value - 55
            in 97..102 -> value - 87
            else -> throw IllegalArgumentException("Invalid Unicode escape")
        }
    }

    context(input: ByteSource, parseScope: JsonParseScope)
    private fun readHex(): Int {
        var result = 0
        repeat(4) {
            val value = JsonReadProtocol.take()
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

    context(parseScope: JsonParseScope)
    private fun append(value: Byte) {
        if (parseScope.scratchSize == parseScope.scratch.size) {
            ensureScratch(parseScope.scratchSize + 1)
        }
        parseScope.scratch[parseScope.scratchSize++] = value
    }

    context(parseScope: JsonParseScope)
    private fun ensureScratch(size: Int) {
        if (size > parseScope.scratch.size) {
            parseScope.scratch = parseScope.scratch.copyOf(maxOf(size, parseScope.scratch.size * 2))
        }
    }
}
