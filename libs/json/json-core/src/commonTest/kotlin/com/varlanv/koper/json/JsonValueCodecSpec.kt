package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class JsonValueCodecSpec : BaseSpec({
    fun <T> roundTrip(
        writerCodec: JsonCodec.Write<T>,
        readerCodec: JsonCodec.Read<T>,
        value: T,
        expectedJson: String,
        reserveFromHints: Boolean = true,
    ) {
        val output = ReusableByteArraySink(512)
        val writer = JsonWriteProtocol()
        writer.reset(output)
        if (reserveFromHints) {
            val maximumBytes = when (val size = writerCodec.hints.size) {
                is JsonValueSize.Static -> size.maximumBytes
                is JsonValueSize.FromValue -> size.maximumBytes(value)
                JsonValueSize.Dynamic -> error("This test requires a bounded codec")
            }
            writer.reserve(maximumBytes)
        }
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

    should("write and read bounded built-in values") {
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
    }

    should("write and read a reserved object with built-in field codecs") {
        val text = "é\n" + "x".repeat(600)
        val value = HandwrittenJsonSample(id = Int.MIN_VALUE, text = text)
        val expectedJson = "{\"id\":-2147483648,\"text\":\"é\\n${"x".repeat(600)}\"}"
        roundTrip(
            writerCodec = HandwrittenJsonSampleCodec,
            readerCodec = HandwrittenJsonSampleCodec,
            value = value,
            expectedJson = expectedJson,
            reserveFromHints = false,
        )
        roundTrip(
            writerCodec = HandwrittenJsonSampleJsonCodec,
            readerCodec = HandwrittenJsonSampleJsonCodec,
            value = value,
            expectedJson = expectedJson,
            reserveFromHints = false,
        )
    }

    should("read object fields in any order and skip unknown values") {
        val bytes = "{\"extra\":[null,{\"nested\":true}],\"text\":\"A\\n\",\"id\":42}".encodeToByteArray()
        for (bufferSize in listOf(1, 32768)) {
            val reader = JsonReadProtocol(bufferSize = bufferSize)
            reader.reset(bytes.asByteSource())
            reader.nextToken()
            HandwrittenJsonSampleCodec.read(reader) shouldBe HandwrittenJsonSample(id = 42, text = "A\n")
            reader.nextToken() shouldBe -1

            reader.reset(bytes.asByteSource())
            reader.nextToken()
            HandwrittenJsonSampleJsonCodec.read(reader) shouldBe HandwrittenJsonSample(id = 42, text = "A\n")
            reader.nextToken() shouldBe -1
        }
    }

    should("write and read all supported generated field types") {
        val longUtf8 = "A\t" + "x".repeat(600)
        val value = GeneratedJsonSample(
            intValue = Int.MIN_VALUE,
            longValue = Long.MIN_VALUE,
            booleanValue = true,
            stringValue = "é\n",
            utf8Value = Utf8Str.allocateFromString(longUtf8),
        )
        val expectedJson =
            "{\"intValue\":-2147483648,\"longValue\":-9223372036854775808," +
                "\"booleanValue\":true,\"stringValue\":\"é\\n\"," +
                "\"utf8Value\":\"A\\t${"x".repeat(600)}\"}"
        roundTrip(
            writerCodec = GeneratedJsonSampleJsonCodec,
            readerCodec = GeneratedJsonSampleJsonCodec,
            value = value,
            expectedJson = expectedJson,
            reserveFromHints = false,
        )
    }

    should("read generated fields with colliding hashes and a non-ASCII name") {
        val expectedJson = "{\"axx\":1,\"bYx\":2,\"aaaaé\":4}"
        val reader = JsonReadProtocol(bufferSize = 1)
        reader.reset(expectedJson.encodeToByteArray().asByteSource())
        reader.nextToken()
        val value = GeneratedJsonNamesJsonCodec.read(reader)
        reader.nextToken() shouldBe -1
        roundTrip(
            writerCodec = GeneratedJsonNamesJsonCodec,
            readerCodec = GeneratedJsonNamesJsonCodec,
            value = value,
            expectedJson = expectedJson,
            reserveFromHints = false,
        )
        val bytes = "{\"bYx\":2,\"aaaaé\":4,\"axx\":1}".encodeToByteArray()
        reader.reset(bytes.asByteSource())
        reader.nextToken()
        GeneratedJsonNamesJsonCodec.read(reader) shouldBe value
        reader.nextToken() shouldBe -1
    }

    should("read through a companion factory") {
        roundTrip(
            writerCodec = GeneratedFactorySampleJsonCodec,
            readerCodec = GeneratedFactorySampleJsonCodec,
            value = GeneratedFactorySample(value = 7),
            expectedJson = "{\"value\":7}",
            reserveFromHints = false,
        )
    }
})
