package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class JsonValueCodecSpec : BaseSpec({
    should("write and read values through public codecs") {
        fun <T> roundTrip(
            writerCodec: JsonSer<T>,
            readerCodec: JsonDe<T>,
            value: T,
            expectedJson: String,
        ) {
            val output = ReusableByteArraySink(512)
            val writer = JsonWriteProtocol()
            writer.reset(output)
            writerCodec.write(writer = writer, value = value)
            writer.flush()
            val bytes = output.toByteArray()
            bytes.decodeToString() shouldBe expectedJson

            for (bufferSize in listOf(1, 32768)) {
                val reader = JsonReadProtocol(bufferSize = bufferSize)
                reader.reset(bytes.asByteSource())
                reader.nextToken()
                readerCodec.read(reader) shouldBe value
                reader.nextToken() shouldBe -1
            }
        }

        roundTrip(
            writerCodec = IntJsonCodec,
            readerCodec = IntJsonCodec,
            value = Int.MIN_VALUE,
            expectedJson = "-2147483648",
        )
        roundTrip(
            writerCodec = LongJsonCodec,
            readerCodec = LongJsonCodec,
            value = Long.MIN_VALUE,
            expectedJson = "-9223372036854775808",
        )
        roundTrip(writerCodec = BooleanJsonCodec, readerCodec = BooleanJsonCodec, value = false, expectedJson = "false")
        roundTrip(
            writerCodec = StringJsonCodec,
            readerCodec = StringJsonCodec,
            value = "A\n",
            expectedJson = "\"A\\n\"",
        )
        roundTrip(
            writerCodec = Utf8StrJsonCodec,
            readerCodec = Utf8StrJsonCodec,
            value = Utf8Str.allocateFromString("é\n"),
            expectedJson = "\"é\\n\"",
        )

        IntJsonCodec.size.maximumBytes shouldBe 11L
        (StringJsonCodec.size as JsonValueSize.FromValue<String>).maximumBytes("A\n") shouldBe 14L
    }
})
