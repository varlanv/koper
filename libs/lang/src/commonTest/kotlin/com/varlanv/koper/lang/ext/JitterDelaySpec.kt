package com.varlanv.koper.lang.ext

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class JitterDelaySpec : BaseSpec({
    should("return the base delay when jitter is zero or less than a millisecond") {
        listOf(Duration.ZERO, 999_999.nanoseconds).forEach { jitter ->
            val delay: DynamicDelay = JitterDelay(
                base = 1_234_567.nanoseconds,
                jitter = jitter,
                random = Random(1),
            )
            repeat(20) { delay.next() shouldBe 1_234_567.nanoseconds }
        }
    }

    should("reject negative and infinite jitter") {
        listOf((-1).nanoseconds, Duration.INFINITE, -Duration.INFINITE).forEach { jitter ->
            shouldThrow<IllegalArgumentException> { JitterDelay(base = 1.seconds, jitter = jitter) }
        }
    }

    should("include both jitter bounds and preserve the fractional base") {
        val base = 1_234_567.nanoseconds
        val delay = JitterDelay(
            base = base,
            jitter = 2.milliseconds,
            random = Random(2),
        )
        val offsets = mutableSetOf<Duration>()
        repeat(1_000) {
            val offset = delay.next() - base
            (offset in -2.milliseconds..2.milliseconds) shouldBe true
            offsets += offset
        }
        offsets shouldBe (-2..2).map { it.milliseconds }.toSet()
    }

    should("truncate jitter to whole milliseconds") {
        val delay = JitterDelay(
            base = Duration.ZERO,
            jitter = 1_999_999.nanoseconds,
            random = Random(3),
        )
        val offsets = (1..1_000).map { delay.next() }.toSet()
        offsets shouldBe setOf((-1).milliseconds, Duration.ZERO, 1.milliseconds)
    }

    should("reproduce the same sequence with the same random seed") {
        val first = JitterDelay(
            base = 1.seconds,
            jitter = 50.milliseconds,
            random = Random(4),
        )
        val second = JitterDelay(
            base = 1.seconds,
            jitter = 50.milliseconds,
            random = Random(4),
        )
        repeat(100) { first.next() shouldBe second.next() }
    }

    should("allow jitter to produce negative delays") {
        val delay = JitterDelay(
            base = Duration.ZERO,
            jitter = 1.milliseconds,
            random = Random(5),
        )
        ((1..100).map { delay.next() }.any { it < Duration.ZERO }) shouldBe true
    }
})
