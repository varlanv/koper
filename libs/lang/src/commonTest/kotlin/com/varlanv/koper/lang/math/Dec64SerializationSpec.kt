package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
private data class Dec64Trade(
    val price: Dec64,
    val quantity: Dec64,
    val fee: Dec64? = null,
    val levels: List<Dec64> = emptyList(),
)

class Dec64SerializationSpec : BaseSpec({
    context("json") {
        should("encode as a decimal string") {
            Json.encodeToString(
                Dec64.serializer(),
                dec("1.25"),
            ) shouldBe "\"1.25\""
            Json.encodeToString(
                Dec64.serializer(),
                dec("-0.000000000000001"),
            ) shouldBe "\"-0.000000000000001\""
            Json.encodeToString(Dec64.serializer(), Dec64.ZERO) shouldBe "\"0\""
        }

        should("decode from a decimal string, normalising") {
            Json.decodeFromString(Dec64.serializer(), "\"1.250\"") shouldBe dec("1.25")
            Json.decodeFromString(Dec64.serializer(), "\"1E-8\"") shouldBe dec("0.00000001")
            Json.decodeFromString(Dec64.serializer(), "\"-0\"") shouldBe Dec64.ZERO
        }

        should("fail on malformed or out of range strings") {
            shouldThrow<NumberFormatException> {
                Json.decodeFromString(Dec64.serializer(), "\"abc\"")
            }
            shouldThrow<ArithmeticException> {
                Json.decodeFromString(Dec64.serializer(), "\"1E-19\"")
            }
            shouldThrow<SerializationException> {
                Json.decodeFromString(Dec64.serializer(), "null")
            }
        }

        should("round-trip inside a class with nullable and list fields") {
            val trade = Dec64Trade(
                price = dec("65432.12345678"),
                quantity = dec("0.001"),
                fee = dec("0.0000123"),
                levels = listOf(
                    dec("1"),
                    dec("2.5"),
                ),
            )
            val json = Json.encodeToString(Dec64Trade.serializer(), trade)
            json shouldBe """{"price":"65432.12345678","quantity":"0.001","fee":"0.0000123","levels":["1","2.5"]}"""
            Json.decodeFromString(Dec64Trade.serializer(), json) shouldBe trade
            val minimal = Json.decodeFromString(Dec64Trade.serializer(), """{"price":"1.50","quantity":"2"}""")
            minimal shouldBe Dec64Trade(
                price = dec("1.5"),
                quantity = dec("2"),
            )
            Json.decodeFromString(Dec64Trade.serializer(), """{"price":"1","quantity":"2","fee":null}""").fee shouldBe
                null
        }

        should("round-trip random values") {
            val r = Random(30)
            repeat(2_000) {
                val d = randomDec64(r)
                Json.decodeFromString(
                    Dec64.serializer(),
                    Json.encodeToString(
                        Dec64.serializer(),
                        d,
                    ),
                ) shouldBe d
            }
        }
    }
})
