package com.varlanv.koper.lang

actual fun Long.addExact(other: Long): Long {
    val result = this + other

    if (((this xor result) and (other xor result)) < 0L) {
        throw ArithmeticException("long overflow")
    }

    return result
}

actual fun Int.addExact(other: Int): Int {
    val result = this + other

    if (((this xor result) and (other xor result)) < 0) {
        throw ArithmeticException("integer overflow")
    }

    return result
}

actual fun Int.multiplyExact(other: Int): Int {
    val result = this * other

    // If both |x| and |y| < 2^15, multiplication cannot overflow Int.
    val ax = if (this < 0) -this else this
    val ay = if (other < 0) -other else other

    if (((ax or ay) ushr 15) != 0) {
        if (
            (this != 0 && result / this != other) ||
            (this == -1 && other == Int.MIN_VALUE)
        ) {
            throw ArithmeticException("integer overflow")
        }
    }

    return result
}

actual fun Long.multiplyExact(other: Long): Long {
    val result = this * other

    // If both |x| and |y| < 2^31, multiplication cannot overflow Long.
    val ax = if (this < 0L) -this else this
    val ay = if (other < 0L) -other else other

    if (((ax or ay) ushr 31) != 0L) {
        if (
            (this != 0L && result / this != other) ||
            (this == -1L && other == Long.MIN_VALUE)
        ) {
            throw ArithmeticException("long overflow")
        }
    }

    return result
}

actual fun Long.subtractExact(other: Long): Long {
    val result = this - other
    // Overflow: operands have different signs, and result changed our sign.
    if (((this xor other) and (this xor result)) < 0L) {
        throw ArithmeticException("long overflow")
    }
    return result
}

actual fun Long.compareUnsigned(other: Long): Int =
    toULong().compareTo(other.toULong())

actual fun Long.remainderUnsigned(other: Long): Long =
    (toULong() % other.toULong()).toLong()

actual fun Int.numberOfLeadingZeros(): Int =
    countLeadingZeroBits()

actual fun Long.multiplyHigh(other: Long): Long {
    val xHigh = this shr 32
    val xLow = this and 0xFFFF_FFFFL
    val yHigh = other shr 32
    val yLow = other and 0xFFFF_FFFFL

    val lowProduct = xLow * yLow
    val cross = xHigh * yLow + (lowProduct ushr 32)
    val crossLow = (cross and 0xFFFF_FFFFL) + xLow * yHigh

    return xHigh * yHigh + (cross shr 32) + (crossLow shr 32)
}

actual fun Long.unsignedMultiplyHigh(other: Long): Long =
    multiplyHigh(other) +
            ((this shr 63) and other) +
            ((other shr 63) and this)
