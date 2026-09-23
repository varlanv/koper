package com.varlanv.koper.lang

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class CharsetsJsSpec : BaseSpec({
    should("keep the untrimmed array behind a single-byte encoded slice") {
        for (charset in listOf(Charset.Ascii, Charset.Latin1)) {
            val slice = charset.allocateByteSlice("A🙂B")
            slice.offset shouldBe 0
            slice.len shouldBe 3
            slice.unsafeBorrowArray().size shouldBe 4
            slice.allocateArray().toList() shouldBe listOf(65, 63, 66).map(Int::toByte)
            "A🙂B".allocateStr(charset).allocateString(charset) shouldBe "A?B"
        }
    }
})
