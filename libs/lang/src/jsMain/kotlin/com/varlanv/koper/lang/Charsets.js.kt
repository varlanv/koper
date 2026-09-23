package com.varlanv.koper.lang

import kotlin.js.unsafeCast
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

private val charCodesToString: dynamic = js("(chars) => String.fromCharCode.apply(null, chars)")

actual fun Charset.toByteArray(
    string: String,
    start: Int,
    end: Int,
): ByteArray {
    // todo revisit this later
    require(start <= end) { "start ($start) > end ($end)" }
    if (start < 0 || end > string.length) {
        throw IndexOutOfBoundsException(
            "range [$start, $end) exceeds string length ${string.length}"
        )
    }

    var size = 0

    if (this == Charset.Utf8) {
        string.forEachCodePointInRange(start, end) { cp ->
            val width = when {
                cp <= 0x7F -> 1
                cp <= 0x7FF -> 2
                cp in 0xD800..0xDFFF -> 1 // Unpaired surrogate → '?'
                cp <= 0xFFFF -> 3
                else -> 4
            }

            if (size > Int.MAX_VALUE - width) {
                throw IllegalArgumentException("Encoded byte array is too large")
            }
            size += width
        }
    } else {
        string.forEachCodePointInRange(start, end) {
            if (size == Int.MAX_VALUE) {
                throw IllegalArgumentException("Encoded byte array is too large")
            }
            size++
        }
    }

    val result = ByteArray(size)
    var position = 0

    when (this) {
        Charset.Utf8 -> string.forEachCodePointInRange(start, end) { cp ->
            Charset.encodeUtf8Inline(cp) { result[position++] = it }
        }
        Charset.Ascii -> string.forEachCodePointInRange(start, end) { cp ->
            Charset.encodeAsciiInline(cp) { result[position++] = it }
        }
        Charset.Latin1 -> string.forEachCodePointInRange(start, end) { cp ->
            Charset.encodeLatin1Inline(cp) { result[position++] = it }
        }
    }

    return result
}

actual fun Charset.allocateString(
    bytes: ByteArray,
    offset: Int,
    len: Int,
): String {
    // Validate before calculating offset + len.
    if (offset < 0 || len < 0 || offset > bytes.size - len) {
        throw IndexOutOfBoundsException(
            "offset=$offset, len=$len, size=${bytes.size}"
        )
    }

    return when (this) {
        Charset.Utf8 ->
            bytes.decodeToString(offset, offset + len)

        Charset.Ascii ->
            CharArray(len) { i ->
                val byte = bytes[offset + i].toInt()
                if (byte >= 0) byte.toChar() else '\uFFFD'
            }.concatToString()

        Charset.Latin1 -> latin1String(bytes, offset, len)
    }
}

private fun latin1String(
    bytes: ByteArray,
    offset: Int,
    len: Int,
): String {
    val signed = bytes.unsafeCast<Int8Array>()
    val unsigned = Uint8Array(signed.buffer, signed.byteOffset + offset, len)
    if (len <= 4096) {
        return charCodesToString(unsigned).unsafeCast<String>()
    }

    var result = ""
    var start = 0
    while (start < len) {
        val end = minOf(start + 4096, len)
        result += charCodesToString(unsigned.subarray(start, end)).unsafeCast<String>()
        start = end
    }
    return result
}
