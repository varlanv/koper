package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes

/**
 * Read-only detection of quotes, backslashes, and unsigned control bytes below 32.
 * Backends expose a lane width for masks and can scan longer ranges without modifying input.
 */
internal interface JsonSpecialScan {
    /**
     * Number of bytes inspected by one [specialMask] call, represented by the bits of a [JsonScanMask].
     */
    val laneCount: Int

    /**
     * Whether this backend uses vector scanning.
     */
    val isVector: Boolean

    /**
     * Inspects bytes in [start] until [end] without modifying bytes or parser state.
     *
     * @return The first special-byte offset as an [Int], or [end] when the range contains none.
     */
    fun firstSpecial(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int

    /**
     * Inspects [laneCount] bytes starting at [start]; the caller must provide a complete lane.
     * Does not modify bytes or parser state.
     *
     * @return A [JsonScanMask] with bit i set when the byte at start + i is special; zero means none are special.
     */
    fun specialMask(bytes: Bytes, start: Int): JsonScanMask
}

/**
 * Selects the platform backend, preferring vector scanning when [vectorized] is true and support is available.
 * May initialize a shared backend, but does not read or modify parser input.
 *
 * @return A [JsonSpecialScan] backend, falling back to scalar scanning when needed.
 */
internal expect fun jsonSpecialScan(vectorized: Boolean): JsonSpecialScan

/**
 * Scalar backend for [JsonSpecialScan]; its read-only methods follow the interface contracts.
 */
internal object ScalarJsonSpecialScan : JsonSpecialScan {
    override val laneCount: Int = 8
    override val isVector: Boolean = false

    override fun specialMask(bytes: Bytes, start: Int): JsonScanMask {
        var mask = jsonEmptyScanMask()
        for (index in 0 until laneCount) {
            val value = bytes[start + index].toInt() and 255
            if (value == 34 || value == 92 || value < 32) {
                mask = mask.withBit(index)
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
