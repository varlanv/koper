package com.varlanv.koper.lang

import java.nio.ByteBuffer
import java.util.*

actual fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int
): Int = Arrays.mismatch(this, aFromIndex, aToIndex, b, bFromIndex, bToIndex)

fun BytesSlice.readBuff(): ByteBuffer {
    return ByteBuffer.wrap(this.bytes.array, offset, len).slice()
}

actual fun ByteArray.setPackedInt(idx: Int, i: Int) {
    intViewHandle.set(this, idx, i)
}

actual fun ByteArray.setPackedLong(idx: Int, l: Long) {
    longViewHandle.set(this, idx, l)
}
