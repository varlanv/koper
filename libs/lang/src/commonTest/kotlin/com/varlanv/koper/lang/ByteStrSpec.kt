package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldEqual

private data class Fixture1(val payload: String, val len: Int)

private val fixtures1 =
    listOf("123", "qwe", "привіт", "é中🙂", "A\u0000z", "Aéÿ").map { Fixture1(it, it.encodeToByteArray().size) }

class ByteStrSpec : BaseSpec({

    fixtures1.forEach { fixture ->
        context("fromString ${fixture.payload}") {
            should("return correct len") {
                ByteStr.allocateFromString(fixture.payload).len() shouldEqual fixture.len
            }

            should("return correct encoding") {
                ByteStr.allocateFromString(fixture.payload).encoding() shouldEqual Charset.Utf8
            }

            should("return correct toString") {
                ByteStr.allocateFromString(fixture.payload).toString() shouldEqual fixture.payload
            }

            should("return do correct asBytesSlice round-trip") {
                ByteStr.allocateFromString(fixture.payload).asBytesSlice()
                    .allocateArray() shouldContainExactly fixture.payload.encodeToByteArray()
            }
        }
    }

    context("packed header") {
        should("read a length spanning multiple bytes") {
            ByteStr.allocateFromString("x".repeat(65_537)).len() shouldEqual 65_537
        }

        should("read the charset from byte four") {
            val s = ByteStr.allocateFromString("x")
            s.bytes.array[4] = Charset.Latin1.ordinal.toByte()
            s.encoding() shouldEqual Charset.Latin1
        }
    }

    context("copyInto") {
        should("correctly copy on small ascii") {
            val s = ByteStr.allocateFromString("12345678")
            val dest = ByteArray(10)

            s.copyInto(2, dest, 4, 4)

            dest shouldContainExactly byteArrayOf(0, 0, 0, 0, 51, 52, 53, 54, 0, 0)
        }
    }
})
