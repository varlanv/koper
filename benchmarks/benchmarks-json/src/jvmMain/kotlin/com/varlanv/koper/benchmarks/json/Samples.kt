package com.varlanv.koper.benchmarks.json

import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

@Ser
@De
data class JsonStrSample(
    val id: Long,
    val symbol: Utf8Str,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)

@Ser
@De
data class JsonStringSample(
    val id: Long,
    val symbol: String,
    val text: String,
    val sequence: Int,
    val active: Boolean,
)

@Ser
@De
data class JsonMixedSample(
    val id: Long,
    val symbol: String,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)
