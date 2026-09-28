package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteArraySource
import com.varlanv.koper.lang.bin.ByteSlice
import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.ReusableByteArraySink

internal fun ByteArray.asByteSource(): ByteArraySource = ByteArraySource(
    ByteSlice(
        bytes = Bytes(this),
        offset = 0,
        len = size,
    ),
)

internal fun ReusableByteArraySink.toByteArray(): ByteArray = unsafeUseBytes { bytes, length -> bytes.copyOf(length) }
