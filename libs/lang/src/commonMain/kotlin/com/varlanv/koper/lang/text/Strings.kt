package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.BytesSlice
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.MutBytesSlice
import kotlin.jvm.JvmInline

/**
 * A string slice without attached encoding.
 */
@JvmInline
value class Str private constructor(val slice: BytesSlice) {
    val len: Int get() = slice.len

    fun asCharSequence(
        charset: Charset,
    ): CharSequence = CharSeq(bytes = slice.bytes.bytes, offset = slice.offset, size = slice.len)

    @Suppress("POTENTIALLY_NON_REPORTED_ANNOTATION")
    @Deprecated(
        "toString without encoding should not be called, because `Str` does not have encoding by design.",
        ReplaceWith("this.allocateString(charset)"),
    )
    override fun toString(): String = super.toString()

    fun allocateString(
        charset: Charset,
    ): String = charset.allocateString(bytes = slice.bytes, offset = slice.offset, len = slice.len)

    // Todo - implement efficient view on parent that will not allocate and don't do extra iteration
    private class CharSeq(
        private val bytes: MutBytes,
        private val offset: Int,
        private val size: Int,
    ) : CharSequence {
        override val length: Int
            get() {
                // todo implement it; maybe cache result?
                TODO()
            }

        override fun get(index: Int): Char {
            TODO()
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            TODO()
        }

        override fun toString(): String {
            // todo probably not worth caching
            TODO()
        }

        override fun hashCode(): Int {
            // todo implement it; maybe cache result?
            TODO()
        }

        override fun equals(other: Any?): Boolean {
            TODO()
        }
    }

    companion object {
        val empty = Str(BytesSlice.empty)

        @PublishedApi
        internal val unsafeInstance: Unsafe = Unsafe()

        /**
         * Provides unsafe [Str] operations.
         * The caller is responsible for ensuring invariants are preserved and guaranteeing thread safety.
         * Turning [Str] into invalid state via usage of [unsafe] results in undefined behavior.
         */
        inline fun unsafe(block: Unsafe.() -> Unit) {
            block(unsafeInstance)
        }

        fun wrapBytes(
            bytes: Bytes,
            offset: Int = 0,
            len: Int = bytes.bytes.size,
        ): Str = Str(BytesSlice(bytes = bytes, offset = offset, len = len))

        fun allocateFromString(string: String): Str {
            if (string.isEmpty()) {
                return empty
            }
            val arr = string.encodeToByteArray()
            return Str(
                BytesSlice(
                    bytes = Bytes(MutBytes(arr)),
                    offset = 0,
                    len = arr.size,
                ),
            )
        }
    }

    class Unsafe internal constructor() {
        /**
         * Provides capability to mutate [Str] backing array in-place.
         */
        inline fun mut(str: Str, block: (bytes: MutBytesSlice) -> Unit) {
            block(MutBytesSlice(str.slice))
        }
    }
}
