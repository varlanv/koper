package com.varlanv.koper.benchmarks.json

import com.dslplatform.json.CompiledJson
import com.dslplatform.json.JsonAttribute
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.serde.De
import com.varlanv.koper.serde.Ser

@Ser
@De
data class JsonUtf8Sample(
    val id: Long,
    val symbol: Utf8Str,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)

@Ser
@De
data class JsonNativeSample(
    val id: Long,
    val symbol: String,
    val text: Utf8Str,
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
    val symbol: Utf8Str,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)

@CompiledJson
@JvmExposeBoxed
@OptIn(ExperimentalStdlibApi::class)
data class DslUtf8DirectSample(
    val id: Long,
    @get:JsonAttribute(converter = Utf8StrConverter::class)
    val symbol: Utf8Str,
    @get:JsonAttribute(converter = Utf8StrConverter::class)
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)
