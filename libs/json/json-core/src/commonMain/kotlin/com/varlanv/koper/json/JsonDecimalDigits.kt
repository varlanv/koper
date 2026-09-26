package com.varlanv.koper.json

internal object JsonDecimalDigits {
    val triplets = IntArray(1000) {
        ('0'.code + it / 100) or (('0'.code + it / 10 % 10) shl 8) or (('0'.code + it % 10) shl 16)
    }
    private val tens = ByteArray(100) { ('0'.code + it / 10).toByte() }
    private val ones = ByteArray(100) { ('0'.code + it % 10).toByte() }

    fun writeLeading(
        buffer: ByteArray,
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
