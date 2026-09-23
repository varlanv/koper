package com.varlanv.koper.lang

expect fun Charset.toByteArray(string: String, start: Int = 0, end: Int = string.length): ByteArray
expect fun Charset.allocateString(bytes: ByteArray, offset: Int = 0, len: Int = bytes.size): String

enum class Charset {
    Utf8,
    Ascii,
    Latin1;

    inline fun encodeInline(codepoint: Int, block: (Byte) -> Unit) {
        when (this) {
            Utf8 -> encodeUtf8Inline(codepoint, block)
            Ascii -> encodeAsciiInline(codepoint, block)
            Latin1 -> encodeLatin1Inline(codepoint, block)
        }
    }

    companion object {
        inline fun encodeUtf8Inline(codepoint: Int, block: (Byte) -> Unit) {
            when (codepoint) {
                in 0..0x7F -> {
                    block(codepoint.toByte())
                }

                in 0x80..0x7FF -> {
                    block((0xC0 or (codepoint ushr 6)).toByte())
                    block((0x80 or (codepoint and 0x3F)).toByte())
                }

                in 0xD800..0xDFFF -> {
                    block(0x3F)
                }

                in 0x800..0xFFFF -> {
                    block((0xE0 or (codepoint ushr 12)).toByte())
                    block((0x80 or ((codepoint ushr 6) and 0x3F)).toByte())
                    block((0x80 or (codepoint and 0x3F)).toByte())
                }

                in 0x10000..0x10FFFF -> {
                    block((0xF0 or (codepoint ushr 18)).toByte())
                    block((0x80 or ((codepoint ushr 12) and 0x3F)).toByte())
                    block((0x80 or ((codepoint ushr 6) and 0x3F)).toByte())
                    block((0x80 or (codepoint and 0x3F)).toByte())
                }

                else -> {
                    block(0x3F)
                }
            }
        }

        inline fun encodeLatin1Inline(codepoint: Int, block: (Byte) -> Unit) {
            block(
                if (codepoint in 0..0xFF) {
                    codepoint.toByte()
                } else {
                    0x3F
                },
            )
        }

        inline fun encodeAsciiInline(codepoint: Int, block: (Byte) -> Unit) {
            block(
                if (codepoint in 0..0x7F) {
                    codepoint.toByte()
                } else {
                    0x3F
                },
            )
        }
    }

    inline fun encodeInline(chars: CharSequence, block: (Byte) -> Unit) {
        when (this) {
            Utf8 -> chars.forEachCodePointInRange(0, chars.length) { encodeUtf8Inline(it, block) }
            Ascii -> chars.forEachCodePointInRange(0, chars.length) { encodeAsciiInline(it, block) }
            Latin1 -> chars.forEachCodePointInRange(0, chars.length) { encodeLatin1Inline(it, block) }
        }
    }
}

@PublishedApi
internal inline fun CharSequence.forEachCodePointInRange(start: Int, end: Int, block: (Int) -> Unit) {
    var index = start
    while (index < end) {
        val char = this[index++]
        var codepoint = char.code
        if (char.isHighSurrogate() && index < end) {
            val next = this[index]
            if (next.isLowSurrogate()) {
                codepoint = char.toCodePoint(next)
                index++
            }
        }
        block(codepoint)
    }
}


private const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x010000
private const val MIN_HIGH_SURROGATE: Char = '\uD800'
private const val MIN_LOW_SURROGATE: Char = '\uDC00'
private const val MAX_LOW_SURROGATE: Char = '\uDFFF'
private const val MAX_HIGH_SURROGATE: Char = '\uDBFF'

fun Char.toCodePoint(low: Char): Int {
    return ((this.code shl 10) + low.code) +
            (MIN_SUPPLEMENTARY_CODE_POINT -
                    (MIN_HIGH_SURROGATE.code shl 10) -
                    MIN_LOW_SURROGATE.code)
}

fun Char.isLowSurrogate(): Boolean {
    return this >= MIN_LOW_SURROGATE && this < (MAX_LOW_SURROGATE + 1)
}

fun Char.isHighSurrogate(): Boolean {
    return this >= MIN_HIGH_SURROGATE && this < (MAX_HIGH_SURROGATE + 1)
}
