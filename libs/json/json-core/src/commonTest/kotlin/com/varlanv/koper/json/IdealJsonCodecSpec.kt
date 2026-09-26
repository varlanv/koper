package com.varlanv.koper.json

import com.varlanv.koper.json.tmp.IdealJsonReader
import com.varlanv.koper.json.tmp.IdealJsonUtf8Codec
import com.varlanv.koper.json.tmp.IdealJsonWriter
import com.varlanv.koper.json.tmp.JsonNativeSample
import com.varlanv.koper.json.tmp.JsonUtf8Sample
import com.varlanv.koper.json.tmp.NativeJsonUtf8Codec
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class IdealJsonCodecSpec : BaseSpec({
    for (vectorized in listOf(false, true)) {
        context(
            if (vectorized) {
                "vector path"
            } else {
                "scalar path"
            },
        ) {
            should("roundtrip strings and numbers through both codecs") {
                val reader = IdealJsonReader(vectorized = vectorized)
                val writer = IdealJsonWriter(vectorized)
                for (id in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
                    for (text in listOf(
                        "",
                        "ASCII",
                        "é中🙂",
                        "\"\\\n\t\u0000",
                        "large".repeat(9000),
                    )) {
                        val value = JsonUtf8Sample(
                            id = id,
                            symbol = Utf8Str.allocateFromString("symbol-$id"),
                            text = Utf8Str.allocateFromString(text),
                            sequence = if (id < 0) {
                                Int.MIN_VALUE
                            } else {
                                Int.MAX_VALUE
                            },
                            active = id % 2 == 0L,
                        )
                        val native = JsonNativeSample(
                            id = id,
                            symbol = "symbol-$id",
                            text = value.text,
                            sequence = value.sequence,
                            active = value.active,
                        )
                        val output = ReusableByteArraySink(512)
                        IdealJsonUtf8Codec.write(writer = writer, value = value, output = output)
                        val json = output.toByteArray()
                        IdealJsonUtf8Codec.read(reader = reader, input = json.asByteSource()) shouldBe value
                        val expected = Json.parseToJsonElement(
                            """{"id":$id,"symbol":"symbol-$id","text":${Json.encodeToString(
                                kotlinx.serialization.serializer<String>(),
                                text,
                            )},"sequence":${value.sequence},"active":${value.active}}""",
                        )
                        Json.parseToJsonElement(json.decodeToString()) shouldBe expected
                        output.reset()
                        NativeJsonUtf8Codec.write(writer = writer, value = native, output = output)
                        NativeJsonUtf8Codec.read(reader = reader, input = output.toByteArray().asByteSource()) shouldBe
                            native
                        Json.parseToJsonElement(output.toByteArray().decodeToString()) shouldBe expected
                    }
                }
            }

            should("accept reordered fields and escaped names across input chunks") {
                val bytes =
                    """ { "active" : false, "other": {"id": [null, 1.2e+3, true]}, "sequence": -7, "te\u0078t":"value", "symbol":"X", "id":42 } """
                        .encodeToByteArray()
                val expected = JsonUtf8Sample(
                    id = 42,
                    symbol = Utf8Str.allocateFromString("X"),
                    text = Utf8Str.allocateFromString("value"),
                    sequence = -7,
                    active = false,
                )
                for (chunk in listOf(1, 7, 31, 32768)) {
                    val input = object : ByteSource {
                        private val delegate = bytes.asByteSource()

                        override fun readAtMostTo(
                            sink: ByteArray,
                            offset: Int,
                            length: Int,
                        ): Int = delegate.readAtMostTo(
                            sink = sink,
                            offset = offset,
                            length = minOf(
                                length,
                                chunk,
                            ),
                        )
                    }
                    IdealJsonUtf8Codec.read(
                        reader = IdealJsonReader(vectorized = vectorized),
                        input = input,
                    ) shouldBe expected
                }
            }

            should("match whole field names and use the last duplicate value") {
                val expected = JsonUtf8Sample(
                    id = 2,
                    symbol = Utf8Str.allocateFromString("new"),
                    text = Utf8Str.allocateFromString("new"),
                    sequence = 2,
                    active = true,
                )
                val known = """"id":2,"symbol":"new","text":"new","sequence":2,"active":true"""
                for (name in listOf("id", "symbol", "text", "sequence", "active")) {
                    for (suffix in listOf("X", "XX", "sequence")) {
                        val json = """{"${name + suffix}":null,$known}"""
                        IdealJsonUtf8Codec.read(
                            reader = IdealJsonReader(vectorized = vectorized),
                            input = json.encodeToByteArray().asByteSource(),
                        ) shouldBe expected
                    }
                }
                val duplicate = """{"id":1,"symbol":"old","text":"old","sequence":1,"active":false,$known}"""
                IdealJsonUtf8Codec.read(
                    reader = IdealJsonReader(vectorized = vectorized),
                    input = duplicate.encodeToByteArray().asByteSource(),
                ) shouldBe expected
                NativeJsonUtf8Codec.read(
                    reader = IdealJsonReader(vectorized = vectorized),
                    input = duplicate.encodeToByteArray().asByteSource(),
                ) shouldBe JsonNativeSample(id = 2, symbol = "new", text = expected.text, sequence = 2, active = true)
            }

            should("reject malformed fields and trailing content") {
                for (json in listOf(
                    "{}",
                    """{"id":1,"symbol":"X","text":"Y","sequence":2,"active":true}true""",
                    """{"id":9223372036854775808,"symbol":"X","text":"Y","sequence":2,"active":true}""",
                    """{"id":1,"symbol":null,"text":"Y","sequence":2,"active":true}""",
                    """{"id":1,"symbol":"X","text":"Y","sequence":2,"active":1}""",
                )) {
                    shouldThrow<IllegalArgumentException> {
                        IdealJsonUtf8Codec.read(
                            reader = IdealJsonReader(vectorized = vectorized),
                            input = json.encodeToByteArray().asByteSource(),
                        )
                    }
                }
            }
        }
    }
})
