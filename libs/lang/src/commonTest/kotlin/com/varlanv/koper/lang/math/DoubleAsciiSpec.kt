package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class DoubleAsciiSpec : BaseSpec({
    should("write canonical special values and difficult finite values into exact fit buffers") {
        for ((bits, expected) in listOf(
            0L to "0.0",
            0x1L to "4.9E-324",
            0x2L to "9.9E-324",
            0x3L to "1.5E-323",
            0xfffffffffffffL to "2.225073858507201E-308",
            0x10000000000000L to "2.2250738585072014E-308",
            0x10000000000001L to "2.225073858507202E-308",
            0x7fefffffffffffffL to "1.7976931348623157E308",
            0x3f50624dd2f1a9fbL to "9.999999999999998E-4",
            0x3f50624dd2f1a9fcL to "0.001",
            0x3f50624dd2f1a9fdL to "0.0010000000000000002",
            0x416312cfffffffffL to "9999999.999999998",
            0x416312d000000000L to "1.0E7",
            0x416312d000000001L to "1.0000000000000002E7",
            0x44b52d02c7e14af6L to "1.0E23",
            0x4340000000000000L to "9.007199254740992E15",
            0x4340000000000001L to "9.007199254740994E15",
            0x3fd5555555555555L to "0.3333333333333333",
            0x3fefffffffffffffL to "0.9999999999999999",
            0x3ff0000000000000L to "1.0",
            0x3ff0000000000001L to "1.0000000000000002",
            0x7ff0000000000000L to "Infinity",
            0x7ff0000000000001L to "NaN",
            0x7ff8000000000000L to "NaN",
            0x7fffffffffffffffL to "NaN",
        )) {
            for (offset in 0..7) {
                for (suffix in intArrayOf(0, 1, 5)) {
                    verifyDoubleAsciiExpected(Double.fromBits(bits), expected, offset, suffix)
                    verifyDoubleAsciiExpected(
                        Double.fromBits(bits or Long.MIN_VALUE),
                        if (expected == "NaN") expected else "-$expected",
                        offset,
                        suffix,
                    )
                }
            }
        }
    }

    should("round trip both neighbors of every binary and decimal exponent boundary") {
        forEachAsciiDoubleBoundary { bits ->
            verifyDoubleAsciiRoundTrip(bits)
        }
    }

    should("round trip deterministic random bit patterns without changing surrounding bytes") {
        val random = Random(93_729_041)
        repeat(50_000) {
            verifyDoubleAsciiRoundTrip(random.nextLong(), random.nextInt(8))
        }
    }

    should("match JDK 26 canonical strings for 30000 deterministic raw bit patterns") {
        val fingerprint = DoubleAsciiFingerprint()
        var bits = 0x123456789abcdefL
        repeat(30_000) {
            bits = bits * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            fingerprint.consume(bits)
        }
        fingerprint.first shouldBe 1_733_187_151
        fingerprint.second shouldBe 104_686_476
    }

    should("match JDK 26 canonical strings across all exponent boundaries") {
        val fingerprint = DoubleAsciiFingerprint()
        forEachAsciiDoubleBoundary { bits ->
            fingerprint.consume(bits)
        }
        fingerprint.first shouldBe -1_899_912_752
        fingerprint.second shouldBe 742_161_885
    }

    should("chain writes and preserve following bytes when reusing the destination") {
        val values = doubleArrayOf(-0.0, Double.MIN_VALUE, 0.001, 1e7, Double.MAX_VALUE, Double.NaN)
        val text = "-0.0,4.9E-324,0.001,1.0E7,1.7976931348623157E308,NaN"
        val output = ByteArray(128) { 0x5a }
        var end = 5
        for (index in values.indices) {
            if (index != 0) output[end++] = ','.code.toByte()
            end = writeDoubleAscii(values[index], output, end)
        }
        val expected = ByteArray(output.size) { 0x5a }
        text.encodeToByteArray().copyInto(expected, 5)
        end shouldBe 5 + text.length
        output shouldBe expected
        writeDoubleAscii(2.0, output, 5) shouldBe 8
        "2.0".encodeToByteArray().copyInto(expected, 5)
        output shouldBe expected
    }
})

internal fun forEachAsciiDoubleBoundary(action: (Long) -> Unit) {
    fun neighbors(bits: Long) {
        for (delta in -1L..1L) {
            action(bits + delta)
            action((bits + delta) or Long.MIN_VALUE)
        }
    }
    for (bit in 0..51) neighbors(1L shl bit)
    for (exponent in 1L..2046L) neighbors(exponent shl 52)
    for (exponent in -323..308) neighbors("1e$exponent".toDouble().toRawBits())
}

private fun verifyDoubleAsciiExpected(value: Double, text: String, offset: Int, suffix: Int) {
    withClue("bits=${value.toRawBits()}, expected=$text, offset=$offset, suffix=$suffix") {
        val expected = ByteArray(offset + text.length + suffix) { (it * 37 + 128).toByte() }
        val output = expected.copyOf()
        text.encodeToByteArray().copyInto(expected, offset)
        writeDoubleAscii(value, output, offset) shouldBe offset + text.length
        output shouldBe expected
    }
}

private fun verifyDoubleAsciiRoundTrip(bits: Long, offset: Int = 3) {
    val value = Double.fromBits(bits)
    withClue("bits=$bits, offset=$offset") {
        val output = ByteArray(40) { 0x5a }
        val end = writeDoubleAscii(value, output, offset)
        check(end in offset + 1..offset + 24) { "Invalid output length: ${end - offset}" }
        for (index in 0 until offset) output[index] shouldBe 0x5a.toByte()
        for (index in end until output.size) output[index] shouldBe 0x5a.toByte()
        val parsed = output.decodeToString(offset, end).toDouble()
        if (value.isNaN()) {
            parsed.isNaN() shouldBe true
        } else {
            parsed.toRawBits() shouldBe bits
        }
    }
}

private class DoubleAsciiFingerprint {
    var first = 0
    var second = -2_128_831_035
    private val output = ByteArray(24)

    fun consume(bits: Long) {
        val end = writeDoubleAscii(Double.fromBits(bits), output, 0)
        for (index in 0 until end) {
            val code = output[index].toInt()
            first = first * 31 + code
            second = (second xor code) * 16_777_619
        }
        first *= 31
        second *= 16_777_619
    }
}
