package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class BytesSliceSpec : BaseSpec({
    should("visit the entire slice when its offset exceeds its length") {
        val slice = ByteSlice(ReadonlyBytes(byteArrayOf(0, 0, 0, 1, 2, 0)), 3, 2)
        val values = mutableListOf<Byte>()
        slice.forEach { values.add(it) }
        values shouldBe listOf<Byte>(1, 2)
        val indexed = mutableListOf<Pair<Int, Byte>>()
        slice.forEachIndexed { index, value -> indexed.add(index to value) }
        indexed shouldBe listOf(3 to 1.toByte(), 4 to 2.toByte())
    }

    should("skip iteration for empty slices at either end of an array") {
        for (offset in listOf(0, 3)) {
            val slice = ByteSlice(ReadonlyBytes(byteArrayOf(1, 2, 3)), offset, 0)
            slice.forEach { error("Unexpected byte") }
            slice.forEachIndexed { _, _ -> error("Unexpected byte") }
            slice.allocateArray().size shouldBe 0
        }
    }

    should("allocate an independent copy of only the slice contents") {
        val array = byteArrayOf(9, 1, 2, 8)
        val slice = ByteSlice(ReadonlyBytes(array), 1, 2)
        val copy = slice.allocateArray()
        copy.toList() shouldBe listOf<Byte>(1, 2)
        copy[0] = 7
        array.toList() shouldBe listOf<Byte>(9, 1, 2, 8)
        array[2] = 6
        copy.toList() shouldBe listOf<Byte>(7, 2)
    }

    should("compare contents regardless of backing array and offset") {
        val first = ByteSlice(ReadonlyBytes(byteArrayOf(9, -128, -1, 127, 8)), 1, 3)
        val second = ByteSlice(ReadonlyBytes(byteArrayOf(-128, -1, 127)), 0, 3)
        first.equals(first) shouldBe true
        first shouldBe second
        second shouldBe first
        first.hashCode() shouldBe second.hashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        setOf(first, second).size shouldBe 1
    }

    should("reject unequal contents lengths and unrelated values") {
        val first = ByteSlice(ReadonlyBytes(byteArrayOf(1, 2, 3)), 0, 3)
        first.equals(ByteSlice(ReadonlyBytes(byteArrayOf(1, 2, 4)), 0, 3)) shouldBe false
        first.equals(ByteSlice(ReadonlyBytes(byteArrayOf(1, 2)), 0, 2)) shouldBe false
        first.equals(null) shouldBe false
        first.equals(byteArrayOf(1, 2, 3)) shouldBe false
    }

    should("give empty slices equal contents and hash codes") {
        val first = ByteSlice(ReadonlyBytes(byteArrayOf()), 0, 0)
        val second = ByteSlice(ReadonlyBytes(byteArrayOf(1, 2)), 2, 0)
        first shouldBe second
        first.hashCode() shouldBe 1
        second.hashCode() shouldBe 1
    }

    should("preserve zero hash codes") {
        val slice = ByteSlice(ReadonlyBytes(byteArrayOf(-31)), 0, 1)
        repeat(2) { slice.hashCode() shouldBe 0 }
    }
})
