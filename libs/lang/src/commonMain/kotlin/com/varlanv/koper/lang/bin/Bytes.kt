package com.varlanv.koper.lang.bin

import kotlin.jvm.JvmInline

expect fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Int

expect fun ByteArray.setPackedInt(idx: Int, i: Int)

expect fun ByteArray.setPackedLong(idx: Int, l: Long)

fun ByteArray.equals(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Boolean = mismatch(
    aFromIndex = aFromIndex,
    aToIndex = aToIndex,
    b = b,
    bFromIndex = bFromIndex,
    bToIndex = bToIndex,
) < 0

fun ByteArray.containsNeedle(
    needle: ByteArray,
    fromIndex: Int = 0,
): Boolean = indexOfNeedle(needle = needle, fromIndex = fromIndex) != -1

fun ByteArray.indexOfNeedle(needle: ByteArray, fromIndex: Int): Int {
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

fun ByteArray.startsWith(
    prefix: ByteArray,
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

@JvmInline
value class ReadonlyBytes(@PublishedApi internal val array: ByteArray)

class ByteSlice(
    @PublishedApi internal val bytes: ReadonlyBytes,
    val offset: Int,
    val len: Int,
) {
    private var hash: Int = 0

    inline fun forEach(block: (Byte) -> Unit) {
        for (idx in offset until offset + len) {
            block(bytes.array[idx])
        }
    }

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) {
        for (idx in offset until offset + len) {
            block(idx, bytes.array[idx])
        }
    }

    /**
     * Returns backing array. The caller is responsible for ensuring "readonly" invariant
     * Returned array should not be modified in range from [offset] to [offset] + [len].
     * Useful for cases when array is needed to be passed to trusted source that works only with [ByteArray]s,
     * and is trusted to not modify the array.
     */
    fun unsafeBorrowArray(): ByteArray = bytes.array

    /**
     * Returns true if this slice covers whole backing array.
     */
    fun isFullRange(): Boolean = offset == 0 && len == bytes.array.size

    fun allocateArray(): ByteArray = bytes.array.copyOfRange(offset, offset + len)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ByteSlice || len != other.len) {
            return false
        }
        return bytes.array.equals(
            aFromIndex = offset,
            aToIndex = offset + len,
            b = other.bytes.array,
            bFromIndex = other.offset,
            bToIndex = other.offset + other.len,
        )
    }

    override fun hashCode(): Int {
        val h = hash
        if (h != 0) {
            return h
        }
        var result = 1
        val end = offset + len

        for (i in offset until end) {
            result = 31 * result + bytes.array[i]
        }
        hash = result
        return result
    }
}

fun ByteArray.validateUtf8(
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
internal expect fun ByteArray.skipAscii(start: Int, end: Int): Int

/** Reads bytes into a caller-provided array. */
interface ByteSource {
    /** Reads up to [length] bytes into [sink] at [offset]; returns -1 at end, or 0 for zero length. */
    fun readAtMostTo(
        sink: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

/** Writes bytes from a caller-provided array. */
interface ByteSink {
    /** Writes [length] bytes from [source] starting at [offset]. */
    fun writeTo(
        source: ByteArray,
        offset: Int,
        length: Int,
    )
}

/** Reads the contents of [slice] in order. */
class ByteArraySource(private val slice: ByteSlice) : ByteSource {
    private var position: Int = 0

    override fun readAtMostTo(
        sink: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (offset < 0 || length < 0 || offset > sink.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) {
            return 0
        }
        val remaining = slice.len - position
        if (remaining == 0) {
            return -1
        }
        val count = minOf(length, remaining)
        val start = slice.offset + position
        slice.bytes.array.copyInto(sink, offset, start, start + count)
        position += count
        return count
    }
}

/** Accumulates bytes in a reusable, growing array. */
class ReusableByteArraySink(initialCapacity: Int) : ByteSink {
    @PublishedApi
    internal var bytes: ByteArray = ByteArray(initialCapacity)

    @PublishedApi
    internal var position: Int = 0

    /** Clears the written length while retaining the allocated array. */
    fun reset() {
        position = 0
    }

    /** Borrows the first `length` bytes without copying; do not mutate or retain the array. */
    inline fun <R> unsafeUseBytes(block: (bytes: ByteArray, length: Int) -> R): R {
        return block(bytes, position)
    }

    override fun writeTo(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (offset < 0 || length < 0 || offset > source.size - length) {
            throw IndexOutOfBoundsException()
        }
        ensureCapacity(length)
        source.copyInto(bytes, position, offset, offset + length)
        position += length
    }

    private fun ensureCapacity(additionalBytes: Int) {
        if (additionalBytes <= bytes.size - position) {
            return
        }
        val requiredCapacity = position.toLong() + additionalBytes
        if (requiredCapacity > Int.MAX_VALUE) {
            error("Required buffer capacity exceeds Int.MAX_VALUE")
        }
        val newCapacity = maxOf(requiredCapacity, bytes.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        bytes = bytes.copyOf(newCapacity)
    }
}
