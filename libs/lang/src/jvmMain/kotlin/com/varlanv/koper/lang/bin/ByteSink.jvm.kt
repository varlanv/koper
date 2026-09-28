package com.varlanv.koper.lang.bin

import java.io.OutputStream

/** Adapts [outs] to a [ByteSink]. */
class OutputStreamByteSink(val outs: OutputStream) : ByteSink {
    override fun writeTo(
        source: MutBytes,
        offset: Int,
        length: Int,
    ) {
        outs.write(source.impl, offset, length)
    }
}
