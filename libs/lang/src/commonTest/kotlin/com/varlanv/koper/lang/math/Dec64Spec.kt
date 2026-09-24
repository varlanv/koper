package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class Dec64Spec : BaseSpec({
    context("construction") {
        should("wrap whole numbers") {
            Dec64(0).toString() shouldBe "0"
            Dec64(42).toString() shouldBe "42"
            Dec64(-42).toString() shouldBe "-42"
            Dec64(Dec64.MAX_COEFFICIENT).toString() shouldBe "288230376151711743"
            Dec64(Dec64.MIN_COEFFICIENT).toString() shouldBe "-288230376151711743"
        }

        should("reject whole numbers outside coefficient range") {
            shouldThrow<ArithmeticException> { Dec64(Dec64.MAX_COEFFICIENT + 1) }
            shouldThrow<ArithmeticException> { Dec64(Dec64.MIN_COEFFICIENT - 1) }
            shouldThrow<ArithmeticException> { Dec64(Long.MAX_VALUE) }
            shouldThrow<ArithmeticException> { Dec64(Long.MIN_VALUE) }
        }

        should("build from unscaled value and scale") {
            Dec64.fromLong(unscaled = 15, scale = 1).toString() shouldBe "1.5"
            Dec64.fromLong(unscaled = 1500, scale = 3).toString() shouldBe "1.5"
            Dec64.fromLong(unscaled = 15, scale = -2).toString() shouldBe "1500"
            Dec64.fromLong(unscaled = 0, scale = 18).toString() shouldBe "0"
            Dec64.fromLong(unscaled = -1, scale = 18).toString() shouldBe "-0.000000000000000001"
            Dec64.fromLong(unscaled = 1000, scale = 18).toString() shouldBe "0.000000000000001"
        }

        should("reject unscaled values that do not fit") {
            shouldThrow<ArithmeticException> { Dec64.fromLong(unscaled = 1, scale = 19) }
            shouldThrow<ArithmeticException> { Dec64.fromLong(unscaled = Dec64.MAX_COEFFICIENT + 1, scale = 0) }
            shouldThrow<ArithmeticException> { Dec64.fromLong(unscaled = Dec64.MAX_COEFFICIENT, scale = -1) }
            shouldThrow<ArithmeticException> { Dec64.fromLong(unscaled = Long.MAX_VALUE, scale = 5) }
            shouldThrow<ArithmeticException> { Dec64.fromLong(unscaled = Long.MIN_VALUE, scale = 0) }
        }

        should("normalise so that bit equality is numeric equality") {
            dec("1.5") shouldBe dec("1.50")
            dec("1.5") shouldBe dec("1.500000000000000")
            dec("100") shouldBe dec("100.000")
            dec("0") shouldBe dec("-0.000")
            dec("1.5").hashCode() shouldBe dec("1.500").hashCode()
            dec("1.5").bits shouldBe dec("1.500").bits
            dec("1.5") shouldNotBe dec("1.51")
            Dec64.ZERO.bits shouldBe 0L
        }

        should("expose coefficient and scale") {
            dec("123.456").coefficient shouldBe 123456L
            dec("123.456").scale shouldBe 3
            dec("-0.001").coefficient shouldBe -1L
            dec("-0.001").scale shouldBe 3
            dec("1000").coefficient shouldBe 1000L
            dec("1000").scale shouldBe 0
        }
    }

    context("parse") {
        should("parse plain decimals") {
            dec("0").toString() shouldBe "0"
            dec("-0").toString() shouldBe "0"
            dec("+7").toString() shouldBe "7"
            dec("007").toString() shouldBe "7"
            dec("1.").toString() shouldBe "1"
            dec(".5").toString() shouldBe "0.5"
            dec("1.50").toString() shouldBe "1.5"
            dec("100.00").toString() shouldBe "100"
            dec("0.000001").toString() shouldBe "0.000001"
            dec("0.000000000000001").toString() shouldBe "0.000000000000001"
            dec("-0.000000000000001").toString() shouldBe "-0.000000000000001"
            dec("1.000000000000000000000000000").toString() shouldBe "1"
            dec("0.0000000000000000000000000").toString() shouldBe "0"
            dec("288230376151711743").toString() shouldBe "288230376151711743"
            dec("-288230376151711743").toString() shouldBe "-288230376151711743"
            dec("2882303761.51711743").toString() shouldBe "2882303761.51711743"
            shouldThrow<ArithmeticException> { dec("288230376151711743000") }
        }

        should("parse exponent notation") {
            dec("1E-8").toString() shouldBe "0.00000001"
            dec("1e-8").toString() shouldBe "0.00000001"
            dec("1.5E3").toString() shouldBe "1500"
            dec("1.5E+3").toString() shouldBe "1500"
            dec("10E-1").toString() shouldBe "1"
            dec("123E0").toString() shouldBe "123"
            dec("0E5").toString() shouldBe "0"
            dec("0E-5").toString() shouldBe "0"
            dec("1.5E-14").toString() shouldBe "0.000000000000015"
            dec("15E-15").toString() shouldBe "0.000000000000015"
            dec("2.88230376151711743E17").toString() shouldBe "288230376151711743"
            dec("28823037615171101500E-3").toString() shouldBe "28823037615171101.5"
            dec("288230376151711743000E-3").toString() shouldBe "288230376151711743"
            dec("1000000000000000000000000E-24").toString() shouldBe "1"
            dec("2882303761517117430E-1") shouldBe Dec64.MAX_VALUE
            shouldThrow<ArithmeticException> { dec("2882303761517117440E-1") }
        }

        should("reject malformed input with NumberFormatException") {
            for (bad in listOf(
                "",
                "-",
                "+",
                ".",
                "-.",
                "abc",
                "1.2.3",
                "1,5",
                " 1",
                "1 ",
                "1e",
                "1e-",
                "1e+",
                "1e1.5",
                "e5",
                "--1",
                "0x10",
                "1_000",
                "NaN",
                "Infinity",
            )) {
                withClue(bad) {
                    shouldThrow<NumberFormatException> { Dec64.parseString(string = bad) }
                    shouldThrow<NumberFormatException> { Dec64.parseBytes(bytes = bad.encodeToByteArray()) }
                }
            }
        }

        should("reject out of range input with ArithmeticException") {
            for (bad in listOf(
                "288230376151711744",
                "-288230376151711744",
                "1E-19",
                "0.0000000000000000001",
                "1E19",
                "9223372036854775807",
                "12345678901234567890",
                "1E99999",
                "0.1234567890123456789",
            )) {
                withClue(bad) {
                    shouldThrow<ArithmeticException> { Dec64.parseString(string = bad) }
                    shouldThrow<ArithmeticException> { Dec64.parseBytes(bytes = bad.encodeToByteArray()) }
                }
            }
        }

        should("parse sub-ranges of strings and byte arrays") {
            val text = "xx-12.50yy"
            Dec64.parseString(string = text, from = 2, to = 8).toString() shouldBe "-12.5"
            Dec64.parseBytes(bytes = text.encodeToByteArray(), from = 2, to = 8).toString() shouldBe "-12.5"
            shouldThrow<NumberFormatException> { Dec64.parseString(string = text, from = 2, to = 9) }
            shouldThrow<NumberFormatException> { Dec64.parseString(string = text, from = 2, to = 2) }
            Dec64.parseString(string = "1.5", from = 0, to = 3) shouldBe dec("1.5")
            Dec64.parseString(string = "1.5", from = 0, to = 1) shouldBe dec("1")
        }
    }

    context("format") {
        should("write into a buffer at an offset") {
            val buf = ByteArray(Dec64.MAX_CHARS + 4) { 'x'.code.toByte() }
            val end = dec("-0.000000000000000001").writeTo(buf = buf, offset = 3)
            end shouldBe 3 + 21
            buf.decodeToString(3, end) shouldBe "-0.000000000000000001"
            buf[0] shouldBe 'x'.code.toByte()
            buf[end] shouldBe 'x'.code.toByte()
        }
    }

    context("comparison") {
        should("compare across scales") {
            (dec("1.5") < dec("1.51")) shouldBe true
            (dec("1.5") > dec("1.49")) shouldBe true
            (dec("1.5") <= dec("1.50")) shouldBe true
            (dec("1.5") >= dec("1.50")) shouldBe true
            (dec("-1") < dec("0.000000000000001")) shouldBe true
            (dec("288230376151711743") > dec("28823037615171174.3")) shouldBe true
            (dec("0.1") > dec("0.099999999999999")) shouldBe true
            dec("1.5").compareTo(dec("1.5")) shouldBe 0
            dec("0").compareTo(Dec64.ZERO) shouldBe 0
        }

        should("expose predicate helpers") {
            dec("0").isZero shouldBe true
            dec("0.000000000000001").isZero shouldBe false
            dec("-3").signum() shouldBe -1
            dec("0").signum() shouldBe 0
            dec("0.5").signum() shouldBe 1
        }

        should("negate and abs") {
            (-dec("1.5")).toString() shouldBe "-1.5"
            (-dec("-1.5")).toString() shouldBe "1.5"
            (-Dec64.ZERO) shouldBe Dec64.ZERO
            dec("-1.5").abs().toString() shouldBe "1.5"
            dec("1.5").abs().toString() shouldBe "1.5"
            (-Dec64.MAX_VALUE) shouldBe Dec64.MIN_VALUE
            (-Dec64.MIN_VALUE) shouldBe Dec64.MAX_VALUE
            Dec64.min(
                a = dec("1"),
                b = dec("2"),
            ) shouldBe dec("1")
            Dec64.max(
                a = dec("1"),
                b = dec("2"),
            ) shouldBe dec("2")
            Dec64.min(
                a = dec("-1"),
                b = dec("-2"),
            ) shouldBe dec("-2")
        }
    }

    context("addition and subtraction") {
        should("handle same scale") {
            (dec("1.5") + dec("1.5")).toString() shouldBe "3"
            (dec("0.1") + dec("0.2")).toString() shouldBe "0.3"
            (dec("1.5") - dec("1.5")) shouldBe Dec64.ZERO
            (dec("-0.5") + dec("0.5")) shouldBe Dec64.ZERO
            (dec("288230376151711743") - dec("288230376151711743")) shouldBe Dec64.ZERO
        }

        should("align different scales") {
            (dec("1") + dec("0.000000000000001")).toString() shouldBe "1.000000000000001"
            (dec("123456789.5") + dec("0.00000001")).toString() shouldBe "123456789.50000001"
            (dec("1000") - dec("0.001")).toString() shouldBe "999.999"
            (dec("0.001") - dec("1000")).toString() shouldBe "-999.999"
        }

        should("succeed when aligned intermediate overflows but the result fits") {
            (dec("30000000000000000") + dec("-10000000000000000.5")).toString() shouldBe "19999999999999999.5"
            (dec("30000000000000000") - dec("10000000000000000.5")).toString() shouldBe "19999999999999999.5"
            (dec("-30000000000000000") + dec("10000000000000000.5")).toString() shouldBe "-19999999999999999.5"
            shouldThrow<ArithmeticException> { dec("280000000000000000") + dec("0.5") }
        }

        should("throw when the result does not fit") {
            shouldThrow<ArithmeticException> { Dec64.MAX_VALUE + Dec64(1) }
            shouldThrow<ArithmeticException> { Dec64.MIN_VALUE - Dec64(1) }
            shouldThrow<ArithmeticException> { dec("288230376151711743") + dec("0.1") }
            shouldThrow<ArithmeticException> { dec("100000000000000000") + dec("0.01") }
            (Dec64.MAX_VALUE + Dec64.MIN_VALUE) shouldBe Dec64.ZERO
            (Dec64.MAX_VALUE - Dec64.MAX_VALUE) shouldBe Dec64.ZERO
        }
    }

    context("multiplication") {
        should("multiply exactly") {
            (dec("1.5") * dec("2")).toString() shouldBe "3"
            (dec("0.1") * dec("0.1")).toString() shouldBe "0.01"
            (dec("-0.5") * dec("0.2")).toString() shouldBe "-0.1"
            (dec("123456789") * dec("0.000000001")).toString() shouldBe "0.123456789"
            (dec("0.00000001") * dec("0.0000001")).toString() shouldBe "0.000000000000001"
            (dec("5") * Dec64.ZERO) shouldBe Dec64.ZERO
            (Dec64.ZERO * Dec64.MAX_VALUE) shouldBe Dec64.ZERO
            (Dec64.MAX_VALUE * Dec64(1)) shouldBe Dec64.MAX_VALUE
            (Dec64.MAX_VALUE * Dec64(-1)) shouldBe Dec64.MIN_VALUE
        }

        should("strip zeros when the raw product overflows 64 bits") {
            (dec("0.125") * dec("240000000000000000")).toString() shouldBe "30000000000000000"
            (dec("0.5") * dec("200000000000000000")).toString() shouldBe "100000000000000000"
            (dec("0.000000000000005") * dec("200000000000000000")).toString() shouldBe "1000"
            (dec("0.000000000000125") * dec("200000000000000000")).toString() shouldBe "25000"
            (dec("-0.000000000000125") * dec("200000000000000000")).toString() shouldBe "-25000"
            (dec("200000000000000000") * dec("-0.000000000000125")).toString() shouldBe "-25000"
            (dec("-0.000000000000125") * dec("-200000000000000000")).toString() shouldBe "25000"
        }

        should("throw when the result does not fit") {
            shouldThrow<ArithmeticException> { Dec64.MAX_VALUE * Dec64(2) }
            shouldThrow<ArithmeticException> { dec("0.0000000001") * dec("0.0000000001") }
            shouldThrow<ArithmeticException> { dec("1000000000") * dec("1000000000") }
            shouldThrow<ArithmeticException> { dec("0.3") * dec("0.000000000000000001") }
            (dec("0.3") * dec("0.00000000000000001")).toString() shouldBe "0.000000000000000003"
            shouldThrow<ArithmeticException> { dec("288230376151711743") * dec("1.1") }
        }
    }

    context("division") {
        should("round recurring quotients at the requested scale") {
            dec("1")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 4,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "0.3333"
            dec("2")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 4,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "0.6667"
            dec("2")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "1"
            dec("-2")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.CEILING,
                    ),
                )
                .toString() shouldBe "0"
            dec("-2")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.FLOOR,
                    ),
                )
                .toString() shouldBe "-1"
            dec("1")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 15,
                        rounding = Rounding.DOWN,
                    ),
                )
                .toString() shouldBe "0.333333333333333"
            dec("1")
                .div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = 15,
                        rounding = Rounding.UP,
                    ),
                )
                .toString() shouldBe "0.333333333333334"
        }

        should("reject invalid scale") {
            shouldThrow<IllegalArgumentException> {
                dec("1").div(
                    other = dec("2"),
                    ctx = Dec64Context(
                        scale = -1,
                        rounding = Rounding.HALF_UP,
                    ),
                )
            }
            shouldThrow<IllegalArgumentException> {
                dec("1").div(
                    other = dec("2"),
                    ctx = Dec64Context(
                        scale = 19,
                        rounding = Rounding.HALF_UP,
                    ),
                )
            }
        }

        should("reject division by zero") {
            for (mode in Rounding.entries) {
                shouldThrow<ArithmeticException> {
                    dec("1").div(
                        other = Dec64.ZERO,
                        ctx = Dec64Context(
                            scale = 2,
                            rounding = mode,
                        ),
                    )
                }
                shouldThrow<ArithmeticException> {
                    Dec64.ZERO.div(
                        other = Dec64.ZERO,
                        ctx = Dec64Context(
                            scale = 2,
                            rounding = mode,
                        ),
                    )
                }
            }
        }

        should("divide exactly with UNNECESSARY") {
            dec("1")
                .div(
                    other = dec("4"),
                    ctx = Dec64Context(
                        scale = Dec64.MAX_SCALE,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "0.25"
            dec("1")
                .div(
                    other = dec("4"),
                    ctx = Dec64Context(
                        scale = 2,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "0.25"
            dec("10")
                .div(
                    other = dec("0.5"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "20"
            dec("3")
                .div(
                    other = dec("1.5"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "2"
            dec("0.005328")
                .div(
                    other = dec("0.000000000000001"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "5328000000000"
            dec("-7.5")
                .div(
                    other = dec("2.5"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "-3"
            Dec64.ZERO.div(
                other = dec("3"),
                ctx = Dec64Context(
                    scale = 0,
                    rounding = Rounding.UNNECESSARY,
                ),
            ) shouldBe Dec64.ZERO
            shouldThrow<ArithmeticException> {
                dec("1").div(
                    other = dec("3"),
                    ctx = Dec64Context(
                        scale = Dec64.MAX_SCALE,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
            }
            shouldThrow<ArithmeticException> {
                dec("1").div(
                    other = dec("4"),
                    ctx = Dec64Context(
                        scale = 1,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
            }
            shouldThrow<ArithmeticException> {
                dec("1").div(
                    other = dec("1024"),
                    ctx = Dec64Context(
                        scale = 9,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
            }
            dec("1")
                .div(
                    other = dec("1024"),
                    ctx = Dec64Context(
                        scale = 10,
                        rounding = Rounding.UNNECESSARY,
                    ),
                )
                .toString() shouldBe "0.0009765625"
        }

        should("handle quotients far below the requested scale") {
            dec("1").div(
                other = dec("1000000000000000"),
                ctx = Dec64Context(
                    scale = 5,
                    rounding = Rounding.DOWN,
                ),
            ) shouldBe Dec64.ZERO
            dec("1")
                .div(
                    other = dec("1000000000000000"),
                    ctx = Dec64Context(
                        scale = 5,
                        rounding = Rounding.UP,
                    ),
                )
                .toString() shouldBe "0.00001"
            dec("-1").div(
                other = dec("1000000000000000"),
                ctx = Dec64Context(
                    scale = 5,
                    rounding = Rounding.CEILING,
                ),
            ) shouldBe Dec64.ZERO
            dec("-1")
                .div(
                    other = dec("1000000000000000"),
                    ctx = Dec64Context(
                        scale = 5,
                        rounding = Rounding.FLOOR,
                    ),
                )
                .toString() shouldBe "-0.00001"
            dec("1").div(
                other = dec("1000000000000000"),
                ctx = Dec64Context(
                    scale = 5,
                    rounding = Rounding.HALF_UP,
                ),
            ) shouldBe Dec64.ZERO
            dec("0.000000000000001")
                .div(
                    other = dec("288230376151711743"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.UP,
                    ),
                )
                .toString() shouldBe "1"
            dec("0.000000000000001").div(
                other = dec("288230376151711743"),
                ctx = Dec64Context(
                    scale = 0,
                    rounding = Rounding.HALF_UP,
                ),
            ) shouldBe Dec64.ZERO
        }

        should("succeed when rounding collapses a too-long quotient") {
            dec("288230376151711742")
                .div(
                    other = dec("288.230376151711743"),
                    ctx = Dec64Context(
                        scale = 2,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "1000000000000000"
            dec("879476.61")
                .div(
                    other = dec("0.3"),
                    ctx = Dec64Context(
                        scale = 15,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "2931588.7"
            dec("199999999999999999")
                .div(
                    other = dec("2"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.HALF_UP,
                    ),
                )
                .toString() shouldBe "100000000000000000"
            shouldThrow<ArithmeticException> {
                dec("288230376151711742").div(
                    other = dec("288.230376151711743"),
                    ctx = Dec64Context(
                        scale = 5,
                        rounding = Rounding.DOWN,
                    ),
                )
            }
            shouldThrow<ArithmeticException> {
                dec("199999999999999999").div(
                    other = dec("2"),
                    ctx = Dec64Context(
                        scale = 1,
                        rounding = Rounding.HALF_UP,
                    ),
                )
            }
        }

        should("throw when the quotient does not fit") {
            shouldThrow<ArithmeticException> {
                Dec64.MAX_VALUE.div(
                    other = dec("0.1"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.DOWN,
                    ),
                )
            }
            shouldThrow<ArithmeticException> {
                dec("1000").div(
                    other = dec("0.000000000000001"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.DOWN,
                    ),
                )
            }
            dec("1")
                .div(
                    other = dec("0.000000000000001"),
                    ctx = Dec64Context(
                        scale = 5,
                        rounding = Rounding.DOWN,
                    ),
                )
                .toString() shouldBe "1000000000000000"
            dec("123456789.123456789")
                .div(
                    other = dec("7"),
                    ctx = Dec64Context(
                        scale = 15,
                        rounding = Rounding.DOWN,
                    ),
                )
                .toString() shouldBe "17636684.160493827"
            shouldThrow<ArithmeticException> {
                dec("12345678.9").div(
                    other = dec("7"),
                    ctx = Dec64Context(
                        scale = 15,
                        rounding = Rounding.DOWN,
                    ),
                )
            }
            shouldThrow<ArithmeticException> { dec("0.142857142857143") * dec("1234.5") }
            dec("1")
                .div(
                    other = dec("0.000000000000001"),
                    ctx = Dec64Context(
                        scale = 0,
                        rounding = Rounding.DOWN,
                    ),
                )
                .toString() shouldBe "1000000000000000"
        }
    }

    context("conversion") {
        should("convert whole and fractional values to double") {
            dec("123.456").toDouble() shouldBe 123.456
            dec("-0.5").toDouble() shouldBe -0.5
            dec("0").toDouble() shouldBe 0.0
            dec("0.1").toDouble() shouldBe 0.1
            dec("0.000000000000001").toDouble() shouldBe 1e-15
        }

        should("convert to long exactly") {
            dec("42").toLongExact() shouldBe 42L
            dec("-42").toLongExact() shouldBe -42L
            dec("42.000").toLongExact() shouldBe 42L
            Dec64.ZERO.toLongExact() shouldBe 0L
            shouldThrow<ArithmeticException> { dec("42.5").toLongExact() }
        }
    }
})
