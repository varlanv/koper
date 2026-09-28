package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes

internal actual fun Bytes.getPackedJsonLong(offset: Int): Long {
    var result = 0L
    for (index in 0 until 8) result = result or ((this[offset + index].toLong() and 255L) shl (index * 8))
    return result
}

internal actual fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan = ScalarJsonSpecialScan
