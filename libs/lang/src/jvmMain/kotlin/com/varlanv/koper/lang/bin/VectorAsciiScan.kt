package com.varlanv.koper.lang.bin

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

/** Loaded only when the incubating Vector API is enabled at JVM startup. */
internal object VectorAsciiScan {
    private val species = ByteVector.SPECIES_PREFERRED

    fun skipAscii(bytes: ByteArray, start: Int, end: Int): Int {
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
