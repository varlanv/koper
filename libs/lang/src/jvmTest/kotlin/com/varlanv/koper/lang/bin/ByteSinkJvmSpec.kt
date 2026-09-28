package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream

class ByteSinkJvmSpec : BaseSpec({
    should("write only the requested range to an output stream") {
        val stream = ByteArrayOutputStream()
        val sink = OutputStreamByteSink(stream)

        sink.writeTo(source = byteArrayOf(9, 1, 2, 8).asMut(), offset = 1, length = 2)
        sink.writeTo(source = byteArrayOf(3).asMut(), offset = 0, length = 1)
        stream.toByteArray().toList() shouldBe listOf<Byte>(1, 2, 3)
    }
})
