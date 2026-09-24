package com.varlanv.koper.json

import com.varlanv.koper.lang.text.Utf8Str

data class JsonUtf8Sample(
    val id: Long,
    val symbol: Utf8Str,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)

data class JsonNativeSample(
    val id: Long,
    val symbol: String,
    val text: Utf8Str,
    val sequence: Int,
    val active: Boolean,
)
