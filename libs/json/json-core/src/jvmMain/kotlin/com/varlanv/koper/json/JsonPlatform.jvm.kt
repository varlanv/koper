package com.varlanv.koper.json

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.bin.getPackedLong
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators

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
    override val isVector: Boolean = true

    override fun smokeTest(): Boolean {
        val bytes = MutBytes((laneCount + 1).bytes()) { 65 }
        val special = laneCount / 2
        bytes[special + 1] = 34
        return specialMask(
            bytes = Bytes(bytes),
            start = 1,
        ) == (1L shl special)
    }

    override fun specialMask(bytes: Bytes, start: Int): JsonScanMask {
        val vector = Bytes.unsafe { useInternal(bytes) { ByteVector.fromArray(species, it, start) } }
        return vector
            .compare(VectorOperators.EQ, 34.toByte())
            .or(vector.compare(VectorOperators.EQ, 92.toByte()))
            .or(vector.compare(VectorOperators.GE, 0.toByte()).and(vector.compare(VectorOperators.LT, 32.toByte())))
            .toLong()
    }

    override fun firstSpecial(
        bytes: Bytes,
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
        while (index <= end - 8) {
            val events = jsonSpecialEvents(bytes.getPackedLong(index))
            if (events != 0L) {
                return index + (events.countTrailingZeroBits() ushr 3)
            }
            index += 8
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
