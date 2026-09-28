package com.varlanv.koper.lang.bin

class BytesSlice(
    val bytes: Bytes,
    val offset: Int,
    val len: Int,
) {
    fun getPackedInt(idx: Int): Int = bytes.bytes.getPackedInt(idx)

    inline fun forEach(block: (Byte) -> Unit) {
        for (idx in offset until offset + len) {
            block(bytes.bytes.impl[idx])
        }
    }

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) {
        for (idx in offset until offset + len) {
            block(idx, bytes.bytes.impl[idx])
        }
    }

    operator fun get(idx: Int): Byte = bytes.bytes[idx]

    override fun equals(other: Any?): Boolean =
        other is BytesSlice &&
            bytes.mismatch(
                aFromIndex = offset,
                aToIndex = len + offset,
                b = other.bytes,
                bFromIndex = other.offset,
                bToIndex = other.offset + other.len,
            ) == -1

    override fun hashCode(): Int = bytes.bytes.hash(offset = offset, length = len)

    companion object {
        val empty: BytesSlice = BytesSlice(bytes = Bytes.empty, offset = 0, len = 0)
    }
}
