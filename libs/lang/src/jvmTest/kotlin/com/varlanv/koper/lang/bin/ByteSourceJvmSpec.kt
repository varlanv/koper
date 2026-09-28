package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class ByteSourceJvmSpec : BaseSpec({
    should("read from an input stream into the requested range") {
        val source = InputStreamByteSource(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val sink = byteArrayOf(9, 9, 9, 9, 9)

        source.readAtMostTo(sink = sink.asMut(), offset = 1, length = 2) shouldBe 2
        source.readAtMostTo(sink = sink.asMut(), offset = 3, length = 2) shouldBe 1
        sink.toList() shouldBe listOf<Byte>(9, 1, 2, 3, 9)
        source.readAtMostTo(sink = sink.asMut(), offset = 0, length = 1) shouldBe -1
    }
})
