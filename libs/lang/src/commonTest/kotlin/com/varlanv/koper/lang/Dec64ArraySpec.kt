package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random

private fun decs(vararg texts: String): Dec64Array {
    return Dec64Array(texts.size) { dec(texts[it]) }
}

class Dec64ArraySpec : BaseSpec({
    context("basics") {
        should("start zeroed") {
            val arr = Dec64Array(3)
            arr.size shouldBe 3
            arr.isEmpty() shouldBe false
            Dec64Array(0).isEmpty() shouldBe true
            for (i in 0 until 3) arr[i] shouldBe Dec64.ZERO
        }

        should("get and set") {
            val arr = Dec64Array(2)
            arr[0] = dec("1.5")
            arr[1] = dec("-0.001")
            arr[0] shouldBe dec("1.5")
            arr[1] shouldBe dec("-0.001")
            arr.toString() shouldBe "[1.5, -0.001]"
            shouldThrow<IndexOutOfBoundsException> { arr[2] }
            shouldThrow<IndexOutOfBoundsException> { arr[-1] }
            shouldThrow<IndexOutOfBoundsException> { arr[-1] = Dec64.ZERO }
            shouldThrow<IndexOutOfBoundsException> { arr[2] = Dec64.ZERO }
            arr.size shouldBe 2
            arr.toString() shouldBe "[1.5, -0.001]"
        }

        should("initialise from lambda") {
            val arr = Dec64Array(4) { Dec64(it.toLong()) }
            arr.toString() shouldBe "[0, 1, 2, 3]"
        }

        should("fill, copy and compare content") {
            val arr = decs("1", "2", "3", "4")
            arr.fill(
                value = dec("9.9"),
                from = 1,
                to = 3,
            )
            arr.toString() shouldBe "[1, 9.9, 9.9, 4]"
            arr.fill(value = Dec64.ZERO)
            arr.toString() shouldBe "[0, 0, 0, 0]"
            val src = decs("1.5", "2.5", "3.5")
            val copy = src.copyOf()
            copy.contentEquals(src) shouldBe true
            copy[0] = Dec64.ZERO
            copy.contentEquals(src) shouldBe false
            src[0] shouldBe dec("1.5")
            src.copyOfRange(from = 1, to = 3).toString() shouldBe "[2.5, 3.5]"
            decs("1.50").contentEquals(decs("1.5")) shouldBe true
        }

        should("iterate") {
            val arr = decs("1", "2.5", "-3")
            val seen = mutableListOf<String>()
            arr.forEach { seen.add(it.toString()) }
            seen shouldBe listOf("1", "2.5", "-3")
            val indexed = mutableListOf<String>()
            arr.forEachIndexed { i, v -> indexed.add("$i=$v") }
            indexed shouldBe listOf("0=1", "1=2.5", "2=-3")
            var count = 0
            Dec64Array(0).forEach { count++ }
            count shouldBe 0
        }

        should("copy ranges without retaining shared storage") {
            val source = decs("1", "2", "3")
            val copy = source.copyOfRange(1, 3)
            copy[0] = Dec64.ZERO
            source.toString() shouldBe "[1, 2, 3]"
            copy.toString() shouldBe "[0, 3]"
            source.copyOfRange(2, 2).isEmpty() shouldBe true
            source.contentEquals(decs("1", "2")) shouldBe false
            Dec64Array(0).contentEquals(Dec64Array(0)) shouldBe true
            Dec64Array(0).toString() shouldBe "[]"
        }
    }

    context("aggregates") {
        should("sum") {
            decs("3.5", "-1", "0", "100", "0.0001", "-1000000").sum().toString() shouldBe "-999897.4999"
            decs("0.1", "0.2", "0.3").sum().toString() shouldBe "0.6"
            Dec64Array(0).sum() shouldBe Dec64.ZERO
            decs("1", "2", "3", "4").sum(from = 1, to = 3).toString() shouldBe "5"
            decs("1", "2", "3", "4").sum(from = 2, to = 2) shouldBe Dec64.ZERO
            shouldThrow<ArithmeticException> { decs("288230376151711743", "1").sum() }
            shouldThrow<ArithmeticException> { decs("100000000000000000", "0.01").sum() }
            decs("288230376151711743", "1", "-1").let { shouldThrow<ArithmeticException> { it.sum() } }
            decs("288230376151711743", "-1", "1").sum() shouldBe Dec64.MAX_VALUE
        }

        should("min and max") {
            val arr = decs("3.5", "-1", "0", "100", "0.0001", "-1000000")
            arr.min().toString() shouldBe "-1000000"
            arr.max().toString() shouldBe "100"
            arr.min(from = 0, to = 1).toString() shouldBe "3.5"
            arr.max(from = 1, to = 3).toString() shouldBe "0"
            shouldThrow<IllegalArgumentException> { Dec64Array(0).min() }
            shouldThrow<IllegalArgumentException> { Dec64Array(0).max() }
            shouldThrow<IllegalArgumentException> { arr.min(from = 2, to = 2) }
        }
    }

    context("sorting") {
        should("keep a multiset intact through sorting") {
            val r = Random(22)
            repeat(50) {
                val values = List(r.nextInt(0, 200)) { randomDec64(r) }
                val arr = Dec64Array(values.size) { values[it] }
                arr.sortAsc()
                (0 until arr.size).map { arr[it].toString() }.sorted() shouldBe
                    values.map { it.toString() }.sorted()
            }
        }

        should("sort small arrays ascending and descending") {
            val arr = decs("3.5", "-1", "0", "100", "0.0001", "-1000000")
            arr.sortAsc()
            arr.toString() shouldBe "[-1000000, -1, 0, 0.0001, 3.5, 100]"
            arr.sortDesc()
            arr.toString() shouldBe "[100, 3.5, 0.0001, 0, -1, -1000000]"
            Dec64Array(0).sortAsc()
            Dec64Array(1) { dec("1") }.apply { sortAsc() }.toString() shouldBe "[1]"
        }

        should("sort a sub-range only") {
            val arr = decs("9", "3", "2", "1", "0")
            arr.sortAsc(from = 1, to = 4)
            arr.toString() shouldBe "[9, 1, 2, 3, 0]"
            arr.sortDesc(from = 0, to = 2)
            arr.toString() shouldBe "[9, 1, 2, 3, 0]"
            arr.sortDesc(from = 1, to = 4)
            arr.toString() shouldBe "[9, 3, 2, 1, 0]"
        }

        should("sort values that are equal numerically but differ in bits as equal") {
            val arr = Dec64Array(3)
            arr[0] = Dec64.fromLong(unscaled = 15, scale = 1)
            arr[1] = Dec64.fromLong(unscaled = 1500, scale = 3)
            arr[2] = Dec64.fromLong(unscaled = 15, scale = 1)
            arr.sortAsc()
            (0 until 3).all { arr[it] == dec("1.5") } shouldBe true
        }

        should("sort large arrays and retain values outside subranges") {
            val random = Random(32)
            for (size in listOf(0, 1, 2, 15, 16, 17, 100, 1_000)) {
                val patterns = listOf(
                    List(size) { randomDec64(random) },
                    List(size) { Dec64(it.toLong()) },
                    List(size) { Dec64((size - it).toLong()) },
                    List(size) { Dec64.fromLong(random.nextLong(-5, 6), random.nextInt(Dec64.MAX_SCALE + 1)) },
                    List(size) { if (it % 2 == 0) Dec64.MAX_VALUE else Dec64.MIN_VALUE },
                )
                for ((index, values) in patterns.withIndex()) {
                    withClue("size=$size pattern=$index") {
                        val array = Dec64Array(size + 2) {
                            when (it) {
                                0 -> Dec64.MAX_VALUE
                                size + 1 -> Dec64.MIN_VALUE
                                else -> values[it - 1]
                            }
                        }
                        val sorted = values.sorted()
                        array.sortAsc(1, size + 1)
                        (1..size).map { array[it] } shouldBe sorted
                        array[0] shouldBe Dec64.MAX_VALUE
                        array[size + 1] shouldBe Dec64.MIN_VALUE
                        array.sortDesc(1, size + 1)
                        (1..size).map { array[it] } shouldBe sorted.asReversed()
                        array[0] shouldBe Dec64.MAX_VALUE
                        array[size + 1] shouldBe Dec64.MIN_VALUE
                    }
                }
            }
        }
    }
})
