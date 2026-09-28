package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.intViewHandle
import com.varlanv.koper.lang.longViewHandle
import com.varlanv.koper.lang.shortViewHandle

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"]) internal actual typealias BytesImpl = ByteArray

actual fun BytesImpl.size(): Int = size

actual operator fun BytesImpl.get(idx: Int): Byte = get(idx)

actual operator fun BytesImpl.set(idx: Int, value: Byte) = set(idx, value)

@JvmInline
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class MutBytes @PublishedApi internal actual constructor(
    @PublishedApi internal actual val impl: BytesImpl,
) {
    actual val size: Int
        get() = impl.size
    actual val readonly: Bytes
        get() = Bytes(this)

    actual constructor(dataSize: DataSize) : this(ByteArray(dataSize.bytes))

    actual fun getPackedLong(idx: Int): Long = longViewHandle.get(impl, idx) as Long

    actual fun setPackedLong(idx: Int, value: Long) {
        longViewHandle.set(impl, idx, value)
    }

    actual fun getPackedInt(idx: Int): Int = intViewHandle.get(impl, idx) as Int

    actual fun setPackedInt(idx: Int, value: Int) {
        intViewHandle.set(impl, idx, value)
    }

    actual fun setPackedShort(idx: Int, value: Short) {
        shortViewHandle.set(impl, idx, value)
    }

    actual fun getPackedShort(idx: Int): Short = shortViewHandle.get(impl, idx) as Short

    actual operator fun get(idx: Int): Byte = impl[idx]

    actual operator fun set(idx: Int, value: Byte) {
        impl[idx] = value
    }

    actual fun hash(offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > impl.size - length) {
            throw IndexOutOfBoundsException()
        }
        val end = offset + length
        return if (VectorApi.enabled && length >= 128) {
            BytesHash.target.hash(bytes = this.readonly, start = offset, end = end)
        } else {
            hashSwar(bytes = this.readonly, start = offset, end = end, initial = 1)
        }
    }

    actual inline fun forEach(block: (Byte) -> Unit) = impl.forEach(block)

    actual inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) = impl.forEachIndexed(block)

    actual fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) {
        require(startIndex <= endIndex)
        System.arraycopy(impl, startIndex, destination.impl, destinationOffset, endIndex - startIndex)
    }

    actual fun copyOf(newCapacity: Int): MutBytes = MutBytes(impl.copyOf(newCapacity))

    actual fun copyOfRange(from: Int, to: Int): MutBytes = MutBytes(impl.copyOfRange(from, to))

    actual companion object {
        actual val empty: MutBytes = MutBytes(ByteArray(0))

        actual inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes {
            val s = dataSize.bytes
            val arr = ByteArray(s)
            for (idx in 0 until s) {
                arr[idx] = init(idx)
            }
            return MutBytes(arr)
        }

        actual operator fun invoke(array: ByteArray): MutBytes = MutBytes(array)
    }
}
