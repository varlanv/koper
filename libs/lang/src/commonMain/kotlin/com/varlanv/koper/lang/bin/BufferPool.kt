package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.ext.ThreadScope

class BufferPool(bufferSize: DataSize) {
    @PublishedApi
    internal val backing: ThreadScope<MutBytes> = ThreadScope({ bufferSize.allocate() })

    fun get(): ReleasableBytes {
        val b = backing.get()
        return object : ReleasableBytes {
            override val bytes: MutBytes = b

            override fun release(value: MutBytes) {
                backing.set(value)
            }
        }
    }

    interface ReleasableBytes {
        /**
         * Bytes value
         */
        val bytes: MutBytes

        /**
         * Returns buffer to pool - either same or potentially new buffer with increased capacity.
         */
        fun release(value: MutBytes)
    }

    //
    //
    //    fun get(): MutBytes
    //
    //    class ThreadScoped(initialSize: DataSize = 64.kilobytes()) : BackingStorage {
    //        internal val tl: ThreadScope<MutBytes> = ThreadScope { initialSize.allocate() }
    //        override fun get(): MutBytes = tl.get()
    //    }
}
