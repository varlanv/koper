package com.varlanv.koper.lang.bin

internal actual fun ByteArray.skipAscii(start: Int, end: Int): Int {
    var index = start
    while (index < end && this[index] >= 0) {
        index++
    }
    return index
}
