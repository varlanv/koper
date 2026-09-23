package com.varlanv.koper.lang

actual fun writeLongAscii(value: Long, array: ByteArray, offset: Int): Int {
    if (value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
        return writeIntAscii(value.toInt(), array, offset)
    }
    var position = offset
    var magnitude = value
    if (value < 0) {
        array[position++] = 45
        if (value == Long.MIN_VALUE) {
            array[position++] = 57
            writeNineAscii(223_372_036, array, position)
            writeNineAscii(854_775_808, array, position + 9)
            return position + 18
        }
        magnitude = -value
    }
    val upper = magnitude / 1_000_000_000
    val lower = (magnitude - upper * 1_000_000_000).toInt()
    if (upper < 1_000_000_000) {
        position = writePositiveIntAscii(upper.toInt(), array, position)
    } else {
        val leading = (upper / 1_000_000_000).toInt()
        array[position++] = (leading + 48).toByte()
        writeNineAscii((upper - leading.toLong() * 1_000_000_000).toInt(), array, position)
        position += 9
    }
    writeNineAscii(lower, array, position)
    return position + 9
}
