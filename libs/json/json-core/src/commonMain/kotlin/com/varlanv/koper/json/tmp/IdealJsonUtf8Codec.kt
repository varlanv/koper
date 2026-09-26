package com.varlanv.koper.json.tmp
//
// import com.varlanv.koper.lang.bin.ByteSink
// import com.varlanv.koper.lang.bin.ByteSource
// import com.varlanv.koper.lang.text.Utf8Str
//
// object IdealJsonUtf8Codec {
//    private const val swarFields = true
//    private val idName = "id".encodeToByteArray()
//    private val symbolName = "symbol".encodeToByteArray()
//    private val textName = "text".encodeToByteArray()
//    private val sequenceName = "sequence".encodeToByteArray()
//    private val activeName = "active".encodeToByteArray()
//    private val idPrefix = "{\"id\":".encodeToByteArray()
//    private val symbolPrefix = ",\"symbol\":".encodeToByteArray()
//    private val textPrefix = ",\"text\":".encodeToByteArray()
//    private val sequencePrefix = ",\"sequence\":".encodeToByteArray()
//    private val activePrefix = ",\"active\":".encodeToByteArray()
//
//    fun writeToStream(
//        value: JsonUtf8Sample,
//        output: ByteSink,
//        writer: IdealJsonWriter = IdealJsonWriter(),
//    ) {
//        write(writer = writer, value = value, output = output)
//    }
//
//    fun writeVectorToStream(
//        value: JsonUtf8Sample,
//        output: ByteSink,
//        writer: IdealJsonWriter = IdealJsonWriter(true),
//    ) {
//        write(writer = writer, value = value, output = output)
//    }
//
//    fun write(
//        writer: IdealJsonWriter,
//        value: JsonUtf8Sample,
//        output: ByteSink,
//    ) {
//        writer.reset(output)
//        writer.writeRaw(bytes = idPrefix)
//        writer.writeLong(value.id)
//        writer.writeRaw(bytes = symbolPrefix)
//        writer.writeUtf8(value.symbol)
//        writer.writeRaw(bytes = textPrefix)
//        writer.writeUtf8(value.text)
//        writer.writeRaw(bytes = sequencePrefix)
//        writer.writeLong(value.sequence.toLong())
//        writer.writeRaw(bytes = activePrefix)
//        writer.writeBoolean(value.active)
//        writer.writeByte('}'.code)
//        writer.flush()
//    }
//
//    fun readFromStream(
//        input: ByteSource,
//        reader: IdealJsonReader = IdealJsonReader(),
//    ): JsonUtf8Sample {
//        return read(reader = reader, input = input)
//    }
//
//    fun readVectorFromStream(
//        input: ByteSource,
//        reader: IdealJsonReader = IdealJsonReader(vectorized = true),
//    ): JsonUtf8Sample {
//        return read(reader = reader, input = input)
//    }
//
//    private fun readField(reader: IdealJsonReader): Int {
//        if (swarFields) {
//            val word = reader.peekFieldWord()
//            when {
//                word and 0xffffffL == 0x226469L && reader.consumeField(2) -> return 1
//                word and 0xffffffffffffffL == 0x226c6f626d7973L && reader.consumeField(6) -> return 2
//                word and 0xffffffffffL == 0x2274786574L && reader.consumeField(4) -> return 4
//                word == 0x65636e6575716573L && reader.consumeField(8) -> return 8
//                word and 0xffffffffffffffL == 0x22657669746361L && reader.consumeField(6) -> return 16
//            }
//        }
//        val hash = reader.readField()
//        return when (hash) {
//            3355 if reader.fieldEquals(idName) -> 1
//            -887523944 if reader.fieldEquals(symbolName) -> 2
//            3556653 if reader.fieldEquals(textName) -> 4
//            1349547969 if reader.fieldEquals(sequenceName) -> 8
//            -1422950650 if reader.fieldEquals(activeName) -> 16
//            else -> 0
//        }
//    }
//
//    fun read(reader: IdealJsonReader, input: ByteSource): JsonUtf8Sample {
//        reader.reset(input)
//        require(reader.nextToken() == '{'.code) { "Expected JSON object" }
//        var id = 0L
//        var symbol = Utf8Str.empty
//        var text = Utf8Str.empty
//        var sequence = 0
//        var active = false
//        var seen = 0
//        var token = reader.nextToken()
//        if (token != '}'.code) {
//            while (true) {
//                require(token == '"'.code) { "Expected JSON field name" }
//                val field = readField(reader)
//                reader.nextFieldValue()
//                when (field) {
//                    1 -> {
//                        id = reader.readLong()
//                        seen = seen or 1
//                    }
//                    2 -> {
//                        symbol = reader.readUtf8()
//                        seen = seen or 2
//                    }
//                    4 -> {
//                        text = reader.readUtf8()
//                        seen = seen or 4
//                    }
//                    8 -> {
//                        sequence = reader.readInt()
//                        seen = seen or 8
//                    }
//                    16 -> {
//                        active = reader.readBoolean()
//                        seen = seen or 16
//                    }
//                    else -> {
//                        reader.skipValue()
//                    }
//                }
//                token = reader.nextToken()
//                if (token == '}'.code) {
//                    break
//                }
//                require(token == ','.code) { "Expected comma or closing brace" }
//                token = reader.nextToken()
//            }
//        }
//        require(seen == 31) { "Missing required JSON field" }
//        require(reader.nextToken() == -1) { "Unexpected trailing JSON content" }
//        return JsonUtf8Sample(id = id, symbol = symbol, text = text, sequence = sequence, active = active)
//    }
// }
