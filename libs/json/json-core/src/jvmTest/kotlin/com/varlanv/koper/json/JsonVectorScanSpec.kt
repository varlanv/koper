package com.varlanv.koper.json

import com.varlanv.koper.lang.VectorApi
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe

class JsonVectorScanSpec : BaseSpec({
    should("activate the vector JSON scanner") {
        VectorApi.enabled shouldBe true
        (jsonSpecialScan(true) is VectorApi) shouldBe true
    }
})
