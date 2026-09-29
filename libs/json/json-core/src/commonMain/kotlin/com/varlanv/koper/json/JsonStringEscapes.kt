package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.getPackedLong

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

    fun writeUtf8Escaped(
        writer: JsonWriteProtocol,
        bytes: Bytes,
        start: Int,
        end: Int,
        targetStart: Int,
    ): Int {
        val target = writer.buffer
        var index = start
        var offset = targetStart
        val width = jsonScanner.laneCount
        while (end - index >= width) {
            val blockStart = index
            val blockEnd = blockStart + width
            var events = jsonScanner.specialMask(bytes = bytes, start = blockStart)
            while (events != 0L) {
                val special = blockStart + events.countTrailingZeroBits()
                if (special > index) {
                    val length = special - index
                    if (length <= 8 && index <= bytes.size - 8) {
                        val word = bytes.getPackedLong(index)
                        target.setPackedLong(idx = offset, value = word)
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
