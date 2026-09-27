package com.varlanv.koper.benchmarks.json

import java.lang.invoke.MethodHandles
import java.nio.ByteOrder

object SwarJsonScan {
    private val longView = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.LITTLE_ENDIAN)
    private const val ONES = 0x0101010101010101L
    private const val HIGH_BITS = -0x7f7f7f7f7f7f7f80L
    private const val CONTROL_BIAS = 0x2020202020202020L
    private const val QUOTES = 0x2222222222222222L
    private const val BACKSLASHES = 0x5c5c5c5c5c5c5c5cL

    fun firstSpecial(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        var index = start
        val wordEnd = end - Long.SIZE_BYTES
        while (index <= wordEnd) {
            val word = longView.get(bytes, index) as Long
            val quotes = word xor QUOTES
            val backslashes = word xor BACKSLASHES
            val quoteMask = (quotes - ONES) and quotes.inv() and HIGH_BITS
            val backslashMask = (backslashes - ONES) and backslashes.inv() and HIGH_BITS
            val controlMask = (word - CONTROL_BIAS) and word.inv() and HIGH_BITS
            val mask = quoteMask or backslashMask or controlMask
            if (mask != 0L) {
                return index + (java.lang.Long.numberOfTrailingZeros(mask) ushr 3)
            }
            index += Long.SIZE_BYTES
        }
        while (index < end) {
            val byte = bytes[index].toInt() and 255
            if (byte == 34 || byte == 92 || byte < 32) {
                return index
            }
            index++
        }
        return end
    }
}
