package com.varlanv.koper.lang

import java.time.Instant

fun Inst.toJavaInstant(): Instant {
    return Instant.ofEpochMilli(millis)
}


fun Inst.Companion.from(instant: Instant): Inst {
    return Inst(instant.toEpochMilli())
}

fun Inst.Companion.parse(iso8601: String): Inst {
    return Inst.from(Instant.parse(iso8601))
}
