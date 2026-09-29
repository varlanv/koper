package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.DataSize
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

class JsonParseScope(bufferSize: DataSize) {
    init {
        require(bufferSize.bytes > 0)
    }

    internal val buffer = bufferSize.allocate()
    var last = -1
    var fieldOffset = 0
    var fieldSize = 0
    var position = 0
    var limit = 0
    var field = buffer
    var scratch = MutBytes(256.bytes())
    var scratchSize = 0
    var scratchBytes: MutBytes = scratch
    var scratchOffset = 0
    var scratchLength = 0

    @PublishedApi
    internal fun reset() {
        position = 0
        limit = 0
        scratchSize = 0
        scratchOffset = 0
        scratchLength = 0
        fieldOffset = 0
        fieldSize = 0
        last = -1
        field = buffer
    }

    inline fun scoped(input: ByteSource, block: context(ByteSource, JsonParseScope) () -> Unit) {
        try {
            block(input, this)
        } finally {
            reset()
        }
    }
}
