package com.varlanv.koper.lang.bin

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual typealias BytesImpl = DataView

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class Bytes private actual constructor(private actual val impl: BytesImpl): BytesOperations {
    actual val size: Int
        get() = impl.byteLength

    actual override fun getInt(idx: Int): Int = impl.getInt32(idx)
    actual override fun setInt(idx: Int, value: Int) {
        impl.setInt32(idx, value)
    }

    actual override operator fun get(idx: Int): Byte = impl.getInt8(idx)
    actual override operator fun set(idx: Int, value: Byte) = impl.setInt8(idx, value)

    actual companion object {
        actual operator fun invoke(capacity: Int): Bytes = Bytes(DataView(ArrayBuffer(capacity)))

        actual operator fun invoke(dataSize: DataSize): Bytes = invoke(dataSize.bytes)
    }

    actual fun hash(offset: Int, length: Int): Int {
        TODO("Implement SWAR hash via `impl.getUint32()`, respecting buffer size")
    }
}


actual fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Int {
    checkMismatchRange(from = aFromIndex, to = aToIndex, size = size)
    checkMismatchRange(from = bFromIndex, to = bToIndex, size = b.size)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    val length = minOf(aLength, bLength)

    // Identical starting positions in the same array need no scan.
    if (this !== b || aFromIndex != bFromIndex) {
        var i = 0
        while (i < length) {
            if (this[aFromIndex + i] != b[bFromIndex + i]) {
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

actual fun ByteArray.setPackedInt(idx: Int, i: Int) {
    this[idx] = i.toByte()
    this[idx + 1] = (i ushr 8).toByte()
    this[idx + 2] = (i ushr 16).toByte()
    this[idx + 3] = (i ushr 24).toByte()
}

actual fun ByteArray.setPackedLong(idx: Int, l: Long) {
    setPackedInt(idx, l.toInt())
    setPackedInt(idx + 4, (l ushr 32).toInt())
}

actual fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int
): Int {
    TODO("Not yet implemented")
}
