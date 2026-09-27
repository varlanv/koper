package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.json.JsonReadProtocol
import com.varlanv.koper.json.JsonWriteProtocol
import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.bin.InputStreamByteSource
import com.varlanv.koper.lang.bin.OutputStreamByteSink
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.text.Utf8Str
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.io.OutputStream

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = ["--add-modules=jdk.incubator.vector", "-Dkoper.lang.utf8.vector.enabled=true"])
class JsonStreamBenchmark {
    @Param("ASCII_SMALL", "UTF8_SMALL", "ESCAPED_SMALL", "UTF8_LARGE")
    @JvmField
    final var payload: String = ""

    @Param("true")
    @JvmField
    final var vectorized: Boolean = false

    private lateinit var utf8Value: JsonUtf8Sample
    private lateinit var mixedValue: JsonMixedSample
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
    private lateinit var reader: JsonReadProtocol
    private lateinit var writer: JsonWriteProtocol

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
        mixedValue =
            JsonMixedSample(
                id = utf8Value.id,
                symbol = "BTCUSDT",
                text = utf8Value.text,
                sequence = utf8Value.sequence,
                active = utf8Value.active,
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
                id = utf8Value.id,
                symbol = mixedValue.symbol,
                text = text,
                sequence = utf8Value.sequence,
                active = utf8Value.active,
            )
        dslUtf8Value =
            DslUtf8Sample(
                id = utf8Value.id,
                symbol = utf8Value.symbol,
                text = utf8Value.text,
                sequence = utf8Value.sequence,
                active = utf8Value.active,
            )
        dslUtf8DirectValue =
            DslUtf8DirectSample(
                id = utf8Value.id,
                symbol = utf8Value.symbol,
                text = utf8Value.text,
                sequence = utf8Value.sequence,
                active = utf8Value.active,
            )
        dslStringParent = object : DslJsonParent<DslStringSample>(DslStringSample::class) {}
        dslMixedParent = object : DslJsonParent<DslMixedSample>(DslMixedSample::class) {}
        dslUtf8Parent = object : DslJsonParent<DslUtf8Sample>(DslUtf8Sample::class) {}
        dslUtf8DirectParent = object : DslJsonParent<DslUtf8DirectSample>(DslUtf8DirectSample::class) {}
        reader = JsonReadProtocol(vectorized = vectorized)
        writer = JsonWriteProtocol(vectorized)

        val expectedOutput = ReusableByteArraySink(512)
        writer.reset(expectedOutput)
        JsonUtf8SampleJsonCodec.write(writer = writer, value = utf8Value)
        writer.flush()
        val bytes = expectedOutput.unsafeUseBytes { data, length -> data.copyOf(length) }
        input = ByteArrayInputStream(bytes)
        streamInput = InputStreamByteSource(input)
        output = RecycledOutputStream(bytes.size + 64)
        streamOutput = OutputStreamByteSink(output)

        check(utf8Read() == utf8Value)
        check(reader.nextToken() == -1)
        check(mixedRead() == mixedValue)
        check(reader.nextToken() == -1)
        utf8Write()
        check(output.toByteArray().contentEquals(bytes))
        mixedWrite()
        check(output.toByteArray().contentEquals(bytes))
        check(idealUtf8Read() == utf8Value)
        check(mixedIdealRead() == mixedValue)
        idealUtf8Write()
        check(output.toByteArray().contentEquals(bytes))
        mixedIdealWrite()
        check(output.toByteArray().contentEquals(bytes))
        check(dslStringRead() == dslStringValue)
        check(dslMixedRead() == dslMixedValue)
        check(dslUtf8Read() == dslUtf8Value)
        check(dslUtf8DirectRead() == dslUtf8DirectValue)
        dslStringWrite()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == utf8Value)
        dslMixedWrite()
        check(MixedJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == mixedValue)
        dslUtf8Write()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == utf8Value)
        dslUtf8DirectWrite()
        check(IdealJsonUtf8Codec.readVectorFromStream(ByteArrayInputStream(output.toByteArray())) == utf8Value)
    }

    @Benchmark
    fun utf8Write(): RecycledOutputStream {
        output.reset()
        writer.reset(streamOutput)
        JsonUtf8SampleJsonCodec.write(writer = writer, value = utf8Value)
        writer.flush()
        return output
    }

    @Benchmark
    fun utf8Read(): JsonUtf8Sample {
        input.reset()
        reader.reset(streamInput)
        reader.nextToken()
        val value = JsonUtf8SampleJsonCodec.read(reader)
        check(reader.nextToken() == -1)
        return value
    }

    @Benchmark
    fun mixedWrite(): RecycledOutputStream {
        output.reset()
        writer.reset(streamOutput)
        JsonMixedSampleJsonCodec.write(writer = writer, value = mixedValue)
        writer.flush()
        return output
    }

    @Benchmark
    fun mixedRead(): JsonMixedSample {
        input.reset()
        reader.reset(streamInput)
        reader.nextToken()
        val value = JsonMixedSampleJsonCodec.read(reader)
        check(reader.nextToken() == -1)
        return value
    }

    @Benchmark
    fun idealUtf8Write(): RecycledOutputStream {
        output.reset()
        IdealJsonUtf8Codec.writeVectorToStream(value = utf8Value, output = output)
        return output
    }

    @Benchmark
    fun idealUtf8Read(): JsonUtf8Sample {
        input.reset()
        return IdealJsonUtf8Codec.readVectorFromStream(input)
    }

    @Benchmark
    fun mixedIdealWrite(): RecycledOutputStream {
        output.reset()
        MixedJsonUtf8Codec.writeVectorToStream(value = mixedValue, output = output)
        return output
    }

    @Benchmark
    fun mixedIdealRead(): JsonMixedSample {
        input.reset()
        return MixedJsonUtf8Codec.readVectorFromStream(input)
    }

    @Benchmark
    fun dslStringWrite(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslStringParent, value = dslStringValue, stream = output)
        return output
    }

    @Benchmark
    fun dslStringRead(): DslStringSample {
        input.reset()
        return JsonV2.readFromStream(parent = dslStringParent, stream = input)
    }

    @Benchmark
    fun dslMixedWrite(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslMixedParent, value = dslMixedValue, stream = output)
        return output
    }

    @Benchmark
    fun dslMixedRead(): DslMixedSample {
        input.reset()
        return JsonV2.readFromStream(parent = dslMixedParent, stream = input)
    }

    @Benchmark
    fun dslUtf8Write(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslUtf8Parent, value = dslUtf8Value, stream = output)
        return output
    }

    @Benchmark
    fun dslUtf8Read(): DslUtf8Sample {
        input.reset()
        return JsonV2.readFromStream(parent = dslUtf8Parent, stream = input)
    }

    @Benchmark
    fun dslUtf8DirectWrite(): RecycledOutputStream {
        output.reset()
        JsonV2.writeToStream(parent = dslUtf8DirectParent, value = dslUtf8DirectValue, stream = output)
        return output
    }

    @Benchmark
    fun dslUtf8DirectRead(): DslUtf8DirectSample {
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
