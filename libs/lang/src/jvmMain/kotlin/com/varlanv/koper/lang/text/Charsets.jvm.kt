package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.ReadonlyBytes

actual fun Charset.allocateString(
    bytes: ByteArray,
    offset: Int,
    len: Int,
): String = String(bytes, offset, len, jdkEncoding())

actual fun Charset.allocateByteSlice(
    string: String,
    start: Int,
    end: Int,
): ByteSlice {
    val array = string.substring(start, end).toByteArray(jdkEncoding())
    return ByteSlice(
        bytes = ReadonlyBytes(array),
        offset = 0,
        len = array.size,
    )
}

actual fun Charset.allocateString(
    bytes: Bytes,
    offset: Int,
    len: Int
): String = allocateString(bytes.impl, offset, len)

fun Charset.jdkEncoding(): java.nio.charset.Charset = when (this) {
    Charset.Utf8 -> Charsets.UTF_8
    Charset.Ascii -> Charsets.US_ASCII
    Charset.Latin1 -> Charsets.ISO_8859_1
}
