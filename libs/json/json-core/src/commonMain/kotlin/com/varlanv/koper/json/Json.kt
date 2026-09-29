package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.BufferAllocator
import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.bin.kilobytes

val GlobalJson = Json()

class Json(private val allocator: BufferAllocator = BufferAllocator(128.kilobytes())) {
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
        return allocator.use { buffer ->
            JsonReadScope(128.bytes()).scoped(source) {
                readCodec.read()
            }
        }
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
