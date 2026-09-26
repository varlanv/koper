package com.varlanv.koper.json

import com.varlanv.koper.lang.text.Utf8Str

sealed interface JsonValueSize<in T> {
    data class Static(val maximumBytes: Long) : JsonValueSize<Any?>

    class FromValue<T>(val maximumBytes: (T) -> Long) : JsonValueSize<T>

    data object Dynamic : JsonValueSize<Any?>
}

interface JsonValueWriter<T> {
    val size: JsonValueSize<T>

    fun write(writer: JsonWriter, value: T)
}

interface JsonValueReader<T> {
    fun read(reader: JsonReader): T
}

object IntJsonCodec : JsonValueWriter<Int>, JsonValueReader<Int> {
    override val size = JsonValueSize.Static(11)

    override fun write(writer: JsonWriter, value: Int) = writer.writeInt(value)

    override fun read(reader: JsonReader): Int = reader.readInt()
}

object LongJsonCodec : JsonValueWriter<Long>, JsonValueReader<Long> {
    override val size = JsonValueSize.Static(20)

    override fun write(writer: JsonWriter, value: Long) = writer.writeLong(value)

    override fun read(reader: JsonReader): Long = reader.readLong()
}

object BooleanJsonCodec : JsonValueWriter<Boolean>, JsonValueReader<Boolean> {
    override val size = JsonValueSize.Static(5)

    override fun write(writer: JsonWriter, value: Boolean) = writer.writeBoolean(value)

    override fun read(reader: JsonReader): Boolean = reader.readBoolean()
}

object StringJsonCodec : JsonValueWriter<String>, JsonValueReader<String> {
    override val size = JsonValueSize.FromValue<String> { value -> 2L + value.length.toLong() * 6L }

    override fun write(writer: JsonWriter, value: String) = writer.writeString(value)

    override fun read(reader: JsonReader): String = reader.readString()
}

object Utf8StrJsonCodec : JsonValueWriter<Utf8Str>, JsonValueReader<Utf8Str> {
    override val size = JsonValueSize.FromValue<Utf8Str> { value -> 2L + value.bytes.len.toLong() * 6L }

    override fun write(writer: JsonWriter, value: Utf8Str) = writer.writeUtf8(value)

    override fun read(reader: JsonReader): Utf8Str = reader.readUtf8()
}
