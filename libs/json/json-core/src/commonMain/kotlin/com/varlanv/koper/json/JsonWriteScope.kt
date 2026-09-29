package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.DataSize
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

class JsonWriteScope(bufferSize: DataSize = 512.bytes()) {
    internal var buffer = bufferSize.allocate()
    internal var position = 0

    @PublishedApi
    internal fun reset() {
        position = 0
    }

    inline fun scoped(sink: ByteSink, block: context(ByteSink, JsonWriteScope) () -> Unit) {
        try {
            block(sink, this)
        } finally {
            reset()
        }
    }
}
