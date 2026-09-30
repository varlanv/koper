@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.bin

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect class BytesImpl

expect inline operator fun BytesImpl.get(idx: Int): Byte

expect inline operator fun BytesImpl.set(idx: Int, value: Byte)

expect inline fun BytesImpl.size(): Int

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect value class MutBytes @PublishedApi internal constructor(@PublishedApi internal val impl: BytesImpl) {
    inline val size: Int

    constructor(dataSize: DataSize)

    inline fun getPackedLong(idx: Int): Long

    inline fun setPackedLong(idx: Int, value: Long)

    inline fun getPackedInt(idx: Int): Int

    inline fun setPackedInt(idx: Int, value: Int)

    inline fun getPackedShort(idx: Int): Short

    inline fun setPackedShort(idx: Int, value: Short)

    inline operator fun get(idx: Int): Byte

    inline operator fun set(idx: Int, value: Byte)

    inline fun forEach(block: (Byte) -> Unit)

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit)

    fun hash(offset: Int, length: Int): Int

    inline fun copyInto(
        destination: MutBytes,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = size,
    )

    inline fun copyOf(newCapacity: Int = impl.size()): MutBytes

    inline fun copyOfRange(from: Int, to: Int): MutBytes

    companion object {
        inline val empty: MutBytes

        inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes

        inline operator fun invoke(array: ByteArray): MutBytes
    }
}

inline fun MutBytes.asReadonly(): Bytes = Bytes(this)

inline fun MutBytes.asList(): List<Byte> = this.asReadonly().asList()
