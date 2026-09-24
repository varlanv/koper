package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.validateUtf8
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class Utf8ValidationSpec : BaseSpec({
    should("accept empty ASCII and multibyte strings") {
        val inputs = listOf(
            "", "plain ASCII /\u0000", "é中🙂",
            "\u007F\u0080\u07FF\u0800\uD7FF\uE000\uFFFF",
            "\uD800\uDC00\uDBFF\uDFFF",
        )
        for (input in inputs) {
            input.encodeToByteArray().validateUtf8() shouldBe true
        }
    }

    should("accept only ASCII as a single byte") {
        for (value in 0..255) {
            byteArrayOf(value.toByte()).validateUtf8() shouldBe (value < 128)
        }
    }

    should("reject overlong sequences surrogates and code points above Unicode") {
        val inputs = listOf(
            listOf(0xC0, 0x80), listOf(0xC1, 0xBF),
            listOf(0xE0, 0x80, 0x80), listOf(0xE0, 0x9F, 0xBF),
            listOf(0xED, 0xA0, 0x80), listOf(0xED, 0xBF, 0xBF),
            listOf(0xF0, 0x80, 0x80, 0x80), listOf(0xF0, 0x8F, 0xBF, 0xBF),
            listOf(0xF4, 0x90, 0x80, 0x80), listOf(0xF4, 0xBF, 0xBF, 0xBF),
            listOf(0xF5, 0x80, 0x80, 0x80), listOf(0xFF, 0xBF, 0xBF, 0xBF),
        )
        for (input in inputs) {
            input.map { it.toByte() }.toByteArray().validateUtf8() shouldBe false
        }
    }

    should("reject invalid continuation bytes at every position") {
        val sequences = listOf(
            listOf(0xC2, 0x80),
            listOf(0xE0, 0xA0, 0x80), listOf(0xE1, 0x80, 0x80),
            listOf(0xED, 0x80, 0x80), listOf(0xEE, 0x80, 0x80),
            listOf(0xF0, 0x90, 0x80, 0x80), listOf(0xF1, 0x80, 0x80, 0x80),
            listOf(0xF2, 0x80, 0x80, 0x80), listOf(0xF3, 0x80, 0x80, 0x80),
            listOf(0xF4, 0x80, 0x80, 0x80),
        )
        for (sequence in sequences) {
            val valid = sequence.map { it.toByte() }.toByteArray()
            valid.validateUtf8() shouldBe true
            for (length in 1 until valid.size) {
                valid.copyOf(length).validateUtf8() shouldBe false
                valid.validateUtf8(len = length) shouldBe false
            }
            for (index in 1 until valid.size) {
                for (replacement in listOf(0x00, 0x7F, 0xC0, 0xFF)) {
                    val invalid = valid.copyOf()
                    invalid[index] = replacement.toByte()
                    invalid.validateUtf8() shouldBe false
                }
            }
        }
    }

    should("validate only the requested slice") {
        val text = "é中🙂".encodeToByteArray()
        val bytes = byteArrayOf(0xFF.toByte()) + text + byteArrayOf(0xFF.toByte())
        bytes.validateUtf8(offset = 1, len = text.size) shouldBe true
        bytes.validateUtf8() shouldBe false
        bytes.validateUtf8(offset = 2, len = text.size - 1) shouldBe false
        bytes.validateUtf8(offset = bytes.size, len = 0) shouldBe true
        bytes.validateUtf8(offset = 0, len = 0) shouldBe true
        (byteArrayOf(0xFF.toByte()) + text).validateUtf8(offset = 1) shouldBe true
    }

    should("reject invalid ranges without overflow or exceptions") {
        val bytes = byteArrayOf(65, 66, 67)
        for ((offset, length) in listOf(
            -1 to 1, 0 to -1, 4 to 0, 2 to 2,
            Int.MIN_VALUE to 0, Int.MAX_VALUE to 1,
            1 to Int.MAX_VALUE, Int.MAX_VALUE to Int.MAX_VALUE,
        )) {
            bytes.validateUtf8(offset, length) shouldBe false
        }
    }
})
