package com.varlanv.koper.lang.math

import com.varlanv.koper.lang.bin.MutBytes

internal actual fun writeDoubleAsciiImpl(
    value: Double,
    array: MutBytes,
    offset: Int,
): Int = writeDoubleAsciiPortable(value = value, array = array, offset = offset)
