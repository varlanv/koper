@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.bin

import kotlin.jvm.JvmInline

@PublishedApi
internal const val MAX_ARRAY_LEN = Int.MAX_VALUE - 8

@PublishedApi
internal const val UNKNOWN_ARRAY_SIZE_HINT = -1

@JvmInline
value class ArraySizeHint @PublishedApi internal constructor(@PublishedApi internal val value: Int) {
    inline fun isUnknown(): Boolean = value == UNKNOWN_ARRAY_SIZE_HINT

    inline fun getOrDefault(fallback: DataSize): DataSize = if (value == UNKNOWN_ARRAY_SIZE_HINT) {
        fallback
    } else {
        value.bytes()
    }

    companion object {
        val unknown: ArraySizeHint = ArraySizeHint(UNKNOWN_ARRAY_SIZE_HINT)

        inline operator fun invoke(dataSize: DataSize): ArraySizeHint {
            require(dataSize.bytes in 0..MAX_ARRAY_LEN)
            return ArraySizeHint(dataSize.bytes)
        }

        inline operator fun invoke(value: Int): ArraySizeHint {
            require(value in 0..MAX_ARRAY_LEN)
            return ArraySizeHint(value.bytes())
        }
    }
}
