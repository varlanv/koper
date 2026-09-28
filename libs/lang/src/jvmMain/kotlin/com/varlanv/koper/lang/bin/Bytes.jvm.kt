package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.intViewHandle
import com.varlanv.koper.lang.longViewHandle
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual typealias BytesImpl = ByteArray

actual fun BytesImpl.size(): Int = size

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

    actual operator fun get(idx: Int): Byte = impl[idx]

    actual operator fun set(idx: Int, value: Byte) {
        impl[idx] = value
    }

    actual fun hash(offset: Int, length: Int): Int {
        TODO("Implement vectorized(when activated) or SWAR fallback hash")
    }

    actual inline fun forEach(block: (Byte) -> Unit) = impl.forEach(block)

    actual inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) = impl.forEachIndexed(block)

    actual fun copyInto(destination: MutBytes, destinationOffset: Int, startIndex: Int, endIndex: Int) {
// todo implement correctly ...        System.arraycopy(impl, startIndex, destination, endIndex)
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

actual fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Int = Arrays.mismatch(this, aFromIndex, aToIndex, b, bFromIndex, bToIndex)

actual fun ByteArray.setPackedInt(idx: Int, i: Int) {
    intViewHandle.set(this, idx, i)
}

actual fun ByteArray.setPackedLong(idx: Int, l: Long) {
    longViewHandle.set(this, idx, l)
}


actual fun MutBytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: MutBytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int = impl.mismatch(aFromIndex, aToIndex, b.impl, bFromIndex, bToIndex)


/** Adapts [ins] to a [ByteSource]. */
class InputStreamByteSource(val ins: InputStream) : ByteSource {
    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        return ins.read(sink.impl, offset, length)
    }
}

/** Adapts [outs] to a [ByteSink]. */
class OutputStreamByteSink(val outs: OutputStream) : ByteSink {
    override fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    ) {
        outs.write(source.impl, offset, length)
    }
}

@JvmInline
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

    inline fun forEachIndex(block: (idx: Int)-> Unit) {
        for (idx in 0 until bytes.impl.size) {
            block(idx)
        }
    }

    class Unsafe internal constructor() {

        inline fun <R> useInternal(bytes: Bytes, block: (ByteArray) -> R): R {
            return block(bytes.bytes.impl)
        }

        inline fun <R> useInternal(bytes: MutBytes, block: (ByteArray) -> R): R {
            return block(bytes.impl)
        }
    }


    actual companion object {
        @PublishedApi
        internal val unsafe: Unsafe = Unsafe()

        actual val empty: Bytes = Bytes(MutBytes.empty)

        inline fun <R> unsafe(block: Unsafe.() -> R): R {
            return block(unsafe)
        }
    }
}
