package com.varlanv.koper.lang

actual fun Charset.allocateString(
    bytes: ByteArray,
    offset: Int,
    len: Int
): String = String(bytes, offset, len, jdkEncoding())

actual fun Charset.toByteArray(string: String, start: Int, end: Int): ByteArray {
    return string.substring(start, end).toByteArray(jdkEncoding())
}

fun Charset.jdkEncoding(): java.nio.charset.Charset = when (this) {
    Charset.Utf8 -> Charsets.UTF_8
    Charset.Ascii -> Charsets.US_ASCII
    Charset.Latin1 -> Charsets.ISO_8859_1
}
