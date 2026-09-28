package com.varlanv.koper.benchmarks.json

import com.dslplatform.json.DslJson
import com.dslplatform.json.JsonConverter
import com.dslplatform.json.JsonReader
import com.dslplatform.json.JsonWriter
import com.dslplatform.json.runtime.Settings
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Str
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass

val dslJson = DslJson(Settings.basicSetup<Any>().allowArrayFormat(true))
private val dslJsonWriters = ThreadLocal.withInitial({ dslJson.newWriter() })
private val dslJsonReaders = ThreadLocal.withInitial {
    dslJson.newReader(ByteArray(32 * 1024))
}

abstract class DslJsonParent<T : Any>(type: KClass<T>) {
    private val t = type.java

    val readObject = dslJson.tryFindReader(t)!!
    val writeObject = dslJson.tryFindWriter(t)!!
}

object JsonV2 {
    fun <T : Any> writeToStream(
        parent: DslJsonParent<T>,
        value: T,
        stream: OutputStream,
    ) {
        val w = dslJsonWriters.get()
        w.reset(stream)

        parent.writeObject.write(w, value)
        w.flush()
    }

    fun <T : Any> readFromStream(parent: DslJsonParent<T>, stream: InputStream): T {
        val r = dslJsonReaders.get()
        r.process(stream)
        r.getNextToken()
        return parent.readObject.read(r)!!
    }
}

@JsonConverter(target = Str::class)
object StrConverter {
    private val readerBuffer = JsonReader::class.java.getDeclaredField("buffer").apply { isAccessible = true }
    private val readerIndex = JsonReader::class.java.getDeclaredField("currentIndex").apply { isAccessible = true }
    private const val hexDigits = "0123456789abcdef"
    private val readBuffers = ThreadLocal.withInitial { ByteArray(256) }
    private val writeBuffers = ThreadLocal.withInitial { ByteArray(512) }

    @JvmStatic
    @JvmExposeBoxed
    @OptIn(ExperimentalStdlibApi::class)
    fun read(reader: JsonReader<*>): Str {
        if (reader.last() != '"'.code.toByte()) {
            throw reader.newParseError("Expected a JSON string")
        }
        val bytes = readerBuffer.get(reader) as ByteArray
        val start = reader.currentIndex
        val end = start + minOf(512, reader.length() - start)
        var index = start
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code) {
                readerIndex.setInt(reader, index + 1)
                return if (index == start) {
                    Str.empty
                } else {
                    Str.wrapBytes(
                        bytes = Bytes(MutBytes(bytes.copyOfRange(start, index))),
                        offset = 0,
                        len = index - start,
                    )
                }
            }
            if (b == '\\'.code || b < 0x20) {
                break
            }
            index++
        }
        return readBuffered(reader)
    }

    private fun readBuffered(reader: JsonReader<*>): Str {
        val initialBuffer = readBuffers.get()
        var buffer = initialBuffer
        var size = 0
        while (true) {
            var b = reader.read().toInt() and 0xFF
            if (b == '"'.code) {
                if (buffer !== initialBuffer) {
                    readBuffers.set(buffer)
                }
                return if (size == 0) {
                    Str.empty
                } else {
                    Str.wrapBytes(
                        bytes = Bytes(MutBytes(buffer.copyOf(size))),
                        offset = 0,
                        len = size,
                    )
                }
            }
            if (b < 0x20) {
                throw reader.newParseError("Unescaped control character in JSON string")
            }
            if (b == '\\'.code) {
                b = when (val escape = reader.read().toInt()) {
                    '"'.code, '\\'.code, '/'.code -> escape
                    'b'.code -> 0x08
                    'f'.code -> 0x0C
                    'n'.code -> 0x0A
                    'r'.code -> 0x0D
                    't'.code -> 0x09
                    'u'.code -> readCodePoint(reader)
                    else -> throw reader.newParseError("Invalid JSON string escape")
                }
                if (b > 0x7F) {
                    if (buffer.size - size < 4) {
                        buffer = buffer.copyOf(buffer.size * 2)
                    }
                    size = writeCodePoint(buffer = buffer, offset = size, codePoint = b)
                    continue
                }
            }
            if (size == buffer.size) {
                buffer = buffer.copyOf(buffer.size * 2)
            }
            buffer[size++] = b.toByte()
        }
    }

    private fun writeCodePoint(
        buffer: ByteArray,
        offset: Int,
        codePoint: Int,
    ): Int {
        var size = offset
        Charset.encodeUtf8Inline(codePoint) { buffer[size++] = it }
        return size
    }

    private fun readCodePoint(reader: JsonReader<*>): Int {
        val first = readHexQuad(reader)
        if (first in 0xD800..0xDBFF) {
            if (reader.read() != '\\'.code.toByte() || reader.read() != 'u'.code.toByte()) {
                throw reader.newParseError("Expected an escaped low surrogate")
            }
            val second = readHexQuad(reader)
            if (second !in 0xDC00..0xDFFF) {
                throw reader.newParseError("Invalid low surrogate")
            }
            return 0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
        }
        if (first in 0xDC00..0xDFFF) {
            throw reader.newParseError("Unexpected low surrogate")
        }
        return first
    }

    private fun readHexQuad(reader: JsonReader<*>): Int {
        var value = 0
        repeat(4) {
            val digit = when (val b = reader.read().toInt()) {
                in '0'.code..'9'.code -> b - '0'.code
                in 'a'.code..'f'.code -> b - 'a'.code + 10
                in 'A'.code..'F'.code -> b - 'A'.code + 10
                else -> throw reader.newParseError("Invalid hexadecimal digit in JSON string escape")
            }
            value = (value shl 4) or digit
        }
        return value
    }

    @JvmStatic
    @JvmExposeBoxed
    @OptIn(ExperimentalStdlibApi::class)
    fun write(writer: JsonWriter, value: Str) {
        val slice = value.slice
        val bytes = slice.bytes
        val end = slice.offset + slice.len
        var index = slice.offset
        writer.writeByte('"'.code.toByte())
        while (index < end) {
            val b = bytes[index].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                if (index > slice.offset) {
                    Bytes.unsafe { useInternal(bytes) { writer.writeRaw(it, slice.offset, index - slice.offset) } }
                }
                writeEscaped(writer = writer, bytes = bytes, start = index, end = end)
                writer.writeByte('"'.code.toByte())
                return
            }
            index++
        }
        if (slice.len > 0) {
            Bytes.unsafe { useInternal(bytes) { writer.writeRaw(it, slice.offset, slice.len) } }

        }
        writer.writeByte('"'.code.toByte())
    }

    private fun writeEscaped(
        writer: JsonWriter,
        bytes: Bytes,
        start: Int,
        end: Int,
    ) {
        val initialBuffer = writeBuffers.get()
        var buffer = initialBuffer
        var size = 0
        var index = start
        while (index < end) {
            if (buffer.size - size < 6) {
                buffer = buffer.copyOf(buffer.size * 2)
            }
            val b = bytes[index++].toInt() and 0xFF
            if (b == '"'.code || b == '\\'.code || b < 0x20) {
                buffer[size++] = '\\'.code.toByte()
                val escaped = when (b) {
                    '"'.code, '\\'.code -> {
                        b.toByte()
                    }

                    0x08 -> {
                        'b'.code.toByte()
                    }

                    0x0C -> {
                        'f'.code.toByte()
                    }

                    0x0A -> {
                        'n'.code.toByte()
                    }

                    0x0D -> {
                        'r'.code.toByte()
                    }

                    0x09 -> {
                        't'.code.toByte()
                    }

                    else -> {
                        buffer[size++] = 'u'.code.toByte()
                        buffer[size++] = '0'.code.toByte()
                        buffer[size++] = '0'.code.toByte()
                        buffer[size++] = hexDigits[b ushr 4].code.toByte()
                        hexDigits[b and 0x0F].code.toByte()
                    }
                }
                buffer[size++] = escaped
            } else {
                buffer[size++] = b.toByte()
            }
        }
        if (buffer !== initialBuffer) {
            writeBuffers.set(buffer)
        }
        writer.writeRaw(buffer, 0, size)
    }
}
