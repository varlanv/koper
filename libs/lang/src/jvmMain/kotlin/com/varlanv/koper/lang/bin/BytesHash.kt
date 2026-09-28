package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.lang.longViewHandle
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators

internal sealed interface BytesHash {
    fun hash(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int

    companion object {
        val target: BytesHash = VectorApi.tryLoad { VectorBytesHash } ?: ScalarBytesHash
    }
}

internal object VectorBytesHash : BytesHash, VectorApi {
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
        return hash(bytes = bytes.asReadonly(), start = 1, end = bytes.size) ==
            hashSwar(bytes = bytes.asReadonly(), start = 1, end = bytes.size, initial = 1)
    }

    override fun hash(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int {
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
        return hashSwar(bytes = bytes, start = index, end = end, initial = result)
    }
}

private object ScalarBytesHash : BytesHash {
    override fun hash(
        bytes: Bytes,
        start: Int,
        end: Int,
    ): Int = hashSwar(bytes = bytes, start = start, end = end, initial = 1)
}

internal fun hashSwar(
    bytes: Bytes,
    start: Int,
    end: Int,
    initial: Int,
): Int {
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
