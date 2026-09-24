package com.varlanv.koper.lang.text

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import org.khronos.webgl.Int8Array

class CharsetsJsSpec : BaseSpec({
    should("match Kotlin UTF-8 decoding for every byte pair") {
        val bytes = ByteArray(2)
        for (first in 0..255) {
            bytes[0] = first.toByte()
            for (second in 0..255) {
                bytes[1] = second.toByte()
                val expected = bytes.decodeToString()
                val actual = Charset.Utf8.allocateString(bytes)
                check(actual == expected) {
                    "UTF-8 decode differs for bytes $first, $second"
                }
            }
        }
    }

    should("preserve UTF-8 BOM and decode only the selected range") {
        val input = byteArrayOf(0x61, 0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x62, 0x63)
        Charset.Utf8.allocateString(input, 1, 4) shouldBe "\uFEFFb"
        Charset.Utf8.allocateString(input, 1, 4) shouldBe input.decodeToString(1, 5)
        Charset.Utf8.allocateString(input, input.size, 0) shouldBe ""
    }

    should("match Kotlin UTF-8 decoding for longer malformed sequences") {
        val cases = listOf(
            intArrayOf(0xE0, 0x80, 0x80), // Overlong encoding
            intArrayOf(0xED, 0xA0, 0x80), // Encoded surrogate
            intArrayOf(0xE2, 0x82), // Truncated three-byte sequence
            intArrayOf(0xF0, 0x80, 0x80, 0x80), // Overlong encoding
            intArrayOf(0xF4, 0x90, 0x80, 0x80), // Above U+10FFFF
            intArrayOf(0xF0, 0x90, 0x80), // Truncated four-byte sequence
            intArrayOf(0xF0, 0x90, 0x80, 0x80), // Valid four-byte sequence
        )
        for (values in cases) {
            val bytes = ByteArray(values.size) { values[it].toByte() }
            Charset.Utf8.allocateString(bytes) shouldBe bytes.decodeToString()
        }
    }

    should("decode large ASCII slices and replace out-of-range bytes") {
        val bytes = ByteArray(10_250) { 'A'.code.toByte() }
        bytes[0] = 0xFF.toByte()
        bytes[10_249] = 0xFF.toByte()
        Charset.Ascii.allocateString(bytes, 5, 10_240) shouldBe "A".repeat(10_240)

        bytes[500] = 0xFF.toByte()
        Charset.Ascii.allocateString(bytes, 5, 10_240) shouldBe
                "A".repeat(495) + "\uFFFD" + "A".repeat(10_240 - 496)
    }

    should("not treat non-ASCII byte sequences as UTF-8") {
        val bytes = byteArrayOf(0xC3.toByte(), 0xA9.toByte())
        Charset.Ascii.allocateString(bytes) shouldBe "\uFFFD\uFFFD"
        val decoder: dynamic = js("new TextDecoder('utf-8')")
        decoder.decode(bytes.unsafeCast<Int8Array>()).unsafeCast<String>() shouldBe "é"
    }

    should("decode large Latin1 slices with and without Windows-1252-only bytes") {
        val bytes = ByteArray(10_250) {
            val value = it % 224
            (if (value < 128) value else value + 32).toByte()
        }
        bytes[0] = 0x80.toByte()
        bytes[10_249] = 0x80.toByte()
        val expected = CharArray(10_240) { (bytes[it + 5].toInt() and 0xFF).toChar() }.concatToString()
        Charset.Latin1.allocateString(bytes, 5, 10_240) shouldBe expected

        bytes[500] = 0x80.toByte()
        val withControl = CharArray(10_240) { (bytes[it + 5].toInt() and 0xFF).toChar() }.concatToString()
        Charset.Latin1.allocateString(bytes, 5, 10_240) shouldBe withControl
    }

    should("decode every Latin1 byte through the large control-byte path") {
        val bytes = ByteArray(4098) { (it % 256).toByte() }
        val expected = CharArray(4096) { (bytes[it + 1].toInt() and 0xFF).toChar() }.concatToString()
        Charset.Latin1.allocateString(bytes, 1, 4096) shouldBe expected
    }

    should("keep the untrimmed array behind a single-byte encoded slice") {
        for (charset in listOf(Charset.Ascii, Charset.Latin1)) {
            val slice = charset.allocateByteSlice("A🙂B")
            slice.offset shouldBe 0
            slice.len shouldBe 3
            slice.unsafeBorrowArray().size shouldBe 4
            slice.allocateArray().toList() shouldBe listOf(65, 63, 66).map(Int::toByte)
            "A🙂B".allocateStr(charset).allocateString(charset) shouldBe "A?B"
        }
    }
})
