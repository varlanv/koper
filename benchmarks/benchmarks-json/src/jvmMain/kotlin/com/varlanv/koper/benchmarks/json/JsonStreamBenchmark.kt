package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.json.tmp.IdealJsonReader
import com.varlanv.koper.json.tmp.IdealJsonUtf8Codec
import com.varlanv.koper.json.tmp.IdealJsonWriter
import com.varlanv.koper.json.tmp.JsonNativeSample
import com.varlanv.koper.json.tmp.JsonUtf8Sample
import com.varlanv.koper.json.tmp.NativeJsonUtf8Codec
import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.bin.InputStreamByteSource
import com.varlanv.koper.lang.bin.OutputStreamByteSink
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.jvm.JvmField
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = ["--add-modules=jdk.incubator.vector", "-Dkoper.lang.utf8.vector.enabled=true"])
class JsonStreamBenchmark {
    @Param("ASCII_SMALL", "UTF8_SMALL", "ESCAPED_SMALL", "UTF8_LARGE")
    @JvmField
    final var payload: String = ""

    private lateinit var utf8Value: JsonUtf8Sample
    private lateinit var nativeValue: JsonNativeSample
    private lateinit var input: ByteArrayInputStream
    private lateinit var streamInput: InputStreamByteSource
    private lateinit var output: RecycledOutputStream
    private lateinit var streamOutput: OutputStreamByteSink
    private val scalarReader = IdealJsonReader()
    private val scalarWriter = IdealJsonWriter()
    private val vectorReader = IdealJsonReader(vectorized = true)
    private val vectorWriter = IdealJsonWriter(true)

    @Setup
    fun setup() {
        check(VectorApi.enabled)
        val text = when (payload) {
            "ASCII_SMALL" -> "order=12345 price=65432.125 quantity=0.001 side=BUY ".repeat(4)
            "UTF8_SMALL" -> "order=123 Київ café 日本語 🙂 ".repeat(4)
            "ESCAPED_SMALL" -> "order=\"123\" path=C:\\orders\\new\n\tstatus=ok\u0000 ".repeat(4)
            "UTF8_LARGE" -> "order=123 Київ café 日本語 🙂 ".repeat(1024)
            else -> error(payload)
        }
        utf8Value = JsonUtf8Sample(
            id = 123456789L,
            symbol = Utf8Str.allocateFromString("BTCUSDT"),
            text = Utf8Str.allocateFromString(text),
            sequence = 42,
            active = true,
        )
        nativeValue =
            JsonNativeSample(id = 123456789L, symbol = "BTCUSDT", text = utf8Value.text, sequence = 42, active = true)
        val expectedOutput = ReusableByteArraySink(512)
        IdealJsonUtf8Codec.write(writer = scalarWriter, value = utf8Value, output = expectedOutput)
        val bytes = expectedOutput.unsafeUseBytes { data, length -> data.copyOf(length) }
        input = ByteArrayInputStream(bytes)
        streamInput = InputStreamByteSource(input)
        output = RecycledOutputStream(bytes.size + 64)
        streamOutput = OutputStreamByteSink(output)
        check(idealUtf8Read() == utf8Value)
        check(vectorUtf8Read() == utf8Value)
        check(nativeUtf8Read() == nativeValue)
        idealUtf8Write()
        check(output.toByteArray().contentEquals(bytes))
        vectorUtf8Write()
        check(output.toByteArray().contentEquals(bytes))
        nativeUtf8Write()
        check(output.toByteArray().contentEquals(bytes))
        println("JSON payload: $payload, ${bytes.size} bytes")
    }

    @Benchmark
    fun idealUtf8Write(): RecycledOutputStream {
        output.reset()
        IdealJsonUtf8Codec.write(writer = scalarWriter, value = utf8Value, output = streamOutput)
        return output
    }

    @Benchmark
    fun idealUtf8Read(): JsonUtf8Sample {
        input.reset()
        return IdealJsonUtf8Codec.read(reader = scalarReader, input = streamInput)
    }

    @Benchmark
    fun vectorUtf8Write(): RecycledOutputStream {
        output.reset()
        IdealJsonUtf8Codec.write(writer = vectorWriter, value = utf8Value, output = streamOutput)
        return output
    }

    @Benchmark
    fun vectorUtf8Read(): JsonUtf8Sample {
        input.reset()
        return IdealJsonUtf8Codec.read(reader = vectorReader, input = streamInput)
    }

    @Benchmark
    fun nativeUtf8Write(): RecycledOutputStream {
        output.reset()
        NativeJsonUtf8Codec.write(writer = vectorWriter, value = nativeValue, output = streamOutput)
        return output
    }

    @Benchmark
    fun nativeUtf8Read(): JsonNativeSample {
        input.reset()
        return NativeJsonUtf8Codec.read(reader = vectorReader, input = streamInput)
    }
}

class RecycledOutputStream(initialCapacity: Int) : OutputStream() {
    private var bytes = ByteArray(initialCapacity)
    private var position = 0

    fun reset() {
        position = 0
    }

    fun toByteArray(): ByteArray = bytes.copyOfRange(0, position)

    override fun write(value: Int) {
        ensureCapacity(1)
        bytes[position++] = value.toByte()
    }

    override fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (offset < 0 || length < 0 || offset > source.size - length) {
            throw IndexOutOfBoundsException()
        }
        ensureCapacity(length)
        System.arraycopy(source, offset, bytes, position, length)
        position += length
    }

    private fun ensureCapacity(additionalBytes: Int) {
        if (additionalBytes <= bytes.size - position) {
            return
        }
        val requiredCapacity = position.toLong() + additionalBytes
        if (requiredCapacity > Int.MAX_VALUE) {
            throw OutOfMemoryError("Required buffer capacity exceeds Int.MAX_VALUE")
        }
        val newCapacity = maxOf(requiredCapacity, bytes.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        bytes = bytes.copyOf(newCapacity)
    }
}
