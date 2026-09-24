package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.longViewHandle

private const val HIGH_BITS = 0x8080808080808080UL
private val vectorEnabled = java.lang.Boolean.getBoolean("koper.lang.utf8.vector") &&
        ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent

internal actual fun ByteArray.skipAscii(start: Int, end: Int): Int {
    if (vectorEnabled && end - start >= 128) {
        // Short ASCII runs are cheaper with SWAR; only continue with vectors
        // after the first 64 bytes are known to be ASCII.
        val probeEnd = start + 64
        val firstNonAscii = skipAsciiSwar(start, probeEnd)
        if (firstNonAscii != probeEnd) return firstNonAscii
        return VectorAsciiScan.skipAscii(this, probeEnd, end)
    }
    return skipAsciiSwar(start, end)
}

private fun ByteArray.skipAsciiSwar(start: Int, end: Int): Int {
    var index = start
    while (index <= end - Long.SIZE_BYTES) {
        val word = longViewHandle.get(this, index) as Long
        if (word.toULong() and HIGH_BITS != 0UL) {
            break
        }
        index += Long.SIZE_BYTES
    }
    while (index < end && this[index] >= 0) {
        index++
    }
    return index
}
