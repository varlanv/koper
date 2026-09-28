package com.varlanv.koper.lang.bin

expect fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect value class Bytes(@PublishedApi internal val bytes: MutBytes) {
    val size: Int

    operator fun get(idx: Int): Byte

    fun copyInto(
        destination: MutBytes,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = size,
    )

    fun asList(): List<Byte>

    companion object {
        val empty: Bytes
    }
}

fun Bytes.isEmpty(): Boolean = size == 0

fun Bytes.copyOf(newCapacity: Int = size): Bytes = this.bytes.copyOf(newCapacity).readonly

fun ByteArray.asReadonly(): Bytes = Bytes(MutBytes(this))

fun ByteArray.asMut(): MutBytes = MutBytes(this)

fun Bytes.equals(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Boolean = mismatch(
    aFromIndex = aFromIndex,
    aToIndex = aToIndex,
    b = b,
    bFromIndex = bFromIndex,
    bToIndex = bToIndex,
) < 0

fun Bytes.containsNeedle(
    needle: Bytes,
    fromIndex: Int = 0,
): Boolean = indexOfNeedle(needle = needle, fromIndex = fromIndex) != -1

fun Bytes.indexOfNeedle(needle: Bytes, fromIndex: Int): Int {
    if (needle.isEmpty()) {
        return fromIndex.coerceIn(0, size)
    }
    val needleSize = needle.size
    val needleFirst = needle[0]
    val lastPossible = size - needleSize
    var i = fromIndex.coerceAtLeast(0)
    while (i <= lastPossible) {
        if (this[i] == needleFirst && (
        needleSize == 1 ||
            mismatch(
                aFromIndex = i + 1,
                aToIndex = i + needleSize,
                b = needle,
                bFromIndex = 1,
                bToIndex = needleSize,
            ) == -1
        )
        ) {
            return i
        }
        i++
    }
    return -1
}

fun Bytes.startsWith(
    prefix: Bytes,
    offset: Int = 0,
): Boolean = offset >= 0 &&
    prefix.size <= size - offset &&
    mismatch(
        aFromIndex = offset,
        aToIndex = offset + prefix.size,
        b = prefix,
        bFromIndex = 0,
        bToIndex = prefix.size,
    ) == -1

fun Bytes.validateUtf8(
    offset: Int = 0,
    len: Int = size - offset,
    //    onError: (errorMessage: String) -> Unit,
): Boolean {
    if (offset < 0) {
        //        onError("negative offset: $offset")
        return false
    }
    if (len < 0) {
        //        onError("negative length: $len")
        return false
    }
    if (offset > size - len) {
        //        onError("range [$offset, ${offset.toLong() + len}) exceeds byte array size $size")
        return false
    }

    var i = offset
    val end = offset + len

    // Small values are cheaper to scan directly than to enter the platform bulk scan.
    if (len < 64) {
        while (i < end && this[i] >= 0) i++
        if (i == end) {
            return true
        }
    }

    while (i < end) {
        val b0 = this[i].toInt() and 0xFF

        // ASCII
        if (b0 < 0x80) {
            i = skipAscii(start = i + 1, end = end)
            continue
        }

        // 2-byte sequence:
        // C2..DF 80..BF
        if (b0 < 0xE0) {
            if (b0 < 0xC2) {
                //                onError("invalid UTF-8 leading byte 0x${b0.toString(16)} at index $i")
                return false
            }
            if (i + 1 >= end) {
                //                onError("truncated 2-byte UTF-8 sequence at index $i")
                return false
            }

            val b1 = this[i + 1].toInt() and 0xFF
            if (b1 and 0xC0 != 0x80) {
                //                onError("invalid UTF-8 continuation byte 0x${b1.toString(16)} " + "at index ${i + 1}")
                return false
            }

            i += 2
            continue
        }

        // 3-byte sequence
        if (b0 < 0xF0) {
            if (i + 2 >= end) {
                //                onError("truncated 3-byte UTF-8 sequence at index $i")
                return false
            }

            val b1 = this[i + 1].toInt() and 0xFF
            val b2 = this[i + 2].toInt() and 0xFF

            if (b2 and 0xC0 != 0x80) {
                //                onError("invalid UTF-8 continuation byte 0x${b2.toString(16)} " + "at index ${i + 2}")
                return false
            }

            when (b0) {
                // Prevent overlong encoding:
                // E0 80..9F xx
                0xE0 -> {
                    if (b1 !in 0xA0..0xBF) {
                        //                        onError("overlong 3-byte UTF-8 sequence at index $i")
                        return false
                    }
                }

                // Prevent UTF-16 surrogate range:
                // ED A0..BF xx
                0xED -> {
                    if (b1 !in 0x80..0x9F) {
                        //                        onError("UTF-8 sequence encodes a surrogate code point at index $i")
                        return false
                    }
                }

                in 0xE1..0xEC, in 0xEE..0xEF -> {
                    if (b1 and 0xC0 != 0x80) {
                        //                        onError("invalid UTF-8 continuation byte 0x${b1.toString(16)} " + "at index ${i + 1}")
                        return false
                    }
                }

                else -> {
                    //                    onError("invalid UTF-8 leading byte 0x${b0.toString(16)} at index $i")
                    return false
                }
            }

            i += 3
            continue
        }

        // 4-byte sequence
        if (b0 <= 0xF4) {
            if (i + 3 >= end) {
                //                onError("truncated 4-byte UTF-8 sequence at index $i")
                return false
            }

            val b1 = this[i + 1].toInt() and 0xFF
            val b2 = this[i + 2].toInt() and 0xFF
            val b3 = this[i + 3].toInt() and 0xFF

            if (b2 and 0xC0 != 0x80) {
                //                onError("invalid UTF-8 continuation byte 0x${b2.toString(16)} " + "at index ${i + 2}")
                return false
            }
            if (b3 and 0xC0 != 0x80) {
                //                onError("invalid UTF-8 continuation byte 0x${b3.toString(16)} " + "at index ${i + 3}")
                return false
            }

            when (b0) {
                // U+10000 minimum, prevents overlong encoding
                0xF0 -> if (b1 !in 0x90..0xBF) {
                    //                    onError("overlong 4-byte UTF-8 sequence at index $i")
                    return false
                }

                0xF1, 0xF2, 0xF3 -> if (b1 and 0xC0 != 0x80) {
                    //                    onError("invalid UTF-8 continuation byte 0x${b1.toString(16)} " + "at index ${i + 1}")
                    return false
                }

                // U+10FFFF maximum
                0xF4 -> if (b1 !in 0x80..0x8F) {
                    //                    onError("UTF-8 code point above U+10FFFF at index $i")
                    return false
                }
            }

            i += 4
            continue
        }

        // F5..FF or a continuation byte used as a leading byte.
        //        onError("invalid UTF-8 leading byte 0x${b0.toString(16)} at index $i")
        return false
    }

    return true
}

/** Returns the first non-ASCII byte, or [end] if the range is all ASCII. */
internal expect fun Bytes.skipAscii(start: Int, end: Int): Int
