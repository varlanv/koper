package com.varlanv.koper.lang.math

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class NumbersSpec : BaseSpec({
    should("write zero, signed extrema and the Int to Long transition") {
        for (value in longArrayOf(
            0,
            1,
            -1,
            Int.MIN_VALUE.toLong() - 1,
            Int.MIN_VALUE.toLong(),
            Int.MIN_VALUE.toLong() + 1,
            Int.MAX_VALUE.toLong() - 1,
            Int.MAX_VALUE.toLong(),
            Int.MAX_VALUE.toLong() + 1,
            Long.MIN_VALUE,
            Long.MIN_VALUE + 1,
            Long.MAX_VALUE - 1,
            Long.MAX_VALUE,
        )) {
            verifyAscii(value = value)
            verifyAscii(value = value, offset = 0, suffix = 0)
            verifyAscii(value = value, offset = 7, suffix = 0)
        }
    }

    should("write values around every decimal length boundary in exact fit buffers") {
        var power = 1L
        repeat(19) {
            for (delta in -2L..2L) {
                val value = power + delta
                for (offset in 0..7) {
                    verifyAscii(value = value, offset = offset, suffix = 0)
                    verifyAscii(value = -value, offset = offset, suffix = 0)
                }
            }
            if (it < 18) {
                power *= 10
            }
        }
    }

    should("write every small positive and negative integer without changing surrounding bytes") {
        for (value in -10_000..10_000) {
            verifyAscii(value = value.toLong(), offset = value and 7)
        }
    }

    should("preserve zero padding inside decimal chunks") {
        for (value in longArrayOf(
            100_001,
            1_000_001,
            10_000_001,
            100_000_001,
            1_000_000_001,
            10_000_000_001,
            100_000_000_001,
            1_000_000_000_001,
            10_000_000_000_001,
            100_000_000_000_001,
            1_000_000_000_000_001,
            10_000_000_000_000_001,
            100_000_000_000_000_001,
            1_000_000_000_000_000_001,
            1_002_003_004_005_006_007,
            9_000_000_000_000_000_009,
            9_090_090_009_090_090_009,
        )) {
            verifyAscii(value = value)
            verifyAscii(value = -value)
        }
    }

    should("match decimal strings for deterministic random Int and Long values") {
        val random = Random(718_291)
        repeat(20_000) {
            val offset = random.nextInt(8)
            val suffix = random.nextInt(8)
            verifyAscii(value = random.nextInt().toLong(), offset = offset, suffix = suffix)
            verifyAscii(value = random.nextLong(), offset = offset, suffix = suffix)
        }
    }

    should("chain mixed writes using returned offsets and reuse the destination") {
        val array = ByteArray(128) { 0x5a }
        val offset = 5
        var end = writeIntAscii(value = Int.MIN_VALUE, array = array, offset = offset)
        array[end++] = ','.code.toByte()
        end = writeLongAscii(value = Long.MAX_VALUE, array = array, offset = end)
        array[end++] = ','.code.toByte()
        end = writeIntAscii(value = 0, array = array, offset = end)
        array[end++] = ','.code.toByte()
        end = writeLongAscii(value = Long.MIN_VALUE, array = array, offset = end)
        array[end++] = ','.code.toByte()
        end = writeIntAscii(value = Int.MAX_VALUE, array = array, offset = end)

        val text = "${Int.MIN_VALUE},${Long.MAX_VALUE},0,${Long.MIN_VALUE},${Int.MAX_VALUE}"
        val expected = ByteArray(array.size) { 0x5a }
        text.encodeToByteArray().copyInto(expected, offset)
        end shouldBe offset + text.length
        array shouldBe expected

        writeIntAscii(value = 7, array = array, offset = offset) shouldBe offset + 1
        expected[offset] = '7'.code.toByte()
        array shouldBe expected

        writeLongAscii(value = -42, array = array, offset = offset) shouldBe offset + 3
        "-42".encodeToByteArray().copyInto(expected, offset)
        array shouldBe expected
    }
})

private fun verifyAscii(
    value: Long,
    offset: Int = 3,
    suffix: Int = 5,
) {
    withClue("value=$value, offset=$offset, suffix=$suffix") {
        val bytes = value.toString().encodeToByteArray()
        val expected = ByteArray(offset + bytes.size + suffix) { (it * 37 + 128).toByte() }
        val longArray = expected.copyOf()
        val intArray = if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            expected.copyOf()
        } else {
            null
        }
        bytes.copyInto(expected, offset)

        writeLongAscii(value = value, array = longArray, offset = offset) shouldBe offset + bytes.size
        longArray shouldBe expected

        if (intArray != null) {
            writeIntAscii(value = value.toInt(), array = intArray, offset = offset) shouldBe offset + bytes.size
            intArray shouldBe expected
        }
    }
}
