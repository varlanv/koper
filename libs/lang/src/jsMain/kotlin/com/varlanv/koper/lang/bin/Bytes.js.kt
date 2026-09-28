package com.varlanv.koper.lang.bin

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual typealias BytesImpl = DataView

actual fun DataView.size(): Int = byteLength

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class MutBytes @PublishedApi internal actual constructor(
    @PublishedApi internal actual val impl: BytesImpl,
) {
    actual val size: Int
        get() = impl.byteLength

    actual val readonly: Bytes get() = Bytes(this)

    actual constructor(dataSize: DataSize) : this(DataView(buffer = ArrayBuffer(dataSize.bytes)))

    actual fun getPackedLong(idx: Int): Long {
        TODO()
    }

    actual fun setPackedLong(idx: Int, value: Long) {
        TODO()
    }

    actual fun getPackedInt(idx: Int): Int = impl.getInt32(byteOffset = idx)

    actual fun setPackedInt(idx: Int, value: Int) {
        impl.setInt32(byteOffset = idx, value = value)
    }

    actual operator fun get(idx: Int): Byte = impl.getInt8(idx)

    actual operator fun set(idx: Int, value: Byte) = impl.setInt8(byteOffset = idx, value = value)

    actual fun hash(offset: Int, length: Int): Int {
        TODO("Implement SWAR hash via `impl.getUint32()`, respecting buffer size")
    }

    actual inline fun forEach(block: (Byte) -> Unit) {
        for (idx in 0 until impl.byteLength) {
            block(impl.getInt8(idx))
        }
    }

    actual inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) {
        for (idx in 0 until impl.byteLength) {
            block(
                idx,
                impl.getInt8(idx),
            )
        }
    }

    actual fun copyInto(destination: MutBytes, destinationOffset: Int, startIndex: Int, endIndex: Int) {
        TODO()
    }

    actual fun copyOf(newCapacity: Int): MutBytes {
        val copy = ArrayBuffer(newCapacity)
        Uint8Array(copy).set(Uint8Array(impl.buffer))
        return MutBytes(DataView(copy))
    }


    actual fun copyOfRange(from: Int, to: Int): MutBytes {
        TODO("Not yet implemented")
    }

    actual companion object {

        actual val empty: MutBytes = MutBytes(DataView(ArrayBuffer(0)))

        actual inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes {
            val view = DataView(buffer = ArrayBuffer(dataSize.bytes))
            val s = dataSize.bytes
            for (idx in 0 until s) {
                view.setInt8(idx, init(idx))
            }
            return MutBytes(view)
        }

        actual operator fun invoke(array: ByteArray): MutBytes =
            MutBytes(DataView(MutBytes.unsafeCast<Uint8Array>().buffer))
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

actual fun MutBytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: MutBytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int {
    TODO("Not yet implemented")
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual value class Bytes actual constructor(@PublishedApi internal actual val bytes: MutBytes) {
    actual val size: Int
        get() = bytes.size

    actual operator fun get(idx: Int): Byte = bytes[idx]

    actual fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int
    ) = bytes.copyInto(destination, destinationOffset, startIndex, endIndex)

    actual companion object {
        actual val empty: Bytes
            get() = Bytes(MutBytes.empty)
    }
}
