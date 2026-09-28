package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import kotlin.random.Random

class BytesJvmSpec : BaseSpec({

    should("match strict JDK UTF-8 validation for random bytes and subranges") {
        val random = Random(42)
        val decoder = Charsets.UTF_8.newDecoder()
        repeat(10_000) {
            val bytes = random.nextBytes(random.nextInt(33))
            val offset = random.nextInt(bytes.size + 1)
            val length = random.nextInt(bytes.size - offset + 1)
            val expected = try {
                decoder.decode(ByteBuffer.wrap(bytes, offset, length))
                true
            } catch (_: CharacterCodingException) {
                false
            }
            bytes.validateUtf8(offset = offset, len = length) shouldBe expected
        }
    }

    should("find a non-ASCII byte at every position around word boundaries") {
        for (length in (0..40).toList() + listOf(64, 127, 128, 129, 160, 256, 1024)) {
            for (offset in 0..7) {
                val bytes = ByteArray(offset + length + 8) { 'a'.code.toByte() }
                bytes.validateUtf8(offset = offset, len = length) shouldBe true
                for (position in offset until offset + length) {
                    bytes[position] = 0xFF.toByte()
                    bytes.validateUtf8(offset = offset, len = length) shouldBe false
                    bytes[position] = 'a'.code.toByte()
                }
            }
        }
    }

    should("resume UTF-8 decoding after long ASCII runs") {
        for (position in listOf(63, 64, 95, 96, 127, 128, 159, 160, 191, 192)) {
            val bytes = ByteArray(256) { 'a'.code.toByte() }
            bytes[position] = 0xC3.toByte()
            bytes[position + 1] = 0xA9.toByte()
            bytes.validateUtf8() shouldBe true
        }
    }
})
