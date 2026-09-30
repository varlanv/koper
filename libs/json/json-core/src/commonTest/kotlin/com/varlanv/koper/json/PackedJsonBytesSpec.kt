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

    should("classify adjacent bytes without marking neighboring ordinary bytes") {
        val scanner = ScalarJsonSpecialScan
        val input = MutBytes(ByteArray(scanner.laneCount + 2) { 65 })
        for (first in 0..255) {
            for (second in 0..255) {
                val lane = (first + second) % (scanner.laneCount - 1)
                input[lane + 1] = first.toByte()
                input[lane + 2] = second.toByte()
                var expected = jsonEmptyScanMask()
                if (first < 32 || first == 34 || first == 92) {
                    expected = expected.withBit(lane)
                }
                if (second < 32 || second == 34 || second == 92) {
                    expected = expected.withBit(lane + 1)
                }
                scanner.specialMask(
                    bytes = Bytes(input),
                    start = 1,
                ) shouldBe expected
                input[lane + 1] = 65
                input[lane + 2] = 65
            }
        }
    }

    should("scan only the supplied slice across complete words and tails") {
        for (scanner in listOf(ScalarJsonSpecialScan, jsonScanner)) {
            for (length in 0..scanner.laneCount * 2 + 1) {
                val input = MutBytes(ByteArray(length + 4) { 34 })
                for (index in 1..length) input[index] = 65
                scanner.firstSpecial(
                    bytes = Bytes(input),
                    start = 1,
                    end = length + 1,
                ) shouldBe length + 1
                for (special in 1..length) {
                    input[special] = 34
                    scanner.firstSpecial(
                        bytes = Bytes(input),
                        start = 1,
                        end = length + 1,
                    ) shouldBe special
                    input[special] = 65
                }
            }
        }
    }

    should("escape complete packed blocks without reading outside the input") {
        for (length in 1..jsonPackedByteCount * 3 + 1) {
            for (special in 0 until length) {
                val chars = CharArray(length) { 'a' }
                chars[0] = '"'
                chars[special] = '\n'
                chars[length - 1] = '\u0000'
                val text = chars.concatToString()
                val input = Bytes(MutBytes(text.encodeToByteArray()))
                val output = MutBytes(ByteArray(length * 6 + 2) { 127 })
                val end = JsonStringEscapes.writeUtf8EscapedPacked(
                    bytes = input,
                    target = output,
                    start = 0,
                    end = input.size,
                    targetStart = 1,
                )
                val actual = (1 until end).map { output[it] }.toByteArray().decodeToString()
                actual shouldBe kotlinx.serialization.json.JsonPrimitive(text).toString().drop(1).dropLast(1)
                output[0] shouldBe 127.toByte()
            }
        }
    }

    should("write packed fragments with negative Int words in little-endian order") {
        val output = ReusableByteArraySink(1.bytes())
        JsonWriteScope().scoped(output) {
            val position = JsonWriteProtocol.reserve(size = 42, position = 0)
            JsonWriteProtocol.writeRaw(value = 0xffeeddccu.toInt(), position = position)
            JsonWriteProtocol.writeRaw(value = 0xbbaa.toShort(), position = position + 4)
            JsonWriteProtocol.writeRaw(first = 0xffeeddccu.toInt(), second = 0xbbaa.toShort(), position = position + 6)
            JsonWriteProtocol.writeRaw(
                first = 0xffeeddccu.toInt(),
                second = 0x99887766u.toInt(),
                position = position + 12,
            )
            JsonWriteProtocol.writeRaw(
                first = 0xffeeddccu.toInt(),
                second = 0x99887766u.toInt(),
                third = 0x5544.toShort(),
                position = position + 20,
            )
            JsonWriteProtocol.writeRaw(
                first = 0xffeeddccu.toInt(),
                second = 0x99887766u.toInt(),
                third = 0x55443322,
                position = position + 30,
            )
            JsonWriteProtocol.flush(position + 42)
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
