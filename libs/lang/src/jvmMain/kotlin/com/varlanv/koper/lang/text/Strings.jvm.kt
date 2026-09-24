package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.intViewHandle

actual fun ByteStr.len(): Int = intViewHandle.get(bytes.array, 0) as Int
actual fun ByteStr.encoding(): Charset = Charset.entries[bytes.array[4].toInt() and 0xff]

actual fun ByteStr.copyInto(
    sourceOffset: Int,
    destination: ByteArray,
    destinationOffset: Int,
    length: Int
) {
    System.arraycopy(this.bytes.array, sourceOffset + 8, destination,
        destinationOffset, length)
}
