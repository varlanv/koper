package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.text.Charset

internal class JsonStringScanner(val vectorized: Boolean) {
    private val scan = jsonSpecialScan(vectorized)
    private var scratch = MutBytes(256.bytes())
    private var scratchSize = 0
    var bytes: MutBytes = scratch
        private set
    var offset = 0
        private set
    var length = 0
        private set

    fun read(reader: JsonReadProtocol) {
        require(reader.token == 34) { "Expected JSON string" }
        val start = reader.parseScope.position
        var index = if (vectorized) {
            scan.firstSpecial(
                bytes = Bytes(reader.buffer),
                start = start,
                end = reader.parseScope.limit,
            )
        } else {
            start
        }
        while (index < reader.parseScope.limit) {
            val value = reader.buffer[index].toInt() and 255
            if (value == 34) {
                reader.parseScope.position = index + 1
                bytes = reader.buffer
                offset = start
                length = index - start
                return
            }
            if (value == 92 || value < 32) {
                break
            }
            index++
        }
        scratchSize = index - start
        ensureScratch(scratchSize)
        reader.buffer.copyInto(destination = scratch, destinationOffset = 0, startIndex = start, endIndex = index)
        reader.parseScope.position = index
        if (index == reader.parseScope.limit || !readBufferedEscapes(reader)) {
            readStringToScratch(reader = reader, clear = false)
        }
        bytes = scratch
        offset = 0
        length = scratchSize
    }

    fun readToScratch(reader: JsonReadProtocol) {
        readStringToScratch(reader = reader)
        bytes = scratch
        offset = 0
        length = scratchSize
    }

    private fun readBufferedEscapes(reader: JsonReadProtocol): Boolean {
        return if (vectorized && reader.parseScope.limit - reader.parseScope.position >= scan.laneCount) {
            readVectorEscapes(reader)
        } else {
            readScalarEscapes(reader)
        }
    }

    private fun readVectorEscapes(reader: JsonReadProtocol): Boolean {
        ensureScratch(scratchSize + reader.parseScope.limit - reader.parseScope.position)
        var index = reader.parseScope.position
        var size = scratchSize
        val output = scratch
        val width = scan.laneCount
        scan@ while (reader.parseScope.limit - index >= width) {
            val start = index
            val end = start + width
            var events = scan.specialMask(
                bytes = Bytes(reader.buffer),
                start = start,
            )
            while (events != 0L) {
                val special = start + events.countTrailingZeroBits()
                if (special > index) {
                    copyRun(reader = reader, output = output, offset = size, start = index, end = special)
                    size += special - index
                }
                index = special
                val value = reader.buffer[index].toInt() and 255
                if (value == 34) {
                    reader.parseScope.position = index + 1
                    scratchSize = size
                    return true
                }
                require(value == 92) { "Unescaped control character" }
                if (reader.parseScope.limit - index < 2) {
                    break@scan
                }
                val escaped = reader.buffer[index + 1].toInt() and 255
                if (escaped == 117) {
                    if (reader.parseScope.limit - index < 6) {
                        break@scan
                    }
                    var codepoint = readHexAt(reader = reader, index = index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (reader.parseScope.limit - index < 12) {
                            break@scan
                        }
                        require(reader.buffer[index + 6].toInt() == 92 && reader.buffer[index + 7].toInt() == 117) {
                            "Expected low surrogate escape"
                        }
                        val low = readHexAt(reader = reader, index = index + 8)
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
                copyRun(reader = reader, output = output, offset = size, start = index, end = end)
                size += end - index
                index = end
            }
        }
        reader.parseScope.position = index
        scratchSize = size
        return readScalarEscapes(reader)
    }

    private fun copyRun(
        reader: JsonReadProtocol,
        output: MutBytes,
        offset: Int,
        start: Int,
        end: Int,
    ) {
        if (end - start <= 8 && start <= reader.buffer.size - 8 && offset <= output.size - 8) {
            val word = reader.buffer.getPackedLong(start)
            output.setPackedLong(idx = offset, value = word)
        } else {
            reader.buffer.copyInto(destination = output, destinationOffset = offset, startIndex = start, endIndex = end)
        }
    }

    private fun readScalarEscapes(reader: JsonReadProtocol): Boolean {
        ensureScratch(scratchSize + reader.parseScope.limit - reader.parseScope.position)
        var index = reader.parseScope.position
        var size = scratchSize
        val output = scratch
        while (index < reader.parseScope.limit) {
            val value = reader.buffer[index].toInt() and 255
            if (value == 34) {
                reader.parseScope.position = index + 1
                scratchSize = size
                return true
            }
            require(value >= 32) { "Unescaped control character" }
            if (value == 92) {
                if (index + 1 == reader.parseScope.limit) {
                    break
                }
                if (reader.buffer[index + 1].toInt() == 117) {
                    if (reader.parseScope.limit - index < 6) {
                        break
                    }
                    var codepoint = readHexAt(reader = reader, index = index + 2)
                    var consumed = 6
                    if (codepoint in 0xD800..0xDBFF) {
                        if (reader.parseScope.limit - index < 12) {
                            break
                        }
                        require(reader.buffer[index + 6].toInt() == 92 && reader.buffer[index + 7].toInt() == 117) {
                            "Expected low surrogate escape"
                        }
                        val low = readHexAt(reader = reader, index = index + 8)
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
                output[size++] = when (val escaped = reader.buffer[index + 1].toInt() and 255) {
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
        reader.parseScope.position = index
        scratchSize = size
        return false
    }

    private fun readStringToScratch(
        reader: JsonReadProtocol,
        clear: Boolean = true,
    ) {
        if (clear) {
            scratchSize = 0
        }
        while (true) {
            val start = reader.parseScope.position
            var end = if (vectorized) {
                scan.firstSpecial(
                    bytes = Bytes(reader.buffer),
                    start = start,
                    end = reader.parseScope.limit,
                )
            } else {
                start
            }
            while (end < reader.parseScope.limit) {
                val value = reader.buffer[end].toInt() and 255
                if (value == 34 || value == 92 || value < 32) {
                    break
                }
                end++
            }
            if (end > start) {
                val size = end - start
                ensureScratch(scratchSize + size)
                reader.buffer.copyInto(
                    destination = scratch,
                    destinationOffset = scratchSize,
                    startIndex = start,
                    endIndex = end,
                )
                scratchSize += size
                reader.parseScope.position = end
            }
            val value = reader.take()
            when {
                value == 34 -> return
                value == 92 -> readEscape(reader)
                value < 32 -> throw IllegalArgumentException("Unterminated string or unescaped control character")
                else -> append(value.toByte())
            }
        }
    }

    private fun readEscape(reader: JsonReadProtocol) {
        when (val value = reader.take()) {
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
                var codepoint = readHex(reader)
                if (codepoint in 0xD800..0xDBFF) {
                    require(reader.take() == 92 && reader.take() == 117) { "Expected low surrogate escape" }
                    val low = readHex(reader)
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

    private fun readHexAt(reader: JsonReadProtocol, index: Int): Int {
        return (hexDigit(reader.buffer[index].toInt() and 255) shl 12) or
            (hexDigit(reader.buffer[index + 1].toInt() and 255) shl 8) or
            (hexDigit(reader.buffer[index + 2].toInt() and 255) shl 4) or
            hexDigit(reader.buffer[index + 3].toInt() and 255)
    }

    private fun hexDigit(value: Int): Int {
        return when (value) {
            in 48..57 -> value - 48
            in 65..70 -> value - 55
            in 97..102 -> value - 87
            else -> throw IllegalArgumentException("Invalid Unicode escape")
        }
    }

    private fun readHex(reader: JsonReadProtocol): Int {
        var result = 0
        repeat(4) {
            val value = reader.take()
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
}
