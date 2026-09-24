package com.varlanv.koper.benchmarks.lang.math

import com.varlanv.koper.lang.math.writeIntAscii
import com.varlanv.koper.lang.math.writeLongAscii
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import kotlin.random.Random

@State(Scope.Thread)
class NumbersBenchmark {
    @JvmField
    @Param("small", "mixed", "random")
    final var distribution = "small"

    private lateinit var ints: IntArray
    private lateinit var longs: LongArray
    private val output = ByteArray(32)
    private var intIndex = 0
    private var longIndex = 0

    @Setup
    fun setup() {
        val random = Random(42)
        ints = IntArray(4096) { index ->
            when (distribution) {
                "small" -> index and 255
                "mixed" -> {
                    val digits = index % 10 + 1
                    var lower = 1L
                    repeat(digits - 1) { lower *= 10 }
                    val magnitude = random.nextLong(lower, minOf(lower * 10, Int.MAX_VALUE.toLong() + 1)).toInt()
                    if (random.nextBoolean()) magnitude else -magnitude
                }
                "random" -> random.nextInt()
                else -> error("Unknown distribution: $distribution")
            }
        }
        longs = LongArray(4096) { index ->
            when (distribution) {
                "small" -> (index and 255).toLong()
                "mixed" -> {
                    val digits = index % 19 + 1
                    var lower = 1L
                    repeat(digits - 1) { lower *= 10 }
                    val magnitude = random.nextLong(lower, if (digits == 19) Long.MAX_VALUE else lower * 10)
                    if (random.nextBoolean()) magnitude else -magnitude
                }
                "random" -> random.nextLong()
                else -> error("Unknown distribution: $distribution")
            }
        }
        if (distribution != "small") {
            ints[0] = Int.MIN_VALUE
            ints[1] = Int.MAX_VALUE
            ints[2] = 0
            longs[0] = Long.MIN_VALUE
            longs[1] = Long.MAX_VALUE
            longs[2] = 0
        }
        ints.shuffle(random)
        longs.shuffle(random)
        intIndex = 0
        longIndex = 0
        for (value in ints) {
            val text = value.toString()
            output.fill(0x55)
            verify(text, writeIntAscii(value, output, 3))

            output.fill(0x55)
            verify(text, writeString(text))
        }
        for (value in longs) {
            val text = value.toString()
            output.fill(0x55)
            verify(text, writeLongAscii(value, output, 3))

            output.fill(0x55)
            verify(text, writeString(text))
        }
    }

    @Benchmark
    fun intForward(blackhole: Blackhole) {
        val end = writeIntAscii(nextInt(), output, 3)
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun intString(blackhole: Blackhole) {
        val end = writeString(nextInt().toString())
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun longForward(blackhole: Blackhole) {
        val end = writeLongAscii(nextLong(), output, 3)
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    @Benchmark
    fun longString(blackhole: Blackhole) {
        val end = writeString(nextLong().toString())
        blackhole.consume(end)
        blackhole.consume(output[3].toInt() + output[end - 1])
        blackhole.consume(output)
    }

    private fun nextInt(): Int {
        val value = ints[intIndex]
        intIndex = (intIndex + 1) and (ints.size - 1)
        return value
    }

    private fun nextLong(): Long {
        val value = longs[longIndex]
        longIndex = (longIndex + 1) and (longs.size - 1)
        return value
    }

    private fun writeString(text: String): Int {
        for (index in text.indices) {
            output[3 + index] = text[index].code.toByte()
        }
        return 3 + text.length
    }

    private fun verify(text: String, end: Int) {
        check(end == 3 + text.length)
        for (index in output.indices) {
            val expected = if (index in 3 until end) text[index - 3].code.toByte() else 0x55.toByte()
            check(output[index] == expected) { "Incorrect encoding for $text at $index" }
        }
    }
}
