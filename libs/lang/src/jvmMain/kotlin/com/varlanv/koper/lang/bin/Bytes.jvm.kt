package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.intViewHandle
import com.varlanv.koper.lang.longViewHandle
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

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
