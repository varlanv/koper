package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.lang.bin.Bytes
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

object  VectorJsonScan {
    private val species = ByteVector.SPECIES_PREFERRED
    private val swarEnabled = java.lang.Boolean.getBoolean("ideal.swar.scan")
    val laneCount: Int = species.length()

    fun specialMask(bytes: Bytes, start: Int): Long {
        val vector = Bytes.unsafe { useInternal(bytes) { ByteVector.fromArray(species, it, start) } }
        return vector
            .compare(VectorOperators.EQ, 34.toByte())
            .or(vector.compare(VectorOperators.EQ, 92.toByte()))
            .or(vector.compare(VectorOperators.GE, 0.toByte()).and(vector.compare(VectorOperators.LT, 32.toByte())))
            .toLong()
    }

    fun firstSpecial(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int {
        var index = start
        val vectorEnd = end - laneCount
        while (index <= vectorEnd) {
            val vector = Bytes.unsafe { useInternal(bytes) { ByteVector.fromArray(species, it, index) } }
            val special = vector
                .compare(VectorOperators.EQ, 34.toByte())
                .or(vector.compare(VectorOperators.EQ, 92.toByte()))
                .or(vector.compare(VectorOperators.GE, 0.toByte()).and(vector.compare(VectorOperators.LT, 32.toByte())))
            if (special.anyTrue()) {
                return index + special.firstTrue()
            }
            index += laneCount
        }
        if (swarEnabled) {
            val vector = Bytes.unsafe {
                useInternal(bytes) { SwarJsonScan.firstSpecial(bytes = it, start = index, end = end) }
            }
        }
        while (index < end) {
            val byte = bytes[index].toInt() and 255
            if (byte == 34 || byte == 92 || byte < 32) {
                return index
            }
            index++
        }
        return end
    }
}
