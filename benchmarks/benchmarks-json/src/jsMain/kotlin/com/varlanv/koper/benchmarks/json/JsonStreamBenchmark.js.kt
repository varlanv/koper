@file:Suppress("filename")

package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.json.JsonReadProtocol
import com.varlanv.koper.json.JsonReadScope
import com.varlanv.koper.json.JsonWriteProtocol
import com.varlanv.koper.json.JsonWriteScope
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.bin.StringSource
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.allocateString
import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser
import kotlin.js.JSON
import kotlin.random.Random
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json as KotlinxJson

@State(Scope.Benchmark)
class JsonStreamBenchmark {
    @Param("ASCII", "ESCAPED", "BIG")
    var payload = "ASCII"

    private lateinit var samples: Array<JsonJsBenchmarkInput>
    private var sampleIndex = 0
    private val output = ReusableByteArraySink(512.bytes())
    private val writeScope = JsonWriteScope()
    private val readScope = JsonReadScope(512.bytes())

    @Setup
    fun setup() {
        val random = Random(42)
        samples = Array(16) {
            val sequence = random.nextInt(-1_000_000, 1_000_000)
            val suffix = random.nextInt().toString(16)
            val text = when (payload) {
                "ASCII" -> {
                    "order=$sequence text=$suffix"
                }
                "ESCAPED" -> {
                    "order=\"$sequence\" path=C:\\orders\\$suffix\n\tstatus=ok\u0000"
                }
                "BIG" -> {
                    "order=$sequence Київ café 日本語 🙂 $suffix ".repeat(random.nextInt(1024, 4097))
                }
                else -> {
                    error(payload)
                }
            }
            val value = JsonJsSample(active = random.nextBoolean(), text = text, sequence = sequence)
            val native = js("({active: false, text: '', sequence: 0})")
            native.active = value.active
            native.text = text
            native.sequence = value.sequence
            val jsonText = JSON.stringify(native)
            JsonJsBenchmarkInput(value = value, nativeValue = native, jsonText = jsonText)
        }
        sampleIndex = 0
        repeat(2) {
            for (sample in samples) {
                check(generatedRead() == sample.value)
            }
            for (sample in samples) {
                check(generatedWrite() == sample.jsonText)
            }
            for (sample in samples) {
                check(nativeWrite() == sample.jsonText)
            }
            for (sample in samples) {
                check(JSON.stringify(nativeRead()) == sample.jsonText)
            }
            for (sample in samples) {
                check(kotlinxRead() == sample.value)
            }
            for (sample in samples) {
                check(kotlinxWrite() == sample.jsonText)
            }
        }
    }

    @Benchmark
    fun generatedRead(): JsonJsSample {
        val sample = nextSample()
        readScope.position = 0
        readScope.limit = 0
        readScope.last = -1
        return readScope.scoped(StringSource(sample.jsonText)) {
            JsonReadProtocol.nextToken()
            JsonJsSampleJsonCodec.read()
        }
    }

    @Benchmark
    fun generatedWrite(): String {
        val sample = nextSample()
        output.reset()
        writeScope.scoped(output) {
            JsonJsSampleJsonCodec.write(sample.value)
            JsonWriteProtocol.flush()
        }
        return output.useBytes { bytes, length ->
            Charset.Utf8.allocateString(bytes = bytes, offset = 0, len = length)
        }
    }

    @Benchmark
    fun nativeRead(): Any = JSON.parse(nextSample().jsonText)

    @Benchmark
    fun nativeWrite(): String = JSON.stringify(nextSample().nativeValue)

    @Benchmark
    fun kotlinxRead(): JsonJsSample = KotlinxJson.decodeFromString(JsonJsSample.serializer(), nextSample().jsonText)

    @Benchmark
    fun kotlinxWrite(): String = KotlinxJson.encodeToString(JsonJsSample.serializer(), nextSample().value)

    private fun nextSample(): JsonJsBenchmarkInput {
        val sample = samples[sampleIndex]
        sampleIndex = (sampleIndex + 1) % samples.size
        return sample
    }
}

@Ser
@De
@Serializable
data class JsonJsSample(
    val active: Boolean,
    val text: String,
    val sequence: Int,
)

private class JsonJsBenchmarkInput(
    val value: JsonJsSample,
    val nativeValue: Any,
    val jsonText: String,
)
