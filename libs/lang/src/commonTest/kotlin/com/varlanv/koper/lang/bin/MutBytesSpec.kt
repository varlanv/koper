package com.varlanv.koper.lang.bin

import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.equals.shouldEqual

class MutBytesSpec : BaseSpec({

    should("create empty bytes with empty size") {
        val subject = MutBytes(0.bytes())

        subject.size shouldEqual 0
    }

    should("create non-empty bytes with correct size") {
        val subject = MutBytes(10.bytes())

        subject.size shouldEqual 10
    }

    should("set and get value") {
        val subject = MutBytes(10.bytes())

        subject[0] = 20

        subject[0] shouldEqual 20
        for (idx in 1 until 10) {
            subject[idx] shouldEqual 0
        }
    }

    should("set and get int value") {
        val subject = MutBytes(10.bytes())

        subject.setPackedInt(idx = 0, value = 1_000_000)

        subject.getPackedInt(0) shouldEqual 1_000_000
        for (idx in 4 until 10) {
            subject[idx] shouldEqual 0
        }
    }
})
