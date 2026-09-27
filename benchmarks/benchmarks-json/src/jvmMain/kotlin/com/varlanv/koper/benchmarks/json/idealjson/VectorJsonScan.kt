package com.varlanv.koper.benchmarks.json

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

object VectorJsonScan {
    private val species = ByteVector.SPECIES_PREFERRED
    private val swarEnabled = java.lang.Boolean.getBoolean("ideal.swar.scan")
    val laneCount: Int = species.length()

    fun specialMask(bytes: ByteArray, start: Int): Long {
        val vector = ByteVector.fromArray(species, bytes, start)
        return vector
            .compare(VectorOperators.EQ, 34.toByte())
            .or(vector.compare(VectorOperators.EQ, 92.toByte()))
            .or(vector.compare(VectorOperators.GE, 0.toByte()).and(vector.compare(VectorOperators.LT, 32.toByte())))
            .toLong()
    }

    fun firstSpecial(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        var index = start
        val vectorEnd = end - laneCount
        while (index <= vectorEnd) {
            val vector = ByteVector.fromArray(species, bytes, index)
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
            return SwarJsonScan.firstSpecial(bytes = bytes, start = index, end = end)
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
