package com.varlanv.koper.json

import com.varlanv.koper.testing.BaseSpec

class JsonIntGeneratedCodecSpec : BaseSpec({
    should("write and read the detected Int property") {
        //        val writer = IdealJsonWriter()
        //        val reader = IdealJsonReader()
        //        val output = ByteArrayJsonOutput()
        //
        //        for (quantity in listOf(Int.MIN_VALUE, -1, 0, 42, Int.MAX_VALUE)) {
        //            val value = JsonIntSample(quantity)
        //            output.reset()
        //            JsonIntSampleGeneratedJsonCodec.write(writer, value, output)
        //            output.toByteArray().decodeToString() shouldBe "{\"quantity\":$quantity}"
        //            JsonIntSampleGeneratedJsonCodec.read(reader, ByteArrayJsonInput(output.toByteArray())) shouldBe value
        //        }
    }

    should("find its field after an unknown field") {
        //        val input = ByteArrayJsonInput("{\"other\":true, \"quantity\":17}".encodeToByteArray())
        //        JsonIntSampleGeneratedJsonCodec.read(IdealJsonReader(), input) shouldBe JsonIntSample(17)
    }

    should("reject a missing field") {
        //        val input = ByteArrayJsonInput("{}".encodeToByteArray())
        //        shouldThrow<IllegalArgumentException> {
        //            JsonIntSampleGeneratedJsonCodec.read(IdealJsonReader(), input)
        //        }
    }
})
