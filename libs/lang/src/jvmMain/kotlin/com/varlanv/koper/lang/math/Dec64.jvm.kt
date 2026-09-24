package com.varlanv.koper.lang.math

import java.math.BigDecimal

internal actual fun Dec64.allocateString(): String {
    val bytes = ByteArray(Dec64.MAX_CHARS)
    val len = writeTo(bytes, 0)
    return String(bytes, 0, len, Charsets.ISO_8859_1)
}

fun Dec64.toBigDecimal(): BigDecimal = BigDecimal.valueOf(coefficient, scale)

fun Dec64.Companion.fromDecimal(value: BigDecimal): Dec64 {
    val stripped = value.stripTrailingZeros()
    val unscaled = stripped.unscaledValue()
    if (unscaled.bitLength() > 63) {
        throw ArithmeticException("Dec64 overflow: $value")
    }
    return fromLong(unscaled = unscaled.toLong(), scale = stripped.scale())
}
