package com.varlanv.koper.lang.text

actual fun ByteStr.len(): Int {
    val array = bytes.array
    return (array[0].toInt() and 0xff) or
            ((array[1].toInt() and 0xff) shl 8) or
            ((array[2].toInt() and 0xff) shl 16) or
            (array[3].toInt() shl 24)
}

actual fun ByteStr.encoding(): Charset = Charset.entries[bytes.array[4].toInt() and 0xff]

actual fun ByteStr.copyInto(
    sourceOffset: Int,
    destination: ByteArray,
    destinationOffset: Int,
    length: Int
) {
    bytes.array.copyInto(destination, destinationOffset, sourceOffset + 8, sourceOffset + 8 + length)
}
