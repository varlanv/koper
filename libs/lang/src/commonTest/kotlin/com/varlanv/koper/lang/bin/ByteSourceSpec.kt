package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class ByteSourceSpec : BaseSpec({
    should("read only the slice into the requested sink range") {
        val source = ByteArraySource(
            BytesSlice(
                bytes = MutBytes(byteArrayOf(9, 1, 2, 3, 8)).readonly,
                offset = 1,
                len = 3,
            ),
        )
        val sink = MutBytes(byteArrayOf(7, 7, 7, 7, 7))

        source.readAtMostTo(sink = sink, offset = 2, length = 2) shouldBe 2
        sink.asList() shouldBe listOf<Byte>(7, 7, 1, 2, 7)
        source.readAtMostTo(sink = sink, offset = 1, length = 3) shouldBe 1
        sink.asList() shouldBe listOf<Byte>(7, 3, 1, 2, 7)
        source.readAtMostTo(sink = sink, offset = 0, length = 1) shouldBe -1
        source.readAtMostTo(sink = sink, offset = sink.size, length = 0) shouldBe 0
    }

    should("allow empty slices and zero-length reads") {
        val source = ByteArraySource(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).readonly, offset = 2, len = 0))
        source.readAtMostTo(
            sink = MutBytes(ByteArray(0)),
            offset = 0,
            length = 0,
        ) shouldBe 0
        source.readAtMostTo(
            sink = MutBytes(ByteArray(1)),
            offset = 0,
            length = 1,
        ) shouldBe -1
    }

    should("reject invalid read ranges without consuming bytes") {
        val source = ByteArraySource(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).readonly, offset = 0, len = 2))
        val sink = MutBytes(ByteArray(2))
        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 1, Int.MAX_VALUE to Int.MAX_VALUE)) {
            shouldThrow<IndexOutOfBoundsException> {
                source.readAtMostTo(sink = sink, offset = offset, length = length)
            }
        }
        source.readAtMostTo(sink = sink, offset = 0, length = 2) shouldBe 2
        sink.asList() shouldBe listOf<Byte>(1, 2)
    }
})
