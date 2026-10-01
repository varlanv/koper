package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.skipAscii
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

actual fun Charset.allocateString(
    bytes: Bytes,
    offset: Int,
    len: Int,
): String = String(bytes.bytes.impl, offset, len, jdkEncoding())

actual fun Charset.allocateByteSlice(
    string: String,
    start: Int,
    end: Int,
): BytesSlice = SubstringFastPath.instance.slice(string = string, encoding = jdkEncoding(), start = start, end = end)

private sealed interface SubstringFastPath {
    fun slice(
        string: String,
        encoding: java.nio.charset.Charset,
        start: Int,
        end: Int,
    ): BytesSlice

    private object Default : SubstringFastPath {
        override fun slice(
            string: String,
            encoding: java.nio.charset.Charset,
            start: Int,
            end: Int,
        ): BytesSlice {
            val array = string.substring(start, end).toByteArray(encoding)
            return BytesSlice(
                bytes = Bytes(MutBytes(array)),
                offset = 0,
                len = array.size,
            )
        }
    }

    private class JdkInternal(
        private val valueGetter: MethodHandle,
        private val isLatin1: MethodHandle,
    ) : SubstringFastPath {
        init {
            require(isJdkInternalLoaded)
        }

        override fun slice(
            string: String,
            encoding: java.nio.charset.Charset,
            start: Int,
            end: Int,
        ): BytesSlice {
            if (start < 0 || end < start || end > string.length) {
                throw IndexOutOfBoundsException("range [$start, $end) exceeds string length ${string.length}")
            }
            if (start == end) {
                return BytesSlice.empty
            }
            val latin1 = isLatin1.invoke(string) as Boolean
            val source = valueGetter.invoke(string) as ByteArray
            if (latin1) {
                val bytes = Bytes(MutBytes(source))
                if (encoding == Charsets.ISO_8859_1 ||
                    ((encoding == Charsets.UTF_8 || encoding == Charsets.US_ASCII) &&
                        bytes.skipAscii(start = start, end = end) == end)
                ) {
                    return BytesSlice(bytes = bytes, offset = start, len = end - start)
                }
            }
            if (encoding == Charsets.UTF_8) {
                val maxBytesPerChar = if (latin1) {
                    2
                } else {
                    3
                }
                if (end - start <= Int.MAX_VALUE / maxBytesPerChar) {
                    return encodeUtf8(source = source, start = start, end = end, latin1 = latin1)
                }
            }
            return Default.slice(string = string, encoding = encoding, start = start, end = end)
        }

        private fun encodeUtf8(
            source: ByteArray,
            start: Int,
            end: Int,
            latin1: Boolean,
        ): BytesSlice {
            val output = ByteArray(
                (end - start) * if (latin1) {
                    2
                } else {
                    3
                },
            )
            var index = start
            var written = 0
            if (latin1) {
                while (index < end) {
                    val char = source[index++].toInt() and 0xFF
                    if (char < 0x80) {
                        output[written++] = char.toByte()
                    } else {
                        output[written++] = (0xC0 or (char ushr 6)).toByte()
                        output[written++] = (0x80 or (char and 0x3F)).toByte()
                    }
                }
            } else {
                while (index < end) {
                    val char = utf16Char(source = source, index = index)
                    if (char.code >= 0x80) {
                        break
                    }
                    output[written++] = char.code.toByte()
                    index++
                }
                while (index < end) {
                    val char = utf16Char(source = source, index = index++)
                    if (char.code < 0x80) {
                        output[written++] = char.code.toByte()
                    } else if (char.code < 0x800) {
                        output[written++] = (0xC0 or (char.code ushr 6)).toByte()
                        output[written++] = (0x80 or (char.code and 0x3F)).toByte()
                    } else if (Character.isSurrogate(char)) {
                        var codepoint = -1
                        if (Character.isHighSurrogate(char) && index < end) {
                            val low = utf16Char(source = source, index = index)
                            if (Character.isLowSurrogate(low)) {
                                codepoint = Character.toCodePoint(char, low)
                            }
                        }
                        if (codepoint < 0) {
                            output[written++] = 0x3F.toByte()
                        } else {
                            output[written++] = (0xF0 or (codepoint ushr 18)).toByte()
                            output[written++] = (0x80 or ((codepoint ushr 12) and 0x3F)).toByte()
                            output[written++] = (0x80 or ((codepoint ushr 6) and 0x3F)).toByte()
                            output[written++] = (0x80 or (codepoint and 0x3F)).toByte()
                            index++
                        }
                    } else {
                        output[written++] = (0xE0 or (char.code ushr 12)).toByte()
                        output[written++] = (0x80 or ((char.code ushr 6) and 0x3F)).toByte()
                        output[written++] = (0x80 or (char.code and 0x3F)).toByte()
                    }
                }
            }
            return BytesSlice(
                bytes = Bytes(MutBytes(output)),
                offset = 0,
                len = written,
            )
        }

        private fun utf16Char(
            source: ByteArray,
            index: Int,
        ): Char = utf16CharGetter!!.invokeExact(source, index) as Char
    }

    companion object {
        internal val instance: SubstringFastPath
        private val isJdkInternalLoaded: Boolean
        private val utf16CharGetter: MethodHandle?

        init {
            val handles = try {
                val value = String::class.java.getDeclaredField("value")
                val isLatin1 = String::class.java.getDeclaredMethod("isLatin1")
                val getChar = Class
                    .forName("java.lang.StringUTF16")
                    .getDeclaredMethod("getChar", ByteArray::class.java, Int::class.javaPrimitiveType)
                check(value.type == ByteArray::class.java)
                check(isLatin1.returnType == Boolean::class.javaPrimitiveType)
                check(getChar.returnType == Char::class.javaPrimitiveType)
                if (value.trySetAccessible() && isLatin1.trySetAccessible() && getChar.trySetAccessible()) {
                    val lookup = MethodHandles.lookup()
                    val getter = lookup.unreflectGetter(value)
                    val latin1Check = lookup.unreflect(isLatin1)
                    check(latin1Check.invoke("A") as Boolean)
                    check((getter.invoke("A") as ByteArray).contentEquals(byteArrayOf(65)))
                    check(latin1Check.invoke("é") as Boolean)
                    check((getter.invoke("é") as ByteArray).contentEquals(byteArrayOf(-23)))
                    check(!(latin1Check.invoke("Ā") as Boolean))
                    val utf16Value = getter.invoke("\u0102") as ByteArray
                    val utf16Getter = lookup.unreflect(getChar)
                    check((utf16Getter.invokeExact(utf16Value, 0) as Char) == '\u0102')
                    Triple(getter, latin1Check, utf16Getter)
                } else {
                    null
                }
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: RuntimeException) {
                null
            } catch (_: LinkageError) {
                null
            }
            utf16CharGetter = handles?.third
            if (handles == null) {
                isJdkInternalLoaded = false
                instance = Default
            } else {
                isJdkInternalLoaded = true
                instance = JdkInternal(valueGetter = handles.first, isLatin1 = handles.second)
            }
        }
    }
}

fun Charset.jdkEncoding(): java.nio.charset.Charset = when (this) {
    Charset.Utf8 -> Charsets.UTF_8
    Charset.Ascii -> Charsets.US_ASCII
    Charset.Latin1 -> Charsets.ISO_8859_1
}
