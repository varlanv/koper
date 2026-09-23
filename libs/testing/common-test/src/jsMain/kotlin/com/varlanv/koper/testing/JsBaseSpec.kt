package com.varlanv.koper.testing

import io.kotest.core.spec.style.ShouldSpec

abstract class JsBaseSpec(body: ShouldSpec.() -> Unit) : BaseSpec(body)

actual fun limitedConcurrency(): Int = 1
