package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class MutBytesSliceSpec : BaseSpec({
    should("set only bytes inside the slice") {
        val backing = MutBytes(byteArrayOf(9, 9, 9, 9))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 1, len = 2))

        slice[0] = 1
        slice[1] = 2
        slice[0] shouldBe 1
        slice[1] shouldBe 2
        backing.asList() shouldBe listOf<Byte>(9, 1, 2, 9)

        for (idx in listOf(-1, 2, 3, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice[idx] }
            shouldThrow<IllegalArgumentException> { slice[idx] = 7 }
        }
        backing.asList() shouldBe listOf<Byte>(9, 1, 2, 9)

        val empty = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 4, len = 0))
        shouldThrow<IllegalArgumentException> { empty[0] }
        shouldThrow<IllegalArgumentException> { empty[0] = 7 }
        backing.asList() shouldBe listOf<Byte>(9, 1, 2, 9)
    }

    should("set packed ints only when every byte fits inside the slice") {
        val backing = MutBytes(ByteArray(16))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 8))

        slice.setPackedInt(idx = 0, value = 0x12345678)
        slice.setPackedInt(idx = 4, value = 0x10203040)
        backing.getPackedInt(2) shouldBe 0x12345678
        backing.getPackedInt(6) shouldBe 0x10203040

        val before = backing.asList().toList()
        for (idx in listOf(-1, 5, 8, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.setPackedInt(idx = idx, value = -1) }
        }
        backing.asList() shouldBe before

        val tooShort = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 3))
        shouldThrow<IllegalArgumentException> { tooShort.setPackedInt(idx = 0, value = -1) }
        backing.asList() shouldBe before
    }

    should("set packed shorts only when every byte fits inside the slice") {
        val backing = MutBytes(ByteArray(16))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 8))

        slice.setPackedShort(idx = 0, value = 0x1234.toShort())
        slice.setPackedShort(idx = 6, value = 0x5678.toShort())
        backing.getPackedShort(2) shouldBe 0x1234.toShort()
        backing.getPackedShort(8) shouldBe 0x5678.toShort()

        val before = backing.asList().toList()
        for (idx in listOf(-1, 7, 8, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.setPackedShort(idx = idx, value = -1) }
        }
        backing.asList() shouldBe before

        val tooShort = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 1))
        shouldThrow<IllegalArgumentException> { tooShort.setPackedShort(idx = 0, value = -1) }
        backing.asList() shouldBe before
    }

    should("set packed longs only when every byte fits inside the slice") {
        val backing = MutBytes(ByteArray(16))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 8))

        slice.setPackedLong(idx = 0, value = 0x0102030405060708L)
        backing.getPackedLong(2) shouldBe 0x0102030405060708L

        val before = backing.asList().toList()
        for (idx in listOf(-1, 1, 8, Int.MAX_VALUE)) {
            shouldThrow<IllegalArgumentException> { slice.setPackedLong(idx = idx, value = -1) }
        }
        backing.asList() shouldBe before

        val tooShort = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 7))
        shouldThrow<IllegalArgumentException> { tooShort.setPackedLong(idx = 0, value = -1) }
        backing.asList() shouldBe before
    }

    should("copy a source range to the start of the slice") {
        val backing = MutBytes(byteArrayOf(9, 9, 9, 9, 9, 9))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 3))

        slice.copyFrom(source = MutBytes(byteArrayOf(1, 2, 3)).asReadonly())
        backing.asList() shouldBe listOf<Byte>(9, 9, 1, 2, 3, 9)

        slice.copyFrom(source = MutBytes(byteArrayOf(7, 4, 5, 8)).asReadonly(), sourceOffset = 1, sourceLength = 2)
        backing.asList() shouldBe listOf<Byte>(9, 9, 4, 5, 3, 9)

        slice.copyFrom(source = MutBytes(byteArrayOf(7, 6, 5)).asReadonly(), sourceOffset = 1)
        backing.asList() shouldBe listOf<Byte>(9, 9, 6, 5, 3, 9)

        slice.copyFrom(source = Bytes.empty, sourceLength = 0)
        backing.asList() shouldBe listOf<Byte>(9, 9, 6, 5, 3, 9)
    }

    should("copy overlapping ranges from the backing bytes") {
        val backing = MutBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 2, len = 3))

        slice.copyFrom(source = backing.asReadonly(), sourceOffset = 0, sourceLength = 3)
        backing.asList() shouldBe listOf<Byte>(0, 1, 0, 1, 2, 5)
    }

    should("reject invalid copy ranges without changing the destination") {
        val backing = MutBytes(byteArrayOf(9, 9, 9, 9, 9))
        val slice = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 1, len = 2))
        val source = MutBytes(byteArrayOf(1, 2, 3)).asReadonly()

        for ((offset, length) in listOf(-1 to 1, 0 to -1, 2 to 2, 0 to 3, Int.MAX_VALUE to 1)) {
            shouldThrow<IllegalArgumentException> {
                slice.copyFrom(source = source, sourceOffset = offset, sourceLength = length)
            }
        }
        backing.asList() shouldBe listOf<Byte>(9, 9, 9, 9, 9)

        val empty = MutBytesSlice(BytesSlice(bytes = backing.asReadonly(), offset = 5, len = 0))
        empty.copyFrom(source = source, sourceOffset = source.size)
        shouldThrow<IllegalArgumentException> { empty.copyFrom(source = source, sourceLength = 1) }
        backing.asList() shouldBe listOf<Byte>(9, 9, 9, 9, 9)
    }
})
