package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.random.Random

class Dec64JvmSpec : BaseSpec({
    context("construction") {
        should("build from BigDecimal") {
            Dec64.fromDecimal(BigDecimal("1.50")).toString() shouldBe "1.5"
            Dec64.fromDecimal(BigDecimal("1E+3")).toString() shouldBe "1000"
            Dec64.fromDecimal(BigDecimal("0E-10")).toString() shouldBe "0"
            Dec64.fromDecimal(BigDecimal.ZERO) shouldBe Dec64.ZERO
            shouldThrow<ArithmeticException> { Dec64.fromDecimal(BigDecimal("1E-19")) }
            shouldThrow<ArithmeticException> { Dec64.fromDecimal(BigDecimal("288230376151711744")) }
            shouldThrow<ArithmeticException> { Dec64.fromDecimal(BigDecimal("12345678901234567890")) }
        }

        should("round-trip random values through of(BigDecimal)") {
            val r = Random(1)
            repeat(5_000) {
                val bd = Dec64Gen.representable(r)
                Dec64.fromDecimal(bd).shouldEqualDecimal(bd)
                Dec64.fromLong(unscaled = bd.unscaledValue().toLong(), scale = bd.scale()).shouldEqualDecimal(bd)
            }
        }

        should("reject random values that do not fit") {
            val r = Random(2)
            var rejected = 0
            repeat(5_000) {
                val bd = Dec64Gen.any(r)
                if (!Dec64Gen.fits(bd)) {
                    shouldThrow<ArithmeticException> { Dec64.fromDecimal(bd) }
                    rejected++
                } else {
                    Dec64.fromDecimal(bd).shouldEqualDecimal(bd)
                }
            }
            (rejected > 1_000) shouldBe true
        }
    }

    context("parse") {
        should("match BigDecimal on random representable input in many textual forms") {
            val r = Random(3)
            repeat(5_000) {
                val bd = Dec64Gen.representable(r)
                val expected = Dec64.fromDecimal(bd)
                val forms = listOf(
                    bd.toPlainString(),
                    bd.toString(),
                    bd.toEngineeringString(),
                    bd.setScale(bd.scale() + r.nextInt(0, 4)).toPlainString(),
                    "+" + bd.abs().toPlainString(),
                    r.nextInt(0, 5).let { k -> bd.movePointLeft(k).toPlainString() + "E" + k },
                    r.nextInt(0, 5).let { k -> bd.movePointRight(k).toPlainString() + "E-" + k },
                    "0000" + bd.abs().toPlainString(),
                )
                for (form in forms) {
                    val text = if (bd.signum() < 0 && !form.startsWith("-")) {
                        "-$form"
                    } else {
                        form
                    }
                    val normalizedText = if (form.startsWith("+") && bd.signum() < 0) {
                        "-" + form.drop(1)
                    } else {
                        text
                    }
                    withClue("form '$normalizedText' of ${bd.toPlainString()}") {
                        Dec64.parseString(string = normalizedText) shouldBe expected
                        Dec64.parseBytes(bytes = normalizedText.encodeToByteArray()) shouldBe expected
                    }
                }
            }
        }

        should("agree with BigDecimal on random arbitrary input") {
            val r = Random(4)
            repeat(5_000) {
                val bd = Dec64Gen.any(r)
                val text = if (r.nextBoolean()) {
                    bd.toPlainString()
                } else {
                    bd.toString()
                }
                expectOracle(expected = bd, clue = text) { Dec64.parseString(string = text) }
                expectOracle(expected = bd, clue = text) { Dec64.parseBytes(bytes = text.encodeToByteArray()) }
            }
        }
    }

    context("format") {
        should("never exceed MAX_CHARS and match toPlainString on random values") {
            val r = Random(5)
            val buf = ByteArray(Dec64.MAX_CHARS)
            repeat(10_000) {
                val bd = Dec64Gen.representable(r)
                val d = Dec64.fromDecimal(bd)
                val len = d.writeTo(buf = buf, offset = 0)
                (len <= Dec64.MAX_CHARS) shouldBe true
                buf.decodeToString(0, len) shouldBe Dec64Gen.normalized(bd).toPlainString()
                d.toString() shouldBe Dec64Gen.normalized(bd).toPlainString()
            }

        }
    }

    context("comparison") {
        should("match BigDecimal ordering on random pairs") {
            val r = Random(6)
            repeat(20_000) {
                val a = Dec64Gen.representable(r)
                val b = Dec64Gen.representable(r)
                val da = Dec64.fromDecimal(a)
                val db = Dec64.fromDecimal(b)
                withClue("${a.toPlainString()} vs ${b.toPlainString()}") {
                    da.compareTo(db) shouldBe a.compareTo(b)
                    db.compareTo(da) shouldBe b.compareTo(a)
                    (da == db) shouldBe (a.compareTo(b) == 0)
                    Dec64.min(a = da, b = db).toBigDecimal().compareTo(a.min(b)) shouldBe 0
                    Dec64.max(a = da, b = db).toBigDecimal().compareTo(a.max(b)) shouldBe 0
                    da.signum() shouldBe a.signum()
                    (-da).toBigDecimal().compareTo(a.negate()) shouldBe 0
                    da.abs().toBigDecimal().compareTo(a.abs()) shouldBe 0
                }
            }
        }
    }

    context("addition and subtraction") {
        should("match BigDecimal on random pairs") {
            val r = Random(7)
            var throws = 0
            repeat(20_000) {
                val a = Dec64Gen.representable(r)
                val b = Dec64Gen.representable(r)
                val da = Dec64.fromDecimal(a)
                val db = Dec64.fromDecimal(b)
                val sum = a.add(b)
                val diff = a.subtract(b)
                if (!Dec64Gen.fits(sum)) {
                    throws++
                }
                expectOracle(expected = sum, clue = "${a.toPlainString()} + ${b.toPlainString()}") { da + db }
                expectOracle(expected = sum, clue = "${b.toPlainString()} + ${a.toPlainString()}") { db + da }
                expectOracle(expected = diff, clue = "${a.toPlainString()} - ${b.toPlainString()}") { da - db }
                expectOracle(expected = diff.negate(), clue = "${b.toPlainString()} - ${a.toPlainString()}") { db - da }
            }
            (throws > 100) shouldBe true
        }
    }

    context("multiplication") {
        should("match BigDecimal on random pairs") {
            val r = Random(8)
            var throws = 0
            repeat(20_000) {
                val a = Dec64Gen.representable(r)
                val b = Dec64Gen.representable(r)
                val da = Dec64.fromDecimal(a)
                val db = Dec64.fromDecimal(b)
                val product = a.multiply(b)
                if (!Dec64Gen.fits(product)) {
                    throws++
                }
                expectOracle(expected = product, clue = "${a.toPlainString()} * ${b.toPlainString()}") { da * db }
                expectOracle(expected = product, clue = "${b.toPlainString()} * ${a.toPlainString()}") { db * da }
            }
            (throws > 100) shouldBe true
        }

        should("match BigDecimal on products that need trailing zero stripping") {
            val r = Random(9)
            repeat(5_000) {
                val a = BigDecimal(
                    BigInteger.valueOf(r.nextLong(1, 1_000_000)) * BigInteger.TWO.pow(r.nextInt(0, 20)),
                    r.nextInt(
                        0,
                        16,
                    ),
                )
                val b = BigDecimal(
                    BigInteger.valueOf(r.nextLong(1, 1_000_000)) * BigInteger.valueOf(5).pow(r.nextInt(0, 20)),
                    r.nextInt(
                        0,
                        16,
                    ),
                )
                if (!Dec64Gen.fits(a) || !Dec64Gen.fits(b)) {
                    return@repeat
                }
                expectOracle(
                    expected = a.multiply(b),
                    clue = "${a.toPlainString()} * ${b.toPlainString()}",
                ) { Dec64.fromDecimal(a) * Dec64.fromDecimal(b) }
            }
        }
    }

    context("division") {
        should("round in every mode like BigDecimal") {
            val cases = listOf("5.5", "2.5", "1.6", "1.1", "1.0", "-1.0", "-1.1", "-1.6", "-2.5", "-5.5")
            for (text in cases) {
                for (mode in RoundingMode.entries.filter { it != RoundingMode.UNNECESSARY }) {
                    val expected = BigDecimal(text).divide(BigDecimal.ONE, 0, mode)
                    withClue("$text mode $mode") {
                        dec(text)
                            .div(
                                other = Dec64(1),
                                ctx = Dec64Context(
                                    scale = 0,
                                    rounding = Rounding.valueOf(mode.name),
                                ),
                            )
                            .shouldEqualDecimal(expected)
                        dec(text)
                            .div(
                                other = dec("1.000"),
                                ctx = Dec64Context(
                                    scale = 0,
                                    rounding = Rounding.valueOf(mode.name),
                                ),
                            )
                            .shouldEqualDecimal(expected)
                    }
                }
            }

        }

        should("match BigDecimal on random pairs for every scale and mode") {
            val r = Random(10)
            var throws = 0
            repeat(20_000) {
                val a = Dec64Gen.representable(r)
                var b = Dec64Gen.representable(r)
                if (b.signum() == 0) {
                    b = BigDecimal.ONE
                }
                val da = Dec64.fromDecimal(a)
                val db = Dec64.fromDecimal(b)
                val scale = r.nextInt(0, Dec64.MAX_SCALE + 1)
                val mode = RoundingMode.entries[r.nextInt(RoundingMode.entries.size)]
                val clue = "${a.toPlainString()} / ${b.toPlainString()} scale=$scale mode=$mode"
                val expected = runCatching { a.divide(b, scale, mode) }
                withClue(clue) {
                    val exp = expected.getOrNull()
                    if (exp == null) {
                        (expected.exceptionOrNull() is ArithmeticException) shouldBe true
                        shouldThrow<ArithmeticException> {
                            da.div(
                                other = db,
                                ctx = Dec64Context(
                                    scale = scale,
                                    rounding = Rounding.valueOf(mode.name),
                                ),
                            )
                        }
                        throws++
                    } else {
                        if (!Dec64Gen.fits(exp)) {
                            throws++
                        }
                        expectOracle(expected = exp, clue = clue) {
                            da.div(
                                other = db,
                                ctx = Dec64Context(
                                    scale = scale,
                                    rounding = Rounding.valueOf(mode.name),
                                ),
                            )
                        }
                    }
                }
            }
            (throws > 100) shouldBe true
        }

        should("match BigDecimal on random small operands where results fit") {
            val r = Random(11)
            repeat(10_000) {
                val a = BigDecimal(
                    BigInteger.valueOf(r.nextLong(-1_000_000, 1_000_000)),
                    r.nextInt(
                        0,
                        6,
                    ),
                )
                val b = BigDecimal(
                    BigInteger.valueOf(r.nextLong(1, 1_000_000)),
                    r.nextInt(
                        0,
                        6,
                    ),
                )
                val scale = r.nextInt(0, 12)
                val mode = RoundingMode.entries.filter { it != RoundingMode.UNNECESSARY }.random(r)
                val expected = a.divide(b, scale, mode)
                expectOracle(
                    expected = expected,
                    clue = "${a.toPlainString()} / ${b.toPlainString()} scale=$scale mode=$mode",
                ) {
                    Dec64.fromDecimal(a).div(
                        other = Dec64.fromDecimal(b),
                        ctx = Dec64Context(
                            scale = scale,
                            rounding = Rounding.valueOf(mode.name),
                        ),
                    )
                }
            }
        }
    }

    context("conversion") {
        should("convert to double like BigDecimal") {
            dec("288230376151711743").toDouble() shouldBe BigDecimal("288230376151711743").toDouble()
            dec("28823037615.1711743").toDouble() shouldBe BigDecimal("28823037615.1711743").toDouble()
            val r = Random(12)
            repeat(10_000) {
                val bd = Dec64Gen.representable(r)
                withClue(bd.toPlainString()) { Dec64.fromDecimal(bd).toDouble() shouldBe bd.toDouble() }
            }
        }

        should("convert to BigDecimal") {
            dec("1.50").toBigDecimal() shouldBe BigDecimal("1.5")
            dec("-0.001").toBigDecimal() shouldBe BigDecimal("-0.001")
            dec("1000").toBigDecimal() shouldBe BigDecimal("1000")
            Dec64.ZERO.toBigDecimal() shouldBe BigDecimal.ZERO
        }
    }
})
