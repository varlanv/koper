package com.varlanv.koper.json

import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

@Ser
@De
internal data class HandwrittenJsonSample(val id: Int, val text: String)

internal object HandwrittenJsonSampleCodec : JsonCodec.Read<HandwrittenJsonSample>, JsonCodec.Write<HandwrittenJsonSample> {
    private val idName = "id".encodeToByteArray()
    private val textName = "text".encodeToByteArray()

    override val hints = JsonCodec.Hints(
        JsonValueSize.FromValue<HandwrittenJsonSample> { value -> maximumBytes(value) },
    )

    override fun write(writer: JsonWriteProtocol, value: HandwrittenJsonSample) {
        val maximumBytes = maximumBytes(value)
        writer.reserve(maximumBytes)
        writer.writeRaw(first = 0x6469227b.toInt(), second = 0x3a22.toShort())
        IntJsonCodec.write(writer = writer, value = value.id)
        writer.writeRaw(0x3a2274786574222cL)
        StringJsonCodec.write(writer = writer, value = value.text)
        writer.writeByte('}'.code)
    }

    override fun read(reader: JsonReadProtocol): HandwrittenJsonSample {
        require(reader.token == '{'.code) { "Expected JSON object" }
        var id = 0
        var text: String? = null
        var seen = 0
        var token = reader.nextToken()
        if (token != '}'.code) {
            while (true) {
                require(token == '"'.code) { "Expected JSON field name" }
                val hash = reader.readField()
                val field = when (hash) {
                    3355 if reader.fieldEquals(idName) -> 1
                    3556653 if reader.fieldEquals(textName) -> 2
                    else -> 0
                }
                reader.nextFieldValue()
                when (field) {
                    1 -> {
                        id = IntJsonCodec.read(reader)
                        seen = seen or 1
                    }
                    2 -> {
                        text = StringJsonCodec.read(reader)
                        seen = seen or 2
                    }
                    else -> {
                        reader.skipValue()
                    }
                }
                token = reader.nextFieldOrEnd()
                if (token == '}'.code) {
                    break
                }
            }
        }
        require(seen == 3) { "Missing required JSON field" }
        return HandwrittenJsonSample(id = id, text = text ?: error("Missing text"))
    }

    private fun maximumBytes(value: HandwrittenJsonSample): Long = 28L + value.text.length.toLong() * 6L
}
