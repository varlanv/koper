package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.*

internal fun ByteArray.asByteSource(): ByteArraySource = ByteArraySource(
    BytesSlice(
        bytes = Bytes(MutBytes(this)),
        offset = 0,
        len = size,
    ),
)

internal fun ReusableByteArraySink.toByteArray(): ByteArray = useBytes { bytes, length ->
    ByteArray(length) { bytes[it] }
}
