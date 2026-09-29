package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.bin.bytes
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
        val output = ReusableByteArraySink(512.bytes())
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
            readJson(bytes, bufferSize) { readerCodec.read() } shouldBe value
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
            value = Utf8Str.allocateFromString(string = "é\n"),
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
            for ((document, expected) in validInts) {
                readJson(document.encodeToByteArray(), bufferSize) { IntJsonCodec.readPrimitive() } shouldBe expected
            }
            for ((document, expected) in validLongs) {
                readJson(document.encodeToByteArray(), bufferSize) { LongJsonCodec.readPrimitive() } shouldBe expected
            }
            for (document in listOf("2147483648", "-2147483649", "01", "1x", "-")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(document.encodeToByteArray(), bufferSize) { IntJsonCodec.readPrimitive() }
                }
            }
            for (document in listOf("9223372036854775808", "-9223372036854775809", "01", "1x", "-")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(document.encodeToByteArray(), bufferSize) { LongJsonCodec.readPrimitive() }
                }
            }
        }
    }

    should("read boolean literals across buffer boundaries and reject invalid suffixes") {
        for (bufferSize in listOf(1, 2, 4, 5, 32768)) {
            for ((document, expected) in mapOf("true " to true, "false " to false)) {
                readJson(document.encodeToByteArray(), bufferSize) { BooleanJsonCodec.readPrimitive() } shouldBe expected
            }
            for (document in listOf("tru", "trux", "truex", "fals", "falsx", "falsex")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(document.encodeToByteArray(), bufferSize) { BooleanJsonCodec.readPrimitive() }
                }
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
            readJson(bytes, bufferSize) { HandwrittenJsonSampleCodec.read() } shouldBe
                HandwrittenJsonSample(id = 42, text = "A\n")
            readJson(bytes, bufferSize) { HandwrittenJsonSampleJsonCodec.read() } shouldBe
                HandwrittenJsonSample(id = 42, text = "A\n")
        }
    }

    should("write and read all supported generated field types") {
        val longUtf8 = "A\t" + "x".repeat(600)
        val value = GeneratedJsonSample(
            intValue = Int.MIN_VALUE,
            longValue = Long.MIN_VALUE,
            booleanValue = true,
            stringValue = "é\n",
            strValue = Utf8Str.allocateFromString(string = longUtf8),
        )
        val expectedJson =
            "{\"intValue\":-2147483648,\"longValue\":-9223372036854775808," +
                "\"booleanValue\":true,\"stringValue\":\"é\\n\"," +
                "\"strValue\":\"A\\t${"x".repeat(600)}\"}"
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
        val value = readJson(expectedJson.encodeToByteArray(), 1) { GeneratedJsonNamesJsonCodec.read() }
        roundTrip(
            writerCodec = GeneratedJsonNamesJsonCodec,
            readerCodec = GeneratedJsonNamesJsonCodec,
            value = value,
            expectedJson = expectedJson,
            reserveFromHints = false,
        )
        val bytes = "{\"bYx\":2,\"aaaaé\":4,\"axx\":1}".encodeToByteArray()
        readJson(bytes, 1) { GeneratedJsonNamesJsonCodec.read() } shouldBe value
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
                readJson(document.encodeToByteArray(), bufferSize) { GeneratedLongFieldNamesJsonCodec.read() } shouldBe value
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
            shouldThrow<IllegalArgumentException> {
                readJson(document.encodeToByteArray(), 32768) { GeneratedLongFieldNamesJsonCodec.read() }
            }
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

private fun <T> readJson(
    bytes: ByteArray,
    bufferSize: Int,
    read: context(ByteSource, JsonParseScope) () -> T,
): T {
    val input = bytes.asByteSource()
    val parseScope = JsonParseScope(bufferSize.bytes())
    return context(input) {
        context(parseScope) {
            JsonReadProtocol.nextToken()
            val value = read(input, parseScope)
            JsonReadProtocol.nextToken() shouldBe -1
            value
        }
    }
}
