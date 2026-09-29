package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.getPackedInt

/**
 * Escape tables and buffered UTF-8 string output. Helpers write string contents without surrounding quotes.
 */
internal object JsonStringEscapes {
    const val unicodePair: Short = 0x755c
    private const val hex = "0123456789abcdef"

    val unicodeTails = IntArray(32) { value ->
        0x3030 or (hex[value ushr 4].code shl 16) or (hex[value and 15].code shl 24)
    }

    val escapePairs = ByteArray(256)
        .also { codes ->
            for (value in 0..31) codes[value] = 'u'.code.toByte()
            codes[8] = 'b'.code.toByte()
            codes[9] = 't'.code.toByte()
            codes[10] = 'n'.code.toByte()
            codes[12] = 'f'.code.toByte()
            codes[13] = 'r'.code.toByte()
            codes[34] = 34
            codes[92] = 92
        }
        .let { codes ->
            ShortArray(256) { index ->
                if (codes[index].toInt() == 0) {
                    0
                } else {
                    (92 or (codes[index].toInt() shl 8)).toShort()
                }
            }
        }

    /**
     * Escapes [bytes] from [start] until [end] into the write buffer beginning at [targetStart].
     * Requires non-aliasing input and sufficient reserved capacity for escaped output and packed-write padding.
     * Mutates the write buffer, but does not reserve, flush, or update the scope's position; the caller sets that position.
     *
     * @return The exclusive output end as an [Int], including inserted JSON escape bytes.
     */
    context(writeScope: JsonWriteScope)
    fun writeUtf8Escaped(
        bytes: Bytes,
        start: Int,
        end: Int,
        targetStart: Int,
    ): Int {
        val target = writeScope.buffer
        var index = start
        var offset = targetStart
        val width = jsonScanner.laneCount
        while (end - index >= width) {
            val blockStart = index
            val blockEnd = blockStart + width
            var events = jsonScanner.specialMask(bytes = bytes, start = blockStart)
            while (events != 0) {
                val special = blockStart + events.countTrailingZeroBits()
                if (special > index) {
                    val length = special - index
                    if (length <= 4 && index <= bytes.size - 4) {
                        val word = bytes.getPackedInt(index)
                        target.setPackedInt(idx = offset, value = word)
                    } else {
                        bytes.copyInto(
                            destination = target,
                            destinationOffset = offset,
                            startIndex = index,
                            endIndex = special,
                        )
                    }
                    offset += length
                }
                val value = bytes[special].toInt() and 255
                val escaped = escapePairs[value]
                target.setPackedShort(idx = offset, value = escaped)
                offset += 2
                if (escaped == unicodePair) {
                    target.setPackedInt(idx = offset, value = unicodeTails[value])
                    offset += 4
                }
                index = special + 1
                events = events and (events - 1)
            }
            if (index < blockEnd) {
                bytes.copyInto(
                    destination = target,
                    destinationOffset = offset,
                    startIndex = index,
                    endIndex = blockEnd,
                )
                offset += blockEnd - index
            }
            index = blockEnd
        }
        while (index < end) {
            val value = bytes[index++].toInt() and 255
            val escaped = escapePairs[value]
            if (escaped.toInt() != 0) {
                target.setPackedShort(idx = offset, value = escaped)
                offset += 2
                if (escaped == unicodePair) {
                    target.setPackedInt(idx = offset, value = unicodeTails[value])
                    offset += 4
                }
            } else {
                target[offset++] = value.toByte()
            }
        }
        return offset
    }
}
