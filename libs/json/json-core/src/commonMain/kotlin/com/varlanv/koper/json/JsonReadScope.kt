package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.DataSize
import com.varlanv.koper.lang.bin.MutBytes

/**
 * Mutable state for a read operation's context. Construction allocates one buffer with [bufferSize] capacity.
 * String decoding uses this buffer in place; refills may overwrite it, compact it, or replace it during growth.
 * Field and string offsets identify borrowed bytes that must be consumed before further buffer mutations.
 *
 * @param bufferSize Positive initial buffer capacity; retained strings may require growth.
 */
class JsonReadScope(bufferSize: DataSize, private val input: ByteSource) {
    init {
        require(bufferSize.bytes > 0)
    }

    /**
     * Shared storage for buffered input and decoded string bytes. Growth replaces this storage.
     */
    internal var buffer: MutBytes = bufferSize.allocate()

    /**
     * First byte of the most recent token consumed by the protocol.
     * Ordinary byte reads and string decoding leave it unchanged.
     * -1 before initialization or when token reading reaches EOF.
     *
     */
    var lastTokenByte: Int = -1

    /**
     * Start of the most recently read field name in [buffer].
     */
    var fieldOffset: Int = 0

    /**
     * Length in bytes of the most recently read, unescaped field name.
     */
    var fieldSize: Int = 0

    /**
     * Next unread input offset; string scans leave incomplete escapes at this position.
     */
    var position: Int = 0

    /**
     * Exclusive end of populated buffer bytes, including any retained decoded prefix.
     */
    var limit: Int = 0

    /**
     * Start of the current decoded string in [buffer]; compaction may move it to zero.
     */
    var stringOffset: Int = 0

    /**
     * Decoded byte count after a successful string read; reset to zero when reading or skipping starts.
     */
    var stringLength: Int = 0

    /**
     * Next output offset during string decoding; always at or before the next unread input offset.
     */
    internal var stringOutputPosition: Int = 0

    fun advanceToStart() {
        val first = buffer[position].toInt()
        // keeping 99%+ cases in small predictable branch
        if (first == '{'.code || first == '['.code) {
            lastTokenByte = first
            return
        } else {
            advanceToStartSlow()
        }
    }

    private fun advanceToStartSlow() {
        val current = skipWhitespace().toInt()
        if (current == '{'.code ||
            current == '['.code ||
            current == '"'.code ||
            current == 't'.code ||
            current == 'f'.code ||
            current == '-'.code ||
            current == 'n'.code ||
            (current >= '0'.code && current <= '9'.code)
        ) {
            lastTokenByte = current
            return
        } else {
            error("Invalid json TODO")
        }
    }

    /**
     * Skips whitespaces after current position.
     * Updates position and returns first non-whitespace byte.
     */
    private fun skipWhitespace(): Byte {
        val buff = buffer
        var pos = position
        while (pos >= limit || isJsonWhitespace(buff[pos])) {
            if (pos >= limit) {
                val n = input.readAtMostTo(sink = buff, offset = 0, length = buff.size)
                if (n <= 0) {
                    error("Unexpected EOF")
                }
                limit = n
                pos = 0
            } else {
                pos++
            }
        }
        position = pos
        return buff[pos]
    }

    /**
     * Runs [block] with [input] and this scope as context values.
     * Does not advance the initial token, reset state on entry or exit, or close the source.
     * Buffer and cursor changes made by the block remain in this scope, including after failure.
     *
     * @return The block's result of type [T].
     */
    inline fun <T> scoped(input: ByteSource, block: context(ByteSource, JsonReadScope) () -> T): T {
        return block(input, this)
    }
}
