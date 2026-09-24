package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class Dec64BehaviorSpec : BaseSpec({
    context("double construction") {
        should("construct finite whole and fractional values") {
            for (text in listOf("1", "-42", "0.1", "-0.5", "123.456", "0.000000000000001", "1000000000000000")) {
                withClue(text) {
                    Dec64.fromDouble(text.toDouble()) shouldBe dec(text)
                }
            }
        }

        should("canonicalize positive and negative zero") {
            Dec64.fromDouble(0.0) shouldBe Dec64.ZERO
            Dec64.fromDouble(-0.0).bits shouldBe 0L
        }

        should("reject non-finite and unrepresentable values") {
            for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, 1e-19, -1e-19)) {
                withClue(value) {
                    shouldThrow<ArithmeticException> { Dec64.fromDouble(value) }
                }
            }
        }

        should("round-trip exactly representable binary fractions") {
            for (numerator in -100..100) {
                for (denominator in listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)) {
                    val value = numerator.toDouble() / denominator
                    withClue("$numerator / $denominator") {
                        Dec64.fromDouble(value).toDouble() shouldBe value
                    }
                }
            }
        }
    }

    context("rounding") {
        should("use maximum scale and half even for DECIMAL64") {
            Dec64Context.DECIMAL64 shouldBe Dec64Context(18, Rounding.HALF_EVEN)
            Dec64.ONE.div(dec("6"), Dec64Context.DECIMAL64) shouldBe dec("0.166666666666666667")
        }

        should("round positive and negative fractions in every mode") {
            val inputs = listOf("5.5", "2.5", "1.6", "1.1", "1", "-1", "-1.1", "-1.6", "-2.5", "-5.5")
            val expected = mapOf(
                Rounding.UP to listOf(6, 3, 2, 2, 1, -1, -2, -2, -3, -6),
                Rounding.DOWN to listOf(5, 2, 1, 1, 1, -1, -1, -1, -2, -5),
                Rounding.CEILING to listOf(6, 3, 2, 2, 1, -1, -1, -1, -2, -5),
                Rounding.FLOOR to listOf(5, 2, 1, 1, 1, -1, -2, -2, -3, -6),
                Rounding.HALF_UP to listOf(6, 3, 2, 1, 1, -1, -1, -2, -3, -6),
                Rounding.HALF_DOWN to listOf(5, 2, 2, 1, 1, -1, -1, -2, -2, -5),
                Rounding.HALF_EVEN to listOf(6, 2, 2, 1, 1, -1, -1, -2, -2, -6),
            )
            for ((mode, results) in expected) {
                for ((index, input) in inputs.withIndex()) {
                    withClue("$input $mode") {
                        dec(input).div(Dec64.ONE, Dec64Context(0, mode)) shouldBe Dec64(results[index].toLong())
                        (-dec(input)).div(-Dec64.ONE, Dec64Context(0, mode)) shouldBe Dec64(results[index].toLong())
                    }
                }
            }
        }
    }

    context("format and parse") {
        should("format and parse every scale with independent decimal text") {
            val random = Random(31)
            for (scale in 0..Dec64.MAX_SCALE) {
                repeat(100) {
                    val coefficient = random.nextLong(1, Dec64.MAX_COEFFICIENT + 1)
                    val digits = coefficient.toString().padStart(scale + 1, '0')
                    val text = if (scale == 0) digits else digits.dropLast(scale) + "." + digits.takeLast(scale)
                    for (sign in listOf(1, -1)) {
                        val expected = (if (sign < 0) "-" else "") + text
                        val value = Dec64.fromLong(coefficient * sign, scale)
                        withClue(expected) {
                            dec(expected) shouldBe value
                            val bytes = ByteArray(Dec64.MAX_CHARS + 4) { 35 }
                            val end = value.writeTo(bytes, 2)
                            bytes.take(2) shouldBe listOf<Byte>(35, 35)
                            bytes.drop(end).all { it == 35.toByte() } shouldBe true
                            val normalized = if (scale == 0) expected else expected.trimEnd('0').trimEnd('.')
                            bytes.decodeToString(2, end) shouldBe normalized
                            value.toString() shouldBe normalized
                            Dec64.parseBytes(bytes, 2, end) shouldBe value
                        }
                    }
                }
            }
        }

        should("reject non-ASCII decimal digits and bytes") {
            for (text in listOf("١", "１２", "1\u0000", "1\n", "1e++2", "+-1", "1e2e3")) {
                shouldThrow<NumberFormatException> { dec(text) }
                shouldThrow<NumberFormatException> { Dec64.parseBytes(text.encodeToByteArray()) }
            }
            shouldThrow<NumberFormatException> { Dec64.parseBytes(byteArrayOf(-1)) }
        }

        should("format boundary values within MAX_CHARS") {
            for (text in listOf("288230376151711743", "-288230376151711743", "-0.000000000000000001", "-28823037615171.1743")) {
                val bytes = ByteArray(Dec64.MAX_CHARS)
                val end = dec(text).writeTo(bytes, 0)
                end shouldBe text.length
                bytes.decodeToString(0, end) shouldBe text
            }
        }
    }
})
