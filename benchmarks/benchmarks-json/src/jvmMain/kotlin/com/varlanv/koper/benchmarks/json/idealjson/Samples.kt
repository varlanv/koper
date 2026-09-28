package com.varlanv.koper.benchmarks.json

import com.dslplatform.json.CompiledJson
import com.dslplatform.json.JsonAttribute
import com.varlanv.koper.lang.text.Str
import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

@Ser
@De
data class JsonStrSample(
    val id: Long,
    val symbol: Str,
    val text: Str,
    val sequence: Int,
    val active: Boolean,
)

@Ser
@De
data class JsonMixedSample(
    val id: Long,
    val symbol: String,
    val text: Str,
    val sequence: Int,
    val active: Boolean,
)

@CompiledJson
@JvmExposeBoxed
@OptIn(ExperimentalStdlibApi::class)
data class DslMixedSample(
    val id: Long,
    val symbol: String,
    @get:JsonAttribute(converter = StrConverter::class)
    val text: Str,
    val sequence: Int,
    val active: Boolean,
)

@CompiledJson
data class DslStringSample(
    val id: Long,
    val symbol: String,
    val text: String,
    val sequence: Int,
    val active: Boolean,
)

@CompiledJson
@JvmExposeBoxed
@OptIn(ExperimentalStdlibApi::class)
data class DslUtf8Sample(
    val id: Long,
    val symbol: Str,
    val text: Str,
    val sequence: Int,
    val active: Boolean,
)

@CompiledJson
@JvmExposeBoxed
@OptIn(ExperimentalStdlibApi::class)
data class DslUtf8DirectSample(
    val id: Long,
    @get:JsonAttribute(converter = StrConverter::class)
    val symbol: Str,
    @get:JsonAttribute(converter = StrConverter::class)
    val text: Str,
    val sequence: Int,
    val active: Boolean,
)
