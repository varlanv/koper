package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.isHighSurrogate
import com.varlanv.koper.lang.text.isLowSurrogate
import com.varlanv.koper.lang.text.toCodePoint

class StringSource(private val string: String) : ByteSource {
    private var position = 0
    private var pending = 0
    private var pendingSize = 0

    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        if (offset < 0 || length < 0 || offset > sink.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) {
            return 0
        }
        if (position == string.length && pendingSize == 0) {
            return -1
        }
        var output = offset
        val end = offset + length
        while (pendingSize > 0 && output < end) {
            sink[output++] = pending.toByte()
            pending = pending ushr 8
            pendingSize--
        }
        while (position < string.length && output < end) {
            val char = string[position++]
            if (char.code < 128) {
                sink[output++] = char.code.toByte()
            } else {
                val codepoint = if (char.isHighSurrogate() &&
                    position < string.length &&
                    string[position].isLowSurrogate()) {
                    char.toCodePoint(string[position++])
                } else {
                    char.code
                }
                if (end - output >= 4) {
                    Charset.Utf8.encodeCodepointInline(codepoint) { sink[output++] = it }
                } else {
                    Charset.Utf8.encodeCodepointInline(codepoint) { byte ->
                        if (output < end) {
                            sink[output++] = byte
                        } else {
                            pending = pending or ((byte.toInt() and 255) shl (pendingSize * 8))
                            pendingSize++
                        }
                    }
                }
            }
        }
        return output - offset
    }
}
