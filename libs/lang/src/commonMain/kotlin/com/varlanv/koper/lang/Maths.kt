package com.varlanv.koper.lang

expect fun Int.addExact(other: Int): Int
expect fun Int.multiplyExact(other: Int): Int

expect fun Long.addExact(other: Long): Long
expect fun Long.subtractExact(other: Long): Long
expect fun Long.multiplyExact(other: Long): Long
expect fun Long.multiplyHigh(other: Long): Long
expect fun Long.unsignedMultiplyHigh(other: Long): Long
expect fun Long.compareUnsigned(other: Long): Int
expect fun Long.remainderUnsigned(other: Long): Long
expect fun Int.numberOfLeadingZeros(): Int
