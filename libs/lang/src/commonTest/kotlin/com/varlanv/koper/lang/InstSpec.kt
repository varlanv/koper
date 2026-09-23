package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class InstHolder(
    val millis: Inst,
    val seconds: InstSeconds,
    val text: InstStringMillis,
    val textSeconds: InstStringSeconds,
)

private fun randomMillis(r: Random): Long {
    return when (r.nextInt(5)) {
        0 -> r.nextLong(-1_000_000L, 1_000_000L)
        1 -> r.nextLong(1_500_000_000_000L, 2_000_000_000_000L)
        2 -> r.nextLong(-62_135_596_800_000L, 0L)
        3 -> r.nextLong(Long.MIN_VALUE / 4, Long.MAX_VALUE / 4)
        else -> r.nextLong()
    }
}

class InstSpec : BaseSpec({
    context("conversion") {
        should("round-trip through Kotlin instants") {
            val r = Random(50)
            repeat(20_000) {
                val millis = randomMillis(r)
                val inst = Inst(millis)
                withClue(millis) {
                    inst.toInstant() shouldBe Instant.fromEpochMilliseconds(millis)
                    Inst.from(Instant.fromEpochMilliseconds(millis)) shouldBe inst
                    inst.toString() shouldBe Instant.fromEpochMilliseconds(millis).toString()
                    inst.seconds shouldBe Instant.fromEpochMilliseconds(millis).epochSeconds
                    inst.millisOfSecond shouldBe Instant.fromEpochMilliseconds(millis).nanosecondsOfSecond / 1_000_000
                }
            }
        }

        should("build from seconds and parse ISO-8601") {
            Inst.fromSeconds(1_787_334_997) shouldBe Inst(1_787_334_997_000)
            Inst.fromSeconds(-1) shouldBe Inst(-1_000)
            shouldThrow<ArithmeticException> { Inst.fromSeconds(Long.MAX_VALUE / 10) }
            Inst.parse("2026-09-04T12:34:56.789Z") shouldBe Inst(1_788_525_296_789)
            Inst.parse("1970-01-01T00:00:00Z") shouldBe Inst.EPOCH
            Inst.parse("2026-09-04T12:34:56.789+02:00") shouldBe
                Inst.from(Instant.parse("2026-09-04T12:34:56.789+02:00"))
            shouldThrow<IllegalArgumentException> { Inst.parse("not a date") }
            Inst(1_788_525_296_789).toString() shouldBe "2026-09-04T12:34:56.789Z"
            Inst(1_788_525_296_000).toString() shouldBe "2026-09-04T12:34:56Z"
            Inst(-1).toString() shouldBe "1969-12-31T23:59:59.999Z"
        }

        should("floor seconds for negative instants") {
            Inst(-1).seconds shouldBe -1L
            Inst(-1).millisOfSecond shouldBe 999
            Inst(-1_000).seconds shouldBe -1L
            Inst(-1_000).millisOfSecond shouldBe 0
            Inst(-1_001).seconds shouldBe -2L
            Inst(999).seconds shouldBe 0L
            Inst(999).millisOfSecond shouldBe 999
            Inst.EPOCH.seconds shouldBe 0L
        }
    }

    context("day boundaries and calendar") {
        should("handle exact day boundaries before and after the epoch") {
            for (day in listOf(-10_000L, -1L, 0L, 1L, 20_700L)) {
                val start = Inst.fromMillis(day * 86_400_000L)
                val end = Inst.fromMillis((day + 1) * 86_400_000L - 1)
                start.epochDay shouldBe day
                end.epochDay shouldBe day
                start.atStartOfDay() shouldBe start
                start.atEndOfDay() shouldBe end
                end.atStartOfDay() shouldBe start
                end.atEndOfDay() shouldBe end
                (start - 1.milliseconds).atEndOfDay() shouldBe start - 1.milliseconds
                (end + 1.milliseconds).atStartOfDay() shouldBe end + 1.milliseconds
            }
        }

        should("reject day boundaries outside the millisecond range") {
            shouldThrow<ArithmeticException> { Inst(Long.MIN_VALUE).atStartOfDay() }
            Inst(Long.MIN_VALUE).atEndOfDay().millis shouldBe
                Long.MIN_VALUE + (86_400_000L - 1 - Long.MIN_VALUE.mod(86_400_000L))
            Inst(Long.MAX_VALUE).atStartOfDay().millis shouldBe
                Long.MAX_VALUE - Long.MAX_VALUE.mod(86_400_000L)
            shouldThrow<ArithmeticException> { Inst(Long.MAX_VALUE).atEndOfDay() }
        }

        should("identify years across leap days and century boundaries") {
            for (year in listOf(0, 1, 400, 1600, 1900, 1969, 1970, 2000, 2024, 2100, 2400)) {
                val textYear = year.toString().padStart(4, '0')
                val start = Inst.parse("$textYear-01-01T00:00:00Z")
                start.year shouldBe year
                (start - 1.milliseconds).year shouldBe year - 1
                Inst.parse("$textYear-03-01T00:00:00Z").year shouldBe year
                Inst.parse("$textYear-12-31T23:59:59.999Z").year shouldBe year
            }
            Inst.parse("2000-02-29T12:00:00Z").year shouldBe 2000
            Inst.parse("2024-02-29T12:00:00Z").year shouldBe 2024
        }
    }

    context("arithmetic and comparison") {
        should("match Kotlin Instant arithmetic within the finite duration range") {
            val random = Random(58)
            repeat(10_000) {
                val millis = random.nextLong(-10_000_000_000_000L, 10_000_000_000_000L)
                val delta = random.nextLong(-100_000_000L, 100_000_000L).milliseconds
                val inst = Inst.fromMillis(millis)
                val expected = Instant.fromEpochMilliseconds(millis)
                withClue("$millis $delta") {
                    (inst + delta).toInstant() shouldBe expected + delta
                    (inst - delta).toInstant() shouldBe expected - delta
                    ((inst + delta) - inst) shouldBe delta
                    ((inst + delta) - delta) shouldBe inst
                }
            }
        }

        should("reject both infinite duration signs and arithmetic overflow") {
            for (duration in listOf(Duration.INFINITE, -Duration.INFINITE)) {
                shouldThrow<ArithmeticException> { Inst.EPOCH + duration }
                shouldThrow<ArithmeticException> { Inst.EPOCH - duration }
            }
            shouldThrow<ArithmeticException> { Inst(Long.MIN_VALUE) + (-1).milliseconds }
            shouldThrow<ArithmeticException> { Inst(Long.MAX_VALUE) - (-1).milliseconds }
            shouldThrow<ArithmeticException> { Inst(Long.MIN_VALUE) - Inst(1) }
            shouldThrow<ArithmeticException> { Inst.fromSeconds(Long.MIN_VALUE / 1_000 - 1) }
            shouldThrow<ArithmeticException> { Inst.fromSeconds(Long.MAX_VALUE / 1_000 + 1) }
            Inst.fromSeconds(Long.MIN_VALUE / 1_000).millis shouldBe Long.MIN_VALUE / 1_000 * 1_000
            Inst.fromSeconds(Long.MAX_VALUE / 1_000).millis shouldBe Long.MAX_VALUE / 1_000 * 1_000
        }

        should("obtain the current instant through the supplied clock") {
            var millis = 123L
            val clock = CurrentTime { Inst.fromMillis(millis) }
            clock() shouldBe Inst(123)
            millis = 456
            clock() shouldBe Inst(456)
        }

        should("truncate sub-millisecond durations") {
            (Inst(10) + 999_999.nanoseconds) shouldBe Inst(10)
            (Inst(10) + 1_000_000.nanoseconds) shouldBe Inst(11)
            (Inst(10) - 999_999.nanoseconds) shouldBe Inst(10)
            (Inst(10) + 1.5.hours) shouldBe Inst(10 + 5_400_000)
            shouldThrow<ArithmeticException> { Inst(Long.MAX_VALUE) + 1.milliseconds }
            shouldThrow<ArithmeticException> { Inst(Long.MIN_VALUE) - 1.milliseconds }
            shouldThrow<ArithmeticException> { Inst(0) + Duration.INFINITE }
            shouldThrow<ArithmeticException> { Inst(Long.MAX_VALUE) - Inst(-1) }
            shouldThrow<ArithmeticException> { Inst(Long.MAX_VALUE / 2) - Inst(Long.MIN_VALUE / 2) }
            shouldThrow<ArithmeticException> { Inst(0) - Duration.INFINITE }
            (Inst(Long.MAX_VALUE) - Inst(Long.MAX_VALUE - 1)) shouldBe 1.milliseconds
        }

        should("compare instants by their milliseconds") {
            val r = Random(54)
            repeat(20_000) {
                val a = randomMillis(r)
                val b = randomMillis(r)
                withClue("$a $b") {
                    Inst(a).compareTo(Inst(b)) shouldBe a.compareTo(b)
                    (Inst(a) < Inst(b)) shouldBe (a < b)
                    (Inst(a) == Inst(b)) shouldBe (a == b)
                }
            }
        }
    }

    context("serialization") {
        should("floor negative timestamps when serializing seconds") {
            Json.encodeToString(InstSecondsSerializer, Inst(-1)) shouldBe "-1"
            Json.encodeToString(InstStringSecondsSerializer, Inst(-1)) shouldBe "\"-1\""
            Json.decodeFromString(InstSecondsSerializer, "-1") shouldBe Inst(-1_000)
            Json.decodeFromString(InstStringSecondsSerializer, "\"-1\"") shouldBe Inst(-1_000)
        }

        should("preserve extreme millisecond values in numeric and string form") {
            for (millis in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
                val inst = Inst.fromMillis(millis)
                Json.decodeFromString(InstSerializer, Json.encodeToString(InstSerializer, inst)) shouldBe inst
                Json.decodeFromString(InstStringMillisSerializer, Json.encodeToString(InstStringMillisSerializer, inst)) shouldBe inst
            }
        }

        should("reject malformed string values and seconds that overflow milliseconds") {
            shouldThrow<NumberFormatException> { Json.decodeFromString(InstStringSecondsSerializer, "\"abc\"") }
            shouldThrow<NumberFormatException> { Json.decodeFromString(InstStringMillisSerializer, "\"9223372036854775808\"") }
            shouldThrow<ArithmeticException> { Json.decodeFromString(InstSecondsSerializer, Long.MAX_VALUE.toString()) }
            shouldThrow<ArithmeticException> { Json.decodeFromString(InstStringSecondsSerializer, "\"${Long.MIN_VALUE}\"") }
        }

        should("encode millis by default, seconds and string via typealiases") {
            val holder = InstHolder(
                millis = Inst(1_787_334_997_999),
                seconds = Inst(1_787_334_997_999),
                text = Inst(1_787_334_997_999),
                textSeconds = Inst(1_787_334_997_999),
            )
            val json = Json.encodeToString(InstHolder.serializer(), holder)
            json shouldBe """{"millis":1787334997999,"seconds":1787334997,"text":"1787334997999","textSeconds":"1787334997"}"""
            Json.decodeFromString(InstHolder.serializer(), json) shouldBe InstHolder(
                millis = Inst(1_787_334_997_999),
                seconds = Inst(1_787_334_997_000),
                text = Inst(1_787_334_997_999),
                textSeconds = Inst(1_787_334_997_000),
            )
            Json.encodeToString(
                Inst.serializer(),
                Inst(-1),
            ) shouldBe "-1"
            Json.decodeFromString(Inst.serializer(), "0") shouldBe Inst.EPOCH
            shouldThrow<NumberFormatException> {
                Json.decodeFromString(InstStringMillisSerializer, "\"abc\"")
            }
        }
    }
})
