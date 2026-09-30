@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.getPackedInt

actual typealias JsonFieldWord = Int

actual inline fun jsonFieldWordMatches(
    head: JsonFieldWord,
    tail: JsonFieldWord,
    low: Int,
    high: Int,
    byteCount: Int,
): Boolean {
    return if (byteCount <= 4) {
        val mask = if (byteCount == 4) {
            -1
        } else {
            (1 shl (byteCount * 8)) - 1
        }
        (head and mask) == low
    } else {
        val mask = if (byteCount == 8) {
            -1
        } else {
            (1 shl ((byteCount - 4) * 8)) - 1
        }
        head == low && (tail and mask) == high
    }
}

internal actual inline fun jsonPeekFieldHead(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord = if (limit - position >= 4) {
    bytes.getPackedInt(position)
} else {
    0
}

internal actual inline fun jsonPeekFieldTail(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord = if (limit - position >= 8) {
    bytes.getPackedInt(position + 4)
} else {
    0
}

internal actual typealias JsonScanMask = Int

internal actual inline fun jsonEmptyScanMask(): JsonScanMask = 0

internal actual inline fun JsonScanMask.withBit(index: Int): JsonScanMask = this or (1 shl index)

internal actual inline fun JsonScanMask.hasEvents(): Boolean = this != 0

internal actual inline fun JsonScanMask.firstEvent(): Int = countTrailingZeroBits()

internal actual inline fun JsonScanMask.dropFirstEvent(): JsonScanMask = this and (this - 1)

internal actual inline fun JsonScanMask.clearBefore(index: Int): JsonScanMask = this and (-1 shl index)

internal actual inline val jsonPackedDigitCount: Int get() = 4

internal actual inline val jsonPackedDigitBase: Int get() = 10000

internal actual inline fun jsonReadPackedDigits(bytes: MutBytes, index: Int): Int {
    val word = bytes.getPackedInt(index)
    if (((word + 0x46464646) or (word - 0x30303030)) and -0x7f7f7f80 != 0) {
        return -1
    }
    val digits = word - 0x30303030
    val pairs = (digits * 10 + (digits ushr 8)) and 0x00ff00ff
    return (pairs * 100 + (pairs ushr 16)) and 0xffff
}

internal actual inline val jsonPackedByteCount: Int get() = 4

internal actual inline fun jsonCopyPackedBytes(
    source: Bytes,
    target: MutBytes,
    sourceOffset: Int,
    targetOffset: Int,
) {
    val word = source.getPackedInt(sourceOffset)
    target.setPackedInt(idx = targetOffset, value = word)
}

internal actual inline fun jsonWritePackedBytes(
    target: MutBytes,
    offset: Int,
    low: Int,
    high: Int,
) {
    target.setPackedInt(idx = offset, value = low)
    target.setPackedInt(idx = offset + 4, value = high)
}
