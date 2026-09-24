package com.varlanv.koper.json

interface JsonInput {
    fun read(destination: ByteArray, offset: Int, length: Int): Int
    fun read(destination: ByteArray): Int = read(destination, 0, destination.size)
    fun read(): Int
}

interface JsonOutput {
    fun write(source: ByteArray, offset: Int, length: Int)
}

class ByteArrayJsonInput(private val bytes: ByteArray) : JsonInput {
    private var position = 0

    fun reset() {
        position = 0
    }

    override fun read(destination: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position == bytes.size) return -1
        val count = minOf(length, bytes.size - position)
        bytes.copyInto(destination, offset, position, position + count)
        position += count
        return count
    }

    override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 255 else -1
}

class ByteArrayJsonOutput(initialCapacity: Int = 512) : JsonOutput {
    private var bytes = ByteArray(initialCapacity)
    private var size = 0

    fun reset() {
        size = 0
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    override fun write(source: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= source.size - length)
        val required = size.toLong() + length
        require(required <= Int.MAX_VALUE)
        if (required > bytes.size) bytes = bytes.copyOf(maxOf(required.toInt(), maxOf(1, bytes.size * 2)))
        source.copyInto(bytes, size, offset, offset + length)
        size += length
    }
}
