package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.bin.MutBytes

sealed interface Charset {
    val ordinal: Int

    data object Ascii : Charset {
        override val ordinal: Int = 0

        inline fun encodeInline(chars: CharSequence, block: (Byte) -> Unit) {
            chars.forEachCodePointInRange(
                start = 0,
                end = chars.length,
            ) { encodeCodepointInline(codepoint = it, block = block) }
        }

        inline fun encodeCodepointInline(codepoint: Int, block: (Byte) -> Unit) {
            block(
                if (codepoint in 0..0x7F) {
                    codepoint.toByte()
                } else {
                    0x3F
                },
            )
        }
    }

    data object Latin1 : Charset {
        override val ordinal: Int = 1

        inline fun encodeInline(chars: CharSequence, block: (Byte) -> Unit) {
            chars.forEachCodePointInRange(
                start = 0,
                end = chars.length,
            ) { encodeCodepointInline(codepoint = it, block = block) }
        }

        inline fun encodeCodepointInline(codepoint: Int, block: (Byte) -> Unit) {
            block(
                if (codepoint in 0..0xFF) {
                    codepoint.toByte()
                } else {
                    0x3F
                },
            )
        }
    }

    data object Utf8 : Charset {
        override val ordinal: Int = 2

        inline fun encodeInline(chars: CharSequence, block: (Byte) -> Unit) {
            chars.forEachCodePointInRange(
                start = 0,
                end = chars.length,
            ) { encodeCodepointInline(codepoint = it, block = block) }
        }

        inline fun encodeCodepointInline(codepoint: Int, block: (Byte) -> Unit) {
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
    }
}

/** The returned slice may use only part of its backing array. */
expect fun Charset.encodeIntoSlice(
    string: String,
    start: Int = 0,
    end: Int = string.length,
): BytesSlice

/**
 * Writes as many complete encoded characters as fit after [destinationOffset] and returns the byte count.
 * Stops before the first character that does not fit; never writes a partial multibyte sequence.
 * Surrogate pairs produce one encoded character, and malformed surrogates are replaced with '?'.
 */
expect fun Charset.encodeAtMostIntoArray(
    source: String,
    destination: MutBytes,
    destinationOffset: Int = 0,
    start: Int = 0,
    end: Int = source.length,
): Int

expect fun Charset.encodeIntoSink(
    source: String,
    sink: ByteSink,
    start: Int = 0,
    end: Int = source.length,
)

expect fun Charset.decodeFromBytes(
    bytes: Bytes,
    offset: Int = 0,
    len: Int = bytes.bytes.size,
): String

fun Charset.decodeFromSlice(
    slice: BytesSlice,
): String = decodeFromBytes(bytes = slice.bytes, offset = slice.offset, len = slice.len)

@PublishedApi
internal inline fun CharSequence.forEachCodePointInRange(
    start: Int,
    end: Int,
    block: (Int) -> Unit,
) {
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
        (MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE.code shl 10) - MIN_LOW_SURROGATE.code)
}

fun Char.isLowSurrogate(): Boolean {
    return this >= MIN_LOW_SURROGATE && this < (MAX_LOW_SURROGATE + 1)
}

fun Char.isHighSurrogate(): Boolean {
    return this >= MIN_HIGH_SURROGATE && this < (MAX_HIGH_SURROGATE + 1)
}
