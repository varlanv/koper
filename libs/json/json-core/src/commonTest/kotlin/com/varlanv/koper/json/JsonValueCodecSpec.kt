package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
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
            writerCodec = StrJsonCodec,
            readerCodec = StrJsonCodec,
            value = Utf8Str.allocateFromString("é\n"),
            expectedJson = "\"é\\n\"",
        )
    }

    should("read integer boundaries and reject malformed integers") {
        val validInts = mapOf("0" to 0, "-0" to 0, "2147483647" to Int.MAX_VALUE, "-2147483648" to Int.MIN_VALUE)
        val validLongs = mapOf(
            "0" to 0L,
            "-0" to 0L,
            "9223372036854775807" to Long.MAX_VALUE,
            "-9223372036854775808" to Long.MIN_VALUE,
        )
        for (bufferSize in listOf(1, 2, 8, 16, 32768)) {
            val reader = JsonReadProtocol(bufferSize = bufferSize)
            for ((document, expected) in validInts) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                IntJsonCodec.readPrimitive(reader) shouldBe expected
                reader.nextToken() shouldBe -1
            }
            for ((document, expected) in validLongs) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                LongJsonCodec.readPrimitive(reader) shouldBe expected
                reader.nextToken() shouldBe -1
            }
            for (document in listOf("2147483648", "-2147483649", "01", "1x", "-")) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                shouldThrow<IllegalArgumentException> { IntJsonCodec.readPrimitive(reader) }
            }
            for (document in listOf("9223372036854775808", "-9223372036854775809", "01", "1x", "-")) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                shouldThrow<IllegalArgumentException> { LongJsonCodec.readPrimitive(reader) }
            }
        }
    }

    should("read boolean literals across buffer boundaries and reject invalid suffixes") {
        for (bufferSize in listOf(1, 2, 4, 5, 32768)) {
            val reader = JsonReadProtocol(bufferSize = bufferSize)
            for ((document, expected) in mapOf("true " to true, "false " to false)) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                BooleanJsonCodec.readPrimitive(reader) shouldBe expected
                reader.nextToken() shouldBe -1
            }
            for (document in listOf("tru", "trux", "truex", "fals", "falsx", "falsex")) {
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                shouldThrow<IllegalArgumentException> { BooleanJsonCodec.readPrimitive(reader) }
            }
        }
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
            strValue = Utf8Str.allocateFromString(longUtf8),
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

    should("read compact, spaced, escaped, and split long field names") {
        val value = GeneratedLongFieldNames(seven77 = 3, eight888 = 4, sequenceAlpha = 1, sequenceBeta = 2)
        val documents = listOf(
            "{\"seven77\":3,\"eight888\":4,\"sequenceAlpha\":1,\"sequenceBeta\":2}",
            "{ \"sequenceBeta\" : 2 , \"eight888\" : 4, \"sequenceAlpha\"\n:\t1, \"seven77\" : 3 }",
            "{\"sequenceGamma\":0,\"sequenceAlpha\":1,\"seven77\":3,\"sequenceBeta\":2,\"eight888\":4}",
            "{\"sequence\\u0041lpha\":1,\"sequenceBeta\":2,\"seven77\":3,\"eight888\":4}",
        )
        for (document in documents) {
            for (bufferSize in listOf(1, 8, 16, 32768)) {
                val reader = JsonReadProtocol(bufferSize = bufferSize)
                reader.reset(document.encodeToByteArray().asByteSource())
                reader.nextToken()
                GeneratedLongFieldNamesJsonCodec.read(reader) shouldBe value
                reader.nextToken() shouldBe -1
            }
        }
    }

    should("reject malformed generated fields after packed-name matching") {
        val documents = listOf(
            "{\"seven77\":3,\"eight888\":4,\"sequenceAlpha\" 1,\"sequenceBeta\":2}",
            "{\"seven77\":3,\"eight888\":4,\"sequenceAlpha\"::1,\"sequenceBeta\":2}",
            "{\"seven77\":3,\"eight888\":4,\"sequenceAlpha\":1,\"sequenceBet\":2}",
        )
        for (document in documents) {
            val reader = JsonReadProtocol()
            reader.reset(document.encodeToByteArray().asByteSource())
            reader.nextToken()
            shouldThrow<IllegalArgumentException> { GeneratedLongFieldNamesJsonCodec.read(reader) }
        }
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
