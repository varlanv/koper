package com.varlanv.koper.lang.bin

import kotlin.jvm.JvmInline

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect class BytesImpl

expect fun BytesImpl.size(): Int

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect value class MutBytes @PublishedApi internal constructor(@PublishedApi internal val impl: BytesImpl) {
    val size: Int
    val readonly: Bytes

    constructor(dataSize: DataSize)

    fun getPackedLong(idx: Int): Long

    fun setPackedLong(idx: Int, value: Long)

    fun getPackedInt(idx: Int): Int

    fun setPackedInt(idx: Int, value: Int)

    fun getPackedShort(idx: Int): Short
    fun setPackedShort(idx: Int, value: Short)

    operator fun get(idx: Int): Byte

    operator fun set(idx: Int, value: Byte)

    inline fun forEach(block: (Byte) -> Unit)

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit)

    fun hash(offset: Int, length: Int): Int

    fun copyInto(destination: MutBytes, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = size)

    fun copyOf(newCapacity: Int = impl.size()): MutBytes
    fun copyOfRange(from: Int, to: Int): MutBytes

    companion object {

        val empty: MutBytes

        inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes

        operator fun invoke(array: ByteArray): MutBytes
    }
}


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

    fun copyInto(destination: MutBytes, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = size)

    companion object {

        val empty: Bytes
    }
}

fun Bytes.isEmpty(): Boolean = size == 0

@JvmInline
value class MutBytesSlice @PublishedApi internal constructor(@PublishedApi internal val delegate: BytesSlice) {

    fun setPackedInt(idx: Int, value: Int) = delegate.bytes.bytes.setPackedInt(idx = idx, value = value)

    operator fun set(idx: Int, value: Byte) {
        delegate.bytes.bytes[idx] = value
    }
}

class BytesSlice(
    val bytes: Bytes,
    val offset: Int,
    val len: Int,
) {
    fun getPackedInt(idx: Int): Int = bytes.bytes.getPackedInt(idx)

    operator fun get(idx: Int): Byte = bytes.bytes[idx]

    override fun equals(other: Any?): Boolean =
        other is BytesSlice &&
                bytes.mismatch(
                    aFromIndex = offset,
                    aToIndex = len + offset,
                    b = other.bytes,
                    bFromIndex = other.offset,
                    bToIndex = other.offset + other.len,
                ) == -1

    override fun hashCode(): Int = bytes.bytes.hash(offset = offset, length = len)

    companion object {
        val empty: BytesSlice = BytesSlice(Bytes.empty, 0, 0)
    }
}

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

/** Reads bytes into a caller-provided array. */
interface ByteSource {
    /** Reads up to [length] bytes into [sink] at [offset]; returns -1 at end, or 0 for zero length. */
    fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int
}

/** Writes bytes from a caller-provided array. */
interface ByteSink {
    /** Writes [length] bytes from [source] starting at [offset]. */
    fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    )
}

/** Reads the contents of [slice] in order. */
class ByteArraySource(private val slice: BytesSlice) : ByteSource {
    private var position: Int = 0

    override fun readAtMostTo(
        sink: MutBytes,
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
        slice.bytes.bytes.copyInto(sink, offset, start, start + count)
        position += count
        return count
    }
}

/** Accumulates bytes in a reusable, growing array. */
class ReusableByteArraySink(initialCapacity: DataSize) : ByteSink {
    @PublishedApi
    internal var bytes: MutBytes = MutBytes(initialCapacity)

    @PublishedApi
    internal var position: Int = 0

    /** Clears the written length while retaining the allocated array. */
    fun reset() {
        position = 0
    }

    /** Borrows the first `length` bytes without copying; do not mutate or retain the array. */
    inline fun <R> unsafeUseBytes(block: (bytes: MutBytes, length: Int) -> R): R {
        return block(bytes, position)
    }

    override fun writeTo(
        source: MutBytes,
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
