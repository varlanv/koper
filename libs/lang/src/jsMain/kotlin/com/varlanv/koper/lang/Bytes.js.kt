package com.varlanv.koper.lang

actual fun ByteArray.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    bToIndex: Int,
): Int {
    checkMismatchRange(aFromIndex, aToIndex, size)
    checkMismatchRange(bFromIndex, bToIndex, b.size)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    val length = minOf(aLength, bLength)

    // Identical starting positions in the same array need no scan.
    if (this !== b || aFromIndex != bFromIndex) {
        var i = 0
        while (i < length) {
            if (this[aFromIndex + i] != b[bFromIndex + i]) {
                return i
            }
            i++
        }
    }

    return if (aLength == bLength) -1 else length
}

private fun checkMismatchRange(from: Int, to: Int, size: Int) {
    require(from <= to) {
        "fromIndex ($from) > toIndex ($to)"
    }
    if (from < 0 || to > size) {
        throw IndexOutOfBoundsException(
            "range [$from, $to) exceeds array size $size"
        )
    }
}
