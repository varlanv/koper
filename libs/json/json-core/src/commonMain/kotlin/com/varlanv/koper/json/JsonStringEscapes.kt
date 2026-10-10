package com.varlanv.koper.json

import com.varlanv.koper.json.JsonStringEscapes.UNICODE_PAIR
import com.varlanv.koper.json.JsonStringEscapes.escapePairs
import com.varlanv.koper.json.JsonStringEscapes.unicodeTails
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes

/**
 * Escape tables and buffered UTF-8 string output. Helpers write string contents without surrounding quotes.
 */
internal object JsonStringEscapes {
    const val UNICODE_PAIR: Short = (('u'.code shl 8) or '\\'.code).toShort()
    private const val HEX = "0123456789abcdef"

    val unicodeTails = IntArray(32) { value ->
        0x3030 or (HEX[value ushr 4].code shl 16) or (HEX[value and 15].code shl 24)
    }

    val escapePairs = ByteArray(256)
        .also { codes ->
            for (value in 0..31) codes[value] = 'u'.code.toByte()
            codes['\b'.code] = 'b'.code.toByte()
            codes['\t'.code] = 't'.code.toByte()
            codes['\n'.code] = 'n'.code.toByte()
            codes[12] = 'f'.code.toByte()
            codes['\r'.code] = 'r'.code.toByte()
            codes['"'.code] = '"'.code.toByte()
            codes['\\'.code] = '\\'.code.toByte()
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
        if (!jsonScanner.isVector) {
            return writeUtf8EscapedPacked(
                bytes = bytes,
                target = target,
                start = start,
                end = end,
                targetStart = targetStart,
            )
        }
        var index = start
        var offset = targetStart
        val width = jsonScanner.laneCount
        while (end - index >= width) {
            val blockStart = index
            val blockEnd = blockStart + width
            var events = jsonScanner.specialMask(bytes = bytes, start = blockStart)
            while (events.hasEvents()) {
                val special = blockStart + events.firstEvent()
                if (special > index) {
                    val length = special - index
                    if (length <= jsonPackedByteCount && index <= bytes.size - jsonPackedByteCount) {
                        jsonCopyPackedBytes(
                            source = bytes,
                            target = target,
                            sourceOffset = index,
                            targetOffset = offset,
                        )
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
                offset = writeUtf8EscapedChunk(bytes = bytes, index = special, offset = offset, target = target)
                index = special + 1
                events = events.dropFirstEvent()
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
            offset = writeUtf8EscapedTail(bytes = bytes, index = index++, offset = offset, target = target)
        }
        return offset
    }

    internal fun writeUtf8EscapedPacked(
        bytes: Bytes,
        target: MutBytes,
        start: Int,
        end: Int,
        targetStart: Int,
    ): Int {
        var index = start
        var offset = targetStart
        val width = jsonPackedByteCount
        while (index <= end - width) {
            val blockStart = index
            val blockEnd = blockStart + width
            var events = jsonPackedSpecialMask(bytes = bytes, start = blockStart)
            while (events.hasEvents()) {
                val special = blockStart + events.firstEvent()
                if (special > index) {
                    jsonCopyPackedRun(
                        source = bytes,
                        target = target,
                        blockStart = blockStart,
                        sourceOffset = index,
                        targetOffset = offset,
                    )
                    offset += special - index
                }
                offset = writeUtf8EscapedChunk(bytes = bytes, index = special, offset = offset, target = target)
                index = special + 1
                events = events.dropFirstEvent()
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
            offset = writeUtf8EscapedTail(bytes = bytes, index = index++, offset = offset, target = target)
        }
        return offset
    }
}

private fun writeUtf8EscapedTail(
    bytes: Bytes,
    index: Int,
    offset: Int,
    target: MutBytes,
): Int {
    val value = bytes[index].toInt() and 255
    val escaped = escapePairs[value]
    if (escaped.toInt() == 0) {
        target[offset] = value.toByte()
        return offset + 1
    } else {
        target.setPackedShort(idx = offset, value = escaped)
        if (escaped != UNICODE_PAIR) {
            return offset + 2
        } else {
            target.setPackedInt(idx = offset + 2, value = unicodeTails[value])
            return offset + 6
        }
    }
}

private fun writeUtf8EscapedChunk(
    bytes: Bytes,
    index: Int,
    offset: Int,
    target: MutBytes,
): Int {
    val value = bytes[index].toInt() and 255
    val escaped = escapePairs[value]
    target.setPackedShort(idx = offset, value = escaped)
    if (escaped != UNICODE_PAIR) {
        return offset + 2
    } else {
        target.setPackedInt(idx = offset + 2, value = unicodeTails[value])
        return offset + 6
    }
}
