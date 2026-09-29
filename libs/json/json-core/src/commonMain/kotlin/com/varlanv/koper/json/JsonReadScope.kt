package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.DataSize

/**
 * Mutable state for a read operation's context. Construction allocates one buffer with [bufferSize] capacity.
 * String decoding uses this buffer in place; refills may overwrite it, compact it, or replace it during growth.
 * Field and string offsets identify borrowed bytes that must be consumed before further buffer mutations.
 *
 * @param bufferSize Positive initial buffer capacity; retained strings may require growth.
 */
class JsonReadScope(bufferSize: DataSize) {
    init {
        require(bufferSize.bytes > 0)
    }

    /**
     * Shared storage for buffered input and decoded string bytes. Growth replaces this storage.
     */
    internal var buffer = bufferSize.allocate()

    /**
     * Last token byte consumed by the protocol, or -1 before initialization and at end of input.
     */
    var last = -1

    /**
     * Start of the most recently read field name in [buffer].
     */
    var fieldOffset = 0

    /**
     * Length in bytes of the most recently read, unescaped field name.
     */
    var fieldSize = 0

    /**
     * Next unread input offset; string scans leave incomplete escapes at this position.
     */
    var position = 0

    /**
     * Exclusive end of populated buffer bytes, including any retained decoded prefix.
     */
    var limit = 0

    /**
     * Start of the current decoded string in [buffer]; compaction may move it to zero.
     */
    var stringOffset = 0

    /**
     * Decoded byte count after a successful string read; reset to zero when reading or skipping starts.
     */
    var stringLength = 0

    /**
     * Next output offset during string decoding; always at or before the next unread input offset.
     */
    internal var stringOutputPosition = 0

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
