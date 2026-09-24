package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import kotlin.random.Random

private fun Dec64Array.shouldBeSortedLike(expected: List<BigDecimal>) {
    size shouldBe expected.size
    for (i in 0 until size) {
        withClue("index $i") { this[i].toBigDecimal().compareTo(expected[i]) shouldBe 0 }
    }
}

class Dec64ArrayJvmSpec : BaseSpec({
    context("aggregates") {
        should("match BigDecimal on random arrays") {
            val r = Random(20)
            repeat(300) {
                val values = List(r.nextInt(1, 50)) {
                    BigDecimal(r.nextLong(-1_000_000L, 1_000_000L)).movePointLeft(r.nextInt(0, 8))
                }
                val arr = Dec64Array(values.size) { Dec64.fromDecimal(values[it]) }
                val from = r.nextInt(0, values.size)
                val to = r.nextInt(from + 1, values.size + 1)
                withClue(values.map { it.toPlainString() }) {
                    arr.sum().shouldEqualDecimal(values.fold(BigDecimal.ZERO, BigDecimal::add))
                    arr
                        .sum(from = from, to = to)
                        .shouldEqualDecimal(values.subList(from, to).fold(BigDecimal.ZERO, BigDecimal::add))
                    arr.min().shouldEqualDecimal(values.min())
                    arr.max().shouldEqualDecimal(values.max())
                    arr.min(from = from, to = to).shouldEqualDecimal(values.subList(from, to).min())
                    arr.max(from = from, to = to).shouldEqualDecimal(values.subList(from, to).max())
                }
            }
        }
    }

    context("sorting") {
        should("sort large random, sorted, reversed, duplicated and mixed-scale arrays like BigDecimal") {
            val r = Random(21)
            val patterns = listOf<(Int) -> List<BigDecimal>>(
                { n -> List(n) { Dec64Gen.representable(r) } },
                { n -> List(n) { BigDecimal(it) } },
                { n -> List(n) { BigDecimal(n - it) } },
                { n ->
                    List(n) { BigDecimal(r.nextInt(0, 5)).movePointLeft(r.nextInt(0, 3)) }
                },
                { n ->
                    List(n) {
                        BigDecimal(r.nextLong(-1_000_000, 1_000_000)).movePointLeft(r.nextInt(0, 16))
                    }
                },
                { n -> List(n) { BigDecimal(1).movePointLeft(r.nextInt(0, 16)) } },
                { n ->
                    List(n) {
                        if (it % 2 == 0) {
                            BigDecimal(it)
                        } else {
                            BigDecimal(-it)
                        }
                    }
                },
                { n -> List(n) { BigDecimal(it / 2) } },
            )
            for (n in listOf(0, 1, 2, 15, 16, 17, 100, 1_000, 5_000)) {
                for ((idx, pattern) in patterns.withIndex()) {
                    val values = pattern(n)
                    val arr = Dec64Array(values.size) { Dec64.fromDecimal(values[it]) }
                    val sorted = values.sorted()
                    withClue("pattern $idx n=$n") {
                        arr.sortAsc()
                        arr.shouldBeSortedLike(sorted)
                        arr.sortDesc()
                        arr.shouldBeSortedLike(sorted.asReversed())
                        arr.sortAsc()
                        arr.shouldBeSortedLike(sorted)
                    }
                }
            }
        }


    }
})
