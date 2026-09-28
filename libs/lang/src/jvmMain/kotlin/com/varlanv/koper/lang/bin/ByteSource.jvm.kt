package com.varlanv.koper.lang.bin

import java.io.InputStream

/** Adapts [ins] to a [ByteSource]. */
class InputStreamByteSource(val ins: InputStream) : ByteSource {
    override fun readAtMostTo(
        sink: MutBytes,
        offset: Int,
        length: Int,
    ): Int {
        return ins.read(sink.impl, offset, length)
    }
}
