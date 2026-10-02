package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.bin.asReadonly
import com.varlanv.koper.lang.bin.toSlice
import com.varlanv.koper.lang.bin.validateUtf8
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class StrSpec : BaseSpec({
    should("allocate strings using the selected encoding") {
        for ((charset, input, bytes) in listOf(
            Triple(
                Charset.Utf8,
                "é中🙂",
                "é中🙂".encodeToByteArray(),
            ),
            Triple(
                Charset.Ascii,
                "A\u0000z",
                byteArrayOf(
                    65,
                    0,
                    122,
                ),
            ),
            Triple(
                Charset.Latin1,
                "Aéÿ",
                byteArrayOf(
                    65,
                    0xE9.toByte(),
                    0xFF.toByte(),
                ),
            ),
        )) {
            val encoded = charset.encodeIntoSlice(string = input)
            encoded.len shouldBe bytes.size
            encoded.copyToArray().toList() shouldBe bytes.toList()
            charset.decodeFromSlice(encoded) shouldBe input
        }
    }

    should("decode exactly the selected slice with each charset") {
        for ((charset, input) in listOf(Charset.Utf8 to "é中🙂", Charset.Ascii to "hello", Charset.Latin1 to "éÿ")) {
            val payload = charset.encodeIntoSlice(string = input)
            val bytes = byteArrayOf(65, 66, 67) + payload.copyToArray() + byteArrayOf(68, 69, 70)
            val slice = BytesSlice(bytes = bytes.asReadonly(), offset = 3, len = payload.len)
            charset.decodeFromBytes(bytes = slice.bytes, offset = slice.offset, len = slice.len) shouldBe input
            charset.decodeFromSlice(slice) shouldBe input
            charset.decodeFromBytes(bytes = bytes.asReadonly(), offset = bytes.size, len = 0) shouldBe ""
        }
    }

    should("allocate empty strings in every charset") {
        for (charset in listOf(Charset.Ascii, Charset.Latin1, Charset.Utf8)) {
            val encoded = charset.encodeIntoSlice(string = "")
            encoded.len shouldBe 0
            charset.decodeFromSlice(encoded) shouldBe ""
        }
        Utf8Str.empty.byteLen shouldBe 0
        Utf8Str.empty.allocateString() shouldBe ""
    }

    should("decode large Latin1 slices") {
        val payload = ByteArray(8193) { it.toByte() }
        val bytes = byteArrayOf(1, 2) + payload + byteArrayOf(3, 4)
        val expected = CharArray(payload.size) { (payload[it].toInt() and 0xFF).toChar() }.concatToString()
        Charset.Latin1.decodeFromBytes(bytes = bytes.asReadonly(), offset = 2, len = payload.size) shouldBe expected
    }

    should("encode character ranges before converting them to bytes") {
        val input = "Aé中🙂Z"
        for (charset in listOf(Charset.Ascii, Charset.Latin1, Charset.Utf8)) {
            for (start in 0..input.length) {
                for (end in start..input.length) {
                    val selected = input.substring(start, end)
                    val expected = mutableListOf<Byte>()
                    when (charset) {
                        Charset.Ascii -> Charset.Ascii.encodeInline(selected) { expected.add(it) }
                        Charset.Latin1 -> Charset.Latin1.encodeInline(selected) { expected.add(it) }
                        Charset.Utf8 -> Charset.Utf8.encodeInline(selected) { expected.add(it) }
                    }
                    charset.encodeIntoSlice(string = input, start = start, end = end).copyToArray().toList() shouldBe
                        expected
                }
            }
        }
    }

    should("allocate UTF-8 strings from empty ASCII and multibyte input") {
        for (input in listOf("", "plain ASCII /", "é中🙂", "\u0000\u007F\u0080\u07FF\u0800\uFFFF")) {
            val str = Utf8Str.decodeFromString(string = input)
            str.slice.copyToArray().toList() shouldBe input.encodeToByteArray().toList()
            str.allocateString() shouldBe input
        }
        Utf8Str.empty.slice.len shouldBe 0
        Utf8Str.empty.allocateString() shouldBe ""
    }

    should("wrap an existing unvalidated slice without copying") {
        val slice = BytesSlice(bytes = byteArrayOf(0xFF.toByte()).asReadonly(), offset = 0, len = 1)
        val str = Utf8Str.unsafeWrap(slice)
        (str.slice.bytes == slice.bytes) shouldBe true
        str.slice.offset shouldBe slice.offset
        str.slice.len shouldBe slice.len
    }

    should("validate and retain the original UTF-8 slice") {
        val payload = "é中🙂".encodeToByteArray()
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + payload + byteArrayOf(0xFF.toByte())
        val slice = BytesSlice(bytes = bytes.asReadonly(), offset = 2, len = payload.size)
        slice.bytes.validateUtf8(offset = slice.offset, len = slice.len) shouldBe true
        val str = Utf8Str.unsafeWrap(slice)
        (str.slice.bytes == slice.bytes) shouldBe true
        str.allocateString() shouldBe "é中🙂"
        Utf8Str.unsafeWrap(bytes.asReadonly().toSlice(offset = bytes.size, len = 0)).allocateString() shouldBe ""
    }

    should("reject malformed UTF-8 and invalid slice bounds") {
        for (bytes in listOf(
            byteArrayOf(0x80.toByte()),
            byteArrayOf(
                0xC0.toByte(),
                0x80.toByte(),
            ),
            byteArrayOf(
                0xED.toByte(),
                0xA0.toByte(),
                0x80.toByte(),
            ),
            byteArrayOf(
                0xF0.toByte(),
                0x90.toByte(),
                0x80.toByte(),
            ),
        )) {
            bytes.asReadonly().validateUtf8() shouldBe false
        }
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 1 to 2)) {
            byteArrayOf(65).asReadonly().validateUtf8(offset = offset, len = length) shouldBe false
        }
    }
})

private fun BytesSlice.copyToArray(): ByteArray = ByteArray(len) { bytes[offset + it] }
