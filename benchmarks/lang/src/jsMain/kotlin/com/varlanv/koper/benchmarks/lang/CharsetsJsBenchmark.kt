package com.varlanv.koper.benchmarks.lang

import com.varlanv.koper.lang.Charset
import com.varlanv.koper.lang.allocateString
import com.varlanv.koper.lang.toByteArray
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
class CharsetsJsBenchmark {
    @Param("ascii", "latin1", "latin1Surrogates", "utf8")
    var scenario = "ascii"

    @Param("32", "10240")
    var length = 32

    private lateinit var charset: Charset
    private lateinit var source: String
    private lateinit var encoded: ByteArray

    @Setup
    fun setup() {
        val seed = when (scenario) {
            "ascii" -> {
                charset = Charset.Ascii
                "abcdefgh"
            }
            "latin1" -> {
                charset = Charset.Latin1
                "Aéÿabcde"
            }
            "latin1Surrogates" -> {
                charset = Charset.Latin1
                "A🙂bcdef"
            }
            "utf8" -> {
                charset = Charset.Utf8
                "Aé中🙂bcd"
            }
            else -> error("Unknown scenario: $scenario")
        }
        source = seed.repeat(length / seed.length)
        check(source.length == length)
        encoded = charset.toByteArray(source)
        check(charset.allocateString(encoded).isNotEmpty())
    }

    @Benchmark
    fun encode(blackhole: Blackhole) {
        blackhole.consume(charset.toByteArray(source))
    }

    @Benchmark
    fun decode(blackhole: Blackhole) {
        blackhole.consume(charset.allocateString(encoded))
    }
}
