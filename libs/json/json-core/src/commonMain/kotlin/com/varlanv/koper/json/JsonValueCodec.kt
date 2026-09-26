package com.varlanv.koper.json

import com.varlanv.koper.lang.text.Utf8Str

sealed interface JsonValueSize<in T> {
    data class Static(val maximumBytes: Long) : JsonValueSize<Any?>

    class FromValue<T>(val maximumBytes: (T) -> Long) : JsonValueSize<T>

    data object Dynamic : JsonValueSize<Any?>
}

object JsonCodec {

    class Hints<T>(val size: JsonValueSize<T> = JsonValueSize.Dynamic)

    interface Read<T> {
        val hints: Hints<T>
        fun read(reader: JsonReadProtocol): T
    }

    interface Write<T> {
        val hints: Hints<T>
        fun write(writer: JsonWriteProtocol, value: T)
    }
}

object IntJsonCodec : JsonCodec.Read<Int>, JsonCodec.Write<Int> {
    override val hints: JsonCodec.Hints<Int> = JsonCodec.Hints(size = JsonValueSize.Static(11))

    override fun write(writer: JsonWriteProtocol, value: Int) = writer.writeInt(value)

    override fun read(reader: JsonReadProtocol): Int = reader.readInt()
}

object LongJsonCodec : JsonCodec.Write<Long>, JsonCodec.Read<Long> {
    override val hints: JsonCodec.Hints<Long> = JsonCodec.Hints(size = JsonValueSize.Static(20))

    override fun write(writer: JsonWriteProtocol, value: Long) = writer.writeLong(value)

    override fun read(reader: JsonReadProtocol): Long = reader.readLong()
}

object BooleanJsonCodec : JsonCodec.Write<Boolean>, JsonCodec.Read<Boolean> {
    override val hints: JsonCodec.Hints<Boolean> = JsonCodec.Hints(size = JsonValueSize.Static(5))

    override fun write(writer: JsonWriteProtocol, value: Boolean) = writer.writeBoolean(value)

    override fun read(reader: JsonReadProtocol): Boolean = reader.readBoolean()
}

object StringJsonCodec : JsonCodec.Write<String>, JsonCodec.Read<String> {
    override val hints: JsonCodec.Hints<String> = JsonCodec.Hints(size = JsonValueSize.FromValue { value -> 2L + value.length.toLong() * 6L })

    override fun write(writer: JsonWriteProtocol, value: String) = writer.writeString(value)

    override fun read(reader: JsonReadProtocol): String = reader.readString()
}

object Utf8StrJsonCodec : JsonCodec.Write<Utf8Str>, JsonCodec.Read<Utf8Str> {
    override val hints: JsonCodec.Hints<Utf8Str> = JsonCodec.Hints(size = JsonValueSize.FromValue { value -> 2L + value.bytes.len.toLong() * 6L })

    override fun write(writer: JsonWriteProtocol, value: Utf8Str) = writer.writeUtf8(value)

    override fun read(reader: JsonReadProtocol): Utf8Str = reader.readUtf8()
}
