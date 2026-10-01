package com.varlanv.koper.lang.bin

import java.io.OutputStream

/** Adapts [outs] to a [ByteSink]. */
class OutputStreamByteSink(val outs: OutputStream) : ByteSink {
    override fun writeTo(
        source: Bytes,
        offset: Int,
        length: Int,
    ) {
        outs.write(source.bytes.impl, offset, length)
    }
}
