package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BytesIoJvmSpec : BaseSpec({
    should("read from an input stream into the requested range") {
        val source = InputStreamByteSource(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val sink = byteArrayOf(9, 9, 9, 9, 9)

        source.readAtMostTo(sink = sink.asMut(), offset = 1, length = 2) shouldBe 2
        source.readAtMostTo(sink = sink.asMut(), offset = 3, length = 2) shouldBe 1
        sink.toList() shouldBe listOf<Byte>(9, 1, 2, 3, 9)
        source.readAtMostTo(sink = sink.asMut(), offset = 0, length = 1) shouldBe -1
    }

    should("write only the requested range to an output stream") {
        val stream = ByteArrayOutputStream()
        val sink = OutputStreamByteSink(stream)

        sink.writeTo(source = byteArrayOf(9, 1, 2, 8).asMut(), offset = 1, length = 2)
        sink.writeTo(source = byteArrayOf(3).asMut(), offset = 0, length = 1)
        stream.toByteArray().toList() shouldBe listOf<Byte>(1, 2, 3)
    }
})
