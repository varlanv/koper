@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.varlanv.koper.benchmarks.lang.math

import com.varlanv.koper.lang.math.writeDoubleAscii
import com.varlanv.koper.lang.math.writeDoubleAsciiPortable
import kotlinx.benchmark.*

@State(Scope.Benchmark)
class DoubleNumbersJsBenchmark {
    @Param("small", "mixed", "random")
    var distribution = "small"

    private lateinit var values: DoubleArray
    private val output = ByteArray(32)
    private var index = 0

    @Setup
    fun setup() {
        values = doubleNumbersData(distribution)
        index = 0
        for (value in values) {
            output.fill(0x55)
            val end = writeDoubleAscii(value, output, 3)
            val text = output.decodeToString(3, end)
            check(text.toDouble().toRawBits() == value.toRawBits())
            for (position in output.indices) {
                if (position !in 3 until end) check(output[position] == 0x55.toByte())
            }
            output.fill(0x55)
            check(writeDoubleAsciiPortable(value, output, 3) == end)
            check(output.decodeToString(3, end) == text)
            for (position in output.indices) {
                if (position !in 3 until end) check(output[position] == 0x55.toByte())
            }
            output.fill(0x55)
            val expected = value.toString()
            check(output.decodeToString(3, writeString(expected)) == expected)
        }
    }

    @Benchmark
    fun doubleForward(blackhole: Blackhole) {
        val end = writeDoubleAscii(nextDouble(), output, 3)
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun doublePortable(blackhole: Blackhole) {
        val end = writeDoubleAsciiPortable(nextDouble(), output, 3)
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun doubleString(blackhole: Blackhole) {
        val end = writeString(nextDouble().toString())
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    private fun nextDouble(): Double {
        val value = values[index]
        index = (index + 1) and (values.size - 1)
        return value
    }

    private fun writeString(text: String): Int {
        for (position in text.indices) {
            output[3 + position] = text[position].code.toByte()
        }
        return 3 + text.length
    }
}
