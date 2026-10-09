package com.varlanv.koper.lang.math

import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.collection.*

object Numbers {
    val asciiTriplets: Ints = Ints(1024) { value ->
        ((value / 100 + 48) shl 16) or ((value / 10 % 10 + 48) shl 8) or (value % 10 + 48)
    }
    val INT_POWERS_OF_10: Ints = intsOf(
        1,
        10,
        100,
        1_000,
        10_000,
        100_000,
        1_000_000,
        10_000_000,
        100_000_000,
        1_000_000_000,
    )

    val LONG_POWERS_OF_10: Longs = longsOf(
        1L,
        10L,
        100L,
        1_000L,
        10_000L,
        100_000L,
        1_000_000L,
        10_000_000L,
        100_000_000L,
        1_000_000_000L,
        10_000_000_000L,
        100_000_000_000L,
        1_000_000_000_000L,
        10_000_000_000_000L,
        100_000_000_000_000L,
        1_000_000_000_000_000L,
        10_000_000_000_000_000L,
        100_000_000_000_000_000L,
        1_000_000_000_000_000_000L,
    )

    fun writeIntAscii(
        value: Int,
        array: MutBytes,
        offset: Int,
    ): Int {
        if (value >= 0) {
            return writePositiveIntAscii(value = value, array = array, offset = offset)
        }
        array[offset] = '-'.code.toByte()
        if (value == Int.MIN_VALUE) {
            array[offset + 1] = '2'.code.toByte()
            writeNineAscii(value = 147_483_648, array = array, offset = offset + 2)
            return offset + 11
        }
        return writePositiveIntAscii(value = -value, array = array, offset = offset + 1)
    }

    fun writePositiveIntAscii(
        value: Int,
        array: MutBytes,
        offset: Int,
    ): Int {
        if (value < 1000) {
            return writeLeadingTripletAscii(value = value, array = array, offset = offset)
        }
        val upper = value / 1000
        val lower = value - upper * 1000
        if (upper < 1000) {
            val position = writeLeadingTripletAscii(value = upper, array = array, offset = offset)
            writeTripletAscii(value = lower, array = array, offset = position)
            return position + 3
        }
        val leading = upper / 1000
        val middle = upper - leading * 1000
        val position = if (leading < 1000) {
            writeLeadingTripletAscii(value = leading, array = array, offset = offset)
        } else {
            val billions = leading / 1000
            array[offset] = (billions + 48).toByte()
            writeTripletAscii(value = leading - billions * 1000, array = array, offset = offset + 1)
            offset + 4
        }
        writeTripletAscii(value = middle, array = array, offset = position)
        writeTripletAscii(value = lower, array = array, offset = position + 3)
        return position + 6
    }

    fun writeLeadingTripletAscii(
        value: Int,
        array: MutBytes,
        offset: Int,
    ): Int {
        if (value < 10) {
            array[offset] = (value + 48).toByte()
            return offset + 1
        }
        val digits = asciiTriplets[value and 1023]
        if (value < 100) {
            array[offset] = (digits ushr 8).toByte()
            array[offset + 1] = digits.toByte()
            return offset + 2
        }
        array[offset] = (digits ushr 16).toByte()
        array[offset + 1] = (digits ushr 8).toByte()
        array[offset + 2] = digits.toByte()
        return offset + 3
    }

    fun writeTripletAscii(
        value: Int,
        array: MutBytes,
        offset: Int,
    ) {
        val digits = asciiTriplets[value and 1023]
        array[offset] = (digits ushr 16).toByte()
        array[offset + 1] = (digits ushr 8).toByte()
        array[offset + 2] = digits.toByte()
    }

    fun writeNineAscii(
        value: Int,
        array: MutBytes,
        offset: Int,
    ) {
        val upper = value / 1000
        val leading = upper / 1000
        writeTripletAscii(value = leading, array = array, offset = offset)
        writeTripletAscii(value = upper - leading * 1000, array = array, offset = offset + 3)
        writeTripletAscii(value = value - upper * 1000, array = array, offset = offset + 6)
    }

    fun writeDoubleAscii(
        value: Double,
        array: MutBytes,
        offset: Int,
    ): Int = writeDoubleAsciiImpl(value = value, array = array, offset = offset)
}

expect fun Numbers.writeLongAscii(
    value: Long,
    array: MutBytes,
    offset: Int,
): Int
