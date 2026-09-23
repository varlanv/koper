package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.nio.CharBuffer
import kotlin.random.Random

class CharsetJvmSpec : BaseSpec({
    for (charset in listOf(Charset.Utf8, Charset.Ascii, Charset.Latin1)) {
        context(charset.toString()) {
            should("match JDK encoding for every Unicode code point") {
                val actual = ByteArray(4)
                for (codepoint in 0..0x10FFFF) {
                    val chars = String(Character.toChars(codepoint))
                    val expected = chars.toByteArray(charset.jdkEncoding())
                    var size = 0
                    charset.encodeInline(codepoint) { actual[size++] = it }
                    check(size == expected.size && expected.indices.all { actual[it] == expected[it] }) {
                        "Code point mismatch: $codepoint"
                    }
                    size = 0
                    charset.encodeInline(chars) { actual[size++] = it }
                    check(size == expected.size && expected.indices.all { actual[it] == expected[it] }) {
                        "Character sequence mismatch: $codepoint"
                    }
                }
            }

            should("match JDK replacement of unpaired surrogates") {
                val inputs = listOf(
                    "",
                    "ASCII\u0000é中🙂",
                    "\uD800",
                    "\uDC00",
                    "\uD800A",
                    "A\uDC00",
                    "\uD800\uD800",
                    "\uDC00\uDC00",
                    "\uDC00\uD800",
                    "\uD800\uD800\uDC00",
                    "\uD800\uDC00\uDC00",
                    "\uD800A\uDC00",
                )
                for (input in inputs) {
                    val expected = input.toByteArray(charset.jdkEncoding()).toList()
                    for (chars in listOf(
                        input,
                        StringBuilder(input),
                        CharBuffer.wrap(input),
                    )) {
                        val actual = mutableListOf<Byte>()
                        charset.encodeInline(chars) { actual.add(it) }
                        actual shouldBe expected
                    }
                }
            }

            should("match JDK encoding for random UTF-16 sequences") {
                val random = Random(42)
                repeat(1_000) {
                    val chars = String(CharArray(random.nextInt(256)) { random.nextInt(0x10000).toChar() })
                    val actual = mutableListOf<Byte>()
                    charset.encodeInline(chars) { actual.add(it) }
                    actual shouldBe chars.toByteArray(charset.jdkEncoding()).toList()
                }
            }
        }
    }
})
