package com.varlanv.koper.lang

internal actual fun writeDoubleAsciiImpl(value: Double, array: ByteArray, offset: Int): Int =
    writeDoubleAsciiPortable(value, array, offset)
