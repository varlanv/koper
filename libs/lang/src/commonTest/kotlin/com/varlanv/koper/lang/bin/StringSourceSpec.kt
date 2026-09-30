package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class StringSourceSpec : BaseSpec({
    should("encode UTF-8 across every destination size without touching surrounding bytes") {
        val fixtures = listOf(
            "" to byteArrayOf(),
            "hello\u0000" to byteArrayOf(104, 101, 108, 108, 111, 0),
            "éЖ日🙂" to
                byteArrayOf(
                    0xC3.toByte(),
                    0xA9.toByte(),
                    0xD0.toByte(),
                    0x96.toByte(),
                    0xE6.toByte(),
                    0x97.toByte(),
                    0xA5.toByte(),
                    0xF0.toByte(),
                    0x9F.toByte(),
                    0x99.toByte(),
                    0x82.toByte(),
                ),
            "\uD800A\uDC00\uD800\uD800\uDC00\uDC00" to
                byteArrayOf(63, 65, 63, 63, 0xF0.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte(), 63),
        )
        for ((text, expected) in fixtures) {
            for (length in 1..16) {
                val source = StringSource(text)
                val actual = mutableListOf<Byte>()
                val sink = MutBytes(ByteArray(length + 4) { 0x55 })
                while (true) {
                    source.readAtMostTo(sink = sink, offset = 2, length = 0) shouldBe 0
                    val count = source.readAtMostTo(sink = sink, offset = 2, length = length)
                    if (count == -1) {
                        break
                    }
                    (count in 1..length) shouldBe true
                    for (index in 0 until count) {
                        actual.add(sink[index + 2])
                    }
                    sink[0] shouldBe 0x55.toByte()
                    sink[1] shouldBe 0x55.toByte()
                    sink[length + 2] shouldBe 0x55.toByte()
                    sink[length + 3] shouldBe 0x55.toByte()
                }
                actual shouldBe expected.toList()
                source.readAtMostTo(sink = sink, offset = 0, length = 1) shouldBe -1
                source.readAtMostTo(sink = sink, offset = sink.size, length = 0) shouldBe 0
            }
        }
    }

    should("reject invalid ranges without consuming text or pending bytes") {
        val source = StringSource("🙂A")
        val sink = MutBytes(ByteArray(2))
        source.readAtMostTo(sink = sink, offset = 0, length = 1) shouldBe 1
        sink[0] shouldBe 0xF0.toByte()
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            shouldThrow<IndexOutOfBoundsException> {
                source.readAtMostTo(sink = sink, offset = offset, length = length)
            }
        }
        source.readAtMostTo(sink = sink, offset = 0, length = 2) shouldBe 2
        sink.asList() shouldBe listOf(0x9F.toByte(), 0x99.toByte())
        source.readAtMostTo(sink = sink, offset = 0, length = 2) shouldBe 2
        sink.asList() shouldBe listOf(0x82.toByte(), 65.toByte())
        source.readAtMostTo(sink = sink, offset = 0, length = 1) shouldBe -1
    }
})
