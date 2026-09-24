package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline
import kotlin.math.ceil

internal const val DEFAULT_LOAD_FACTOR = 0.75

/**
 * JDK 19 implementation.
 */
fun calculateHashMapCapacity(numMappings: Int): Int = ceil(numMappings / DEFAULT_LOAD_FACTOR).toInt()
