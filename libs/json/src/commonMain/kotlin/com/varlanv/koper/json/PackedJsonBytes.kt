package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.setPackedInt
import com.varlanv.koper.lang.bin.setPackedLong

internal object PackedJsonBytes {
    fun setShort(bytes: ByteArray, offset: Int, value: Short) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value.toInt() ushr 8).toByte()
    }

    fun setInt(bytes: ByteArray, offset: Int, value: Int) = bytes.setPackedInt(offset, value)
    fun setLong(bytes: ByteArray, offset: Int, value: Long) = bytes.setPackedLong(offset, value)
    fun getInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            (bytes[offset + 3].toInt() shl 24)
    fun getLong(bytes: ByteArray, offset: Int): Long = bytes.getPackedJsonLong(offset)
}

internal expect fun ByteArray.getPackedJsonLong(offset: Int): Long

interface JsonSpecialScan  {
    val laneCount: Int
    fun firstSpecial(bytes: ByteArray, start: Int, end: Int): Int
    fun specialMask(bytes: ByteArray, start: Int): Long
}

internal expect fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan

internal object ScalarJsonSpecialScan : JsonSpecialScan {
    override val laneCount: Int = 8

    override fun specialMask(bytes: ByteArray, start: Int): Long {
        var mask = 0L
        for (index in 0 until laneCount) {
            val value = bytes[start + index].toInt() and 255
            if (value == 34 || value == 92 || value < 32) mask = mask or (1L shl index)
        }
        return mask
    }

    override fun firstSpecial(bytes: ByteArray, start: Int, end: Int): Int {
        var index = start
        while (index < end) {
            val value = bytes[index].toInt() and 255
            if (value == 34 || value == 92 || value < 32) return index
            index++
        }
        return end
    }
}
