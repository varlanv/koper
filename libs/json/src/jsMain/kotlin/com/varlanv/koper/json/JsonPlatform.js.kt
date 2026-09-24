package com.varlanv.koper.json

internal actual fun ByteArray.getPackedJsonLong(offset: Int): Long {
    var result = 0L
    for (index in 0 until 8) result = result or ((this[offset + index].toLong() and 255L) shl (index * 8))
    return result
}

internal actual fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan = ScalarJsonSpecialScan
