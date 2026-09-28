package com.varlanv.koper.lang.bin

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect class BytesImpl

expect operator fun BytesImpl.get(idx: Int): Byte

expect operator fun BytesImpl.set(idx: Int, value: Byte)

expect fun BytesImpl.size(): Int

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
expect value class MutBytes @PublishedApi internal constructor(@PublishedApi internal val impl: BytesImpl) {
    val size: Int
    val readonly: Bytes

    constructor(dataSize: DataSize)

    fun getPackedLong(idx: Int): Long

    fun setPackedLong(idx: Int, value: Long)

    fun getPackedInt(idx: Int): Int

    fun setPackedInt(idx: Int, value: Int)

    fun getPackedShort(idx: Int): Short

    fun setPackedShort(idx: Int, value: Short)

    operator fun get(idx: Int): Byte

    operator fun set(idx: Int, value: Byte)

    inline fun forEach(block: (Byte) -> Unit)

    inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit)

    fun hash(offset: Int, length: Int): Int

    fun copyInto(
        destination: MutBytes,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = size,
    )

    fun copyOf(newCapacity: Int = impl.size()): MutBytes

    fun copyOfRange(from: Int, to: Int): MutBytes

    companion object {
        val empty: MutBytes

        inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes

        operator fun invoke(array: ByteArray): MutBytes
    }
}

fun MutBytes.asList(): List<Byte> = this.readonly.asList()
