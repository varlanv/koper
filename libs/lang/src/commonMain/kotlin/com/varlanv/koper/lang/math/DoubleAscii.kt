package com.varlanv.koper.lang.math

internal expect fun writeDoubleAsciiImpl(value: Double, array: ByteArray, offset: Int): Int

internal fun writeDoubleAsciiPortable(value: Double, array: ByteArray, offset: Int): Int {
    val bits = value.toRawBits()
    val fraction = bits and 0x000f_ffff_ffff_ffffL
    val exponentBits = ((bits ushr 52) and 2047).toInt()
    if (exponentBits == 2047 && fraction != 0L) {
        array[offset] = 78
        array[offset + 1] = 97
        array[offset + 2] = 78
        return offset + 3
    }
    var start = offset
    if (bits < 0) array[start++] = 45
    if (exponentBits == 2047) {
        array[start] = 73
        array[start + 1] = 110
        array[start + 2] = 102
        array[start + 3] = 105
        array[start + 4] = 110
        array[start + 5] = 105
        array[start + 6] = 116
        array[start + 7] = 121
        return start + 8
    }
    if (exponentBits == 0 && fraction == 0L) {
        array[start] = 48
        array[start + 1] = 46
        array[start + 2] = 48
        return start + 3
    }
    var significand = fraction
    val binaryExponent: Int
    var decimalAdjustment = 0
    if (exponentBits == 0) {
        binaryExponent = -1074
        if (significand < 3) {
            significand *= 10
            decimalAdjustment = -1
        }
    } else {
        significand = significand or 0x0010_0000_0000_0000L
        binaryExponent = exponentBits - 1075
        if (binaryExponent in -52..-1) {
            val integral = significand ushr -binaryExponent
            if ((integral shl -binaryExponent) == significand) {
                return formatDoubleDecimal(integral, 0, array, start)
            }
        }
    }
    val asymmetric = significand == 0x0010_0000_0000_0000L && binaryExponent != -1074
    val decimalExponent = (binaryExponent * 315653 - if (asymmetric) 131008 else 0) shr 20
    val power = -decimalExponent
    val tableIndex = (power + 292) * 2
    val high = doubleDecimalPowers[tableIndex]
    val low = doubleDecimalPowers[tableIndex + 1]
    val shift = binaryExponent + ((power * 1741647) shr 19) + 2
    val center = significand shl 2
    val lower = doubleScaledOdd((center - if (asymmetric) 1 else 2) shl shift, high, low)
    val upper = doubleScaledOdd((center + 2) shl shift, high, low)
    val scaled = doubleScaledOdd(center shl shift, high, low)
    val excluded = significand and 1
    val down = scaled ushr 2
    if (down >= 100) {
        val shortDown = down / 10 * 10
        val shortUp = shortDown + 10
        val downInside = (shortDown shl 2) >= lower + excluded
        val upInside = (shortUp shl 2) + excluded <= upper
        if (downInside != upInside) {
            return formatDoubleDecimal(if (downInside) shortDown else shortUp, decimalExponent, array, start)
        }
    }
    val up = down + 1
    val downInside = (down shl 2) >= lower + excluded
    val upInside = (up shl 2) + excluded <= upper
    val rounded = when {
        !downInside -> up
        !upInside -> down
        scaled > (down shl 2) + 2 -> up
        scaled < (down shl 2) + 2 -> down
        else -> down + (down and 1)
    }
    return formatDoubleDecimal(rounded, decimalExponent + decimalAdjustment, array, start)
}

private fun doubleScaledOdd(value: Long, high: Long, low: Long): Long {
    val middleLow = value * high
    val middle = middleLow + value.unsignedMultiplyHigh(low)
    val carry = if (middle.compareUnsigned(middleLow) < 0) 1 else 0
    val top = value.unsignedMultiplyHigh(high) + carry
    return (top ushr 1) or if (middle != 0L || (top and 1) != 0L) 1L else 0L
}

private fun formatDoubleDecimal(value: Long, exponent: Int, array: ByteArray, offset: Int): Int {
    var digits = value
    var scale = exponent
    if (digits % 10 == 0L) {
        digits /= 10
        scale++
        if (digits % 10 == 0L) {
            if (digits % 100_000_000 == 0L) {
                digits /= 100_000_000
                scale += 8
            }
            if (digits % 10_000 == 0L) {
                digits /= 10_000
                scale += 4
            }
            if (digits % 100 == 0L) {
                digits /= 100
                scale += 2
            }
            if (digits % 10 == 0L) {
                digits /= 10
                scale++
            }
        }
    }
    val length = doubleDecimalLength(digits)
    val point = length + scale
    if (point in 1..7) {
        if (point >= length) {
            var end = writeLongAscii(digits, array, offset)
            repeat(point - length) { array[end++] = 48 }
            array[end] = 46
            array[end + 1] = 48
            return end + 2
        }
        writeDoubleDigitsWithPoint(digits, array, offset + length + 1, offset + point)
        return offset + length + 1
    }
    if (point in -2..0) {
        array[offset] = 48
        array[offset + 1] = 46
        var start = offset + 2
        repeat(-point) { array[start++] = 48 }
        return writeLongAscii(digits, array, start)
    }
    val end: Int
    if (length == 1) {
        array[offset] = (digits.toInt() + 48).toByte()
        array[offset + 1] = 46
        array[offset + 2] = 48
        end = offset + 3
    } else {
        end = offset + length + 1
        writeDoubleDigitsWithPoint(digits, array, end, offset + 1)
    }
    array[end] = 69
    return writeIntAscii(point - 1, array, end + 1)
}

private val doubleLengthPowers = LongArray(32).also { values ->
    var power = 1L
    for (index in 0..17) {
        values[index] = power
        power *= 10
    }
}

private fun doubleDecimalLength(value: Long): Int {
    if (value >= 10_000_000_000_000_000L) return 17
    val estimate = ((64 - value.countLeadingZeroBits()) * 1233) ushr 12
    return estimate + if (value >= doubleLengthPowers[estimate and 31]) 1 else 0
}

private fun writeDoubleDigitsWithPoint(value: Long, array: ByteArray, end: Int, point: Int) {
    array[point] = 46
    var cursor = end
    val head: Int
    if (value >= 1_000_000_000L) {
        val high = value / 1_000_000_000L
        val low = (value - high * 1_000_000_000L).toInt()
        val middle = low / 1000
        val upper = middle / 1000
        cursor = writeDoubleTripletWithPoint(low - middle * 1000, array, cursor, point)
        cursor = writeDoubleTripletWithPoint(middle - upper * 1000, array, cursor, point)
        cursor = writeDoubleTripletWithPoint(upper, array, cursor, point)
        head = high.toInt()
    } else {
        head = value.toInt()
    }
    var remaining = head
    while (remaining >= 1000) {
        val quotient = remaining / 1000
        cursor = writeDoubleTripletWithPoint(remaining - quotient * 1000, array, cursor, point)
        remaining = quotient
    }
    if (remaining >= 100) {
        writeDoubleTripletWithPoint(remaining, array, cursor, point)
    } else if (remaining >= 10) {
        val packed = asciiTriplets[remaining and 1023]
        if (--cursor == point) cursor--
        array[cursor] = packed.toByte()
        if (--cursor == point) cursor--
        array[cursor] = (packed ushr 8).toByte()
    } else {
        if (--cursor == point) cursor--
        array[cursor] = (remaining + 48).toByte()
    }
}

private fun writeDoubleTripletWithPoint(value: Int, array: ByteArray, end: Int, point: Int): Int {
    val packed = asciiTriplets[value and 1023]
    if (end - point > 3 || end <= point) {
        array[end - 3] = (packed ushr 16).toByte()
        array[end - 2] = (packed ushr 8).toByte()
        array[end - 1] = packed.toByte()
        return end - 3
    }
    var cursor = end
    if (--cursor == point) cursor--
    array[cursor] = packed.toByte()
    if (--cursor == point) cursor--
    array[cursor] = (packed ushr 8).toByte()
    if (--cursor == point) cursor--
    array[cursor] = (packed ushr 16).toByte()
    return cursor
}
