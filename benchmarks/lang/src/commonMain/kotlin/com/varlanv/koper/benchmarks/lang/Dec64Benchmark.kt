package com.varlanv.koper.benchmarks.lang

import com.varlanv.koper.lang.Dec64
import com.varlanv.koper.lang.Dec64Array
import com.varlanv.koper.lang.Dec64Context
import com.varlanv.koper.lang.Rounding
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

@State(Scope.Benchmark)
class Dec64Benchmark {
    private val coefficients = LongArray(4096)
    private val scales = IntArray(4096)
    private var left = Dec64Array(4096)
    private var right = Dec64Array(4096)
    private lateinit var text: Array<String>
    private val context = Dec64Context(6, Rounding.HALF_EVEN)
    private val output = ByteArray(32)
    private var index = 0

    @Setup
    fun setup() {
        val random = Random(42)
        for (position in coefficients.indices) {
            coefficients[position] = random.nextLong(-10_000_000_000L, 10_000_000_001L)
            scales[position] = random.nextInt(2, 7)
            left[position] = Dec64.fromLong(coefficients[position], scales[position])
            val magnitude = random.nextLong(1001, 100_001)
            right[position] = Dec64.fromLong(if (random.nextBoolean()) magnitude else -magnitude, random.nextInt(4))
        }
        text = Array(left.size) { left[it].toString() }
        val a = Dec64.parseString("1.25")
        val b = Dec64.parseString("2")
        check((a + b).toString() == "3.25")
        check((a - b).toString() == "-0.75")
        check((a * b).toString() == "2.5")
        check(a.div(b, context).toString() == "0.625")
        for (position in coefficients.indices) {
            val value = left[position]
            check(Dec64.parseString(text[position]) == value)
            check(value.toDouble() == text[position].toDouble())
            check((value + right[position]) - right[position] == value)
            check((value * right[position]).div(right[position], context) == value)
            output.fill(85)
            val end = value.writeTo(output, 3)
            check(output.decodeToString(3, end) == text[position])
            for (cursor in output.indices) {
                if (cursor !in 3 until end) check(output[cursor] == 85.toByte())
            }
        }
        index = 0
    }

    @Benchmark
    fun constructAndReadCoefficient(): Long {
        val position = nextIndex()
        return Dec64.fromLong(coefficients[position], scales[position]).coefficient
    }

    @Benchmark
    fun add(): Long {
        val position = nextIndex()
        return (left[position] + right[position]).bits
    }

    @Benchmark
    fun subtract(): Long {
        val position = nextIndex()
        return (left[position] - right[position]).bits
    }

    @Benchmark
    fun multiply(): Long {
        val position = nextIndex()
        return (left[position] * right[position]).bits
    }

    @Benchmark
    fun divideHalfEven(): Long {
        val position = nextIndex()
        return left[position].div(right[position], context).bits
    }

    @Benchmark
    fun compare(): Int {
        val position = nextIndex()
        return left[position].compareTo(right[position])
    }

    @Benchmark
    fun toDouble(): Double = left[nextIndex()].toDouble()

    @Benchmark
    fun formatString(): String = left[nextIndex()].toString()

    @Benchmark
    fun parseString(): Long = Dec64.parseString(text[nextIndex()]).bits

    @Benchmark
    fun writeTo(blackhole: Blackhole) {
        val end = left[nextIndex()].writeTo(output, 3)
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun arraySum16(): Long {
        val start = nextIndex() and 4080
        return left.sum(start, start + 16).bits
    }

    private fun nextIndex(): Int {
        val position = index
        index = (index + 1) and 4095
        return position
    }
}
