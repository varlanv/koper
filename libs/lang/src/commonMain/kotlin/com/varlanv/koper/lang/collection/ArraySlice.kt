package com.varlanv.koper.lang.collection

class ArraySlice<T>(
    @PublishedApi internal val array: ReadonlyArray<T>,
    internal val offset: Int,
    val size: Int
)
