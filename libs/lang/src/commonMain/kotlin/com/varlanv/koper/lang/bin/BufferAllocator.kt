package com.varlanv.koper.lang.bin

class BufferAllocator(dataSize: DataSize) {
    inline fun<T> use(block: (buffer: MovingBytesSlice) -> T): T {
        try {
            return block(MovingBytesSlice())
        } finally {
        }
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

class MovingBytesSlice {
    //    fun ensureCapacity(size: Int) {
    //        if (bytes.size < )
    //    }
}
