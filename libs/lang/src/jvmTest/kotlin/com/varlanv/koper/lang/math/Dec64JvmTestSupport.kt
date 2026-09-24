package com.varlanv.koper.lang.math

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.random.Random

internal object Dec64Gen {
    private val maxCoefficient = BigInteger.valueOf(Dec64.MAX_COEFFICIENT)

    fun normalized(bd: BigDecimal): BigDecimal {
        val stripped = bd.stripTrailingZeros()
        return if (stripped.scale() < 0) {
            stripped.setScale(0)
        } else {
            stripped
        }
    }

    fun fits(bd: BigDecimal): Boolean {
        val n = normalized(bd)
        return n.scale() in 0..Dec64.MAX_SCALE && n.unscaledValue().abs() <= maxCoefficient
    }

    fun digits(r: Random, count: Int): BigInteger {
        val sb = StringBuilder(count)
        sb.append(r.nextInt(1, 10))
        repeat(count - 1) { sb.append(r.nextInt(10)) }
        return BigInteger(sb.toString())
    }

    fun representable(r: Random): BigDecimal {
        while (true) {
            val candidate = when (r.nextInt(6)) {
                0 -> BigDecimal(
                    BigInteger.valueOf(r.nextLong(-1000, 1000)),
                    r.nextInt(
                        0,
                        4,
                    ),
                )
                1 -> BigDecimal(
                    digits(
                        r = r,
                        count = r.nextInt(
                            1,
                            10,
                        ),
                    ),
                    r.nextInt(
                        2,
                        9,
                    ),
                )
                2 -> BigDecimal(
                    digits(
                        r = r,
                        count = r.nextInt(
                            1,
                            6,
                        ),
                    ),
                    r.nextInt(
                        8,
                        16,
                    ),
                )
                3 -> BigDecimal(
                    maxCoefficient - BigInteger.valueOf(r.nextLong(0, 1000)),
                    r.nextInt(
                        0,
                        16,
                    ),
                )
                else -> BigDecimal(
                    digits(
                        r = r,
                        count = r.nextInt(
                            1,
                            19,
                        ),
                    ),
                    r.nextInt(
                        0,
                        16,
                    ),
                )
            }
            val signed = if (r.nextBoolean()) {
                candidate.negate()
            } else {
                candidate
            }
            if (fits(signed)) {
                return signed
            }
        }
    }

    fun any(r: Random): BigDecimal {
        val candidate = BigDecimal(
            digits(
                r = r,
                count = r.nextInt(
                    1,
                    24,
                ),
            ),
            r.nextInt(
                -3,
                22,
            ),
        )
        return if (r.nextBoolean()) {
            candidate.negate()
        } else {
            candidate
        }
    }
}

internal fun Dec64.shouldEqualDecimal(expected: BigDecimal) {
    withClue("Dec64 $this vs BigDecimal ${expected.toPlainString()}") {
        toBigDecimal().compareTo(expected) shouldBe 0
        toString() shouldBe Dec64Gen.normalized(expected).toPlainString()
    }
}

internal inline fun expectOracle(
    expected: BigDecimal,
    clue: String,
    actual: () -> Dec64,
) {
    withClue(clue) {
        if (Dec64Gen.fits(expected)) {
            actual().shouldEqualDecimal(expected)
        } else {
            shouldThrow<ArithmeticException> { actual() }
        }
    }
}
