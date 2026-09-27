package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.lang.text.Utf8Str
import java.io.InputStream
import java.io.OutputStream

object NativeJsonUtf8Codec {
    private val readers = ThreadLocal.withInitial { IdealJsonReader() }
    private val writers = ThreadLocal.withInitial { IdealJsonWriter() }
    private val vectorReaders = ThreadLocal.withInitial { IdealJsonReader(vectorized = true) }
    private val vectorWriters = ThreadLocal.withInitial { IdealJsonWriter(true) }
    private val idName = "id".encodeToByteArray()
    private val symbolName = "symbol".encodeToByteArray()
    private val textName = "text".encodeToByteArray()
    private val sequenceName = "sequence".encodeToByteArray()
    private val activeName = "active".encodeToByteArray()

    fun writeToStream(value: JsonNativeSample, output: OutputStream) {
        write(writer = writers.get(), value = value, output = output)
    }

    fun writeVectorToStream(value: JsonNativeSample, output: OutputStream) {
        write(writer = vectorWriters.get(), value = value, output = output)
    }

    private fun write(
        writer: IdealJsonWriter,
        value: JsonNativeSample,
        output: OutputStream,
    ) {
        val maximumSize = 87L + value.symbol.length.toLong() * 6L + value.text.bytes.len.toLong() * 6L
        require(maximumSize <= Int.MAX_VALUE) { "JSON object is too large" }
        writer.reset(output)
        if (maximumSize <= reservedObjectLimit) {
            writer.reserveObject(maximumSize.toInt())
            writer.writeRawReserved(0x6469227b.toInt(), 0x3a22.toShort())
            writer.writeLongReserved(value.id)
            writer.writeRawReserved(0x6c6f626d7973222cL, 0x3a22.toShort())
            writer.writeStringReserved(value.symbol)
            writer.writeRawReserved(0x3a2274786574222cL)
            writer.writeUtf8Reserved(value.text)
            writer.writeRawReserved(0x6e6575716573222cL, 0x3a226563.toInt())
            writer.writeIntReserved(value.sequence)
            writer.writeBooleanObjectEndReserved(value.active)
        } else {
            writer.writeRaw(0x6469227b.toInt(), 0x3a22.toShort())
            writer.writeLong(value.id)
            writer.writeRaw(0x6c6f626d7973222cL, 0x3a22.toShort())
            writer.writeString(value.symbol)
            writer.writeRaw(0x3a2274786574222cL)
            writer.writeUtf8(value.text)
            writer.writeRaw(0x6e6575716573222cL, 0x3a226563.toInt())
            writer.writeInt(value.sequence)
            writer.reserveObject(16)
            writer.writeBooleanObjectEndReserved(value.active)
        }
        writer.flush()
    }

    fun readFromStream(input: InputStream): JsonNativeSample {
        return read(reader = readers.get(), input = input)
    }

    fun readVectorFromStream(input: InputStream): JsonNativeSample {
        return read(reader = vectorReaders.get(), input = input)
    }

    private fun readField(reader: IdealJsonReader): Int {
        val hash = reader.readField()
        return when (hash) {
            3355 if reader.fieldEquals(idName) -> 1
            -887523944 if reader.fieldEquals(symbolName) -> 2
            3556653 if reader.fieldEquals(textName) -> 4
            1349547969 if reader.fieldEquals(sequenceName) -> 8
            -1422950650 if reader.fieldEquals(activeName) -> 16
            else -> 0
        }
    }

    private fun read(reader: IdealJsonReader, input: InputStream): JsonNativeSample {
        reader.reset(input)
        require(reader.nextToken() == '{'.code) { "Expected JSON object" }
        var id = 0L
        var symbol = ""
        var text = Utf8Str.empty
        var sequence = 0
        var active = false
        var seen = 0
        var token = reader.nextToken()
        if (token != '}'.code) {
            while (true) {
                require(token == '"'.code) { "Expected JSON field name" }
                val word = reader.peekFieldWord()
                when {
                    word and 0xffffffffL == 0x3a226469L -> {
                        reader.consumeMatchedFieldColon(2)
                        id = reader.readLongReserved()
                        seen = seen or 1
                    }
                    word == 0x3a226c6f626d7973L -> {
                        reader.consumeMatchedFieldColon(6)
                        symbol = reader.readString()
                        seen = seen or 2
                    }
                    word and 0xffffffffffffL == 0x3a2274786574L -> {
                        reader.consumeMatchedFieldColon(4)
                        text = reader.readUtf8()
                        seen = seen or 4
                    }
                    word == 0x65636e6575716573L && reader.consumeFieldColon(8) -> {
                        sequence = reader.readIntReserved()
                        seen = seen or 8
                    }
                    word == 0x3a22657669746361L -> {
                        reader.consumeMatchedFieldColon(6)
                        active = reader.readBooleanReserved()
                        seen = seen or 16
                    }
                    else -> {
                        when (readField(reader)) {
                            1 -> {
                                reader.nextFieldValue()
                                id = reader.readLongReserved()
                                seen = seen or 1
                            }
                            2 -> {
                                reader.nextFieldValue()
                                symbol = reader.readString()
                                seen = seen or 2
                            }
                            4 -> {
                                reader.nextFieldValue()
                                text = reader.readUtf8()
                                seen = seen or 4
                            }
                            8 -> {
                                reader.nextFieldValue()
                                sequence = reader.readIntReserved()
                                seen = seen or 8
                            }
                            16 -> {
                                reader.nextFieldValue()
                                active = reader.readBooleanReserved()
                                seen = seen or 16
                            }
                            else -> {
                                reader.nextFieldValue()
                                reader.skipValue()
                            }
                        }
                    }
                }
                token = reader.nextFieldOrEnd()
                if (token == '}'.code) {
                    break
                }
            }
        }
        require(seen == 31) { "Missing required JSON field" }
        require(reader.nextToken() == -1) { "Unexpected trailing JSON content" }
        return JsonNativeSample(id = id, symbol = symbol, text = text, sequence = sequence, active = active)
    }

    private const val reservedObjectLimit = 32768L
}
