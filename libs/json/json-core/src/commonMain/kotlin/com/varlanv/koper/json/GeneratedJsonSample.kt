package com.varlanv.koper.json

import com.varlanv.koper.lang.text.Str
import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

@Ser
@De
internal data class GeneratedJsonSample(
    val intValue: Int,
    val longValue: Long,
    val booleanValue: Boolean,
    val stringValue: String,
    val strValue: Str,
)

@Ser
@De
@ConsistentCopyVisibility
internal data class GeneratedFactorySample private constructor(val value: Int) {
    companion object {
        operator fun invoke(value: Int): GeneratedFactorySample = GeneratedFactorySample(value)
    }
}

@Ser
@De
internal data class GeneratedJsonNames(
    val axx: Int,
    val bYx: Int,
    val aaaaé: Int,
)

@Ser
@De
internal data class GeneratedLongFieldNames(
    val seven77: Int,
    val eight888: Int,
    val sequenceAlpha: Int,
    val sequenceBeta: Int,
)
