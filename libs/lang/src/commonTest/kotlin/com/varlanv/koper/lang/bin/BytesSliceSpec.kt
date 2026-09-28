package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class BytesSliceSpec : BaseSpec({
    should("accept ranges within the backing bytes") {
        val bytes = MutBytes(byteArrayOf(1, 2, 3)).asReadonly()
        for ((offset, len) in listOf(0 to 0, 0 to 3, 1 to 2, 2 to 1, 3 to 0)) {
            val slice = BytesSlice(bytes = bytes, offset = offset, len = len)
            slice.offset shouldBe offset
            slice.len shouldBe len
        }
    }

    should("reject ranges outside the backing bytes") {
        val bytes = MutBytes(byteArrayOf(1, 2, 3)).asReadonly()
        for ((offset, len) in listOf(-1 to 0, 0 to -1, 4 to 0, 2 to 2, 0 to 4, Int.MAX_VALUE to Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> {
                BytesSlice(bytes = bytes, offset = offset, len = len)
            }
        }
    }

    should("get only bytes inside the slice") {
        val slice = BytesSlice(bytes = MutBytes(byteArrayOf(9, 1, 2, 8)).asReadonly(), offset = 1, len = 2)
        slice[1] shouldBe 1
        slice[2] shouldBe 2
        for (idx in listOf(-1, 0, 3, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice[idx] }
        }
        shouldThrow<IllegalArgumentException> {
            BytesSlice(bytes = slice.bytes, offset = 4, len = 0)[4]
        }
    }

    should("get packed values only when every byte fits inside the slice") {
        val backing = MutBytes(ByteArray(16) { it.toByte() })
        val slice = BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 8)

        slice.getPackedShort(2) shouldBe backing.getPackedShort(2)
        slice.getPackedShort(8) shouldBe backing.getPackedShort(8)
        slice.getPackedInt(2) shouldBe backing.getPackedInt(2)
        slice.getPackedInt(6) shouldBe backing.getPackedInt(6)
        slice.getPackedLong(2) shouldBe backing.getPackedLong(2)

        for (idx in listOf(1, 9, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.getPackedShort(idx) }
        }
        for (idx in listOf(1, 7, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.getPackedInt(idx) }
        }
        for (idx in listOf(1, 3, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.getPackedLong(idx) }
        }

        val tooShort = BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 1)
        shouldThrow<IllegalArgumentException> { tooShort.getPackedShort(2) }
        shouldThrow<IllegalArgumentException> { tooShort.getPackedInt(2) }
        shouldThrow<IllegalArgumentException> { tooShort.getPackedLong(2) }
    }

    should("visit the entire slice when its offset exceeds its length") {
        val slice = BytesSlice(bytes = MutBytes(byteArrayOf(0, 0, 0, 1, 2, 0)).asReadonly(), offset = 3, len = 2)
        val values = mutableListOf<Byte>()
        slice.forEach { values.add(it) }
        values shouldBe listOf<Byte>(1, 2)
        val indexed = mutableListOf<Pair<Int, Byte>>()
        slice.forEachIndexed { index, value -> indexed.add(index to value) }
        indexed shouldBe listOf(3 to 1.toByte(), 4 to 2.toByte())
    }

    should("skip iteration for empty slices at either end of an array") {
        for (offset in listOf(0, 3)) {
            val slice = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 3)).asReadonly(), offset = offset, len = 0)
            slice.forEach { error("Unexpected byte") }
            slice.forEachIndexed { _, _ -> error("Unexpected byte") }
            slice.len shouldBe 0
        }
    }

    should("compare contents regardless of backing array and offset") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf(9, -128, -1, 127, 8)).asReadonly(), offset = 1, len = 3)
        val second = BytesSlice(bytes = MutBytes(byteArrayOf(-128, -1, 127)).asReadonly(), offset = 0, len = 3)
        first.equals(first) shouldBe true
        first shouldBe second
        second shouldBe first
        first.hashCode() shouldBe second.hashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        first.hashCode() shouldBe byteArrayOf(-128, -1, 127).contentHashCode()
        setOf(first, second).size shouldBe 1
    }

    should("reject unequal contents lengths and unrelated values") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 3)).asReadonly(), offset = 0, len = 3)
        first.equals(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2, 4)).asReadonly(), offset = 0, len = 3)) shouldBe
            false
        first.equals(BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).asReadonly(), offset = 0, len = 2)) shouldBe false
        first.equals(null) shouldBe false
        first.equals(byteArrayOf(1, 2, 3)) shouldBe false
    }

    should("give empty slices equal contents and hash codes") {
        val first = BytesSlice(bytes = MutBytes(byteArrayOf()).asReadonly(), offset = 0, len = 0)
        val second = BytesSlice(bytes = MutBytes(byteArrayOf(1, 2)).asReadonly(), offset = 2, len = 0)
        first shouldBe second
        first.hashCode() shouldBe 1
        second.hashCode() shouldBe 1
    }

    should("preserve zero hash codes") {
        val slice = BytesSlice(bytes = MutBytes(byteArrayOf(-31)).asReadonly(), offset = 0, len = 1)
        repeat(2) { slice.hashCode() shouldBe 0 }
    }
})
