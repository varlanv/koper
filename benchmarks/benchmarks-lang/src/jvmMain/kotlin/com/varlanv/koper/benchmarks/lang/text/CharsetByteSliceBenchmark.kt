package com.varlanv.koper.benchmarks.lang.text

import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.allocateByteSlice
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
class CharsetByteSliceBenchmark {
    @JvmField
    @Param("64", "4096")
    final var length = 64

    @JvmField
    @Param("ascii", "mixed")
    final var content = "ascii"

    private lateinit var string: String
    private var start = 0
    private var end = 0

    @Setup
    fun setup() {
        val instanceField = Class
            .forName("com.varlanv.koper.lang.text.SubstringFastPath\$Companion")
            .getDeclaredField("instance")
        instanceField.isAccessible = true
        val actualPath = instanceField.get(null).javaClass.simpleName
        val expectedPath = System.getProperty("koper.benchmark.substring.path")
        check(actualPath == expectedPath) { "Expected $expectedPath, selected $actualPath" }

        val pattern = when (content) {
            "ascii" -> "abcdefghijklmnopqrstuvwxyz"
            "mixed" -> "Hello, Привіт, é 🙂! "
            else -> error("Unknown content: $content")
        }
        val text = String(CharArray(length) { pattern[it % pattern.length] })
        val prefix = "prefix:"
        string = prefix + text + ":suffix"
        start = prefix.length
        end = start + length
        val expected = string.substring(start, end).toByteArray(Charsets.UTF_8)
        val actual = Charset.Utf8.allocateByteSlice(string = string, start = start, end = end)
        check(actual.len == expected.size)
        check(expected.indices.all { actual[it] == expected[it] })
    }

    @Benchmark
    @Fork(jvmArgsAppend = ["-Dkoper.benchmark.substring.path=Default"])
    fun defaultPath(): BytesSlice = Charset.Utf8.allocateByteSlice(string = string, start = start, end = end)

    @Benchmark
    @Fork(
        jvmArgsAppend = [
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector",
        "-Dkoper.lang.utf8.vector.enabled=true",
        "-Dkoper.benchmark.substring.path=JdkInternal",
        ],
    )
    fun jdkInternal(): BytesSlice = Charset.Utf8.allocateByteSlice(string = string, start = start, end = end)
}
