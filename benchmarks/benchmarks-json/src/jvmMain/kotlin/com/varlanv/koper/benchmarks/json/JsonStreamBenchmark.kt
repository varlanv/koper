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
    private lateinit var dslMixedValue: DslMixedSample
    private lateinit var dslStringValue: DslStringSample
    private lateinit var dslUtf8Value: DslUtf8Sample
    private lateinit var dslUtf8DirectValue: DslUtf8DirectSample
    private lateinit var dslStringParent: DslJsonParent<DslStringSample>
    private lateinit var dslMixedParent: DslJsonParent<DslMixedSample>
    private lateinit var dslUtf8Parent: DslJsonParent<DslUtf8Sample>
    private lateinit var dslUtf8DirectParent: DslJsonParent<DslUtf8DirectSample>
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
        dslMixedValue =
            DslMixedSample(
                id = mixedValue.id,
                symbol = mixedValue.symbol,
                text = mixedValue.text,
                sequence = mixedValue.sequence,
                active = mixedValue.active,
            )
        dslStringValue =
            DslStringSample(
                id = strValue.id,
                symbol = mixedValue.symbol,
                text = text,
                sequence = strValue.sequence,
                active = strValue.active,
            )
        dslUtf8Value =
            DslUtf8Sample(
                id = strValue.id,
                symbol = strValue.symbol,
                text = strValue.text,
                sequence = strValue.sequence,
                active = strValue.active,
            )
        dslUtf8DirectValue =
            DslUtf8DirectSample(
                id = strValue.id,
                symbol = strValue.symbol,
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

        dslStringParent = object : DslJsonParent<DslStringSample>(DslStringSample::class) {}
        dslMixedParent = object : DslJsonParent<DslMixedSample>(DslMixedSample::class) {}
        dslUtf8Parent = object : DslJsonParent<DslUtf8Sample>(DslUtf8Sample::class) {}
        dslUtf8DirectParent = object : DslJsonParent<DslUtf8DirectSample>(DslUtf8DirectSample::class) {}
        writeScope = JsonWriteScope()

        val expectedOutput = ReusableByteArraySink(512.bytes())
        writeScope.scoped(expectedOutput) {
            JsonStrSampleJsonCodec.write(strValue)
            JsonWriteProtocol.flush()
        }
        val bytes = expectedOutput.useBytes { data, length -> data.copyOf(length) }
        val arr = Bytes.unsafe { useInternal(bytes) { it } }
        input = ByteArrayInputStream(arr)
        streamInput = InputStreamByteSource(input)
        output = RecycledOutputStream(bytes.size + 64)
        streamOutput = OutputStreamByteSink(output)
        parseScope = JsonReadScope(32.kilobytes())

        check(generatedReadUtf8() == strValue)
        check(generatedReadMixed() == mixedValue)
        check(generatedReadString() == stringValue)
        generatedWriteUtf8()
        check(output.toByteArray().contentEquals(arr))
        generatedWriteString()
        check(output.toByteArray().contentEquals(arr))
        generatedWriteMixed()
        check(output.toByteArray().contentEquals(arr))
        check(idealReadUtf8() == strValue)
        check(mixedReadIdeal() == mixedValue)
        idealWriteUtf8()
        check(output.toByteArray().contentEquals(arr))
        mixedWriteIdeal()
        check(output.toByteArray().contentEquals(arr))
        check(dslReadString() == dslStringValue)
        check(dslReadMixed() == dslMixedValue)
        check(dslReadUtf8() == dslUtf8Value)
        check(dslReadDirectUtf8() == dslUtf8DirectValue)
        dslWriteString()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == strValue)
        dslWriteMixed()
        check(MixedJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == mixedValue)
        dslWriteUtf8()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == strValue)
        dslWriteDirectUtf8()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == strValue)
    }

    @Benchmark
    fun generatedWriteUtf8(): RecycledOutputStream {
        output.reset()
        writeScope.scoped(streamOutput) {
            JsonStrSampleJsonCodec.write(strValue)
            JsonWriteProtocol.flush()
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
            JsonStringSampleJsonCodec.write(stringValue)
            JsonWriteProtocol.flush()
        }
        return output
    }

    @Benchmark
    fun generatedWriteMixed(): RecycledOutputStream {
        output.reset()
        writeScope.scoped(streamOutput) {
            JsonMixedSampleJsonCodec.write(mixedValue)
            JsonWriteProtocol.flush()
        }
        return output
    }

    @Benchmark
    fun generatedReadMixed(): JsonMixedSample {
        input.reset()
        lateinit var value: JsonMixedSample
        parseScope.scoped(streamInput) {
            JsonReadProtocol.nextToken()
            value = JsonMixedSampleJsonCodec.read()
            check(JsonReadProtocol.nextToken() == -1)
        }
        return value
    }

    @Benchmark
    fun idealWriteUtf8(): RecycledOutputStream {
        output.reset()
        IdealJsonUtf8Codec.writeVectorToStream(value = strValue, output = output)
        return output
    }

    @Benchmark
    fun idealReadUtf8(): JsonStrSample {
        input.reset()
        return IdealJsonUtf8Codec.readVectorFromStream(input)
    }

    @Benchmark
    fun mixedWriteIdeal(): RecycledOutputStream {
        output.reset()
        MixedJsonUtf8Codec.writeVectorToStream(value = mixedValue, output = output)
        return output
    }

    @Benchmark
    fun mixedReadIdeal(): JsonMixedSample {
        input.reset()
        return MixedJsonUtf8Codec.readVectorFromStream(input)
    }

    @Benchmark
    fun dslWriteString(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslStringParent, value = dslStringValue, stream = output)
        return output
    }

    @Benchmark
    fun dslReadString(): DslStringSample {
        input.reset()
        return JsonV2.readFromStream(parent = dslStringParent, stream = input)
    }

    @Benchmark
    fun dslWriteMixed(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslMixedParent, value = dslMixedValue, stream = output)
        return output
    }

    @Benchmark
    fun dslReadMixed(): DslMixedSample {
        input.reset()
        return JsonV2.readFromStream(parent = dslMixedParent, stream = input)
    }

    @Benchmark
    fun dslWriteUtf8(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslUtf8Parent, value = dslUtf8Value, stream = output)
        return output
    }

    @Benchmark
    fun dslReadUtf8(): DslUtf8Sample {
        input.reset()
        return JsonV2.readFromStream(parent = dslUtf8Parent, stream = input)
    }

    @Benchmark
    fun dslWriteDirectUtf8(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslUtf8DirectParent, value = dslUtf8DirectValue, stream = output)
        return output
    }

    @Benchmark
    fun dslReadDirectUtf8(): DslUtf8DirectSample {
        input.reset()
        return JsonV2.readFromStream(parent = dslUtf8DirectParent, stream = input)
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
