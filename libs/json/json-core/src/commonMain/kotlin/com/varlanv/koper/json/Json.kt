package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.bytes

class Json {
    fun <T> writeTo(
        sink: ByteSink,
        writeCodec: JsonCodec.Write<T>,
        value: T,
    ) {
        JsonWriteScope().scoped(sink) {
            writeCodec.write(value)
        }
    }

    fun <T> readFrom(source: ByteSource, readCodec: JsonCodec.Read<T>): T {
        JsonReadScope(10.bytes()).scoped(source) {
            readCodec.read()
        }
        TODO()
    }
}

fun main() {
    // usage sample
    //    val json = Json()

    // sample 1 - Codec has both "Read" and "Write" sides implemented
    //    json.writeTo(
    //        sink = ReusableByteArraySink(10.bytes()),
    //        write = IntJsonCodec,
    //        value = 1,
    //    )

    // todo sample 2 - for example user specified his type `User` with only @Ser annotation.
    //  Then, we generate `UserJsonCodec` that implements only `Write` side.
    //  Then user can write it as `json.writeTo(sink, UserJsonCodec, user)`
    //  But if user tries to `val user = json.readFrom(source, UserJsonCodec)` - it will not compile, because read side was not implemented
}
