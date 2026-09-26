package com.varlanv.koper.json

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.longViewHandle
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

internal actual fun ByteArray.getPackedJsonLong(offset: Int): Long = longViewHandle.get(this, offset) as Long

internal actual fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan = if (vectorized) {
    VectorApi.tryLoad { VectorJsonSpecialScan } ?: ScalarJsonSpecialScan
} else {
    ScalarJsonSpecialScan
}

private object VectorJsonSpecialScan : JsonSpecialScan, VectorApi {
    init {
        VectorApi.ensureEnabled(VectorJsonSpecialScan::class)
    }

    private val species = ByteVector.SPECIES_PREFERRED
    override val laneCount: Int = species.length()

    override fun smokeTest(): Boolean {
        val bytes = ByteArray(laneCount + 1) { 65 }
        val special = laneCount / 2
        bytes[special + 1] = 34
        return specialMask(bytes = bytes, start = 1) == (1L shl special)
    }

    override fun specialMask(bytes: ByteArray, start: Int): Long {
        val vector = ByteVector.fromArray(species, bytes, start)
        return vector
            .compare(VectorOperators.EQ, 34.toByte())
            .or(vector.compare(VectorOperators.EQ, 92.toByte()))
            .or(vector.compare(VectorOperators.GE, 0.toByte()).and(vector.compare(VectorOperators.LT, 32.toByte())))
            .toLong()
    }

    override fun firstSpecial(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        var index = start
        while (index <= end - laneCount) {
            val mask = specialMask(bytes = bytes, start = index)
            if (mask != 0L) {
                return index + mask.countTrailingZeroBits()
            }
            index += laneCount
        }
        while (index < end) {
            val value = bytes[index].toInt() and 255
            if (value == 34 || value == 92 || value < 32) {
                return index
            }
            index++
        }
        return end
    }
}
