package com.varlanv.koper.lang.date

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun randomMillis(r: Random): Long {
    return when (r.nextInt(5)) {
        0 -> r.nextLong(-1_000_000L, 1_000_000L)
        1 -> r.nextLong(1_500_000_000_000L, 2_000_000_000_000L)
        2 -> r.nextLong(-62_135_596_800_000L, 0L)
        3 -> r.nextLong(Long.MIN_VALUE / 4, Long.MAX_VALUE / 4)
        else -> r.nextLong()
    }
}

private fun randomDuration(r: Random): Duration {
    return when (r.nextInt(4)) {
        0 -> r.nextLong(-1_000_000L, 1_000_000L).milliseconds
        1 -> r.nextLong(-100_000L, 100_000L).seconds
        2 -> r.nextLong(-10_000L, 10_000L).days
        else -> r.nextLong(-1_000_000_000_000L, 1_000_000_000_000L).nanoseconds
    }
}

private fun utcDayStart(millis: Long): Long {
    return java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().let { it.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
}

class InstJvmSpec : BaseSpec({
    should("round-trip through Java instants") {
        val random = Random(50)
        repeat(20_000) {
            val millis = randomMillis(random)
            val inst = Inst.fromMillis(millis)
            withClue(millis) {
                inst.toJavaInstant() shouldBe java.time.Instant.ofEpochMilli(millis)
                Inst.from(java.time.Instant.ofEpochMilli(millis)) shouldBe inst
            }
        }
    }

    context("day boundaries") {
        should("match LocalDate based helpers on random instants") {
            val r = Random(51)
            repeat(20_000) {
                val millis = when (r.nextInt(3)) {
                    0 -> r.nextLong(-100L * MILLIS_DAY, 100L * MILLIS_DAY)
                    1 -> r.nextLong(1_500_000_000_000L, 2_000_000_000_000L)
                    else -> r.nextLong(-300_000L * MILLIS_DAY, 300_000L * MILLIS_DAY)
                }
                val inst = Inst(millis)
                withClue(millis) {
                    val date = inst.toJavaInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    inst.atStartOfDay().millis shouldBe dayStartMillis(date)
                    inst.atEndOfDay().millis shouldBe dayEndMillis(date)
                    inst.atStartOfDay().millis shouldBe utcDayStart(millis)
                }
            }
        }

    }

    context("arithmetic and comparison") {
        should("match Instant on random duration arithmetic") {
            val r = Random(52)
            repeat(50_000) {
                val millis = randomMillis(r)
                val d = randomDuration(r)
                val inst = Inst(millis)
                val instant = Instant.fromEpochMilliseconds(millis)
                val wholeMillis = d.inWholeMilliseconds.milliseconds
                withClue("$millis $d") {
                    val plus = runCatching { inst + d }
                    val minus = runCatching { inst - d }
                    val expectedPlus = runCatching { Math.addExact(millis, d.inWholeMilliseconds) }
                    val expectedMinus = runCatching { Math.subtractExact(millis, d.inWholeMilliseconds) }
                    plus.isSuccess shouldBe expectedPlus.isSuccess
                    minus.isSuccess shouldBe expectedMinus.isSuccess
                    if (plus.isSuccess) plus.getOrThrow().millis shouldBe expectedPlus.getOrThrow()
                    if (minus.isSuccess) minus.getOrThrow().millis shouldBe expectedMinus.getOrThrow()
                    if (plus.isSuccess) {
                        plus.getOrThrow().toInstant() shouldBe instant + wholeMillis
                    }
                    if (minus.isSuccess) {
                        minus.getOrThrow().toInstant() shouldBe instant - wholeMillis
                    }
                }
            }
        }

        should("match Instant on differences") {
            val r = Random(53)
            repeat(50_000) {
                val a = randomMillis(r)
                val b = randomMillis(r)
                withClue("$a $b") {
                    val diff = runCatching { Inst(a) - Inst(b) }
                    val expected = runCatching {
                        Math.subtractExact(a, b)
                    }.mapCatching {
                        if (it.milliseconds.isInfinite()) {
                            throw ArithmeticException()
                        } else {
                            it
                        }
                    }
                    diff.map { it.inWholeMilliseconds }.isSuccess shouldBe expected.isSuccess
                    if (expected.isSuccess) {
                        diff.getOrThrow().inWholeMilliseconds shouldBe expected.getOrThrow()
                        diff.getOrThrow() shouldBe (
                            Instant.fromEpochMilliseconds(a) - Instant.fromEpochMilliseconds(b)
                        )
                    }
                }
            }
        }

    }

    context("calendar") {
        should("match java.time on year and epoch day") {
            val r = Random(57)
            repeat(50_000) {
                val millis = when (r.nextInt(3)) {
                    0 -> r.nextLong(-400L * 365 * MILLIS_DAY, 400L * 365 * MILLIS_DAY)
                    1 -> r.nextLong(1_500_000_000_000L, 2_000_000_000_000L)
                    else -> r.nextLong(-1_000_000L * MILLIS_DAY, 1_000_000L * MILLIS_DAY)
                }
                val inst = Inst(millis)
                val zoned = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
                withClue(millis) {
                    inst.year shouldBe zoned.year
                    inst.epochDay shouldBe zoned.toLocalDate().toEpochDay()
                }
            }
            Inst.EPOCH.year shouldBe 1970
            Inst(-1).year shouldBe 1969
        }
    }
})

private const val MILLIS_DAY = 86_400_000L

private fun dayStartMillis(date: LocalDate): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun dayEndMillis(date: LocalDate): Long = dayStartMillis(date.plusDays(1)) - 1
