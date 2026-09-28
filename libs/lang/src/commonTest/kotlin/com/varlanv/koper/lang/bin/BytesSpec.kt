package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class BytesSpec : BaseSpec({
    should("report mismatches relative to the selected ranges") {
        val first = bytesOf(9, 1, 2, 3, 8)
        val second = bytesOf(7, 7, 1, 2, 4, 6)
        first.mismatch(aFromIndex = 1, aToIndex = 4, b = second, bFromIndex = 2, bToIndex = 5) shouldBe 2
        first.mismatch(aFromIndex = 1, aToIndex = 3, b = second, bFromIndex = 2, bToIndex = 4) shouldBe -1
        first.mismatch(aFromIndex = 1, aToIndex = 4, b = second, bFromIndex = 2, bToIndex = 4) shouldBe 2
        first.mismatch(aFromIndex = 1, aToIndex = 3, b = second, bFromIndex = 2, bToIndex = 5) shouldBe 2
        first.mismatch(aFromIndex = 0, aToIndex = 0, b = second, bFromIndex = 2, bToIndex = 2) shouldBe -1
        first.mismatch(aFromIndex = 0, aToIndex = 0, b = second, bFromIndex = 2, bToIndex = 3) shouldBe 0
        first.equals(aFromIndex = 1, aToIndex = 3, b = second, bFromIndex = 2, bToIndex = 4) shouldBe true
        first.equals(aFromIndex = 1, aToIndex = 4, b = second, bFromIndex = 2, bToIndex = 5) shouldBe false
        first.equals(aFromIndex = 1, aToIndex = 3, b = second, bFromIndex = 2, bToIndex = 5) shouldBe false
        first.equals(aFromIndex = 0, aToIndex = 0, b = second, bFromIndex = 2, bToIndex = 2) shouldBe true
    }

    should("find overlapping matches and report whether a needle exists") {
        val bytes = bytesOf(1, 1, 1, 2)
        bytes.indexOfNeedle(
            needle = bytesOf(
                1,
                1,
                2,
            ),
            fromIndex = 0,
        ) shouldBe 1
        bytes.containsNeedle(needle = bytesOf(1, 1, 2)) shouldBe true
        bytes.containsNeedle(
            needle = bytesOf(
                1,
                1,
                2,
            ),
            fromIndex = 2,
        ) shouldBe false
        bytes.containsNeedle(
            needle = bytesOf(2),
            fromIndex = 3,
        ) shouldBe true
        bytes.containsNeedle(needle = bytesOf(3)) shouldBe false
        bytes.containsNeedle(needle = bytesOf(), fromIndex = Int.MAX_VALUE) shouldBe true
        bytes.indexOfNeedle(
            needle = bytesOf(1),
            fromIndex = Int.MIN_VALUE,
        ) shouldBe 0
        bytes.indexOfNeedle(
            needle = bytesOf(1),
            fromIndex = Int.MAX_VALUE,
        ) shouldBe -1
        bytesOf(-128, -1, 127).indexOfNeedle(
            needle = bytesOf(
                -1,
                127,
            ),
            fromIndex = 0,
        ) shouldBe 1
    }

    context("Bytes.startsWith") {
        should("return true when both the array and the prefix are empty") {
            bytesOf().startsWith(prefix = bytesOf()) shouldBe true
        }

        should("return true when the prefix is empty and the array is non-empty") {
            bytesOf(1, 2, 3).startsWith(prefix = bytesOf()) shouldBe true
        }

        should("return false when the array is empty and the prefix is non-empty") {
            bytesOf().startsWith(prefix = bytesOf(1)) shouldBe false
        }

        should("return false when the prefix is longer than the array") {
            bytesOf(1, 2).startsWith(prefix = bytesOf(1, 2, 3)) shouldBe false
        }

        should("return true when the prefix equals the array exactly") {
            bytesOf(1, 2, 3).startsWith(prefix = bytesOf(1, 2, 3)) shouldBe true
        }

        should("return true when the array starts with the prefix at the default offset") {
            bytesOf(1, 2, 3, 4).startsWith(prefix = bytesOf(1, 2)) shouldBe true
        }

        should("return false when the leading bytes of the array differ from the prefix") {
            bytesOf(1, 2, 3, 4).startsWith(prefix = bytesOf(2, 3)) shouldBe false
        }

        should("return false when only the last byte of the prefix differs") {
            bytesOf(1, 2, 3).startsWith(prefix = bytesOf(1, 2, 4)) shouldBe false
        }

        should("distinguish bytes with different values at the same position") {
            bytesOf(0, 1).startsWith(prefix = bytesOf(1)) shouldBe false
        }

        should("return true for a single-byte prefix matching the first byte") {
            bytesOf(7, 8, 9).startsWith(prefix = bytesOf(7)) shouldBe true
        }

        should("return true when the prefix matches starting at a non-zero offset") {
            bytesOf(1, 2, 3, 4).startsWith(
                prefix = bytesOf(
                    2,
                    3,
                ),
                offset = 1,
            ) shouldBe true
        }

        should("return false when the prefix matches elsewhere but not at the given offset") {
            bytesOf(1, 2, 3, 4).startsWith(
                prefix = bytesOf(
                    2,
                    3,
                ),
                offset = 0,
            ) shouldBe false
        }

        should("return true when the prefix matches the tail of the array") {
            bytesOf(1, 2, 3, 4).startsWith(
                prefix = bytesOf(
                    3,
                    4,
                ),
                offset = 2,
            ) shouldBe true
        }

        should("return false when the prefix would extend past the array end") {
            bytesOf(1, 2, 3, 4).startsWith(
                prefix = bytesOf(
                    3,
                    4,
                    5,
                ),
                offset = 2,
            ) shouldBe false
        }

        should("return false for a negative offset") {
            bytesOf(1, 2, 3).startsWith(
                prefix = bytesOf(1),
                offset = -1,
            ) shouldBe false
        }

        should("return true when the offset equals the array size and the prefix is empty") {
            bytesOf(1, 2, 3).startsWith(prefix = bytesOf(), offset = 3) shouldBe true
        }

        should("return false when the offset equals the array size and the prefix is non-empty") {
            bytesOf(1, 2, 3).startsWith(
                prefix = bytesOf(4),
                offset = 3,
            ) shouldBe false
        }

        should("return false when the offset is past the end of the array") {
            bytesOf(1, 2, 3).startsWith(prefix = bytesOf(), offset = 4) shouldBe false
        }

        should("handle the full byte range including negative byte values") {
            val data = bytesOf(-128, -1, 0, 127)
            data.startsWith(prefix = bytesOf(-128, -1)) shouldBe true
            data.startsWith(
                prefix = bytesOf(
                    0,
                    127,
                ),
                offset = 2,
            ) shouldBe true
            data.startsWith(
                prefix = bytesOf(-128),
                offset = 1,
            ) shouldBe false
        }
    }

    context("Bytes.indexOfNeedle") {
        should("return the fromIndex when the needle is empty and the index is within bounds") {
            bytesOf(1, 2, 3).indexOfNeedle(needle = bytesOf(), fromIndex = 0) shouldBe 0
            bytesOf(1, 2, 3).indexOfNeedle(needle = bytesOf(), fromIndex = 2) shouldBe 2
            bytesOf(1, 2, 3).indexOfNeedle(needle = bytesOf(), fromIndex = 3) shouldBe 3
        }

        should("clamp the fromIndex into bounds for an empty needle") {
            bytesOf(1, 2, 3).indexOfNeedle(needle = bytesOf(), fromIndex = -5) shouldBe 0
            bytesOf(1, 2, 3).indexOfNeedle(needle = bytesOf(), fromIndex = 99) shouldBe 3
        }

        should("return -1 when the needle is longer than the array") {
            bytesOf(1, 2).indexOfNeedle(
                needle = bytesOf(
                    1,
                    2,
                    3,
                ),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("return -1 when the array is empty and the needle is non-empty") {
            bytesOf().indexOfNeedle(
                needle = bytesOf(1),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("find the needle at the start of the array") {
            bytesOf(1, 2, 3, 4).indexOfNeedle(
                needle = bytesOf(
                    1,
                    2,
                ),
                fromIndex = 0,
            ) shouldBe 0
        }

        should("find the needle in the middle of the array") {
            bytesOf(1, 2, 3, 4, 5).indexOfNeedle(
                needle = bytesOf(
                    3,
                    4,
                ),
                fromIndex = 0,
            ) shouldBe 2
        }

        should("find the needle at the tail of the array") {
            bytesOf(1, 2, 3, 4).indexOfNeedle(
                needle = bytesOf(
                    3,
                    4,
                ),
                fromIndex = 0,
            ) shouldBe 2
        }

        should("return the first occurrence when the needle appears more than once") {
            bytesOf(1, 2, 1, 2, 1, 2).indexOfNeedle(
                needle = bytesOf(
                    1,
                    2,
                ),
                fromIndex = 0,
            ) shouldBe 0
        }

        should("skip occurrences before the fromIndex") {
            bytesOf(1, 2, 1, 2, 1, 2).indexOfNeedle(
                needle = bytesOf(
                    1,
                    2,
                ),
                fromIndex = 1,
            ) shouldBe 2
        }

        should("return -1 when the needle is not present") {
            bytesOf(1, 2, 3).indexOfNeedle(
                needle = bytesOf(9),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("treat a negative fromIndex as zero when searching for a non-empty needle") {
            bytesOf(1, 2, 3).indexOfNeedle(
                needle = bytesOf(1),
                fromIndex = -10,
            ) shouldBe 0
        }

        should("return -1 when the fromIndex is past the array end and the needle is non-empty") {
            bytesOf(1, 2, 3).indexOfNeedle(
                needle = bytesOf(1),
                fromIndex = 5,
            ) shouldBe -1
        }
    }
})

private fun bytesOf(vararg values: Byte): Bytes = Bytes(MutBytes(values))
