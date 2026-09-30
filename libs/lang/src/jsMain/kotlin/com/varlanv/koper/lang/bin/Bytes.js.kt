@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.bin

actual inline operator fun BytesImpl.get(idx: Int): Byte = getInt8(idx)

actual inline operator fun BytesImpl.set(idx: Int, value: Byte) {
    setInt8(byteOffset = idx, value = value)
}

private fun checkMismatchRange(
    from: Int,
    to: Int,
    size: Int,
) {
    require(from <= to) {
        "fromIndex ($from) > toIndex ($to)"
    }
    if (from < 0 || to > size) {
        throw IndexOutOfBoundsException("range [$from, $to) exceeds array size $size")
    }
}

actual fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int {
    checkMismatchRange(from = aFromIndex, to = aToIndex, size = size)
    checkMismatchRange(from = bFromIndex, to = bToIndex, size = b.size)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    val length = minOf(aLength, bLength)

    if (bytes.impl !== b.bytes.impl || aFromIndex != bFromIndex) {
        var i = 0
        while (i <= length - 4) {
            if (bytes.impl.getUint32(byteOffset = aFromIndex + i) !=
                b.bytes.impl.getUint32(byteOffset = bFromIndex + i)) {
                break
            }
            i += 4
        }
        while (i < length) {
            if (bytes.impl.getInt8(aFromIndex + i) != b.bytes.impl.getInt8(bFromIndex + i)) {
                return i
            }
            i++
        }
    }

    return if (aLength == bLength) {
        -1
    } else {
        length
    }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual value class Bytes actual constructor(@PublishedApi internal actual val bytes: MutBytes) {
    actual inline val size: Int
        get() = bytes.size

    actual inline operator fun get(idx: Int): Byte = bytes[idx]

    actual inline fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) = bytes.copyInto(
        destination = destination,
        destinationOffset = destinationOffset,
        startIndex = startIndex,
        endIndex = endIndex,
    )

    actual inline fun asList(): List<Byte> {
        return Array(size) { idx -> bytes[idx] }.asList()
    }

    actual companion object {
        actual inline val empty: Bytes
            get() = Bytes(MutBytes.empty)
    }
}
