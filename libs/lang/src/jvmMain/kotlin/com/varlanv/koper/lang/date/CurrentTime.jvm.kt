package com.varlanv.koper.lang.date

actual val systemTime: CurrentTime = { Inst.fromMillis(System.currentTimeMillis()) }
