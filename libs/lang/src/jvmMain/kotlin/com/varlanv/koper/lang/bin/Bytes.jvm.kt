package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.intViewHandle
import com.varlanv.koper.lang.longViewHandle
import com.varlanv.koper.lang.shortViewHandle
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual typealias BytesImpl = ByteArray

actual fun BytesImpl.size(): Int = size

@JvmInline
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual value class MutBytes @PublishedApi internal actual constructor(
    @PublishedApi internal actual val impl: BytesImpl,
) {
    actual val size: Int
        get() = impl.size
    actual val readonly: Bytes
        get() = Bytes(this)

    actual constructor(dataSize: DataSize) : this(ByteArray(dataSize.bytes))

    actual fun getPackedLong(idx: Int): Long = longViewHandle.get(impl, idx) as Long

    actual fun setPackedLong(idx: Int, value: Long) {
        longViewHandle.set(impl, idx, value)
    }

    actual fun getPackedInt(idx: Int): Int = intViewHandle.get(impl, idx) as Int

    actual fun setPackedInt(idx: Int, value: Int) {
        intViewHandle.set(impl, idx, value)
    }

    actual fun setPackedShort(idx: Int, value: Short) {
        shortViewHandle.set(impl, idx, value)
    }

    actual fun getPackedShort(idx: Int): Short = shortViewHandle.get(impl, idx) as Short

    actual operator fun get(idx: Int): Byte = impl[idx]

    actual operator fun set(idx: Int, value: Byte) {
        impl[idx] = value
    }

    actual fun hash(offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > impl.size - length) {
            throw IndexOutOfBoundsException()
        }
        val end = offset + length
        return if (VectorApi.enabled && length >= 128) {
            BytesHash.target.hash(this.readonly, offset, end)
        } else {
            hashSwar(this.readonly, offset, end, 1)
        }
    }

    actual inline fun forEach(block: (Byte) -> Unit) = impl.forEach(block)

    actual inline fun forEachIndexed(block: (idx: Int, Byte) -> Unit) = impl.forEachIndexed(block)

    actual fun copyInto(destination: MutBytes, destinationOffset: Int, startIndex: Int, endIndex: Int) {
        require(startIndex <= endIndex)
        System.arraycopy(impl, startIndex, destination.impl, destinationOffset, endIndex - startIndex)
    }

    actual fun copyOf(newCapacity: Int): MutBytes = MutBytes(impl.copyOf(newCapacity))

    actual fun copyOfRange(from: Int, to: Int): MutBytes = MutBytes(impl.copyOfRange(from, to))

    actual companion object {

        actual val empty: MutBytes = MutBytes(ByteArray(0))

        actual inline operator fun invoke(dataSize: DataSize, init: (idx: Int) -> Byte): MutBytes {
            val s = dataSize.bytes
            val arr = ByteArray(s)
            for (idx in 0 until s) {
                arr[idx] = init(idx)
            }
            return MutBytes(arr)
        }

        actual operator fun invoke(array: ByteArray): MutBytes = MutBytes(array)
    }
}

private sealed interface BytesHash {
    fun hash(bytes: Bytes, start: Int, end: Int): Int

    companion object {
        val target: BytesHash = VectorApi.tryLoad { VectorBytesHash } ?: ScalarBytesHash
    }
}

private object VectorBytesHash : BytesHash, VectorApi {
    init {
        VectorApi.ensureEnabled(VectorBytesHash::class)
    }

    private val byteSpecies = ByteVector.SPECIES_PREFERRED
    private val intSpecies = byteSpecies.withLanes(Int::class.javaPrimitiveType!!)
    private val blockBytes = byteSpecies.length()
    private val blockMultiplier: Int
    private val weights0: IntVector
    private val weights1: IntVector
    private val weights2: IntVector
    private val weights3: IntVector

    init {
        val laneWeights = IntArray(blockBytes)
        var power = 1
        for (lane in blockBytes - 1 downTo 0) {
            laneWeights[lane] = power
            power *= 31
        }
        blockMultiplier = power
        val lanes = intSpecies.length()
        weights0 = IntVector.fromArray(intSpecies, laneWeights, 0)
        weights1 = IntVector.fromArray(intSpecies, laneWeights, lanes)
        weights2 = IntVector.fromArray(intSpecies, laneWeights, lanes * 2)
        weights3 = IntVector.fromArray(intSpecies, laneWeights, lanes * 3)
    }

    override fun smokeTest(): Boolean {
        val bytes = MutBytes(ByteArray(blockBytes * 2 + 5) { (it * 73 - 128).toByte() })
        return hash(bytes.readonly, 1, bytes.size) == hashSwar(bytes.readonly, 1, bytes.size, 1)
    }

    override fun hash(bytes: Bytes, start: Int, end: Int): Int {
        var result = 1
        var index = start
        while (index <= end - blockBytes) {
            val vector = ByteVector.fromArray(byteSpecies, bytes.unsafeInternal, index)
            val part0 = vector.convert(VectorOperators.B2I, 0) as IntVector
            val part1 = vector.convert(VectorOperators.B2I, 1) as IntVector
            val part2 = vector.convert(VectorOperators.B2I, 2) as IntVector
            val part3 = vector.convert(VectorOperators.B2I, 3) as IntVector
            val weighted = part0.mul(weights0).reduceLanes(VectorOperators.ADD) +
                    part1.mul(weights1).reduceLanes(VectorOperators.ADD) +
                    part2.mul(weights2).reduceLanes(VectorOperators.ADD) +
                    part3.mul(weights3).reduceLanes(VectorOperators.ADD)
            result = blockMultiplier * result + weighted
            index += blockBytes
        }
        return hashSwar(bytes, index, end, result)
    }
}

private object ScalarBytesHash : BytesHash {
    override fun hash(bytes: Bytes, start: Int, end: Int): Int = hashSwar(bytes, start, end, 1)
}

private fun hashSwar(bytes: Bytes, start: Int, end: Int, initial: Int): Int {
    var result = initial
    var index = start
    while (index <= end - Long.SIZE_BYTES) {
        val word = longViewHandle.get(bytes, index) as Long
        result = 31 * result + word.toByte()
        result = 31 * result + (word ushr 8).toByte()
        result = 31 * result + (word ushr 16).toByte()
        result = 31 * result + (word ushr 24).toByte()
        result = 31 * result + (word ushr 32).toByte()
        result = 31 * result + (word ushr 40).toByte()
        result = 31 * result + (word ushr 48).toByte()
        result = 31 * result + (word ushr 56).toByte()
        index += Long.SIZE_BYTES
    }
    while (index < end) {
        result = 31 * result + bytes[index]
        index++
    }
    return result
}

actual fun Bytes.mismatch(
    aFromIndex: Int,
    aToIndex: Int,
    b: Bytes,
    bFromIndex: Int,
    bToIndex: Int,
): Int = Arrays.mismatch(this.bytes.impl, aFromIndex, aToIndex, b.bytes.impl, bFromIndex, bToIndex)


/** Adapts [ins] to a [ByteSource]. */
class InputStreamByteSource(val ins: InputStream) : ByteSource {
    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        return ins.read(sink.impl, offset, length)
    }
}

/** Adapts [outs] to a [ByteSink]. */
class OutputStreamByteSink(val outs: OutputStream) : ByteSink {
    override fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    ) {
        outs.write(source.impl, offset, length)
    }
}

@JvmInline
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual value class Bytes actual constructor(@PublishedApi internal actual val bytes: MutBytes) {

    actual val size: Int
        get() = bytes.size

    internal val unsafeInternal: ByteArray get() = bytes.impl

    actual operator fun get(idx: Int): Byte = bytes[idx]

    actual fun copyInto(
        destination: MutBytes,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int
    ) = bytes.copyInto(destination, destinationOffset, startIndex, endIndex)

    inline fun forEachIndex(block: (idx: Int) -> Unit) {
        for (idx in 0 until bytes.impl.size) {
            block(idx)
        }
    }

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

        inline fun <R> unsafe(block: Unsafe.() -> R): R {
            return block(unsafe)
        }
    }
}
