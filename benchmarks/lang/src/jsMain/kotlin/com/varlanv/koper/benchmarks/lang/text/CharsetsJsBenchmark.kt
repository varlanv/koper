package com.varlanv.koper.benchmarks.lang.text

import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.allocateByteSlice
import com.varlanv.koper.lang.text.allocateString
import kotlinx.benchmark.*

@State(Scope.Benchmark)
class CharsetsJsBenchmark {
    @Param("ascii", "latin1", "latin1Surrogates", "utf8")
    var scenario = "ascii"

    @Param("32", "10240")
    var length = 32

    private lateinit var charset: Charset
    private lateinit var source: String
    private lateinit var encoded: ByteSlice

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
        encoded = charset.allocateByteSlice(source)
        check(charset.allocateString(encoded.unsafeBorrowArray(), encoded.offset, encoded.len).isNotEmpty())
    }

    @Benchmark
    fun encode(blackhole: Blackhole) {
        val result = charset.allocateByteSlice(source)
        blackhole.consume(result.unsafeBorrowArray())
        blackhole.consume(result.len)
    }

    @Benchmark
    fun decode(blackhole: Blackhole) {
        blackhole.consume(charset.allocateString(encoded.unsafeBorrowArray(), encoded.offset, encoded.len))
    }
}
