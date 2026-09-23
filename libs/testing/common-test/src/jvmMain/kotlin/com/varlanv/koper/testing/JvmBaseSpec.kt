package com.varlanv.koper.testing

import io.kotest.core.spec.style.ShouldSpec

abstract class JvmBaseSpec(body: ShouldSpec.() -> Unit) : BaseSpec(body)

actual fun limitedConcurrency(): Int = Runtime.getRuntime().availableProcessors()
