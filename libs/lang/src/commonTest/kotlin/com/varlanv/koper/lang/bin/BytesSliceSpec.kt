package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class BytesSliceSpec : BaseSpec({
    should("visit the entire slice when its offset exceeds its length") {
        val slice = BytesSlice(bytes = MutBytes(byteArrayOf(0, 0, 0, 1, 2, 0)).readonly, offset = 3, len = 2)
        val values = mutableListOf<Byte>()
        slice.forEach { values.add(it) }
        values shouldBe listOf<Byte>(1, 2)
        val indexed = mutableListOf<Pair<Int, Byte>>()
        slice.forEachIndexed { index, value -> indexed.add(index to value) }
        indexed shouldBe listOf(3 to 1.toByte(), 4 to 2.toByte())
    }

    should("skip iteration for empty slices at either end of an array") {
        for (offset in listOf(0, 3)) {
            val slice = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 3)).readonly, offset = offset, len = 0)
            slice.forEach { error("Unexpected byte") }
            slice.forEachIndexed { _, _ -> error("Unexpected byte") }
            slice.len shouldBe 0
        }
    }

    should("compare contents regardless of backing array and offset") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf(9, -128, -1, 127, 8)).readonly, offset = 1, len = 3)
        val second = BytesSlice(bytes = MutBytes(byteArrayOf(-128, -1, 127)).readonly, offset = 0, len = 3)
        first.equals(first) shouldBe true
        first shouldBe second
        second shouldBe first
        first.hashCode() shouldBe second.hashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        setOf(first, second).size shouldBe 1
    }

    should("reject unequal contents lengths and unrelated values") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 3)).readonly, offset = 0, len = 3)
        first.equals(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 4)).readonly, offset = 0, len = 3)) shouldBe false
        first.equals(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).readonly, offset = 0, len = 2)) shouldBe false
        first.equals(null) shouldBe false
        first.equals(byteArrayOf(1, 2, 3)) shouldBe false
    }

    should("give empty slices equal contents and hash codes") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf()).readonly, offset = 0, len = 0)
        val second = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).readonly, offset = 2, len = 0)
        first shouldBe second
        first.hashCode() shouldBe 1
        second.hashCode() shouldBe 1
    }

    should("preserve zero hash codes") {
        val slice = BytesSlice(bytes = MutBytes(byteArrayOf(-31)).readonly, offset = 0, len = 1)
        repeat(2) { slice.hashCode() shouldBe 0 }
    }
})
