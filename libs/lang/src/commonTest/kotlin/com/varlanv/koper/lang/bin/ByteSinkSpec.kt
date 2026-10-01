package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class ByteSinkSpec : BaseSpec({
    should("append source ranges across growth and reuse the array on reset") {
        val sink = ReusableByteArraySink(1.bytes())
        sink.writeTo(source = MutBytes(byteArrayOf(9, 1, 2, 8)).asReadonly(), offset = 1, length = 2)
        sink.writeTo(source = MutBytes(byteArrayOf(3, 4, 5)).asReadonly(), offset = 0, length = 3)

        var backing = Bytes.empty
        sink.useBytes { bytes, length ->
            backing = bytes
            bytes.asList().take(length) shouldBe listOf<Byte>(1, 2, 3, 4, 5)
        }

        sink.reset()
        sink.useBytes { bytes, length ->
            (bytes == backing) shouldBe true
            length shouldBe 0
        }
        sink.writeTo(source = MutBytes(byteArrayOf(7, 6)).asReadonly(), offset = 1, length = 1)
        sink.useBytes { bytes, length ->
            bytes.asList().take(length) shouldBe listOf<Byte>(6)
        }
    }

    should("accept zero-length writes and reject invalid ranges without changing contents") {
        val sink = ReusableByteArraySink(0.bytes())
        sink.writeTo(source = MutBytes(byteArrayOf(1, 2)).asReadonly(), offset = 2, length = 0)
        sink.writeTo(source = MutBytes(byteArrayOf(1, 2)).asReadonly(), offset = 0, length = 1)
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            shouldThrow<IndexOutOfBoundsException> {
                sink.writeTo(source = MutBytes(byteArrayOf(1, 2)).asReadonly(), offset = offset, length = length)
            }
        }
        sink.useBytes { bytes, length ->
            bytes.asList().take(length) shouldBe listOf<Byte>(1)
        }
    }
})
