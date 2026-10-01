package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ArraySizeHint
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class JsonStringScannerSpec : BaseSpec({
    should("decode every escape across each possible input split") {
        val escapes = listOf(
            "\\\"" to "\"",
            "\\\\" to "\\",
            "\\/" to "/",
            "\\b" to "\b",
            "\\f" to "\u000C",
            "\\n" to "\n",
            "\\r" to "\r",
            "\\t" to "\t",
            "\\u0000" to "\u0000",
            "\\u007f" to "\u007F",
            "\\u0080" to "\u0080",
            "\\u07FF" to "\u07FF",
            "\\u0800" to "\u0800",
            "\\uD7FF" to "\uD7FF",
            "\\uE000" to "\uE000",
            "\\uFFFF" to "\uFFFF",
            "\\uD800\\uDC00" to "\uD800\uDC00",
            "\\uDBFF\\uDFFF" to "\uDBFF\uDFFF",
            "\\uD83d\\ude03" to "😃",
        )
        for ((escaped, decoded) in escapes) {
            val document = "\"before${escaped}after\"".encodeToByteArray()
            for (bufferSize in listOf(1, 2, 8, 16, 128)) {
                for (splitAt in 1 until document.size) {
                    scanJson(
                        bytes = document,
                        bufferSize = bufferSize,
                        splitAt = splitAt,
                    ) { StringJsonCodec.read() } shouldBe "before${decoded}after"
                    scanJson(
                        bytes = document,
                        bufferSize = bufferSize,
                        splitAt = splitAt,
                    ) { Utf8StrJsonCodec.read() } shouldBe Utf8Str.allocateFromString(string = "before${decoded}after")
                }
            }
        }
    }

    should("decode multibyte input and long strings with repeated short reads") {
        val prefix = "é\u0800😃".repeat(200)
        val suffix = "long".repeat(2000)
        val document = "\"${prefix}\\n${suffix}\\tend\"".encodeToByteArray()
        for (bufferSize in listOf(1, 2, 8, 16, 128)) {
            for (maximumRead in listOf(1, 3, 7)) {
                scanJson(
                    bytes = document,
                    bufferSize = bufferSize,
                    maximumRead = maximumRead,
                ) { StringJsonCodec.read() } shouldBe "${prefix}\n${suffix}\tend"
            }
        }
        val plain = "\"${prefix}${suffix}\"".encodeToByteArray()
        scanJson(bytes = plain, bufferSize = 8, maximumRead = 3) { StringJsonCodec.read() } shouldBe prefix + suffix
    }

    should("preserve unread escapes and following values around packed and vector boundaries") {
        for (prefixSize in 0..129) {
            for (runSize in 0..17) {
                val prefix = "p".repeat(prefixSize)
                val run = "r".repeat(runSize)
                val document = "[\"${prefix}\\n${run}\\tend\",\"next\\u0041\",123]".encodeToByteArray()
                scanJson(bytes = document, bufferSize = 512) {
                    JsonReadProtocol.nextToken() shouldBe 34
                    StringJsonCodec.read() shouldBe "${prefix}\n${run}\tend"
                    JsonReadProtocol.nextToken() shouldBe 44
                    JsonReadProtocol.nextToken() shouldBe 34
                    StringJsonCodec.read() shouldBe "nextA"
                    JsonReadProtocol.nextToken() shouldBe 44
                    JsonReadProtocol.nextToken() shouldBe 49
                    IntJsonCodec.readPrimitive() shouldBe 123
                    JsonReadProtocol.nextToken() shouldBe 93
                }
            }
        }
    }

    should("decode Unicode escapes that cross vector lanes and precede short packed runs") {
        val width = jsonScanner.laneCount
        val escapes = listOf("\\u0043" to "C", "\\u00E9" to "é", "\\u0800" to "\u0800", "\\uD83D\\uDE03" to "😃")
        for ((escaped, decoded) in escapes) {
            for (boundaryOffset in -12..12) {
                for (shortRunSize in 0..8) {
                    val prefix = "p".repeat(width + 3)
                    val boundaryRun = "b".repeat(maxOf(0, width - 12 + boundaryOffset))
                    val shortRun = "s".repeat(shortRunSize)
                    val tail = "z".repeat(width * 2)
                    val encoded = "${prefix}\\u0041\\u0042${boundaryRun}${escaped}${shortRun}\\t${tail}\\nend"
                    val expected = "${prefix}AB${boundaryRun}${decoded}${shortRun}\t${tail}\nend"
                    val document = "[\"${encoded}\",123]".encodeToByteArray()
                    scanJson(bytes = document, bufferSize = document.size) {
                        JsonReadProtocol.nextToken() shouldBe 34
                        StringJsonCodec.read() shouldBe expected
                        JsonReadProtocol.nextToken() shouldBe 44
                        JsonReadProtocol.nextToken() shouldBe 49
                        IntJsonCodec.readPrimitive() shouldBe 123
                        JsonReadProtocol.nextToken() shouldBe 93
                    }
                }
            }
        }
    }

    should("scan strings with scalar and platform scanners using scope progress") {
        for (specialScan in listOf(ScalarJsonSpecialScan, jsonScanner)) {
            val width = specialScan.laneCount
            val prefix = "prefix".repeat(width)
            val tail = "tail".repeat(width)
            val encoded = "${prefix}\\u0041\\u0042abc\\u00E9d\\u0800ef\\uD83D\\uDE03gh\\t${tail}\\nend"
            val expected = "${prefix}ABabcéd\u0800ef😃gh\t${tail}\nend"
            val document = "\"${encoded}\",123".encodeToByteArray()
            val afterQuote = encoded.encodeToByteArray().size + 2
            for (decode in listOf(false, true)) {
                for (incremental in listOf(false, true)) {
                    val parseScope = JsonReadScope(document.size.bytes())
                    val bytes = parseScope.buffer
                    for (index in document.indices) bytes[index] = document[index]
                    parseScope.position = 1
                    parseScope.stringOutputPosition = 1
                    context(parseScope) {
                        if (incremental) {
                            for (limit in 1..afterQuote) {
                                parseScope.limit = limit
                                JsonStringScanner.scan(decode = decode, specialScan = specialScan) shouldBe
                                    (limit == afterQuote)
                                (parseScope.position <= limit) shouldBe true
                                (parseScope.stringOutputPosition <= parseScope.position) shouldBe true
                            }
                        } else {
                            parseScope.limit = document.size
                            JsonStringScanner.scan(decode = decode, specialScan = specialScan) shouldBe true
                        }
                        parseScope.position shouldBe afterQuote
                        if (decode) {
                            val output = ByteArray(parseScope.stringOutputPosition - 1) { bytes[it + 1] }
                            output.contentEquals(expected.encodeToByteArray()) shouldBe true
                        } else {
                            parseScope.stringOutputPosition shouldBe 1
                            ByteArray(bytes.size) { bytes[it] }.contentEquals(document) shouldBe true
                        }
                        ByteArray(document.size - afterQuote) { bytes[afterQuote + it] }
                            .contentEquals(",123".encodeToByteArray()) shouldBe true
                    }
                }
            }
        }
    }

    should("preserve following tokens after buffer growth and compaction") {
        val first = "a".repeat(300) + "\n" + "b".repeat(500)
        val document = "[\"${"a".repeat(300)}\\n${"b".repeat(500)}\",\"\\tsecond\",-42]".encodeToByteArray()
        for (bufferSize in listOf(1, 2, 8, 16, 128)) {
            scanJson(bytes = document, bufferSize = bufferSize, maximumRead = 7) {
                JsonReadProtocol.nextToken() shouldBe 34
                val firstValue = Utf8StrJsonCodec.read()
                JsonReadProtocol.nextToken() shouldBe 44
                JsonReadProtocol.nextToken() shouldBe 34
                StringJsonCodec.read() shouldBe "\tsecond"
                JsonReadProtocol.nextToken() shouldBe 44
                JsonReadProtocol.nextToken() shouldBe 45
                IntJsonCodec.readPrimitive() shouldBe -42
                JsonReadProtocol.nextToken() shouldBe 93
                firstValue shouldBe Utf8Str.allocateFromString(string = first)
            }
        }
    }

    should("hash and compare decoded field bytes at nonzero buffer offsets") {
        val names = listOf(
            "aaé",
            "sequenceAlpha",
            "long".repeat(50),
        )
        for (name in names) {
            val escapedName = "\\u0061" + name.drop(1)
            val document = "{\"first\":0,\"${escapedName}\":42}".encodeToByteArray()
            val expected = ("a" + name.drop(1)).encodeToByteArray()
            val expectedHash = expected.fold(0) { hash, byte -> 31 * hash + (byte.toInt() and 255) }
            for (bufferSize in listOf(1, 2, 8, 16, 512)) {
                scanJson(bytes = document, bufferSize = bufferSize) {
                    JsonReadProtocol.nextToken() shouldBe 34
                    JsonReadProtocol.readField()
                    JsonReadProtocol.nextFieldValue()
                    IntJsonCodec.readPrimitive() shouldBe 0
                    JsonReadProtocol.nextFieldOrEnd() shouldBe 34
                    JsonReadProtocol.readField() shouldBe expectedHash
                    JsonReadProtocol.fieldEquals(expected) shouldBe true
                    JsonReadProtocol.fieldEquals("wrong".encodeToByteArray()) shouldBe false
                    JsonReadProtocol.nextFieldValue()
                    IntJsonCodec.readPrimitive() shouldBe 42
                    JsonReadProtocol.nextFieldOrEnd() shouldBe 125
                }
            }
        }
    }

    should("reject malformed strings when decoding or skipping across splits") {
        val malformed = listOf(
            "\"unterminated",
            "\"trailing\\",
            "\"\\x\"",
            "\"\\u\"",
            "\"\\u123\"",
            "\"\\u12xz\"",
            "\"\\uDC00\"",
            "\"\\uDFFF\"",
            "\"\\uD800\"",
            "\"\\uD800a\"",
            "\"\\uD800\\n\"",
            "\"\\uD800\\u0041\"",
            "\"\\uD800\\uD800\"",
            "\"\\uD800\\uDC0x\"",
        ) + (0..31).map { "\"before${it.toChar()}after\"" }
        for (document in malformed) {
            val bytes = document.encodeToByteArray()
            for (bufferSize in listOf(1, 2, 8, 16, 128)) {
                for (splitAt in 1 until bytes.size) {
                    shouldThrow<IllegalArgumentException> {
                        scanJson(bytes = bytes, bufferSize = bufferSize, splitAt = splitAt) { StringJsonCodec.read() }
                    }
                    shouldThrow<IllegalArgumentException> {
                        scanJson(
                            bytes = bytes,
                            bufferSize = bufferSize,
                            splitAt = splitAt,
                        ) { JsonReadProtocol.skipValue() }
                    }
                }
            }
        }
    }

    should("skip long strings and object names without accumulating their contents") {
        val longText = "text\\n\\uD83D\\uDE03".repeat(5000)
        val documents = listOf("[\"${longText}\",42]", "[{\"${longText}\":\"${longText}\"},42]")
        for (document in documents) {
            for (bufferSize in listOf(1, 2, 8, 16, 128)) {
                scanJson(bytes = document.encodeToByteArray(), bufferSize = bufferSize, maximumRead = 7) {
                    JsonReadProtocol.nextToken()
                    JsonReadProtocol.skipValue()
                    assertSkippedStringCapacity()
                    JsonReadProtocol.nextToken() shouldBe 44
                    JsonReadProtocol.nextToken() shouldBe 52
                    IntJsonCodec.readPrimitive() shouldBe 42
                    JsonReadProtocol.nextToken() shouldBe 93
                }
            }
        }
    }

    should("read empty and consecutive strings without losing borrowed results") {
        for (bufferSize in listOf(1, 2, 8, 16, 128)) {
            scanJson(
                bytes = "[\"\",\"\\n\",\"plain\",\"\"]".encodeToByteArray(),
                bufferSize = bufferSize,
                maximumRead = 3,
            ) {
                JsonReadProtocol.nextToken() shouldBe 34
                StringJsonCodec.read() shouldBe ""
                JsonReadProtocol.nextToken() shouldBe 44
                JsonReadProtocol.nextToken() shouldBe 34
                val first = Utf8StrJsonCodec.read()
                JsonReadProtocol.nextToken() shouldBe 44
                JsonReadProtocol.nextToken() shouldBe 34
                StringJsonCodec.read() shouldBe "plain"
                JsonReadProtocol.nextToken() shouldBe 44
                JsonReadProtocol.nextToken() shouldBe 34
                StringJsonCodec.read() shouldBe ""
                JsonReadProtocol.nextToken() shouldBe 93
                first shouldBe Utf8Str.allocateFromString(string = "\n")
            }
        }
    }
})

private fun <T> scanJson(
    bytes: ByteArray,
    bufferSize: Int,
    splitAt: Int = bytes.size,
    maximumRead: Int = Int.MAX_VALUE,
    read: context(ByteSource, JsonReadScope) () -> T,
): T {
    val input = SplitJsonByteSource(bytes = bytes, splitAt = splitAt, maximumRead = maximumRead)
    val parseScope = JsonReadScope(bufferSize.bytes())
    return context(input) {
        context(parseScope) {
            JsonReadProtocol.nextToken()
            val result = read(input, parseScope)
            JsonReadProtocol.nextToken() shouldBe -1
            result
        }
    }
}

context(parseScope: JsonReadScope)
private fun assertSkippedStringCapacity() {
    (parseScope.buffer.size < 1024) shouldBe true
}

private class SplitJsonByteSource(
    private val bytes: ByteArray,
    private val splitAt: Int,
    private val maximumRead: Int,
) : ByteSource {
    private var position = 0
    override val sizeHint: ArraySizeHint = ArraySizeHint(bytes.size)

    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset <= sink.size - length)
        if (length == 0) {
            return 0
        }
        if (position == bytes.size) {
            return -1
        }
        val boundary = if (position < splitAt) {
            splitAt
        } else {
            bytes.size
        }
        val count = minOf(length, maximumRead, boundary - position)
        for (index in 0 until count) sink[offset + index] = bytes[position + index]
        position += count
        return count
    }
}
