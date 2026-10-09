package com.varlanv.koper.lang.serde

actual fun DecimalDigits.intDigits(value: Int): Int {
    return if (value < 1_000) {
        1 + ((9 - value) ushr 31) + ((99 - value) ushr 31)
    } else if (value < 1_000_000) {
        4 + ((9_999 - value) ushr 31) + ((99_999 - value) ushr 31)
    } else {
        7 + ((9_999_999 - value) ushr 31) + ((99_999_999 - value) ushr 31)
    }
}
