package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.DataSize
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.bytes

class JsonReadScope(bufferSize: DataSize) {
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

    inline fun <T> scoped(input: ByteSource, block: context(ByteSource, JsonReadScope) () -> T): T {
        return block(input, this)
    }
}
