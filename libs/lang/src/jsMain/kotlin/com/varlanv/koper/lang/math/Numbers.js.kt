package com.varlanv.koper.lang.math

import kotlin.math.floor

actual fun writeLongAscii(value: Long, array: ByteArray, offset: Int): Int {
    var low = value.toInt()
    var high = (value ushr 32).toInt()
    if (high == low shr 31) {
        return writeIntAscii(low, array, offset)
    }
    var position = offset
    if (high < 0) {
        array[position++] = 45
        low = -low
        high = high.inv() + if (low == 0) 1 else 0
    }
    val unsignedLow = if (low < 0) low.toDouble() + 4_294_967_296.0 else low.toDouble()
    val unsignedHigh = if (high < 0) high.toDouble() + 4_294_967_296.0 else high.toDouble()
    val highQuotient = floor(unsignedHigh / 1_000_000.0)
    val highRemainder = unsignedHigh - highQuotient * 1_000_000.0
    val combined = highRemainder * 4_294_967_296.0 + unsignedLow
    val lowQuotient = floor(combined / 1_000_000.0)
    val last = (combined - lowQuotient * 1_000_000.0).toInt()
    val quotient = highQuotient * 4_294_967_296.0 + lowQuotient
    val top = floor(quotient / 1_000_000.0).toInt()
    val middle = (quotient - top.toDouble() * 1_000_000.0).toInt()
    if (top == 0) {
        position = writeIntAscii(middle, array, position)
    } else {
        position = writeIntAscii(top, array, position)
        writeLimbSix(middle, array, position)
        position += 6
    }
    writeLimbSix(last, array, position)
    return position + 6
}

private fun writeLimbSix(value: Int, array: ByteArray, offset: Int) {
    val upper = value / 1000
    val leading = asciiTriplets[upper and 1023]
    val trailing = asciiTriplets[(value - upper * 1000) and 1023]
    array[offset] = (leading ushr 16).toByte()
    array[offset + 1] = (leading ushr 8).toByte()
    array[offset + 2] = leading.toByte()
    array[offset + 3] = (trailing ushr 16).toByte()
    array[offset + 4] = (trailing ushr 8).toByte()
    array[offset + 5] = trailing.toByte()
}
