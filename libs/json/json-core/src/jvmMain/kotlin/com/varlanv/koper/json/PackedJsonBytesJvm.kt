@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.getPackedLong

actual typealias JsonFieldWord = Long

actual inline fun jsonFieldWordMatches(
    head: JsonFieldWord,
    tail: JsonFieldWord,
    low: Int,
    high: Int,
    byteCount: Int,
): Boolean {
    val expected = (low.toLong() and 0xffffffffL) or (high.toLong() shl 32)
    val mask = if (byteCount == 8) {
        -1L
    } else {
        (1L shl (byteCount * 8)) - 1L
    }
    return (head and mask) == expected
}

internal actual inline fun jsonPeekFieldHead(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord = if (limit - position >= 8) {
    bytes.getPackedLong(position)
} else {
    0L
}

internal actual inline fun jsonPeekFieldTail(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord = 0L

internal actual typealias JsonScanMask = Long

internal actual inline fun jsonEmptyScanMask(): JsonScanMask = 0L

internal actual inline fun JsonScanMask.withBit(index: Int): JsonScanMask = this or (1L shl index)

internal actual inline fun JsonScanMask.hasEvents(): Boolean = this != 0L

internal actual inline fun JsonScanMask.firstEvent(): Int = countTrailingZeroBits()

internal actual inline fun JsonScanMask.dropFirstEvent(): JsonScanMask = this and (this - 1L)

internal actual inline fun JsonScanMask.clearBefore(index: Int): JsonScanMask = this and (-1L shl index)

internal actual inline val jsonPackedDigitCount: Int get() = 8

internal actual inline val jsonPackedDigitBase: Int get() = 100000000

internal actual inline fun jsonReadPackedDigits(bytes: MutBytes, index: Int): Int {
    val word = bytes.getPackedLong(index)
    if (((word + 0x4646464646464646L) or (word - 0x3030303030303030L)) and -0x7f7f7f7f7f7f7f80L != 0L) {
        return -1
    }
    val digits = word - 0x3030303030303030L
    val pairs = (digits * 10 + (digits ushr 8)) and 0x00ff00ff00ff00ffL
    val quads = (pairs * 100 + (pairs ushr 16)) and 0x0000ffff0000ffffL
    return ((quads * 10000 + (quads ushr 32)) and 0xffffffffL).toInt()
}

internal actual inline val jsonPackedByteCount: Int get() = 8

internal actual inline fun jsonCopyPackedBytes(
    source: Bytes,
    target: MutBytes,
    sourceOffset: Int,
    targetOffset: Int,
) {
    val word = source.getPackedLong(sourceOffset)
    target.setPackedLong(idx = targetOffset, value = word)
}

internal actual inline fun jsonWritePackedBytes(
    target: MutBytes,
    offset: Int,
    low: Int,
    high: Int,
) {
    target.setPackedLong(idx = offset, value = (low.toLong() and 0xffffffffL) or (high.toLong() shl 32))
}

internal inline fun jsonSpecialEvents(word: Long): Long {
    val low = 0x7f7f7f7f7f7f7f7fL
    val high = -0x7f7f7f7f7f7f7f80L
    val quote = word xor 0x2222222222222222L
    val slash = word xor 0x5c5c5c5c5c5c5c5cL
    val quotes = (((quote and low) + low) or quote or low).inv() and high
    val slashes = (((slash and low) + low) or slash or low).inv() and high
    val controls = (word or ((word and low) + 0x6060606060606060L)).inv() and high
    return quotes or slashes or controls
}

internal actual inline fun jsonPackedSpecialMask(bytes: Bytes, start: Int): JsonScanMask {
    val events = jsonSpecialEvents(bytes.getPackedLong(start))
    return ((events ushr 7) * 0x0102040810204080L) ushr 56
}

internal actual inline fun jsonCopyPackedRun(
    source: Bytes,
    target: MutBytes,
    blockStart: Int,
    sourceOffset: Int,
    targetOffset: Int,
) {
    val word = source.getPackedLong(blockStart) ushr ((sourceOffset - blockStart) * 8)
    target.setPackedLong(idx = targetOffset, value = word)
}
