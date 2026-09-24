package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ByteUtilsSpec : BaseSpec({
    should("report mismatches relative to the selected ranges") {
        val first = byteArrayOf(9, 1, 2, 3, 8)
        val second = byteArrayOf(7, 7, 1, 2, 4, 6)
        first.mismatch(1, 4, second, 2, 5) shouldBe 2
        first.mismatch(1, 3, second, 2, 4) shouldBe -1
        first.mismatch(1, 4, second, 2, 4) shouldBe 2
        first.mismatch(1, 3, second, 2, 5) shouldBe 2
        first.mismatch(0, 0, second, 2, 2) shouldBe -1
        first.mismatch(0, 0, second, 2, 3) shouldBe 0
        first.equals(1, 3, second, 2, 4) shouldBe true
        first.equals(1, 4, second, 2, 5) shouldBe false
        first.equals(1, 3, second, 2, 5) shouldBe false
        first.equals(0, 0, second, 2, 2) shouldBe true
    }

    should("find overlapping matches and report whether a needle exists") {
        val bytes = byteArrayOf(1, 1, 1, 2)
        bytes.indexOfNeedle(byteArrayOf(1, 1, 2), 0) shouldBe 1
        bytes.containsNeedle(byteArrayOf(1, 1, 2)) shouldBe true
        bytes.containsNeedle(byteArrayOf(1, 1, 2), 2) shouldBe false
        bytes.containsNeedle(byteArrayOf(2), 3) shouldBe true
        bytes.containsNeedle(byteArrayOf(3)) shouldBe false
        bytes.containsNeedle(byteArrayOf(), Int.MAX_VALUE) shouldBe true
        bytes.indexOfNeedle(byteArrayOf(1), Int.MIN_VALUE) shouldBe 0
        bytes.indexOfNeedle(byteArrayOf(1), Int.MAX_VALUE) shouldBe -1
        byteArrayOf(-128, -1, 127).indexOfNeedle(byteArrayOf(-1, 127), 0) shouldBe 1
    }

    context("ByteArray.startsWith") {
        should("return true when both the array and the prefix are empty") {
            byteArrayOf().startsWith(prefix = byteArrayOf()) shouldBe true
        }

        should("return true when the prefix is empty and the array is non-empty") {
            byteArrayOf(1, 2, 3).startsWith(prefix = byteArrayOf()) shouldBe true
        }

        should("return false when the array is empty and the prefix is non-empty") {
            byteArrayOf().startsWith(prefix = byteArrayOf(1)) shouldBe false
        }

        should("return false when the prefix is longer than the array") {
            byteArrayOf(1, 2).startsWith(prefix = byteArrayOf(1, 2, 3)) shouldBe false
        }

        should("return true when the prefix equals the array exactly") {
            byteArrayOf(1, 2, 3).startsWith(prefix = byteArrayOf(1, 2, 3)) shouldBe true
        }

        should("return true when the array starts with the prefix at the default offset") {
            byteArrayOf(1, 2, 3, 4).startsWith(prefix = byteArrayOf(1, 2)) shouldBe true
        }

        should("return false when the leading bytes of the array differ from the prefix") {
            byteArrayOf(1, 2, 3, 4).startsWith(prefix = byteArrayOf(2, 3)) shouldBe false
        }

        should("return false when only the last byte of the prefix differs") {
            byteArrayOf(1, 2, 3).startsWith(prefix = byteArrayOf(1, 2, 4)) shouldBe false
        }

        should("distinguish bytes with different values at the same position") {
            byteArrayOf(0, 1).startsWith(prefix = byteArrayOf(1)) shouldBe false
        }

        should("return true for a single-byte prefix matching the first byte") {
            byteArrayOf(7, 8, 9).startsWith(prefix = byteArrayOf(7)) shouldBe true
        }

        should("return true when the prefix matches starting at a non-zero offset") {
            byteArrayOf(1, 2, 3, 4).startsWith(
                prefix = byteArrayOf(
                    2,
                    3,
                ),
                offset = 1,
            ) shouldBe true
        }

        should("return false when the prefix matches elsewhere but not at the given offset") {
            byteArrayOf(1, 2, 3, 4).startsWith(
                prefix = byteArrayOf(
                    2,
                    3,
                ),
                offset = 0,
            ) shouldBe false
        }

        should("return true when the prefix matches the tail of the array") {
            byteArrayOf(1, 2, 3, 4).startsWith(
                prefix = byteArrayOf(
                    3,
                    4,
                ),
                offset = 2,
            ) shouldBe true
        }

        should("return false when the prefix would extend past the array end") {
            byteArrayOf(1, 2, 3, 4).startsWith(
                prefix = byteArrayOf(
                    3,
                    4,
                    5,
                ),
                offset = 2,
            ) shouldBe false
        }

        should("return false for a negative offset") {
            byteArrayOf(1, 2, 3).startsWith(
                prefix = byteArrayOf(1),
                offset = -1,
            ) shouldBe false
        }

        should("return true when the offset equals the array size and the prefix is empty") {
            byteArrayOf(1, 2, 3).startsWith(prefix = byteArrayOf(), offset = 3) shouldBe true
        }

        should("return false when the offset equals the array size and the prefix is non-empty") {
            byteArrayOf(1, 2, 3).startsWith(
                prefix = byteArrayOf(4),
                offset = 3,
            ) shouldBe false
        }

        should("return false when the offset is past the end of the array") {
            byteArrayOf(1, 2, 3).startsWith(prefix = byteArrayOf(), offset = 4) shouldBe false
        }

        should("handle the full byte range including negative byte values") {
            val data = byteArrayOf(-128, -1, 0, 127)
            data.startsWith(prefix = byteArrayOf(-128, -1)) shouldBe true
            data.startsWith(
                prefix = byteArrayOf(
                    0,
                    127,
                ),
                offset = 2,
            ) shouldBe true
            data.startsWith(
                prefix = byteArrayOf(-128),
                offset = 1,
            ) shouldBe false
        }
    }

    context("ByteArray.indexOf") {
        should("return the fromIndex when the needle is empty and the index is within bounds") {
            byteArrayOf(1, 2, 3).indexOfNeedle(needle = byteArrayOf(), fromIndex = 0) shouldBe 0
            byteArrayOf(1, 2, 3).indexOfNeedle(needle = byteArrayOf(), fromIndex = 2) shouldBe 2
            byteArrayOf(1, 2, 3).indexOfNeedle(needle = byteArrayOf(), fromIndex = 3) shouldBe 3
        }

        should("clamp the fromIndex into bounds for an empty needle") {
            byteArrayOf(1, 2, 3).indexOfNeedle(needle = byteArrayOf(), fromIndex = -5) shouldBe 0
            byteArrayOf(1, 2, 3).indexOfNeedle(needle = byteArrayOf(), fromIndex = 99) shouldBe 3
        }

        should("return -1 when the needle is longer than the array") {
            byteArrayOf(1, 2).indexOfNeedle(
                needle = byteArrayOf(
                    1,
                    2,
                    3,
                ),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("return -1 when the array is empty and the needle is non-empty") {
            byteArrayOf().indexOfNeedle(
                needle = byteArrayOf(1),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("find the needle at the start of the array") {
            byteArrayOf(1, 2, 3, 4).indexOfNeedle(
                needle = byteArrayOf(
                    1,
                    2,
                ),
                fromIndex = 0,
            ) shouldBe 0
        }

        should("find the needle in the middle of the array") {
            byteArrayOf(1, 2, 3, 4, 5).indexOfNeedle(
                needle = byteArrayOf(
                    3,
                    4,
                ),
                fromIndex = 0,
            ) shouldBe 2
        }

        should("find the needle at the tail of the array") {
            byteArrayOf(1, 2, 3, 4).indexOfNeedle(
                needle = byteArrayOf(
                    3,
                    4,
                ),
                fromIndex = 0,
            ) shouldBe 2
        }

        should("return the first occurrence when the needle appears more than once") {
            byteArrayOf(1, 2, 1, 2, 1, 2).indexOfNeedle(
                needle = byteArrayOf(
                    1,
                    2,
                ),
                fromIndex = 0,
            ) shouldBe 0
        }

        should("skip occurrences before the fromIndex") {
            byteArrayOf(1, 2, 1, 2, 1, 2).indexOfNeedle(
                needle = byteArrayOf(
                    1,
                    2,
                ),
                fromIndex = 1,
            ) shouldBe 2
        }

        should("return -1 when the needle is not present") {
            byteArrayOf(1, 2, 3).indexOfNeedle(
                needle = byteArrayOf(9),
                fromIndex = 0,
            ) shouldBe -1
        }

        should("treat a negative fromIndex as zero when searching for a non-empty needle") {
            byteArrayOf(1, 2, 3).indexOfNeedle(
                needle = byteArrayOf(1),
                fromIndex = -10,
            ) shouldBe 0
        }

        should("return -1 when the fromIndex is past the array end and the needle is non-empty") {
            byteArrayOf(1, 2, 3).indexOfNeedle(
                needle = byteArrayOf(1),
                fromIndex = 5,
            ) shouldBe -1
        }
    }
})
