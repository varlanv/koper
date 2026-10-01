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
        JsonWriteScope().scoped(output) {
            var position = 0
            if (reserveFromHints) {
                val maximumBytes = when (val size = writerCodec.hints.size) {
                    is JsonValueSize.Static -> size.maximumBytes
                    is JsonValueSize.FromValue -> size.maximumBytes(value)
                    JsonValueSize.Dynamic -> error("This test requires a bounded codec")
                }
                position = JsonWriteProtocol.reserve(size = maximumBytes, position = position)
            }
            position = writerCodec.write(value = value, position = position)
            position shouldBe expectedJson.encodeToByteArray().size
            JsonWriteProtocol.flush(position) shouldBe 0
        }
        val bytes = output.toByteArray()
        bytes.decodeToString() shouldBe expectedJson

        for (bufferSize in listOf(1, 32768)) {
            readJson(bytes = bytes, bufferSize = bufferSize) { readerCodec.read() } shouldBe value
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

    should("chain writes at explicit positions and restart after reservation flushes") {
        val output = ReusableByteArraySink(1.bytes())
        val scope = JsonWriteScope(16.bytes())
        scope.scoped(output) {
            var position = JsonWriteProtocol.reserve(size = 16, position = 0)
            JsonWriteProtocol.writeRaw(value = 0x34333231, position = position)
            position += 4
            position = BooleanJsonCodec.writePrimitive(value = true, position = position)
            position shouldBe 8
            position = JsonWriteProtocol.reserve(size = 8, position = position)
            position shouldBe 8
            position = StringJsonCodec.write(value = "ABCD", position = position)
            position shouldBe 14
            position = JsonWriteProtocol.reserve(size = 32, position = position)
            position shouldBe 0
            position = Utf8StrJsonCodec.writePrimitive(
                value = Utf8Str.allocateFromString(string = "é\n"),
                position = position,
            )
            position shouldBe 6
            position = IntJsonCodec.writePrimitive(value = -12, position = position)
            position shouldBe 9
            position = LongJsonCodec.writePrimitive(value = 34L, position = position)
            position shouldBe 11
            position = BooleanJsonCodec.writePrimitive(value = false, position = position)
            position shouldBe 16
            JsonWriteProtocol.flush(position) shouldBe 0
        }
        output.toByteArray().decodeToString() shouldBe "1234true\"ABCD\"\"é\\n\"-1234false"
        scope.scoped(output) {
            var position = JsonWriteProtocol.reserve(size = 3, position = 0)
            JsonWriteProtocol.writeByte(value = '!'.code, position = position)
            position++
            position = IntJsonCodec.writePrimitive(value = 56, position = position)
            JsonWriteProtocol.flush(position) shouldBe 0
        }
        output.toByteArray().decodeToString() shouldBe "1234true\"ABCD\"\"é\\n\"-1234false!56"
    }

    should("write signed integers at every decimal boundary") {
        val values = mutableSetOf(0L, Long.MIN_VALUE, Long.MAX_VALUE, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        for (number in -999..999) {
            values.add(number.toLong())
        }
        var power = 1L
        repeat(19) {
            for (delta in -1L..1L) {
                values.add(power + delta)
                values.add(-(power + delta))
            }
            if (it < 18) {
                power *= 10
            }
        }
        for (value in values) {
            roundTrip(
                writerCodec = LongJsonCodec,
                readerCodec = LongJsonCodec,
                value = value,
                expectedJson = value.toString(),
            )
            if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                roundTrip(
                    writerCodec = IntJsonCodec,
                    readerCodec = IntJsonCodec,
                    value = value.toInt(),
                    expectedJson = value.toString(),
                )
            }
        }
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
                readJson(
                    bytes = document.encodeToByteArray(),
                    bufferSize = bufferSize,
                ) { IntJsonCodec.readPrimitive() } shouldBe expected
            }
            for ((document, expected) in validLongs) {
                readJson(
                    bytes = document.encodeToByteArray(),
                    bufferSize = bufferSize,
                ) { LongJsonCodec.readPrimitive() } shouldBe expected
            }
            for (document in listOf("2147483648", "-2147483649", "01", "1x", "-")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(
                        bytes = document.encodeToByteArray(),
                        bufferSize = bufferSize,
                    ) { IntJsonCodec.readPrimitive() }
                }
            }
            for (document in listOf("9223372036854775808", "-9223372036854775809", "01", "1x", "-")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(
                        bytes = document.encodeToByteArray(),
                        bufferSize = bufferSize,
                    ) { LongJsonCodec.readPrimitive() }
                }
            }
        }
    }

    should("read packed integers at every buffer boundary and preserve their delimiters") {
        val values = listOf(
            1,
            -1,
            9999,
            10000,
            -10000,
            99999999,
            -99999999,
            100000000,
            -100000000,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )
        for (value in values) {
            roundTrip(
                writerCodec = IntJsonCodec,
                readerCodec = IntJsonCodec,
                value = value,
                expectedJson = value.toString(),
            )
            for (bufferSize in 1..20) {
                readJson(bytes = "$value ".encodeToByteArray(), bufferSize = bufferSize) {
                    IntJsonCodec.readPrimitive()
                } shouldBe value
                readJson(bytes = "$value ".encodeToByteArray(), bufferSize = bufferSize) {
                    LongJsonCodec.readPrimitive()
                } shouldBe value.toLong()
            }
        }
        val malformed = listOf(
            "00000",
            "-00000",
            "01234",
            "-01234",
            "2147483648",
            "-2147483649",
            "9999999999999999999999999999999",
            "1234x",
            "12345x",
            "123456789x",
            "1234567890x",
        )
        for (document in malformed) {
            for (bufferSize in 1..20) {
                shouldThrow<IllegalArgumentException> {
                    readJson(bytes = document.encodeToByteArray(), bufferSize = bufferSize) {
                        IntJsonCodec.readPrimitive()
                    }
                }
            }
        }
    }

    should("escape short packed UTF-8 runs and every control byte") {
        for (runSize in 0..12) {
            val text = "\"" + (0..31).joinToString("") { "a".repeat(runSize) + it.toChar() } + "é😃\\end"
            val expected = kotlinx.serialization.json.JsonPrimitive(text).toString()
            roundTrip(
                writerCodec = StringJsonCodec,
                readerCodec = StringJsonCodec,
                value = text,
                expectedJson = expected,
            )
            roundTrip(
                writerCodec = Utf8StrJsonCodec,
                readerCodec = Utf8StrJsonCodec,
                value = Utf8Str.allocateFromString(string = text),
                expectedJson = expected,
            )
        }
    }

    should("write strings around packed ASCII boundaries") {
        for (length in 0..32) {
            val text = "aB3~\u007f".repeat(7).take(length)
            roundTrip(
                writerCodec = StringJsonCodec,
                readerCodec = StringJsonCodec,
                value = text,
                expectedJson = kotlinx.serialization.json.JsonPrimitive(text).toString(),
            )
        }
        for (prefixLength in 0..7) {
            for (special in listOf("\"", "\\", "\n", "\u0000", "\u0080", "é", "日", "🙂")) {
                val text = "a".repeat(prefixLength) + special + "BCDEFGHIJ"
                roundTrip(
                    writerCodec = StringJsonCodec,
                    readerCodec = StringJsonCodec,
                    value = text,
                    expectedJson = kotlinx.serialization.json.JsonPrimitive(text).toString(),
                )
            }
        }
    }

    should("write long strings around the bulk encoding boundary") {
        val fragments = listOf(
            "éBCDEFGHIJ",
            "\nBCDEFGHIJ",
            "\u0000BCDEFGHIJ",
            "日ABC🙂DEFéGHI\"\\\n",
            "é日🙂".repeat(128),
        )
        for (length in listOf(1023, 1024, 1025, 4096)) {
            for (fragment in fragments) {
                val text = "a".repeat(length - fragment.length) + fragment
                roundTrip(
                    writerCodec = StringJsonCodec,
                    readerCodec = StringJsonCodec,
                    value = text,
                    expectedJson = kotlinx.serialization.json.JsonPrimitive(text).toString(),
                )
            }
        }
    }

    should("reject string-size overflow before multiplying or accumulating") {
        JsonWriteProtocol.addStringSize(maximumBytes = 2, length = 0) shouldBe 2
        val length = (Int.MAX_VALUE - 2) / 6
        val maximum = JsonWriteProtocol.addStringSize(maximumBytes = 2, length = length)
        maximum shouldBe 2 + length * 6
        JsonWriteProtocol.addStringSize(maximumBytes = Int.MAX_VALUE, length = 0) shouldBe Int.MAX_VALUE
        for ((base, count) in listOf(2 to length + 1, maximum to 1, -1 to 0, 0 to -1)) {
            shouldThrow<IllegalArgumentException> {
                JsonWriteProtocol.addStringSize(maximumBytes = base, length = count)
            }
        }
    }

    should("read boolean literals across buffer boundaries and reject invalid suffixes") {
        for (bufferSize in listOf(1, 2, 4, 5, 32768)) {
            for ((document, expected) in mapOf("true " to true, "false " to false)) {
                readJson(
                    bytes = document.encodeToByteArray(),
                    bufferSize = bufferSize,
                ) { BooleanJsonCodec.readPrimitive() } shouldBe expected
            }
            for (document in listOf("tru", "trux", "truex", "fals", "falsx", "falsex")) {
                shouldThrow<IllegalArgumentException> {
                    readJson(
                        bytes = document.encodeToByteArray(),
                        bufferSize = bufferSize,
                    ) { BooleanJsonCodec.readPrimitive() }
                }
            }
        }
    }
})

private fun <T> readJson(
    bytes: ByteArray,
    bufferSize: Int,
    read: context(ByteSource, JsonReadScope) () -> T,
): T {
    val input = bytes.asByteSource()
    val parseScope = JsonReadScope(bufferSize.bytes())
    return context(input) {
        context(parseScope) {
            JsonReadProtocol.nextToken()
            val value = read(input, parseScope)
            JsonReadProtocol.nextToken() shouldBe -1
            value
        }
    }
}
