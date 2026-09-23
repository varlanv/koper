package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlin.random.Random

class DoubleAsciiJvmSpec : BaseSpec({
    should("bound every normalized decimal power with exact integer arithmetic") {
        doubleDecimalPowers.size shouldBe 617 * 2
        for (exponent in -292..324) {
            var numerator = BigInteger.TEN.pow(maxOf(exponent, 0))
            var denominator = BigInteger.TEN.pow(maxOf(-exponent, 0))
            while (numerator < denominator.shiftLeft(127)) numerator = numerator.shiftLeft(1)
            while (numerator >= denominator.shiftLeft(128)) denominator = denominator.shiftLeft(1)
            val index = (exponent + 292) * 2
            val high = BigInteger(doubleDecimalPowers[index].toULong().toString())
            val low = BigInteger(doubleDecimalPowers[index + 1].toULong().toString())
            val cached = high.shiftLeft(64).add(low)
            withClue("decimal exponent=$exponent") {
                cached.bitLength() shouldBe 128
                (cached * denominator > numerator) shouldBe true
                ((cached - BigInteger.ONE) * denominator <= numerator) shouldBe true
            }
        }
    }

    should("match JDK canonical strings for 250000 random raw bit patterns") {
        val random = Random(718_903_521)
        val output = ByteArray(40)
        repeat(250_000) {
            verifyDoubleAsciiAgainstJdk(random.nextLong(), output)
        }
    }

    should("match JDK canonical strings at both sides of every exponent boundary") {
        val output = ByteArray(40)
        forEachAsciiDoubleBoundary { bits ->
            verifyDoubleAsciiAgainstJdk(bits, output)
        }
    }

    should("match JDK formatting across digit chunks decimal positions and zero tails") {
        val output = ByteArray(40)
        var caseIndex = 0
        for (length in 1..17) {
            val significands = linkedSetOf(
                "1".padEnd(length, '0'),
                "9".repeat(length),
                "12345678912345678".take(length),
                "10000000100000001".take(length),
                "99900099900099900".take(length),
            )
            for (zeroTail in 0 until length) {
                significands += "12345678987654321".take(length - zeroTail).padEnd(length, '0')
            }
            val points = ((-2..10).toList() + listOf(length - 1, length, length + 1)).distinct()
            for (significand in significands) {
                for (point in points) {
                    val positive = "${significand}e${point - length}".toDouble()
                    for (value in doubleArrayOf(positive, -positive)) {
                        verifyDoubleAsciiAgainstJdk(value.toRawBits(), output)
                        val expected = value.toString()
                        val offset = caseIndex++ and 7
                        val exact = ByteArray(offset + expected.length) { 0x5a }
                        withClue("significand=$significand, point=$point, expected=$expected, offset=$offset") {
                            val end = writeDoubleAscii(value, exact, offset)
                            end shouldBe exact.size
                            exact.decodeToString(offset, end) shouldBe expected
                            for (index in 0 until offset) exact[index] shouldBe 0x5a.toByte()
                        }
                    }
                }
            }
        }
    }
})

private fun verifyDoubleAsciiAgainstJdk(bits: Long, output: ByteArray) {
    val value = Double.fromBits(bits)
    val expected = value.toString()
    val offset = bits.toInt() and 7
    output.fill(0x5a)
    withClue("bits=$bits, expected=$expected, offset=$offset") {
        val end = writeDoubleAscii(value, output, offset)
        end shouldBe offset + expected.length
        output.decodeToString(offset, end) shouldBe expected
        for (index in 0 until offset) output[index] shouldBe 0x5a.toByte()
        for (index in end until output.size) output[index] shouldBe 0x5a.toByte()
    }
}
