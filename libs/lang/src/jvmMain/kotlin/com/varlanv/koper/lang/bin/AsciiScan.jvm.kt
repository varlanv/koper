package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.SIMD
import com.varlanv.koper.lang.longViewHandle
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

private const val HIGH_BITS = 0x8080808080808080UL

internal actual fun ByteArray.skipAscii(start: Int, end: Int): Int {
    if (SIMD.enabled && end - start >= 128) {
        // Short ASCII runs are cheaper with SWAR; only continue with vectors
        // after the first 64 bytes are known to be ASCII.
        val probeEnd = start + 64
        val firstNonAscii = skipAsciiSwar(start, probeEnd)
        if (firstNonAscii != probeEnd) return firstNonAscii
        return AsciiScan.target.skipAscii(this, probeEnd, end)
    }
    return this.skipAsciiSwar(start, end)
}

sealed interface AsciiScan {
    fun skipAscii(bytes: ByteArray, start: Int, end: Int): Int

    companion object {
        val target = SIMD.tryLoad { VectorAsciiScan } ?: ScalarAsciiScan
    }
}

/** Loaded only when the incubating Vector API is enabled at JVM startup. */
private object VectorAsciiScan : AsciiScan {

    init {
        SIMD.ensureEnabled(VectorAsciiScan::class)
    }

    private val species = ByteVector.SPECIES_PREFERRED

    override fun skipAscii(bytes: ByteArray, start: Int, end: Int): Int {
        var index = start
        val lanes = species.length()
        while (index <= end - lanes) {
            val mask = ByteVector.fromArray(species, bytes, index)
                .compare(VectorOperators.LT, 0.toByte())
            if (mask.anyTrue()) return index + mask.firstTrue()
            index += lanes
        }
        while (index < end && bytes[index] >= 0) index++
        return index
    }

}

private object ScalarAsciiScan : AsciiScan {
    override fun skipAscii(bytes: ByteArray, start: Int, end: Int): Int = bytes.skipAsciiSwar(start, end)
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
