package com.varlanv.koper.lang.bin

import kotlin.jvm.JvmInline

@Suppress("NOTHING_TO_INLINE")
@JvmInline
value class MutBytesSlice @PublishedApi internal constructor(@PublishedApi internal val delegate: BytesSlice) {
    inline  fun setPackedLong(idx: Int, value: Long) {
        require(idx >= delegate.offset && idx <= delegate.offset + delegate.len - Long.SIZE_BYTES)
        delegate.bytes.bytes.setPackedLong(idx = idx, value = value)
    }

    inline fun setPackedInt(idx: Int, value: Int) {
        require(idx >= delegate.offset && idx <= delegate.offset + delegate.len - Int.SIZE_BYTES)
        delegate.bytes.bytes.setPackedInt(idx = idx, value = value)
    }

    inline fun setPackedShort(idx: Int, value: Short) {
        require(idx >= delegate.offset && idx <= delegate.offset + delegate.len - Short.SIZE_BYTES)
        delegate.bytes.bytes.setPackedShort(idx = idx, value = value)
    }

    inline  operator fun set(idx: Int, value: Byte) {
        require(idx >= delegate.offset && idx < delegate.offset + delegate.len)
        delegate.bytes.bytes.impl[idx] = value
    }

    inline  operator fun get(idx: Int): Byte {
        require(idx >= delegate.offset && idx < delegate.offset + delegate.len)
        return delegate.bytes.bytes.impl[idx]
    }

    inline   fun copyFrom(
        source: Bytes,
        sourceOffset: Int = 0,
        sourceLength: Int = source.size - sourceOffset,
    ) {
        require(
            sourceOffset >= 0 &&
                sourceLength >= 0 &&
                sourceOffset <= source.size - sourceLength &&
                sourceLength <= delegate.len,
        )
        source.bytes.copyInto(
            destination = delegate.bytes.bytes,
            destinationOffset = delegate.offset,
            startIndex = sourceOffset,
            endIndex = sourceOffset + sourceLength,
        )
    }
}
