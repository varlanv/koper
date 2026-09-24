package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import kotlin.random.Random

class BytesJvmSpec : BaseSpec({
    should("expose exactly the slice as a byte buffer without copying") {
        val bytes = byteArrayOf(9, 8, 1, 2, 7)
        val slice = ByteSlice(ReadonlyBytes(bytes), 2, 2)
        val buffer = slice.readBuff()
        buffer.position() shouldBe 0
        buffer.limit() shouldBe 2
        buffer.capacity() shouldBe 2
        buffer.arrayOffset() shouldBe 2
        (buffer.array() === bytes) shouldBe true
        buffer.get() shouldBe 1.toByte()
        buffer.get() shouldBe 2.toByte()
        buffer.hasRemaining() shouldBe false
        slice.offset shouldBe 2
        slice.len shouldBe 2
    }

    should("expose empty slices as empty buffers") {
        for (offset in listOf(0, 3)) {
            val buffer = ByteSlice(ReadonlyBytes(byteArrayOf(1, 2, 3)), offset, 0).readBuff()
            buffer.remaining() shouldBe 0
            buffer.capacity() shouldBe 0
        }
    }

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
            bytes.validateUtf8(offset, length) shouldBe expected
        }
    }
})
