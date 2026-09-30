package com.varlanv.koper.json

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.MutBytes
import com.varlanv.koper.lang.bin.ReusableByteArraySink
import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class PackedJsonBytesSpec : BaseSpec({
    should("read decimal groups and reject every non-digit in every packed lane") {
        val input = MutBytes(ByteArray(jsonPackedDigitCount + 2))
        for (number in 0..9999) {
            val digits = number.toString().padStart(jsonPackedDigitCount, '0')
            for (lane in digits.indices) input[lane + 1] = digits[lane].code.toByte()
            jsonReadPackedDigits(bytes = input, index = 1) shouldBe number
        }
        val largest = "9".repeat(jsonPackedDigitCount)
        for (lane in largest.indices) input[lane + 1] = largest[lane].code.toByte()
        jsonReadPackedDigits(bytes = input, index = 1) shouldBe largest.toInt()
        for (fill in listOf(48, 57)) {
            for (lane in 0 until jsonPackedDigitCount) {
                for (value in 0..255) {
                    for (index in 0 until jsonPackedDigitCount) input[index + 1] = fill.toByte()
                    input[lane + 1] = value.toByte()
                    if (value !in 48..57) {
                        jsonReadPackedDigits(bytes = input, index = 1) shouldBe -1
                    }
                }
            }
        }
    }

    should("match each prefix length with unsigned bytes at an unaligned offset") {
        val expected = byteArrayOf(0x80.toByte(), 34, 58, 0xff.toByte(), 92, 65, 0xfe.toByte(), 0xf0.toByte())
        val input = MutBytes(ByteArray(10))

        fun match(size: Int): Boolean {
            var low = 0
            var high = 0
            for (index in 0 until minOf(4, size)) {
                low = low or ((expected[index].toInt() and 255) shl (index * 8))
            }
            for (index in 4 until size) {
                high = high or ((expected[index].toInt() and 255) shl ((index - 4) * 8))
            }
            return jsonFieldWordMatches(
                head = jsonPeekFieldHead(
                    bytes = input,
                    position = 1,
                    limit = input.size,
                ),
                tail = jsonPeekFieldTail(
                    bytes = input,
                    position = 1,
                    limit = input.size,
                ),
                low = low,
                high = high,
                byteCount = size,
            )
        }
        for (size in 1..8) {
            for (index in expected.indices) input[index + 1] = expected[index]
            match(size) shouldBe true
            for (index in expected.indices) {
                input[index + 1] = (expected[index].toInt() xor 1).toByte()
                match(size) shouldBe (index >= size)
                input[index + 1] = expected[index]
            }
        }
    }

    should("find and clear every scalar and vector mask lane") {
        for (scanner in listOf(ScalarJsonSpecialScan, jsonScanner)) {
            val input = MutBytes(ByteArray(scanner.laneCount + 2) { 65 })
            for (lane in 0 until scanner.laneCount) {
                for (value in 0..255) {
                    input[lane + 1] = value.toByte()
                    val special = value < 32 || value == 34 || value == 92
                    val mask = scanner.specialMask(
                        bytes = Bytes(input),
                        start = 1,
                    )
                    mask.hasEvents() shouldBe special
                    scanner.firstSpecial(
                        bytes = Bytes(input),
                        start = 1,
                        end = scanner.laneCount + 1,
                    ) shouldBe if (special) {
                        lane + 1
                    } else {
                        scanner.laneCount + 1
                    }
                    if (special) {
                        mask.firstEvent() shouldBe lane
                        mask.dropFirstEvent().hasEvents() shouldBe false
                        mask.clearBefore(lane).firstEvent() shouldBe lane
                        if (lane + 1 < scanner.laneCount) {
                            mask.clearBefore(lane + 1).hasEvents() shouldBe false
                        }
                    }
                    input[lane + 1] = 65
                }
            }
            var mask = jsonEmptyScanMask()
            for (lane in 0 until scanner.laneCount) mask = mask.withBit(lane)
            for (lane in 0 until scanner.laneCount) {
                mask.firstEvent() shouldBe lane
                mask = mask.dropFirstEvent()
            }
            mask.hasEvents() shouldBe false
        }
    }

    should("write packed fragments with negative Int words in little-endian order") {
        val output = ReusableByteArraySink(1.bytes())
        JsonWriteScope().scoped(output) {
            JsonWriteProtocol.reserve(42)
            JsonWriteProtocol.writeRaw(value = 0xffeeddccu.toInt())
            JsonWriteProtocol.writeRaw(value = 0xbbaa.toShort())
            JsonWriteProtocol.writeRaw(first = 0xffeeddccu.toInt(), second = 0xbbaa.toShort())
            JsonWriteProtocol.writeRaw(first = 0xffeeddccu.toInt(), second = 0x99887766u.toInt())
            JsonWriteProtocol.writeRaw(
                first = 0xffeeddccu.toInt(),
                second = 0x99887766u.toInt(),
                third = 0x5544.toShort(),
            )
            JsonWriteProtocol.writeRaw(first = 0xffeeddccu.toInt(), second = 0x99887766u.toInt(), third = 0x55443322)
            JsonWriteProtocol.flush()
        }
        output.toByteArray().map { it.toInt() and 255 } shouldBe
            listOf(
                204,
                221,
                238,
                255,
                170,
                187,
                204,
                221,
                238,
                255,
                170,
                187,
                204,
                221,
                238,
                255,
                102,
                119,
                136,
                153,
                204,
                221,
                238,
                255,
                102,
                119,
                136,
                153,
                68,
                85,
                204,
                221,
                238,
                255,
                102,
                119,
                136,
                153,
                34,
                51,
                68,
                85,
            )
    }
})
