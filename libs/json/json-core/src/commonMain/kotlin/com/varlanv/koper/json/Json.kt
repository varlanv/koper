package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.BufferAllocator
import com.varlanv.koper.lang.bin.ByteSink
import com.varlanv.koper.lang.bin.ByteSource
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.bin.kilobytes

/**
 * Default shared [Json] entry point; operation state is created separately for each call.
 */
val GlobalJson = Json()

/**
 * Codec-based read and write entry points. Currently creates fresh operation scopes directly;
 * the allocator is leased during reading but its storage is not yet connected to the read scope.
 *
 * @param allocator Allocator leased by [readFrom] and released through its use callback.
 */
class Json(private val allocator: BufferAllocator = BufferAllocator(128.kilobytes())) {
    /**
     * Invokes [writeCodec] for [value] with a newly allocated write scope and the supplied [sink].
     * The codec may flush output, but this entry point does not flush final pending bytes; scope exit discards them.
     * Does not close the sink.
     *
     * @return [Unit] after the codec returns and the write scope resets.
     */
    fun <T> writeTo(
        sink: ByteSink,
        writeCodec: JsonCodec.Write<T>,
        value: T,
    ) {
        JsonWriteScope().scoped(sink) {
            writeCodec.write(value)
        }
    }

    /**
     * Invokes [readCodec] with [source] and a newly allocated read scope while leasing the allocator.
     * The initial last token remains -1: this stub does not advance it before invoking the codec.
     * Reads may consume source bytes ahead of the decoded value through buffering; this method neither checks for trailing
     * input nor closes the source. The allocator lease ends even if reading fails.
     *
     * @return The codec's decoded result of type [T].
     */
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
