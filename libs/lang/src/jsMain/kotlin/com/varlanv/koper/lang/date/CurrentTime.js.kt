package com.varlanv.koper.lang.date

import kotlin.js.Date

actual val systemTime: CurrentTime = { Inst.fromMillis(Date.now().toLong()) }
