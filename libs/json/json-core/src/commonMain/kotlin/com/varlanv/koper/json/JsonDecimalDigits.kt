package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.MutBytes

/**
 * Decimal digit tables and sizing helpers used by integer writers; helpers do not reserve or flush output.
 */
internal object JsonDecimalDigits {
    val triplets = IntArray(1000) {
        ('0'.code + it / 100) or (('0'.code + it / 10 % 10) shl 8) or (('0'.code + it % 10) shl 16)
    }
    private val tens = ByteArray(100) { ('0'.code + it / 10).toByte() }
    private val ones = ByteArray(100) { ('0'.code + it % 10).toByte() }

    /**
     * Writes a leading decimal group of [number] backwards into [buffer], ending immediately before [end].
     * Requires a number in 0..999 and sufficient preceding capacity; writes one to three bytes without changing scope state.
     *
     * @return The first written offset as an [Int].
     */
    fun writeLeading(
        buffer: MutBytes,
        number: Int,
        end: Int,
    ): Int {
        var index = end
        buffer[--index] = ones[number % 100]
        if (number >= 10) {
            buffer[--index] = tens[number % 100]
        }
        if (number >= 100) {
            buffer[--index] = ('0'.code + number / 100).toByte()
        }
        return index
    }

    fun readFourDigits(input: MutBytes, index: Int): Int {
        val word = input.getPackedInt(index)
        if (((word + 0x46464646) or (word - 0x30303030)) and -0x7f7f7f80 != 0) {
            return -1
        }
        val digits = word - 0x30303030
        val pairs = (digits * 10 + (digits ushr 8)) and 0x00ff00ff
        return (pairs * 100 + (pairs ushr 16)) and 0xffff
    }

    fun decimalSize(value: Int): Int {
        val number = if (value > 0) {
            -value
        } else {
            value
        }
        val digits = if (number > -1000000000) {
            intDigits(-number)
        } else {
            10
        }
        return digits + if (value < 0) {
            1
        } else {
            0
        }
    }

    /**
     * Computes the decimal byte length of [value], including a minus sign when negative, without changing state.
     *
     * @return The exact length as an [Int], including for Long.MIN_VALUE.
     */
    fun decimalSize(value: Long): Int {
        val number = if (value > 0) {
            -value
        } else {
            value
        }
        val digits = when {
            number > -1000000000L -> intDigits((-number).toInt())
            number > -1000000000000000000L -> 9 + intDigits(-(number / 1000000000L).toInt())
            else -> 19
        }
        return digits + if (value < 0) {
            1
        } else {
            0
        }
    }

    /**
     * Counts digits of a nonnegative decimal group below 1,000,000,000 without changing state.
     *
     * @return The decimal digit count as an [Int], with zero requiring one digit.
     */
    private fun intDigits(value: Int): Int {
        return if (value < 100000) {
            if (value < 100) {
                if (value < 10) {
                    1
                } else {
                    2
                }
            } else if (value < 1000) {
                3
            } else if (value < 10000) {
                4
            } else {
                5
            }
        } else if (value < 10000000) {
            if (value < 1000000) {
                6
            } else {
                7
            }
        } else if (value < 100000000) {
            8
        } else {
            9
        }
    }
}
