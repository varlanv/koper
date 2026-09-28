package com.varlanv.koper.lang.bin

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"]) internal actual typealias BytesImpl = DataView
actual fun BytesImpl.size(): Int = byteLength

actual operator fun BytesImpl.get(idx: Int): Byte = getInt8(idx)

actual operator fun BytesImpl.set(idx: Int, value: Byte) {
    setInt8(byteOffset = idx, value = value)
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class MutBytes @PublishedApi internal actual constructor(
    @PublishedApi internal actual val impl: BytesImpl,
) {
    actual val size: Int
        get() = impl.byteLength

    actual val readonly: Bytes get() = Bytes(this)

    actual constructor(dataSize: DataSize) : this(DataView(buffer = ArrayBuffer(dataSize.bytes)))
    constructor(arrayBuffer: ArrayBuffer) : this(DataView(buffer = arrayBuffer))

    actual fun getPackedLong(idx: Int): Long {
        val low = impl.getInt32(byteOffset = idx, littleEndian = true).toLong() and 0xffffffffL
        val high = impl.getInt32(byteOffset = idx + 4, littleEndian = true).toLong()
        return low or (high shl 32)
    }

    actual fun setPackedLong(idx: Int, value: Long) {
        impl.setInt32(byteOffset = idx, value = value.toInt(), littleEndian = true)
        impl.setInt32(byteOffset = idx + 4, value = (value ushr 32).toInt(), littleEndian = true)
    }

    actual fun getPackedInt(idx: Int): Int = impl.getInt32(byteOffset = idx, littleEndian = true)

    actual fun setPackedInt(idx: Int, value: Int) {
        impl.setInt32(byteOffset = idx, value = value, littleEndian = true)
    }

    actual fun setPackedShort(idx: Int, value: Short) {
        impl.setInt16(byteOffset = idx, value = value, littleEndian = true)
    }

    actual fun getPackedShort(idx: Int): Short = impl.getInt16(byteOffset = idx, littleEndian = true)

    actual operator fun get(idx: Int): Byte = impl.getInt8(idx)

    actual operator fun set(idx: Int, value: Byte) = impl.setInt8(byteOffset = idx, value = value)

    actual fun hash(offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw IndexOutOfBoundsException()
        }

        var result = 1
        var i = 0
        while (i <= length - 4) {
            val word = impl.getUint32(byteOffset = offset + i)
            result = 31 * result + (word shr 24)
            result = 31 * result + ((word shl 8) shr 24)
            result = 31 * result + ((word shl 16) shr 24)
            result = 31 * result + ((word shl 24) shr 24)
            i += 4
        }
        while (i < length) {
            result = 31 * result + impl.getInt8(offset + i)
            i++
        }
        return result
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

    actual fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) {
        require(startIndex <= endIndex)
        val length = endIndex - startIndex
        if (startIndex < 0 ||
            endIndex > size ||
            destinationOffset < 0 ||
            destinationOffset > destination.size - length) {
            throw IndexOutOfBoundsException()
        }
        Uint8Array(
            buffer = destination.impl.buffer,
            byteOffset = destination.impl.byteOffset,
            length = destination.size,
        ).set(
            Uint8Array(
                buffer = impl.buffer,
                byteOffset = impl.byteOffset + startIndex,
                length = length,
            ),
            destinationOffset,
        )
    }

    actual fun copyOf(newCapacity: Int): MutBytes {
        val copy = ArrayBuffer(newCapacity)
        Uint8Array(buffer = copy).set(Uint8Array(buffer = impl.buffer))
        return MutBytes(DataView(buffer = copy))
    }

    actual fun copyOfRange(from: Int, to: Int): MutBytes {
        require(from <= to)
        if (from < 0 || to > size) {
            throw IndexOutOfBoundsException()
        }
        return MutBytes(
            DataView(buffer = impl.buffer.slice(begin = impl.byteOffset + from, end = impl.byteOffset + to)),
        )
    }

    actual companion object {
        actual val empty: MutBytes = MutBytes(DataView(buffer = ArrayBuffer(0)))

        actual inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes {
            val view = DataView(buffer = ArrayBuffer(dataSize.bytes))
            val s = dataSize.bytes
            for (idx in 0 until s) {
                view.setInt8(
                    byteOffset = idx,
                    value = init(idx),
                )
            }
            return MutBytes(view)
        }

        actual operator fun invoke(
            array: ByteArray,
        ): MutBytes = MutBytes(DataView(buffer = array.unsafeCast<Uint8Array>().buffer))
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
    actual val size: Int
        get() = bytes.size

    actual operator fun get(idx: Int): Byte = bytes[idx]

    actual fun copyInto(
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

    actual fun asList(): List<Byte> {
        return Array(size) { idx -> bytes[idx] }.asList()
    }

    actual companion object {
        actual val empty: Bytes
            get() = Bytes(MutBytes.empty)
    }
}
