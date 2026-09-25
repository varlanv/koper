package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class NumbersJsSpec : BaseSpec({
    should("write limb boundaries and values around decimal chunks exactly") {
        val limbs = intArrayOf(0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE, 999_999, 1_000_000, 1_000_001)
        for (high in limbs) {
            for (low in limbs) {
                verifyLimbAscii(value = (high.toLong() shl 32) or (low.toLong() and 0xffff_ffffL))
            }
        }
        for (base in longArrayOf(1_000_000, 1_000_000_000_000, 1_000_000_000_000_000_000, 9_007_199_254_740_992)) {
            for (delta in -20L..20L) {
                verifyLimbAscii(value = base + delta)
                verifyLimbAscii(value = -base + delta)
            }
        }
        verifyLimbAscii(value = Long.MIN_VALUE)
        verifyLimbAscii(value = Long.MAX_VALUE)
    }

    should("match decimal strings for arbitrary 64-bit inputs with exact capacity") {
        val random = Random(71)
        repeat(100_000) { verifyLimbAscii(value = random.nextLong(), offset = it and 7, suffix = it and 1) }
    }
})

private fun verifyLimbAscii(
    value: Long,
    offset: Int = 3,
    suffix: Int = 5,
) {
    val text = value.toString()
    val expected = ByteArray(offset + text.length + suffix) { 85 }
    text.encodeToByteArray().copyInto(expected, offset)
    val output = ByteArray(expected.size) { 85 }
    writeLongAscii(value = value, array = output, offset = offset) shouldBe offset + text.length
    output shouldBe expected
}
