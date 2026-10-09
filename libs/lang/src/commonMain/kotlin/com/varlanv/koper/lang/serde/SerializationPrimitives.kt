package com.varlanv.koper.lang.serde

import com.varlanv.koper.lang.bin.MutBytes

object SerializationPrimitives {
    fun writePrimitive(
        output: MutBytes,
        value: Int,
        position: Int,
    ): Int {
        val end = position + DecimalDigits.decimalSize(value)
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = DecimalDigits.triplets[quotient * 1000 - number]
            index -= 3
            output.setPackedShort(idx = index, value = digits.toShort())
            output[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = DecimalDigits.writeLeading(buffer = output, number = -number, end = index)
        if (value < 0) {
            output[index - 1] = '-'.code.toByte()
        }
        return end
    }

    fun writePrimitive(
        output: MutBytes,
        value: Long,
        position: Int,
    ): Int {
        val end = position + DecimalDigits.decimalSize(value)
        var index = end
        var number = if (value > 0) {
            -value
        } else {
            value
        }
        while (number <= -1000) {
            val quotient = number / 1000
            val digits = DecimalDigits.triplets[(quotient * 1000 - number).toInt()]
            index -= 3
            output.setPackedShort(idx = index, value = digits.toShort())
            output[index + 2] = (digits ushr 16).toByte()
            number = quotient
        }
        index = DecimalDigits.writeLeading(buffer = output, number = (-number).toInt(), end = index)
        if (value < 0) {
            output[index - 1] = '-'.code.toByte()
        }
        return end
    }
}
