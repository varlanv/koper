package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.DataSize
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

/**
 * Mutable output state for a write operation's context. Construction allocates a buffer with [bufferSize] capacity.
 * Position counts pending bytes; reservations may flush them to the sink and replace the buffer with a larger allocation.
 * Leaving [scoped] discards pending bytes while retaining the allocation for reuse; callers must flush before leaving.
 *
 * @param bufferSize Initial output buffer capacity.
 */
class JsonWriteScope(bufferSize: DataSize = 512.bytes()) {
    /**
     * Storage for pending output; reservations may replace it after flushing.
     */
    internal var buffer = bufferSize.allocate()

    /**
     * Exclusive end of pending output bytes in [buffer].
     */
    internal var position = 0

    /**
     * Discards pending output by setting position to zero. Retains the allocation and does not write to the sink.
     *
     * @return [Unit] after resetting position.
     */
    @PublishedApi
    internal fun reset() {
        position = 0
    }

    /**
     * Runs [block] with [sink] and this scope as context values, then resets position even if the block fails.
     * Does not flush pending output, clear buffer contents, or close the sink. The block must flush bytes it needs to preserve.
     *
     * @return [Unit] after the block and reset complete.
     */
    inline fun scoped(sink: ByteSink, block: context(ByteSink, JsonWriteScope) () -> Unit) {
        try {
            block(sink, this)
        } finally {
            reset()
        }
    }
}
