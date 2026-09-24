package com.varlanv.koper.benchmarks.lang.text

import com.varlanv.koper.lang.bin.validateUtf8
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
class Utf8ValidationBenchmark {
    @JvmField
    @Param("8", "32", "64", "100", "4096", "65536")
    final var length = 64

    @JvmField
    @Param("ascii", "mixed")
    final var content = "ascii"

    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        bytes = ByteArray(length) { ('a'.code + it % 26).toByte() }
        if (content == "mixed") {
            for (index in minOf(16, length / 2) until length - 1 step 32) {
                bytes[index] = 0xC3.toByte()
                bytes[index + 1] = 0xA9.toByte()
            }
        }
        check(bytes.validateUtf8())
    }

    @Benchmark
    fun validate(): Boolean = bytes.validateUtf8()
}
