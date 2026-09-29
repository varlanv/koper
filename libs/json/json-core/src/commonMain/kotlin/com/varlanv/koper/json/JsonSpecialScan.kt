package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes

internal expect fun Bytes.getPackedJsonLong(offset: Int): Long

interface JsonSpecialScan {
    val laneCount: Int
    val isVector: Boolean

    fun firstSpecial(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int

    fun specialMask(bytes: Bytes, start: Int): Long
}

internal expect fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan

internal object ScalarJsonSpecialScan : JsonSpecialScan {
    override val laneCount: Int = 8
    override val isVector: Boolean = false

    override fun specialMask(bytes: Bytes, start: Int): Long {
        var mask = 0L
        for (index in 0 until laneCount) {
            val value = bytes[start + index].toInt() and 255
            if (value == 34 || value == 92 || value < 32) {
                mask = mask or (1L shl index)
            }
        }
        return mask
    }

    override fun firstSpecial(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int {
        var index = start
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
