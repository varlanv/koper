package com.varlanv.koper.lang.bin

import kotlin.jvm.JvmInline

@JvmInline
value class MutBytesSlice @PublishedApi internal constructor(@PublishedApi internal val delegate: BytesSlice) {
    fun setPackedInt(idx: Int, value: Int) = delegate.bytes.bytes.setPackedInt(idx = idx, value = value)

    operator fun set(idx: Int, value: Byte) {
        delegate.bytes.bytes[idx] = value
    }
}
