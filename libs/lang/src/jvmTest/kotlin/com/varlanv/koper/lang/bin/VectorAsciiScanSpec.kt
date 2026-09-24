package com.varlanv.koper.lang.bin

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class VectorAsciiScanSpec : BaseSpec({
    should("activate the vector ASCII scanner") {
        VectorApi.enabled shouldBe true
        (AsciiScan.target is VectorApi) shouldBe true
    }

    should("fall back when vector implementation cannot link") {
        VectorApi.tryLoad<VectorApi> { throw NoClassDefFoundError("unavailable") } shouldBe null
    }
})
