package com.varlanv.koper.lang.bin

/** Writes bytes from a caller-provided array. */
interface ByteSink {
    /** Writes [length] bytes from [source] starting at [offset]. */
    fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    )
}

/** Accumulates bytes in a reusable, growing array. */
class ReusableByteArraySink(initialCapacity: DataSize) : ByteSink {
    @PublishedApi
    internal var bytes: MutBytes = MutBytes(initialCapacity)

    @PublishedApi
    internal var position: Int = 0

    /** Clears the written length while retaining the allocated array. */
    fun reset() {
        position = 0
    }

    /** Peek into readonly view of backing array without copying. */
    inline fun <R> useBytes(block: (bytes: Bytes, len: Int) -> R): R {
        return block(bytes.readonly, position)
    }

    override fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    ) {
        if (offset < 0 || length < 0 || offset > source.size - length) {
            throw IndexOutOfBoundsException()
        }
        ensureCapacity(length)
        source.copyInto(
            destination = bytes,
            destinationOffset = position,
            startIndex = offset,
            endIndex = offset + length,
        )
        position += length
    }

    private fun ensureCapacity(additionalBytes: Int) {
        if (additionalBytes <= bytes.size - position) {
            return
        }
        val requiredCapacity = position.toLong() + additionalBytes
        if (requiredCapacity > Int.MAX_VALUE) {
            error("Required buffer capacity exceeds Int.MAX_VALUE")
        }
        val newCapacity = maxOf(requiredCapacity, bytes.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        bytes = bytes.copyOf(newCapacity)
    }
}
