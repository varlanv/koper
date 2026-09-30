@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")

package com.varlanv.koper.lang.bin

import java.util.*

actual fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int = Arrays.mismatch(this.bytes.impl, aFromIndex, aToIndex, b.bytes.impl, bFromIndex, bToIndex)

@JvmInline
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual value class Bytes actual constructor(@PublishedApi internal actual val bytes: MutBytes) : BytesOperations {
    actual inline val size: Int
        get() = bytes.impl.size

    internal inline val unsafeInternal: ByteArray get() = bytes.impl

    actual override inline operator fun get(idx: Int): Byte = bytes[idx]

    actual inline fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) = bytes.copyInto(
        destination = destination,
        destinationOffset = destinationOffset,
        startIndex = startIndex,
        endIndex = endIndex,
    )

    actual override inline fun forEachIndex(block: (idx: Int) -> Unit) {
        val sz = bytes.impl.size
        for (idx in 0 until sz) {
            block(idx)
        }
    }

    actual override inline fun forEachIndexed(block: (Byte) -> Unit) {
        val impl = bytes.impl
        val sz = impl.size
        for (idx in 0 until sz) {
            block(impl[idx])
        }
    }

    actual override inline fun forEachIndexed(block: (index: Int, Byte) -> Unit) {
        val impl = bytes.impl
        val sz = impl.size
        for (idx in 0 until sz) {
            block(idx, impl[idx])
        }
    }

    actual override inline fun asList(): List<Byte> = bytes.impl.asList()

    class Unsafe internal constructor() {
        inline fun <R> useInternal(bytes: Bytes, block: (ByteArray) -> R): R {
            return block(bytes.bytes.impl)
        }

        inline fun <R> useInternal(bytes: MutBytes, block: (ByteArray) -> R): R {
            return block(bytes.impl)
        }
    }

    actual companion object {
        @PublishedApi
        internal val unsafe: Unsafe = Unsafe()

        actual val empty: Bytes = Bytes(MutBytes.empty)

        /**
         * Provides unsafe [Bytes] operations.
         * The caller is responsible for ensuring invariants are preserved and guaranteeing thread safety.
         * Turning [Bytes] into invalid state via usage of [unsafe] results in undefined behavior.
         */
        inline fun <R> unsafe(block: Unsafe.() -> R): R {
            return block(unsafe)
        }
    }
}
