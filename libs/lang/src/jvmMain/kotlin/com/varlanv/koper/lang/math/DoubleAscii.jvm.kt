package com.varlanv.koper.lang.math

internal actual fun writeDoubleAsciiImpl(value: Double, array: ByteArray, offset: Int): Int =
    writeDoubleAsciiPortable(value, array, offset)
