package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class DoubleAsciiJsSpec : BaseSpec({
    should("match JDK 26 for 250000 raw bit patterns through primitive limb arithmetic") {
        val fingerprint = JsDoubleFingerprint()
        var bits = 0x71c2a39e504bd68fL
        repeat(250_000) {
            bits = bits xor (bits shl 13)
            bits = bits xor (bits ushr 7)
            bits = bits xor (bits shl 17)
            fingerprint.consume(bits)
        }
        fingerprint.first shouldBe 1_218_439_791
        fingerprint.second shouldBe 1_602_246_768
    }

    should("preserve carries at every limb boundary across all finite binary exponents") {
        val fingerprint = JsDoubleFingerprint(exactFit = true)
        val mantissas = longArrayOf(
            0, 1, 2,
            (1L shl 22) - 1, 1L shl 22, (1L shl 22) + 1,
            (1L shl 32) - 1, 1L shl 32, (1L shl 32) + 1,
            (1L shl 44) - 1, 1L shl 44, (1L shl 44) + 1,
            (1L shl 51) - 1, 1L shl 51, (1L shl 51) + 1,
            (1L shl 52) - 3, (1L shl 52) - 2, (1L shl 52) - 1,
        )
        for (exponent in 0L..2046L) {
            for (mantissa in mantissas) {
                val bits = (exponent shl 52) or mantissa
                fingerprint.consume(bits)
                fingerprint.consume(bits or Long.MIN_VALUE)
            }
        }
        fingerprint.first shouldBe -977_219_000
        fingerprint.second shouldBe -382_877_477
    }

    should("write exact decimal chunks around integer precision boundaries and zero tails") {
        val fingerprint = JsDoubleFingerprint(exactFit = true)
        for (base in doubleArrayOf(
            1e6, 1e9, 1e12, 1e15, 1e16,
            4_294_967_296.0, 9_007_199_254_740_992.0,
            999_999_999_999_999.0, 9_999_999_999_999_999.0,
        )) {
            val bits = base.toRawBits()
            for (delta in -32L..32L) {
                fingerprint.consume(bits + delta)
                fingerprint.consume((bits + delta) or Long.MIN_VALUE)
            }
        }
        fingerprint.first shouldBe -582_271_581
        fingerprint.second shouldBe 497_763_998
    }
})

private class JsDoubleFingerprint(private val exactFit: Boolean = false) {
    var first = 0
    var second = -2_128_831_035
    private var count = 0
    private val output = ByteArray(40)

    fun consume(bits: Long) {
        val value = Double.fromBits(bits)
        val offset = count++ and 7
        output.fill(85)
        val end = writeDoubleAscii(value, output, offset)
        check(end in offset + 1..offset + 24) { "Invalid output length for bits=$bits" }
        for (index in 0 until offset) {
            check(output[index] == 85.toByte()) { "Modified prefix for bits=$bits" }
        }
        for (index in end until output.size) {
            check(output[index] == 85.toByte()) { "Modified suffix for bits=$bits" }
        }
        for (index in offset until end) {
            val code = output[index].toInt()
            first = first * 31 + code
            second = (second xor code) * 16_777_619
        }
        first *= 31
        second *= 16_777_619
        if (exactFit) {
            val exact = ByteArray(end) { 85 }
            check(writeDoubleAscii(value, exact, offset) == end) { "Invalid exact fit length for bits=$bits" }
            for (index in exact.indices) {
                check(exact[index] == output[index]) { "Different exact fit output for bits=$bits" }
            }
            check(output.decodeToString(offset, end).toDouble().toRawBits() == bits) {
                "Failed round trip for bits=$bits"
            }
        }
    }
}
