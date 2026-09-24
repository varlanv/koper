package com.varlanv.koper.json

import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class IdealJsonCodecSpec : BaseSpec({
    for (vectorized in listOf(false, true)) {
        context(if (vectorized) "vector path" else "scalar path") {
            should("roundtrip strings and numbers through both codecs") {
                val reader = IdealJsonReader(vectorized = vectorized)
                val writer = IdealJsonWriter(vectorized = vectorized)
                for (id in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
                    for (text in listOf("", "ASCII", "é中🙂", "\"\\\n\t\u0000", "large".repeat(9000))) {
                        val value = JsonUtf8Sample(
                            id,
                            Utf8Str.allocateFromString("symbol-$id"),
                            Utf8Str.allocateFromString(text),
                            if (id < 0) Int.MIN_VALUE else Int.MAX_VALUE,
                            id % 2 == 0L,
                        )
                        val native = JsonNativeSample(id, "symbol-$id", value.text, value.sequence, value.active)
                        val output = ByteArrayJsonOutput()
                        IdealJsonUtf8Codec.write(writer, value, output)
                        val json = output.toByteArray()
                        IdealJsonUtf8Codec.read(reader, ByteArrayJsonInput(json)) shouldBe value
                        val expected = Json.parseToJsonElement(
                            """{"id":$id,"symbol":"symbol-$id","text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)},"sequence":${value.sequence},"active":${value.active}}"""
                        )
                        Json.parseToJsonElement(json.decodeToString()) shouldBe expected
                        output.reset()
                        NativeJsonUtf8Codec.write(writer, native, output)
                        NativeJsonUtf8Codec.read(reader, ByteArrayJsonInput(output.toByteArray())) shouldBe native
                        Json.parseToJsonElement(output.toByteArray().decodeToString()) shouldBe expected
                    }
                }
            }

            should("accept reordered fields and escaped names across input chunks") {
                val bytes = """ { "active" : false, "other": {"id": [null, 1.2e+3, true]}, "sequence": -7, "te\u0078t":"value", "symbol":"X", "id":42 } """.encodeToByteArray()
                val expected = JsonUtf8Sample(
                    42,
                    Utf8Str.allocateFromString("X"),
                    Utf8Str.allocateFromString("value"),
                    -7,
                    false,
                )
                for (chunk in listOf(1, 7, 31, 32768)) {
                    val input = object : JsonInput {
                        private val delegate = ByteArrayJsonInput(bytes)
                        override fun read(destination: ByteArray, offset: Int, length: Int): Int =
                            delegate.read(destination, offset, minOf(length, chunk))
                        override fun read(): Int = delegate.read()
                    }
                    IdealJsonUtf8Codec.read(IdealJsonReader(vectorized = vectorized), input) shouldBe expected
                }
            }

            should("match whole field names and use the last duplicate value") {
                val expected = JsonUtf8Sample(
                    2,
                    Utf8Str.allocateFromString("new"),
                    Utf8Str.allocateFromString("new"),
                    2,
                    true,
                )
                val known = """"id":2,"symbol":"new","text":"new","sequence":2,"active":true"""
                for (name in listOf("id", "symbol", "text", "sequence", "active")) {
                    for (suffix in listOf("X", "XX", "sequence")) {
                        val json = """{"${name + suffix}":null,$known}"""
                        IdealJsonUtf8Codec.read(
                            IdealJsonReader(vectorized = vectorized),
                            ByteArrayJsonInput(json.encodeToByteArray()),
                        ) shouldBe expected
                    }
                }
                val duplicate = """{"id":1,"symbol":"old","text":"old","sequence":1,"active":false,$known}"""
                IdealJsonUtf8Codec.read(
                    IdealJsonReader(vectorized = vectorized),
                    ByteArrayJsonInput(duplicate.encodeToByteArray()),
                ) shouldBe expected
                NativeJsonUtf8Codec.read(
                    IdealJsonReader(vectorized = vectorized),
                    ByteArrayJsonInput(duplicate.encodeToByteArray()),
                ) shouldBe JsonNativeSample(2, "new", expected.text, 2, true)
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
                        IdealJsonUtf8Codec.read(IdealJsonReader(vectorized = vectorized), ByteArrayJsonInput(json.encodeToByteArray()))
                    }
                }
            }
        }
    }
})
