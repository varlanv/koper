package com.varlanv.koper.lang.math

internal actual fun Dec64.allocateString(): String {
    val c = coefficient
    if (c == 0L) {
        return "0"
    }
    if (scale == 0) {
        return c.toString()
    }

    val negative = c < 0L
    val digits = (if (negative) -c else c).toString()
    val body = if (digits.length > scale) {
        val point = digits.length - scale
        digits.substring(0, point) + "." + digits.substring(point)
    } else {
        "0." + digits.padStart(scale, '0')
    }
    return if (negative) "-$body" else body
}
