package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.ext.ThreadScope

class BufferAllocator(size: DataSize) {
    @PublishedApi
    internal val tl = ThreadScope {
        MovingMutBytesSlice(offset = 0, capacity = size.bytes, bytes = size.allocate())
    }

    inline fun <T> use(block: (MovingMutBytesSlice) -> T): T {
        val value = tl.get()
        try {
            return block(value)
        } finally {
            value.sz = 0
        }
    }
}

class MovingMutBytesSlice(
    /**
     * Offset from beginning of array
     */
    internal val offset: Int,
    /**
     *  Capacity of this slice.
     */
    internal val capacity: Int,
    internal var bytes: MutBytes,
) {
    /**
     * Current occupied length
     */
    @PublishedApi
    internal var sz: Int = 0
    val size: Int get() = sz

    fun add(byte: Byte) {
        ensureCapacity(1)
        bytes[sz++] = byte
    }

    inline fun unsafeReserved(block: (MovingMutBytesSlice) -> Unit) {
    }

    private fun ensureCapacity(requested: Int) {
        TODO("Not yet implemented")
    }
}
