package com.varlanv.koper.lang.serde

import com.varlanv.koper.lang.collection.Longs
import com.varlanv.koper.lang.collection.get
import com.varlanv.koper.lang.math.Numbers

private fun intDigitsBias(value: Int): Int {
    return if (value < 100000) {
        if (value < 100) {
            if (value < 10) {
                1
            } else {
                2
            }
        } else if (value < 1000) {
            3
        } else if (value < 10000) {
            4
        } else {
            5
        }
    } else if (value < 10000000) {
        if (value < 1000000) {
            6
        } else {
            7
        }
    } else if (value < 100000000) {
        8
    } else {
        9
    }
}

private val intDigitBiases = Longs(33) { leadingZeros ->
    when (leadingZeros) {
        0, 1 -> {
            10L shl 32
        }

        32 -> {
            1L shl 32
        }

        else -> {
            val digits = intDigitsBias(1 shl (31 - leadingZeros))
            ((digits + 1).toLong() shl 32) - Numbers.INT_POWERS_OF_10[digits]
        }
    }
}

actual fun DecimalDigits.intDigits(value: Int): Int {
    return ((value.toLong() + intDigitBiases[value.countLeadingZeroBits()]) ushr 32).toInt()
}
