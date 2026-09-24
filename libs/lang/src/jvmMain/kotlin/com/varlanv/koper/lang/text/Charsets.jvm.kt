package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.bin.ReadonlyBytes

actual fun Charset.allocateString(
    bytes: ByteArray,
    offset: Int,
    len: Int
): String = String(bytes, offset, len, jdkEncoding())

actual fun Charset.allocateByteSlice(string: String, start: Int, end: Int): ByteSlice {
    val array = string.substring(start, end).toByteArray(jdkEncoding())
    return ByteSlice(ReadonlyBytes(array), 0, array.size)
}

fun Charset.jdkEncoding(): java.nio.charset.Charset = when (this) {
    Charset.Utf8 -> Charsets.UTF_8
    Charset.Ascii -> Charsets.US_ASCII
    Charset.Latin1 -> Charsets.ISO_8859_1
}
