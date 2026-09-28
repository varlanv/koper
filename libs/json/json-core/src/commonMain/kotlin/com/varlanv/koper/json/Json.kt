package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.bin.bytes

class Json(
    private val readProtocol: JsonReadProtocol = JsonReadProtocol(),
    private val writeProtocol: JsonWriteProtocol = JsonWriteProtocol(),
) {
    fun <T> writeTo(
        sink: ByteSink,
        write: JsonCodec.Write<T>,
        value: T,
    ) {}

    fun <T> readFrom(source: ByteSource, read: JsonCodec.Read<T>): T {
        read.read(readProtocol)
        TODO()
    }
}

fun main() {
    // usage sample
    val json = Json()

    // sample 1 - Codec has both "Read" and "Write" sides implemented
    json.writeTo(
        sink = ReusableByteArraySink(10.bytes()),
        write = IntJsonCodec,
        value = 1,
    )

    // todo sample 2 - for example user specified his type `User` with only @Ser annotation.
    //  Then, we generate `UserJsonCodec` that implements only `Write` side.
    //  Then user can write it as `json.writeTo(sink, UserJsonCodec, user)`
    //  But if user tries to `val user = json.readFrom(source, UserJsonCodec)` - it will not compile, because read side was not implemented
}
