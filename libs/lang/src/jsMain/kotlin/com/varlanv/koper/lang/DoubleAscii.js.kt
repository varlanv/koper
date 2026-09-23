package com.varlanv.koper.lang

import kotlin.js.unsafeCast
import kotlin.math.abs
import kotlin.math.floor

private val doubleBitView: dynamic = js("new DataView(new ArrayBuffer(8))")

private val doublePowerLimbs = IntArray(617 * 6) { index ->
    val power = index / 6 * 2
    val high = doubleDecimalPowers[power]
    val low = doubleDecimalPowers[power + 1]
    when (index % 6) {
        0 -> low.toInt()
        1 -> (low ushr 22).toInt()
        2 -> (low ushr 44).toInt() or (high.toInt() shl 20)
        3 -> (high ushr 2).toInt()
        4 -> (high ushr 24).toInt()
        else -> (high ushr 46).toInt()
    } and 0x3fffff
}

internal actual fun writeDoubleAsciiImpl(value: Double, array: ByteArray, offset: Int): Int {
    doubleBitView.setFloat64(0, value, true)
    var low = doubleBitView.getInt32(0, true) as Int
    val bitsHigh = doubleBitView.getInt32(4, true) as Int
    var high = bitsHigh and 0xfffff
    val exponentBits = (bitsHigh ushr 20) and 2047
    if (exponentBits == 2047 && (high != 0 || low != 0)) {
        array[offset] = 78
        array[offset + 1] = 97
        array[offset + 2] = 78
        return offset + 3
    }
    var start = offset
    if (bitsHigh < 0) array[start++] = 45
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
    if (exponentBits == 0 && high == 0 && low == 0) {
        array[start] = 48
        array[start + 1] = 46
        array[start + 2] = 48
        return start + 3
    }
    val binaryExponent: Int
    var adjustment = 0
    if (exponentBits == 0) {
        binaryExponent = -1074
        if (high == 0 && low in 1..2) {
            low *= 10
            adjustment = -1
        }
    } else {
        high = high or 0x100000
        binaryExponent = exponentBits - 1075
        if (binaryExponent in -52..-1) {
            val magnitude = abs(value)
            if (floor(magnitude) == magnitude) {
                val upper = floor(magnitude / 4294967296.0).toInt()
                return formatDoubleLimbs(upper, magnitude - upper * 4294967296.0, 0, array, start)
            }
        }
    }
    val asymmetric = high == 0x100000 && low == 0 && binaryExponent != -1074
    val exponent = (binaryExponent * 315653 - if (asymmetric) 131008 else 0) shr 20
    val power = -exponent
    val shift = binaryExponent + ((power * 1741647) shr 19) + 2
    val table = (power + 292) * 6
    var lowerHigh = 0
    var lowerLow = 0.0
    var upperHigh = 0
    var upperLow = 0.0
    var scaledHigh = 0
    var scaledLow = 0.0
    scaledDoubleLimbs(high, low, if (asymmetric) -1 else -2, shift, table) { h, l ->
        lowerHigh = h
        lowerLow = l
    }
    scaledDoubleLimbs(high, low, 2, shift, table) { h, l ->
        upperHigh = h
        upperLow = l
    }
    scaledDoubleLimbs(high, low, 0, shift, table) { h, l ->
        scaledHigh = h
        scaledLow = l
    }
    val excluded = low and 1
    val quarter = scaledLow.unsafeCast<Int>() and 3
    val downHigh = scaledHigh ushr 2
    val downLow = floor(((scaledHigh and 3) * 4294967296.0 + scaledLow) / 4.0)
    var delta: Int
    if (downHigh != 0 || downLow >= 100.0) {
        val remainder = (((downHigh % 10) * 4294967296.0 + downLow) % 10.0).toInt()
        val downInside = compareDoubleLimbs(scaledHigh, scaledLow, -quarter - remainder * 4 - excluded, lowerHigh, lowerLow) >= 0
        val upInside = compareDoubleLimbs(scaledHigh, scaledLow, -quarter - remainder * 4 + 40 + excluded, upperHigh, upperLow) <= 0
        if (downInside != upInside) {
            delta = if (downInside) -remainder else 10 - remainder
            return formatAdjustedDouble(downHigh, downLow, delta, exponent, array, start)
        }
    }
    val downInside = compareDoubleLimbs(scaledHigh, scaledLow, -quarter - excluded, lowerHigh, lowerLow) >= 0
    val upInside = compareDoubleLimbs(scaledHigh, scaledLow, 4 - quarter + excluded, upperHigh, upperLow) <= 0
    delta = when {
        !downInside -> 1
        !upInside -> 0
        quarter > 2 -> 1
        quarter < 2 -> 0
        else -> (scaledLow.unsafeCast<Int>() ushr 2) and 1
    }
    return formatAdjustedDouble(downHigh, downLow, delta, exponent + adjustment, array, start)
}

private inline fun scaledDoubleLimbs(high: Int, low: Int, delta: Int, shift: Int, table: Int, consume: (Int, Double) -> Unit) {
    val factor = (1 shl shift).toDouble()
    val first = (low and 0x3fffff) * (factor * 4.0) + delta * factor
    val firstCarry = floor(first / 4194304.0)
    val a0 = first - firstCarry * 4194304.0
    val middle = (((low ushr 22) or (high shl 10)) and 0x3fffff) * (factor * 4.0) + firstCarry
    val middleCarry = floor(middle / 4194304.0)
    val a1 = middle - middleCarry * 4194304.0
    val a2 = (high ushr 12) * (factor * 4.0) + middleCarry
    val b0 = doublePowerLimbs[table].toDouble()
    val b1 = doublePowerLimbs[table + 1].toDouble()
    val b2 = doublePowerLimbs[table + 2].toDouble()
    val b3 = doublePowerLimbs[table + 3].toDouble()
    val b4 = doublePowerLimbs[table + 4].toDouble()
    val b5 = doublePowerLimbs[table + 5].toDouble()
    var sum = floor(a0 * b0 / 4194304.0) + a0 * b1 + a1 * b0
    sum = floor(sum / 4194304.0) + a0 * b2 + a1 * b1 + a2 * b0
    val c2 = sum.unsafeCast<Int>() and 0x3fffff
    sum = floor(sum / 4194304.0) + a0 * b3 + a1 * b2 + a2 * b1
    val c3 = sum.unsafeCast<Int>() and 0x3fffff
    sum = floor(sum / 4194304.0) + a0 * b4 + a1 * b3 + a2 * b2
    val c4 = sum.unsafeCast<Int>() and 0x3fffff
    sum = floor(sum / 4194304.0) + a0 * b5 + a1 * b4 + a2 * b3
    val c5 = sum.unsafeCast<Int>() and 0x3fffff
    sum = floor(sum / 4194304.0) + a1 * b5 + a2 * b4
    val c6 = sum.unsafeCast<Int>() and 0x3fffff
    sum = floor(sum / 4194304.0) + a2 * b5
    val c7 = sum.unsafeCast<Int>() and 0x3fffff
    val c8 = floor(sum / 4194304.0).toInt()
    val sticky = (c2 ushr 20) or c3 or c4 or (c5 and 0x7ffff)
    val resultLow = ((c5 ushr 19) or (c6 shl 3) or (c7 shl 25)) or if (sticky != 0) 1 else 0
    consume((c7 ushr 7) or (c8 shl 15), if (resultLow < 0) resultLow + 4294967296.0 else resultLow.toDouble())
}

private fun compareDoubleLimbs(high: Int, low: Double, delta: Int, otherHigh: Int, otherLow: Double): Double =
    (high - otherHigh) * 4294967296.0 + (low - otherLow + delta)

private fun formatAdjustedDouble(high: Int, low: Double, delta: Int, exponent: Int, array: ByteArray, offset: Int): Int {
    val adjusted = low + delta
    return when {
        adjusted < 0 -> formatDoubleLimbs(high - 1, adjusted + 4294967296.0, exponent, array, offset)
        adjusted >= 4294967296.0 -> formatDoubleLimbs(high + 1, adjusted - 4294967296.0, exponent, array, offset)
        else -> formatDoubleLimbs(high, adjusted, exponent, array, offset)
    }
}

private fun formatDoubleLimbs(high: Int, low: Double, exponent: Int, array: ByteArray, offset: Int): Int {
    val highQuotient = high / 1_000_000
    val combined = (high - highQuotient * 1_000_000) * 4294967296.0 + low
    val lowQuotient = floor(combined / 1_000_000.0)
    val last = combined - lowQuotient * 1_000_000.0
    val quotient = highQuotient * 4294967296.0 + lowQuotient
    var leading = floor(quotient / 1000.0).toInt()
    var trailing = ((quotient - leading * 1000.0) * 1_000_000.0 + last).toInt()
    var scale = exponent
    var trailingLength = 9
    if (trailing == 0 && leading != 0) {
        trailing = leading
        leading = 0
        scale += 9
    }
    while (trailing % 10 == 0) {
        trailing /= 10
        trailingLength--
        scale++
    }
    val length: Int
    if (leading == 0) {
        trailingLength = doubleIntLength(trailing)
        length = trailingLength
    } else {
        length = doubleIntLength(leading) + trailingLength
    }
    val point = length + scale
    if (point in 1..7) {
        if (point >= length) {
            var end = offset + length
            writeDoubleChunks(leading, trailing, trailingLength, array, end, -1)
            repeat(point - length) { array[end++] = 48 }
            array[end] = 46
            array[end + 1] = 48
            return end + 2
        }
        val end = offset + length + 1
        writeDoubleChunks(leading, trailing, trailingLength, array, end, offset + point)
        return end
    }
    if (point in -2..0) {
        array[offset] = 48
        array[offset + 1] = 46
        var start = offset + 2
        repeat(-point) { array[start++] = 48 }
        val end = start + length
        writeDoubleChunks(leading, trailing, trailingLength, array, end, -1)
        return end
    }
    val end: Int
    if (length == 1) {
        array[offset] = (trailing + 48).toByte()
        array[offset + 1] = 46
        array[offset + 2] = 48
        end = offset + 3
    } else {
        end = offset + length + 1
        writeDoubleChunks(leading, trailing, trailingLength, array, end, offset + 1)
    }
    array[end] = 69
    return writeIntAscii(point - 1, array, end + 1)
}

private fun doubleIntLength(value: Int): Int = when {
    value >= 100_000_000 -> 9
    value >= 10_000_000 -> 8
    value >= 1_000_000 -> 7
    value >= 100_000 -> 6
    value >= 10_000 -> 5
    value >= 1000 -> 4
    value >= 100 -> 3
    value >= 10 -> 2
    else -> 1
}

private fun writeDoubleChunks(leading: Int, trailing: Int, count: Int, array: ByteArray, end: Int, point: Int) {
    var cursor = end
    var digits = trailing
    repeat(count) {
        val quotient = digits / 10
        if (--cursor == point) array[cursor--] = 46
        array[cursor] = (digits - quotient * 10 + 48).toByte()
        digits = quotient
    }
    digits = leading
    while (digits != 0) {
        val quotient = digits / 10
        if (--cursor == point) array[cursor--] = 46
        array[cursor] = (digits - quotient * 10 + 48).toByte()
        digits = quotient
    }
}
