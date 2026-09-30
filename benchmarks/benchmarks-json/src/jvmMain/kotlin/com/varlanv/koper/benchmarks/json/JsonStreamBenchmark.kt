package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.json.JsonReadProtocol
import com.varlanv.koper.json.JsonReadScope
import com.varlanv.koper.json.JsonWriteProtocol
import com.varlanv.koper.json.JsonWriteScope
import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.bin.*
import com.varlanv.koper.lang.text.Utf8Str
import java.io.ByteArrayInputStream
import java.io.OutputStream
import org.openjdk.jmh.annotations.*

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = ["--add-modules=jdk.incubator.vector", "-Dkoper.lang.utf8.vector.enabled=true"])
class JsonStreamBenchmark {
    @Param("ASCII_SMALL", "UTF8_SMALL", "ESCAPED_SMALL", "UTF8_LARGE")
    @JvmField
    final var payload: String = ""

    private lateinit var strValue: JsonStrSample
    private lateinit var mixedValue: JsonMixedSample
    private lateinit var stringValue: JsonStringSample
    private lateinit var input: ByteArrayInputStream
    private lateinit var streamInput: InputStreamByteSource
    private lateinit var output: RecycledOutputStream
    private lateinit var streamOutput: OutputStreamByteSink
    private lateinit var writeScope: JsonWriteScope
    private lateinit var parseScope: JsonReadScope

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
        strValue = JsonStrSample(
            id = 123456789L,
            symbol = Utf8Str.allocateFromString(string = "BTCUSDT"),
            text = Utf8Str.allocateFromString(string = text),
            sequence = 42,
            active = true,
        )
        mixedValue =
            JsonMixedSample(
                id = strValue.id,
                symbol = "BTCUSDT",
                text = strValue.text,
                sequence = strValue.sequence,
                active = strValue.active,
            )
        stringValue =
            JsonStringSample(
                id = strValue.id,
                symbol = mixedValue.symbol,
                text = text,
                sequence = strValue.sequence,
                active = strValue.active,
            )

        writeScope = JsonWriteScope()

        val expectedOutput = ReusableByteArraySink(512.bytes())
        writeScope.scoped(expectedOutput) {
            val position = JsonStrSampleJsonCodec.write(value = strValue, position = 0)
            JsonWriteProtocol.flush(position)
        }
        val bytes = expectedOutput.useBytes { data, length -> data.copyOf(length) }
        val arr = Bytes.unsafe { useInternal(bytes) { it } }
        input = ByteArrayInputStream(arr)
        streamInput = InputStreamByteSource(input)
        output = RecycledOutputStream(bytes.size + 64)
        streamOutput = OutputStreamByteSink(output)
        parseScope = JsonReadScope(32.kilobytes())

        check(generatedReadUtf8() == strValue)
        check(generatedReadString() == stringValue)
        generatedWriteUtf8()
        check(output.toByteArray().contentEquals(arr))
        generatedWriteString()
        check(output.toByteArray().contentEquals(arr))
    }

    @Benchmark
    fun generatedWriteUtf8(): RecycledOutputStream {
        output.reset()
        writeScope.scoped(streamOutput) {
            val position = JsonStrSampleJsonCodec.write(value = strValue, position = 0)
            JsonWriteProtocol.flush(position)
        }
        return output
    }

    @Benchmark
    fun generatedReadUtf8(): JsonStrSample {
        input.reset()
        lateinit var value: JsonStrSample
        parseScope.scoped(streamInput) {
            JsonReadProtocol.nextToken()
            value = JsonStrSampleJsonCodec.read()
            check(JsonReadProtocol.nextToken() == -1)
        }
        return value
    }

    @Benchmark
    fun generatedReadString(): JsonStringSample {
        input.reset()
        lateinit var value: JsonStringSample
        parseScope.scoped(streamInput) {
            JsonReadProtocol.nextToken()
            value = JsonStringSampleJsonCodec.read()
            check(JsonReadProtocol.nextToken() == -1)
        }
        return value
    }

    @Benchmark
    fun generatedWriteString(): RecycledOutputStream {
        output.reset()
        writeScope.scoped(streamOutput) {
            val position = JsonStringSampleJsonCodec.write(value = stringValue, position = 0)
            JsonWriteProtocol.flush(position)
        }
        return output
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
