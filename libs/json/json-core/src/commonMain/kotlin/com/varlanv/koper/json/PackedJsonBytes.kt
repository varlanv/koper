@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes

/** Cached field-name bytes: a primitive Long on JVM and a primitive Int on JS. */
expect class JsonFieldWord

/**
 * Matches one to eight little-endian bytes against constants emitted as two Ints.
 * JVM uses [head] alone; JS uses [tail] for bytes beyond the first four.
 * The constants must have zeroes above [byteCount]. Does not consume input.
 */
expect inline fun jsonFieldWordMatches(
    head: JsonFieldWord,
    tail: JsonFieldWord,
    low: Int,
    high: Int,
    byteCount: Int,
): Boolean

internal expect inline fun jsonPeekFieldHead(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord

internal expect inline fun jsonPeekFieldTail(
    bytes: MutBytes,
    position: Int,
    limit: Int,
): JsonFieldWord

/** Primitive special-byte mask, wide enough for the platform's scanner lanes. */
internal expect class JsonScanMask

internal expect inline fun jsonEmptyScanMask(): JsonScanMask

internal expect inline fun JsonScanMask.withBit(index: Int): JsonScanMask

internal expect inline fun JsonScanMask.hasEvents(): Boolean

internal expect inline fun JsonScanMask.firstEvent(): Int

internal expect inline fun JsonScanMask.dropFirstEvent(): JsonScanMask

internal expect inline fun JsonScanMask.clearBefore(index: Int): JsonScanMask

internal expect inline val jsonPackedDigitCount: Int

internal expect inline val jsonPackedDigitBase: Int

/** Reads a complete digit group, returning -1 if any byte is not an ASCII digit. */
internal expect inline fun jsonReadPackedDigits(bytes: MutBytes, index: Int): Int

internal expect inline val jsonPackedByteCount: Int

/** Copies a complete platform word; callers must allow padding beyond the logical run. */
internal expect inline fun jsonCopyPackedBytes(
    source: Bytes,
    target: MutBytes,
    sourceOffset: Int,
    targetOffset: Int,
)

/** Writes eight little-endian bytes, using one JVM Long store or two JS Int stores. */
internal expect inline fun jsonWritePackedBytes(
    target: MutBytes,
    offset: Int,
    low: Int,
    high: Int,
)

internal expect inline fun jsonPackedSpecialMask(bytes: Bytes, start: Int): JsonScanMask

internal expect inline fun jsonCopyPackedRun(
    source: Bytes,
    target: MutBytes,
    blockStart: Int,
    sourceOffset: Int,
    targetOffset: Int,
)
