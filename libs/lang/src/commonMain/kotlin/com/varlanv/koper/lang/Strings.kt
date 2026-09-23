package com.varlanv.koper.lang

import kotlin.jvm.JvmInline

/**
 * A string slice without attached encoding.
 */
@JvmInline
value class Str(val bytes: BytesSlice) {
    @Suppress("POTENTIALLY_NON_REPORTED_ANNOTATION")
    @Deprecated(
        "toString without encoding should not be called, because `Str` does not have encoding by design.",
        ReplaceWith("this.allocateString(charset)"),
    )
    override fun toString(): String = bytes.bytes.array.decodeToString(bytes.offset, bytes.offset + bytes.len)

    fun allocateString(charset: Charset): String = charset.allocateString(bytes.bytes.array, bytes.offset, bytes.len)
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
            destination.setPackedInt(LEN_OFFSET, size)
            destination[CHARSET_OFFSET] = Charset.Utf8.ordinal.toByte()
            source.copyInto(destination, DATA_OFFSET)
            return ByteStr(ReadonlyBytes(destination))
        }
    }

    fun asBytesSlice(): BytesSlice = BytesSlice(bytes, DATA_OFFSET, len())

    override fun toString(): String = bytes.array.decodeToString(DATA_OFFSET)
}

expect fun ByteStr.len(): Int
expect fun ByteStr.encoding(): Charset
expect fun ByteStr.copyInto(
    sourceOffset: Int,
    destination: ByteArray,
    destinationOffset: Int,
    length: Int
)


/**
 * Bytes view on Latin1 string.
 * For performance reasons, actual Latin1 encoding is not guaranteed invariant.
 * It is application bug to represent non-latin1 bytes as [Latin1Str].
 * This type merely serves as a type-level documentation.
 */
@JvmInline
value class Latin1Str private constructor(val bytes: BytesSlice) {

    fun allocateFromString(str: String): Utf8Str = Utf8Str(str.allocateStr(charset = Charset.Latin1))
}

/**
 * Bytes view on Utf8 string.
 * For performance reasons, actual Latin1 encoding is not guaranteed invariant.
 * It is application bug to represent non-utf8 bytes as [Utf8Str].
 * This type merely serves as a type-level documentation.
 */
@JvmInline
value class Utf8Str private constructor(val bytes: BytesSlice) {

    companion object {
        val empty = Utf8Str(
            BytesSlice(
                ReadonlyBytes(
                    ByteArray(0)
                ),
                0,
                0,
            ),
        )

        /**
         * Creates Utf8Str out of given Str, without validation.
         */
        operator fun invoke(str: Str): Utf8Str = Utf8Str(str.bytes)

        /**
         * Converts given string to utf8 bytes.
         */
        fun allocateFromString(str: String): Utf8Str = Utf8Str(str.allocateStr(charset = Charset.Utf8))

        /**
         * Validates given byte slice against utf8 and wraps into Utf8Str.
         * No allocations are performed, input bytes are only validated.
         */
        fun fromTainted(bytes: BytesSlice): Utf8Str {
            if (!bytes.bytes.array.validateUtf8(bytes.offset, bytes.len)) {
                error("received invalid utf-8 sequence bytes")
            }
            return Utf8Str(Str(bytes))
        }
    }

    fun allocateString(): String = Charset.Utf8.allocateString(bytes.bytes.array, bytes.offset, bytes.len)

    override fun toString(): String {
        return allocateString()
    }
}

fun String.allocateStr(charset: Charset): Str {
    val arr = charset.toByteArray(this)
    return Str(BytesSlice(ReadonlyBytes(arr), 0, arr.size))
}
