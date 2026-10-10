package com.varlanv.koper.json

internal val jsonScanner = jsonSpecialScan(true)
internal val isVectorized = jsonScanner.isVector

internal fun isJsonWhitespace(b: Byte): Boolean =
    b == ' '.code.toByte() || b == '\t'.code.toByte() || b == '\n'.code.toByte() || b == '\r'.code.toByte()
