package com.varlanv.koper.benchmarks.lang

import com.varlanv.koper.lang.Charset
import com.varlanv.koper.lang.allocateString
import kotlin.js.unsafeCast
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Uint16Array

private val utf8Decoder: dynamic = js("new TextDecoder('utf-8', { ignoreBOM: true })")
private val utf16leDecoder: dynamic = js("new TextDecoder('utf-16le', { ignoreBOM: true })")
private val nativeUtf16Decoder: dynamic = js("new Uint8Array(new Uint16Array([0x0102]).buffer)[0] === 0x02 ? new TextDecoder('utf-16le', { ignoreBOM: true }) : new TextDecoder('utf-16be', { ignoreBOM: true })")
private val spreadChars: dynamic = js("(bytes) => String.fromCharCode(...bytes)")
private val applyChars: dynamic = js("(bytes) => String.fromCharCode.apply(null, bytes)")
private val isAsciiString: dynamic = js("(value) => /^[\\x00-\\x7F]*$/.test(value)")

private fun unsigned(bytes: ByteArray): Uint8Array {
    val signed = bytes.unsafeCast<Int8Array>()
    return Uint8Array(signed.buffer, signed.byteOffset, signed.byteLength)
}

private fun latin1Utf16Array(bytes: ByteArray): String {
    val chars = Uint16Array(bytes.size)
    chars.asDynamic().set(unsigned(bytes))
    return nativeUtf16Decoder.decode(Uint8Array(chars.buffer, chars.byteOffset, chars.byteLength))
        .unsafeCast<String>()
}

private fun latin1Portable(bytes: ByteArray): String {
    val expanded = ByteArray(bytes.size * 2)
    var output = 0
    for (byte in bytes) {
        expanded[output++] = byte
        expanded[output++] = 0
    }
    return utf16leDecoder.decode(expanded.unsafeCast<Int8Array>()).unsafeCast<String>()
}

@State(Scope.Benchmark)
class Latin1DecoderCandidatesJsBenchmark {
    @Param("ascii", "high", "controls", "mixed")
    var scenario = "ascii"

    @Param("32", "256", "4096", "10240")
    var length = 32

    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        bytes = ByteArray(length) { index ->
            when (scenario) {
                "ascii" -> (index % 128).toByte()
                "high" -> (0xA0 + index % 96).toByte()
                "controls" -> (0x80 + index % 32).toByte()
                "mixed" -> (index % 256).toByte()
                else -> error("Unknown scenario: $scenario")
            }
        }
        val expected = CharArray(length) { (bytes[it].toInt() and 0xFF).toChar() }.concatToString()
        check(Charset.Latin1.allocateString(bytes) == expected)
        check(latin1Utf16Array(bytes) == expected)
        check(latin1Portable(bytes) == expected)
        check(spreadChars(unsigned(bytes)).unsafeCast<String>() == expected)
        check(applyChars(unsigned(bytes)).unsafeCast<String>() == expected)
    }

    @Benchmark
    fun current(blackhole: Blackhole) {
        blackhole.consume(Charset.Latin1.allocateString(bytes))
    }

    @Benchmark
    fun utf16Array(blackhole: Blackhole) {
        blackhole.consume(latin1Utf16Array(bytes))
    }

    @Benchmark
    fun portableUtf16(blackhole: Blackhole) {
        blackhole.consume(latin1Portable(bytes))
    }

    @Benchmark
    fun spread(blackhole: Blackhole) {
        blackhole.consume(spreadChars(unsigned(bytes)).unsafeCast<String>())
    }

    @Benchmark
    fun apply(blackhole: Blackhole) {
        blackhole.consume(applyChars(unsigned(bytes)).unsafeCast<String>())
    }
}

@State(Scope.Benchmark)
class AsciiDecoderCandidatesJsBenchmark {
    @Param("32", "256", "4096", "10240")
    var length = 32

    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        bytes = ByteArray(length) { (it % 128).toByte() }
        val expected = CharArray(length) { bytes[it].toInt().toChar() }.concatToString()
        check(Charset.Ascii.allocateString(bytes) == expected)
        check(utf8Decoder.decode(bytes.unsafeCast<Int8Array>()).unsafeCast<String>() == expected)
    }

    @Benchmark
    fun current(blackhole: Blackhole) {
        blackhole.consume(Charset.Ascii.allocateString(bytes))
    }

    @Benchmark
    fun directUtf8(blackhole: Blackhole) {
        blackhole.consume(utf8Decoder.decode(bytes.unsafeCast<Int8Array>()).unsafeCast<String>())
    }
}

@State(Scope.Benchmark)
class AsciiValidatedDecoderJsBenchmark {
    @Param("valid", "invalidSparse", "invalidDense")
    var scenario = "valid"

    @Param("256", "4096", "10240")
    var length = 256

    private lateinit var bytes: ByteArray

    @Setup
    fun setup() {
        bytes = ByteArray(length) { index ->
            when (scenario) {
                "valid" -> (index % 128).toByte()
                "invalidSparse" -> if (index == length / 2) 0xC3.toByte() else (index % 128).toByte()
                "invalidDense" -> (0x80 + index % 128).toByte()
                else -> error("Unknown scenario: $scenario")
            }
        }
        check(validatedAscii(bytes) == Charset.Ascii.allocateString(bytes))
    }

    @Benchmark
    fun current(blackhole: Blackhole) {
        blackhole.consume(Charset.Ascii.allocateString(bytes))
    }

    @Benchmark
    fun validated(blackhole: Blackhole) {
        blackhole.consume(validatedAscii(bytes))
    }
}

private fun validatedAscii(bytes: ByteArray): String {
    val decoded = utf8Decoder.decode(bytes.unsafeCast<Int8Array>()).unsafeCast<String>()
    if (isAsciiString(decoded).unsafeCast<Boolean>()) return decoded
    return Charset.Ascii.allocateString(bytes)
}
