package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.bin.ReadonlyBytes
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint8Array

private val charCodesToString: dynamic = js("(chars) => String.fromCharCode.apply(null, chars)")
private val utf8Decoder: dynamic = js("new TextDecoder('utf-8', { ignoreBOM: true })")
private val windows1252Decoder: dynamic = js("new TextDecoder('windows-1252')")
private val nativeUtf16Decoder: dynamic = js("new Uint8Array(new Uint16Array([0x0102]).buffer)[0] === 0x02 ? new TextDecoder('utf-16le') : new TextDecoder('utf-16be')")

actual fun Charset.allocateByteSlice(
    string: String,
    start: Int,
    end: Int,
): ByteSlice {
    require(start <= end) { "start ($start) > end ($end)" }
    if (start < 0 || end > string.length) {
        throw IndexOutOfBoundsException(
            "range [$start, $end) exceeds string length ${string.length}"
        )
    }

    return when (this) {
        Charset.Utf8 -> encodeUtf8(string, start, end)
        Charset.Ascii -> encodeSingleByte(string, start, end, 0x7F)
        Charset.Latin1 -> encodeSingleByte(string, start, end, 0xFF)
    }
}

private fun encodeSingleByte(string: String, start: Int, end: Int, maxCodePoint: Int): ByteSlice {
    val result = ByteArray(end - start)
    var input = start
    var output = 0
    while (input < end) {
        val char = string[input++]
        if (char.isHighSurrogate() && input < end && string[input].isLowSurrogate()) {
            input++
        }
        result[output++] = if (char.code <= maxCodePoint) char.code.toByte() else 0x3F
    }
    return ByteSlice(ReadonlyBytes(result), 0, output)
}

private fun encodeUtf8(string: String, start: Int, end: Int): ByteSlice {
    var size = 0
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

    val result = ByteArray(size)
    var position = 0
    string.forEachCodePointInRange(start, end) { cp ->
        Charset.encodeUtf8Inline(cp) { result[position++] = it }
    }

    return ByteSlice(ReadonlyBytes(result), 0, result.size)
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
        Charset.Utf8 -> decodeWithTextDecoder(utf8Decoder, bytes, offset, len)

        Charset.Ascii -> {
            if (len >= 256 && allAscii(bytes, offset, len)) {
                decodeWithTextDecoder(utf8Decoder, bytes, offset, len)
            } else {
                CharArray(len) { i ->
                    val byte = bytes[offset + i].toInt()
                    if (byte >= 0) byte.toChar() else '\uFFFD'
                }.concatToString()
            }
        }

        Charset.Latin1 -> latin1String(bytes, offset, len)
    }
}

private fun allAscii(bytes: ByteArray, offset: Int, len: Int): Boolean {
    val end = offset + len
    var index = offset
    while (index < end) {
        if (bytes[index] < 0) return false
        index++
    }
    return true
}

private fun decodeWithTextDecoder(decoder: dynamic, bytes: ByteArray, offset: Int, len: Int): String {
    if (len == 0) return ""
    val signed = bytes.unsafeCast<Int8Array>()
    val input = if (offset == 0 && len == bytes.size) signed else signed.subarray(offset, offset + len)
    return decoder.decode(input).unsafeCast<String>()
}

private fun latin1String(
    bytes: ByteArray,
    offset: Int,
    len: Int,
): String {
    if (len >= 256) {
        val end = offset + len
        var index = offset
        while (index < end) {
            val value = bytes[index].toInt()
            if (value in -128..-97) break // Windows-1252 differs from Latin1 at 0x80..0x9F.
            index++
        }
        if (index == end) return decodeWithTextDecoder(windows1252Decoder, bytes, offset, len)
        if (len >= 1024) return latin1Utf16String(bytes, offset, len)
    }

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

private fun latin1Utf16String(bytes: ByteArray, offset: Int, len: Int): String {
    val signed = bytes.unsafeCast<Int8Array>()
    val unsigned = Uint8Array(signed.buffer, signed.byteOffset + offset, len)
    val chars = Uint16Array(len)
    chars.asDynamic().set(unsigned)
    val view = Uint8Array(chars.buffer, chars.byteOffset, chars.byteLength)
    return nativeUtf16Decoder.decode(view).unsafeCast<String>()
}
