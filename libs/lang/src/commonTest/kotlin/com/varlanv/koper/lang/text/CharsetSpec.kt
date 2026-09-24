package com.varlanv.koper.lang.text

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class CharsetSpec : BaseSpec({
    should("encode UTF-8 code point boundaries") {
        val cases = listOf(
            0 to listOf(0x00),
            0x7F to listOf(0x7F),
            0x80 to listOf(0xC2, 0x80),
            0x7FF to listOf(0xDF, 0xBF),
            0x800 to listOf(0xE0, 0xA0, 0x80),
            0xD7FF to listOf(0xED, 0x9F, 0xBF),
            0xE000 to listOf(0xEE, 0x80, 0x80),
            0xFFFF to listOf(0xEF, 0xBF, 0xBF),
            0x10000 to listOf(0xF0, 0x90, 0x80, 0x80),
            0x10FFFF to listOf(0xF4, 0x8F, 0xBF, 0xBF),
        )
        for ((codepoint, expected) in cases) {
            val actual = mutableListOf<Byte>()
            Charset.Utf8.encodeInline(codepoint) { actual.add(it) }
            actual shouldBe expected.map { it.toByte() }
        }
    }

    should("encode every representable ASCII and Latin1 byte") {
        for ((charset, last) in listOf(Charset.Ascii to 0x7F, Charset.Latin1 to 0xFF)) {
            for (codepoint in 0..last) {
                val actual = mutableListOf<Byte>()
                charset.encodeInline(codepoint) { actual.add(it) }
                actual shouldBe listOf(codepoint.toByte())
            }
        }
    }

    should("replace unrepresentable and invalid code points with a question mark") {
        for (charset in Charset.entries) {
            val invalid = listOf(Int.MIN_VALUE, -1, 0xD800, 0xDFFF, 0x110000, Int.MAX_VALUE) +
                when (charset) {
                    Charset.Ascii -> listOf(0x80, 0xFF, 0x100, 0x10000)
                    Charset.Latin1 -> listOf(0x100, 0x10000)
                    Charset.Utf8 -> emptyList()
                }
            for (codepoint in invalid) {
                val actual = mutableListOf<Byte>()
                charset.encodeInline(codepoint) { actual.add(it) }
                actual shouldBe listOf(0x3F.toByte())
            }
        }
    }

    should("combine surrogate pairs and replace each unpaired surrogate") {
        val cases = listOf(
            "" to "",
            "\uD800" to "?",
            "\uDC00" to "?",
            "\uD800A" to "?A",
            "A\uDC00" to "A?",
            "\uD800\uD800" to "??",
            "\uDC00\uDC00" to "??",
            "\uDC00\uD800" to "??",
            "\uD800A\uDC00" to "?A?",
        )
        for (charset in Charset.entries) {
            for ((input, expected) in cases) {
                for (chars in listOf(input, StringBuilder(input))) {
                    val actual = mutableListOf<Byte>()
                    charset.encodeInline(chars) { actual.add(it) }
                    actual shouldBe expected.encodeToByteArray().toList()
                }
            }
            val actual = mutableListOf<Byte>()
            charset.encodeInline("\uD800\uD800\uDC00\uDC00") { actual.add(it) }
            actual shouldBe if (charset == Charset.Utf8) {
                listOf(0x3F, 0xF0, 0x90, 0x80, 0x80, 0x3F).map { it.toByte() }
            } else {
                listOf<Byte>(0x3F, 0x3F, 0x3F)
            }
        }
    }

    should("classify surrogate boundaries and combine pairs") {
        for (char in listOf('\uD7FF', '\uE000', 'A')) {
            char.isHighSurrogate() shouldBe false
            char.isLowSurrogate() shouldBe false
        }
        for (char in listOf('\uD800', '\uDBFF')) {
            char.isHighSurrogate() shouldBe true
            char.isLowSurrogate() shouldBe false
        }
        for (char in listOf('\uDC00', '\uDFFF')) {
            char.isHighSurrogate() shouldBe false
            char.isLowSurrogate() shouldBe true
        }
        '\uD800'.toCodePoint('\uDC00') shouldBe 0x10000
        '\uDBFF'.toCodePoint('\uDFFF') shouldBe 0x10FFFF
        '\uD83D'.toCodePoint('\uDE42') shouldBe 0x1F642
    }
})
