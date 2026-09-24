package com.varlanv.koper.lang.ext

import com.varlanv.koper.lang.bin.bytes
import com.varlanv.koper.lang.bin.gigabytes
import com.varlanv.koper.lang.bin.kilobytes
import com.varlanv.koper.lang.bin.megabytes
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class DataSizeSpec : BaseSpec({
    should("convert binary units to bytes") {
        0.bytes().bytes shouldBe 0
        1.bytes().bytes shouldBe 1
        2.kilobytes().bytes shouldBe 2_048
        3.megabytes().bytes shouldBe 3_145_728
        1.gigabytes().bytes shouldBe 1_073_741_824
        (-2).gigabytes().bytes shouldBe Int.MIN_VALUE
        (-3).megabytes().bytes shouldBe -3_145_728
        (-2).kilobytes().bytes shouldBe -2_048
    }

    should("add byte counts and data sizes") {
        (1.kilobytes() + 512) shouldBe 1_536.bytes()
        (1.kilobytes() + 2.kilobytes()) shouldBe 3.kilobytes()
        (1.kilobytes() + (-1).kilobytes()) shouldBe 0.bytes()
        (Int.MIN_VALUE.bytes() + Int.MAX_VALUE) shouldBe (-1).bytes()
        (Int.MAX_VALUE.bytes() + 0.bytes()) shouldBe Int.MAX_VALUE.bytes()
    }

    should("reject arithmetic that exceeds the byte range") {
        shouldThrow<ArithmeticException> { Int.MAX_VALUE.bytes() + 1 }
        shouldThrow<ArithmeticException> { Int.MIN_VALUE.bytes() + (-1).bytes() }
        shouldThrow<ArithmeticException> { 2_097_152.kilobytes() }
        shouldThrow<ArithmeticException> { (-2_097_153).kilobytes() }
        shouldThrow<ArithmeticException> { 2_048.megabytes() }
        shouldThrow<ArithmeticException> { (-2_049).megabytes() }
        shouldThrow<ArithmeticException> { 2.gigabytes() }
        shouldThrow<ArithmeticException> { (-3).gigabytes() }
    }

    should("format byte counts with binary units and trim fractional zeroes") {
        val cases = mapOf(
            0 to "0B",
            1 to "1B",
            -1 to "-1B",
            1_023 to "1023B",
            1_024 to "1KiB (1024 B)",
            1_025 to "1.001KiB (1025 B)",
            1_536 to "1.5KiB (1536 B)",
            -1_536 to "-1.5KiB (-1536 B)",
            1_048_576 to "1MiB (1048576 B)",
            -1_048_576 to "-1MiB (-1048576 B)",
            1_073_741_824 to "1GiB (1073741824 B)",
            Int.MAX_VALUE to "2GiB (2147483647 B)",
            Int.MIN_VALUE to "-2GiB (-2147483648 B)",
        )
        cases.forEach { (bytes, expected) -> bytes.bytes().toString() shouldBe expected }
    }
})
