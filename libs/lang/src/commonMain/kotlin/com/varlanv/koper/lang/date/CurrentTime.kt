package com.varlanv.koper.lang.date

fun interface CurrentTime {
    operator fun invoke(): Inst
}

expect val systemTime: CurrentTime
