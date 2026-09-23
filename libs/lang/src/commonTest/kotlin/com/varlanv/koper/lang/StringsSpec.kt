package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class StringsSpec : BaseSpec({
    should("allocate strings using the selected encoding") {
        for ((charset, input, bytes) in listOf(
            Triple(Charset.Utf8, "é中🙂", "é中🙂".encodeToByteArray()),
            Triple(Charset.Ascii, "A\u0000z", byteArrayOf(65, 0, 122)),
            Triple(Charset.Latin1, "Aéÿ", byteArrayOf(65, 0xE9.toByte(), 0xFF.toByte())),
        )) {
            val str = input.allocateStr(charset)
            str.bytes.offset shouldBe 0
            str.bytes.len shouldBe bytes.size
            str.bytes.allocateArray().toList() shouldBe bytes.toList()
            str.allocateString(charset) shouldBe input
        }
    }

    should("decode exactly the selected slice with each charset") {
        for ((charset, input) in listOf(
            Charset.Utf8 to "é中🙂", Charset.Ascii to "hello", Charset.Latin1 to "éÿ",
        )) {
            val payload = charset.allocateByteSlice(input)
            val bytes = byteArrayOf(65, 66, 67) + payload.allocateArray() + byteArrayOf(68, 69, 70)
            val slice = ByteSlice(ReadonlyBytes(bytes), 3, payload.len)
            charset.allocateString(slice.bytes.array, slice.offset, slice.len) shouldBe input
            Str(slice).allocateString(charset) shouldBe input
            charset.allocateString(bytes, bytes.size, 0) shouldBe ""
        }
    }

    should("allocate empty strings in every charset") {
        for (charset in Charset.entries) {
            val str = "".allocateStr(charset)
            str.bytes.len shouldBe 0
            str.allocateString(charset) shouldBe ""
        }
    }

    should("decode large Latin1 slices") {
        val payload = ByteArray(8193) { it.toByte() }
        val bytes = byteArrayOf(1, 2) + payload + byteArrayOf(3, 4)
        val expected = CharArray(payload.size) { (payload[it].toInt() and 0xFF).toChar() }.concatToString()
        Charset.Latin1.allocateString(bytes, 2, payload.size) shouldBe expected
    }

    should("encode character ranges before converting them to bytes") {
        val input = "Aé中🙂Z"
        for (charset in Charset.entries) {
            for (start in 0..input.length) {
                for (end in start..input.length) {
                    val expected = mutableListOf<Byte>()
                    charset.encodeInline(input.substring(start, end)) { expected.add(it) }
                    charset.allocateByteSlice(input, start, end).allocateArray().toList() shouldBe expected
                }
            }
        }
    }

    should("allocate UTF-8 strings from empty ASCII and multibyte input") {
        for (input in listOf("", "plain ASCII /", "é中🙂", "\u0000\u007F\u0080\u07FF\u0800\uFFFF")) {
            val str = Utf8Str.allocateFromString(input)
            str.bytes.allocateArray().toList() shouldBe input.encodeToByteArray().toList()
            str.allocateString() shouldBe input
            str.toString() shouldBe input
        }
        Utf8Str.empty.bytes.len shouldBe 0
        Utf8Str.empty.allocateString() shouldBe ""
    }

    should("wrap an existing unvalidated slice without copying") {
        val slice = ByteSlice(ReadonlyBytes(byteArrayOf(0xFF.toByte())), 0, 1)
        (Utf8Str(Str(slice)).bytes === slice) shouldBe true
    }

    should("validate and retain the original UTF-8 slice") {
        val payload = "é中🙂".encodeToByteArray()
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + payload + byteArrayOf(0xFF.toByte())
        val slice = ByteSlice(ReadonlyBytes(bytes), 2, payload.size)
        val str = Utf8Str.fromTainted(slice)
        (str.bytes === slice) shouldBe true
        str.allocateString() shouldBe "é中🙂"
        Utf8Str.fromTainted(ByteSlice(ReadonlyBytes(bytes), bytes.size, 0)).allocateString() shouldBe ""
    }

    should("reject malformed UTF-8 and invalid slice bounds") {
        for (bytes in listOf(
            byteArrayOf(0x80.toByte()),
            byteArrayOf(0xC0.toByte(), 0x80.toByte()),
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
            byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte()),
        )) {
            shouldThrow<IllegalStateException> {
                Utf8Str.fromTainted(ByteSlice(ReadonlyBytes(bytes), 0, bytes.size))
            }
        }
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 1 to 2)) {
            shouldThrow<IllegalStateException> {
                Utf8Str.fromTainted(ByteSlice(ReadonlyBytes(byteArrayOf(65)), offset, length))
            }
        }
    }
})
