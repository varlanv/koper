package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.bin.MutBytes

actual fun Charset.allocateString(
    bytes: Bytes,
    offset: Int,
    len: Int,
): String = String(bytes.bytes.impl, offset, len, jdkEncoding())

actual fun Charset.allocateByteSlice(
    string: String,
    start: Int,
    end: Int,
): BytesSlice {
    val array = string.substring(start, end).toByteArray(jdkEncoding())
    return BytesSlice(
        bytes = Bytes(MutBytes(array)),
        offset = 0,
        len = array.size,
    )
}

fun Charset.jdkEncoding(): java.nio.charset.Charset = when (this) {
    Charset.Utf8 -> Charsets.UTF_8
    Charset.Ascii -> Charsets.US_ASCII
    Charset.Latin1 -> Charsets.ISO_8859_1
}
