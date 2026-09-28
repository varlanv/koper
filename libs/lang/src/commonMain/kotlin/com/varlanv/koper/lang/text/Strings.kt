package com.varlanv.koper.lang.text

import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.bin.ReadonlyBytes
import com.varlanv.koper.lang.bin.setPackedInt
import com.varlanv.koper.lang.bin.validateUtf8
import kotlin.jvm.JvmInline

/**
 * A string slice without attached encoding.
 */
@JvmInline
value class Str(val bytes: ByteSlice) {
    @Suppress("POTENTIALLY_NON_REPORTED_ANNOTATION")
    @Deprecated(
        "toString without encoding should not be called, because `Str` does not have encoding by design.",
        ReplaceWith("this.allocateString(charset)"),
    )
    override fun toString(): String = bytes.bytes.array.decodeToString(bytes.offset, bytes.offset + bytes.len)

    fun allocateString(
        charset: Charset,
    ): String = charset.allocateString(bytes = bytes.bytes.array, offset = bytes.offset, len = bytes.len)
}

/**
 * String representation packed into single byte array,
 * Bytes 0-3 are reserved for string length.
 * Byte 4 is reserved for charset.
 * Bytes 5-7 are unused.
 * In some rare cases this class may be favorable to [Str] family,
 * because it doesn't require extra object allocations - after inlining it is exposed directly as [ByteArray].
 * Main downside of this class is that unlike [Str], it has to be sole owner of the backing array.
 */
@JvmInline
value class ByteStr private constructor(internal val bytes: ReadonlyBytes) {
    companion object {
        private const val DATA_OFFSET = 8
        private const val CHARSET_OFFSET = 4
        private const val LEN_OFFSET = 0

        fun allocateFromString(string: String): ByteStr {
            val source = string.encodeToByteArray()
            val size = source.size
            val destination = ByteArray(size + DATA_OFFSET)
            destination.setPackedInt(idx = LEN_OFFSET, i = size)
            destination[CHARSET_OFFSET] = Charset.Utf8.ordinal.toByte()
            source.copyInto(destination, DATA_OFFSET)
            return ByteStr(ReadonlyBytes(destination))
        }
    }

    fun asBytesSlice(): ByteSlice = ByteSlice(bytes = bytes, offset = DATA_OFFSET, len = len())

    override fun toString(): String = bytes.array.decodeToString(DATA_OFFSET)
}

expect fun ByteStr.len(): Int

expect fun ByteStr.encoding(): Charset

expect fun ByteStr.copyInto(
    sourceOffset: Int,
    destination: ByteArray,
    destinationOffset: Int,
    length: Int,
)

/**
 * Bytes view on Latin1 string.
 * For performance reasons, actual Latin1 encoding is not guaranteed invariant.
 * It is application bug to represent non-latin1 bytes as [Latin1Str].
 * This type merely serves as a type-level documentation.
 */
@JvmInline
value class Latin1Str private constructor(val bytes: ByteSlice)

/**
 * Bytes view on Utf8 string.
 * For performance reasons, actual Latin1 encoding is not guaranteed invariant.
 * It is application bug to represent non-utf8 bytes as [Utf8Str].
 * This type merely serves as a type-level documentation.
 */
@JvmInline
value class Utf8Str private constructor(val bytes: ByteSlice) {
    companion object {
        val empty = Utf8Str(
            ByteSlice(
                bytes = ReadonlyBytes(ByteArray(0)),
                offset = 0,
                len = 0,
            ),
        )

        /**
         * Validates given byte slice against utf8 and wraps into Utf8Str.
         * No allocations are performed, input bytes are only validated.
         */
        operator fun invoke(str: Str): Utf8Str {
            if (!str.bytes.bytes.array.validateUtf8(offset = str.bytes.offset, len = str.bytes.len)) {
                error("received invalid utf-8 sequence bytes")
            }
            return Utf8Str(str.bytes)
        }

        /**
         * Allocates new [Utf8Str] out of given [String].
         */
        fun allocateFromString(str: String): Utf8Str = Utf8Str(str.allocateStr(Charset.Utf8))

        /**
         * Creates Utf8Str out of given ByteSlice, without validation.
         * The caller is responsible for ensuring that given bytes window represent valid UTF-8 encoded character sequence.
         * Useful for performance-critical paths where it is guaranteed that UTF-8 encoding was already validated somewhere else.
         */
        fun unsafeWrapBytes(bytes: ByteSlice): Utf8Str = Utf8Str(bytes)
    }

    /**
     * Allocates new [String] out of bytes slice.
     */
    fun allocateString(): String = Charset.Utf8.allocateString(
        bytes = bytes.bytes.array,
        offset = bytes.offset,
        len = bytes.len,
    )

    override fun toString(): String {
        return allocateString()
    }
}

fun String.allocateStr(charset: Charset): Str {
    return Str(charset.allocateByteSlice(string = this))
}
