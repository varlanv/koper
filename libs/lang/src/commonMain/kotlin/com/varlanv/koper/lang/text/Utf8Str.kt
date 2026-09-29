package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.BytesSlice
import kotlin.jvm.JvmInline

@JvmInline
value class Utf8Str private constructor(val slice: BytesSlice) {
    val byteLen: Int get() = slice.len

    fun allocateString(): String = Charset.Utf8.allocateString(slice)

    companion object {
        val empty: Utf8Str = Utf8Str(BytesSlice.empty)

        fun unsafeWrap(slice: BytesSlice): Utf8Str = Utf8Str(slice)

        fun parse(slice: BytesSlice): Utf8Str {
            TODO()
        }

        fun allocateFromString(
            string: String,
            start: Int = 0,
            end: Int = string.length,
        ): Utf8Str = Utf8Str(Charset.Utf8.allocateByteSlice(string = string, start = start, end = end))
    }
}
