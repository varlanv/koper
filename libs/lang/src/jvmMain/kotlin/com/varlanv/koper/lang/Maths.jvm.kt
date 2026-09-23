package com.varlanv.koper.lang

actual fun Int.multiplyExact(other: Int): Int = Math.multiplyExact(this, other)
actual fun Long.multiplyExact(other: Long): Long = Math.multiplyExact(this, other)
actual fun Int.addExact(other: Int): Int = Math.addExact(this, other)
actual fun Long.addExact(other: Long): Long = Math.addExact(this, other)
actual fun Long.subtractExact(other: Long): Long = Math.subtractExact(this, other)
actual fun Long.multiplyHigh(other: Long): Long = Math.multiplyHigh(this, other)
actual fun Long.unsignedMultiplyHigh(other: Long): Long =Math.unsignedMultiplyHigh(this, other)
actual fun Long.compareUnsigned(other: Long): Int = toULong().compareTo(other.toULong())

actual fun Long.remainderUnsigned(other: Long): Long = (toULong() % other.toULong()).toLong()

actual fun Int.numberOfLeadingZeros(): Int = countLeadingZeroBits()
