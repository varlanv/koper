package com.varlanv.koper.lang

internal val asciiTriplets = IntArray(1024) { value ->
    ((value / 100 + 48) shl 16) or
        ((value / 10 % 10 + 48) shl 8) or
        (value % 10 + 48)
}

expect fun writeLongAscii(value: Long, array: ByteArray, offset: Int): Int

fun writeIntAscii(value: Int, array: ByteArray, offset: Int): Int {
    if (value >= 0) {
        return writePositiveIntAscii(value, array, offset)
    }
    array[offset] = 45
    if (value == Int.MIN_VALUE) {
        array[offset + 1] = 50
        writeNineAscii(147_483_648, array, offset + 2)
        return offset + 11
    }
    return writePositiveIntAscii(-value, array, offset + 1)
}

internal fun writePositiveIntAscii(value: Int, array: ByteArray, offset: Int): Int {
    if (value < 1000) {
        return writeLeadingTripletAscii(value, array, offset)
    }
    val upper = value / 1000
    val lower = value - upper * 1000
    if (upper < 1000) {
        val position = writeLeadingTripletAscii(upper, array, offset)
        writeTripletAscii(lower, array, position)
        return position + 3
    }
    val leading = upper / 1000
    val middle = upper - leading * 1000
    val position = if (leading < 1000) {
        writeLeadingTripletAscii(leading, array, offset)
    } else {
        val billions = leading / 1000
        array[offset] = (billions + 48).toByte()
        writeTripletAscii(leading - billions * 1000, array, offset + 1)
        offset + 4
    }
    writeTripletAscii(middle, array, position)
    writeTripletAscii(lower, array, position + 3)
    return position + 6
}

private fun writeLeadingTripletAscii(value: Int, array: ByteArray, offset: Int): Int {
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

private fun writeTripletAscii(value: Int, array: ByteArray, offset: Int) {
    val digits = asciiTriplets[value and 1023]
    array[offset] = (digits ushr 16).toByte()
    array[offset + 1] = (digits ushr 8).toByte()
    array[offset + 2] = digits.toByte()
}

internal fun writeNineAscii(value: Int, array: ByteArray, offset: Int) {
    val upper = value / 1000
    val leading = upper / 1000
    writeTripletAscii(leading, array, offset)
    writeTripletAscii(upper - leading * 1000, array, offset + 3)
    writeTripletAscii(value - upper * 1000, array, offset + 6)
}

fun writeDoubleAscii(value: Double, array: ByteArray, offset: Int): Int =
    writeDoubleAsciiImpl(value, array, offset)
