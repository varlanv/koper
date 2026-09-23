@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.varlanv.koper.benchmarks.lang

import com.varlanv.koper.lang.writeDoubleAscii
import jdk.internal.math.DoubleToDecimal
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Thread)
@Fork(jvmArgsAppend = ["--add-exports=java.base/jdk.internal.math=ALL-UNNAMED"])
class DoubleNumbersBenchmark {
    @JvmField
    @Param("small", "mixed", "random")
    final var distribution = "small"

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
            check(text == value.toString())
            for (position in output.indices) {
                if (position !in 3 until end) check(output[position] == 0x55.toByte())
            }
            output.fill(0x55)
            val expected = value.toString()
            val stringEnd = writeString(expected)
            check(output.decodeToString(3, stringEnd) == expected)
            for (position in output.indices) {
                if (position !in 3 until stringEnd) check(output[position] == 0x55.toByte())
            }
            output.fill(0x55)
            val jdkEnd = DoubleToDecimal.LATIN1.putDecimal(output, 3, value)
            check(jdkEnd == end)
            check(output.decodeToString(3, jdkEnd) == expected)
            for (position in output.indices) {
                if (position !in 3 until 3 + DoubleToDecimal.MAX_CHARS) {
                    check(output[position] == 0x55.toByte())
                }
            }
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
    fun doubleString(blackhole: Blackhole) {
        val end = writeString(nextDouble().toString())
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun doubleJdk(blackhole: Blackhole) {
        val end = DoubleToDecimal.LATIN1.putDecimal(output, 3, nextDouble())
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
