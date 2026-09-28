package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.intViewHandle
import com.varlanv.koper.lang.longViewHandle
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual typealias BytesImpl = ByteArray

@JvmInline
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class Bytes private actual constructor(actual val impl: BytesImpl): BytesOperations {
    actual val size: Int
        get() = impl.size

    actual override fun getInt(idx: Int): Int = intViewHandle.get(impl, idx) as Int
    actual override fun setInt(idx: Int, value: Int) {
        intViewHandle.set(impl, idx, value)
    }

    actual override operator fun get(idx: Int): Byte = impl[idx]
    actual override operator fun set(idx: Int, value: Byte) {
        impl[idx] = value
    }

    actual companion object {
        actual operator fun invoke(capacity: Int): Bytes = Bytes(ByteArray(capacity))

        actual operator fun invoke(dataSize: DataSize): Bytes = invoke(dataSize.bytes)
    }

    actual fun hash(offset: Int, length: Int): Int {
        TODO("Implement vectorized(when activated) or SWAR fallback hash")
    }
}


actual fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Int = Arrays.mismatch(this, aFromIndex, aToIndex, b, bFromIndex, bToIndex)

fun ByteSlice.readBuff(): ByteBuffer {
    return ByteBuffer.wrap(this.bytes.array, offset, len).slice()
}

actual fun ByteArray.setPackedInt(idx: Int, i: Int) {
    intViewHandle.set(this, idx, i)
}

actual fun ByteArray.setPackedLong(idx: Int, l: Long) {
    longViewHandle.set(this, idx, l)
}

/** Adapts [ins] to a [ByteSource]. */
class InputStreamByteSource(val ins: InputStream) : ByteSource {
    override fun readAtMostTo(
        sink: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        return ins.read(sink, offset, length)
    }
}

/** Adapts [outs] to a [ByteSink]. */
class OutputStreamByteSink(val outs: OutputStream) : ByteSink {
    override fun writeTo(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        outs.write(source, offset, length)
    }
}

actual fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int
): Int = impl.mismatch(aFromIndex, aToIndex, b.impl, bFromIndex, bToIndex)
