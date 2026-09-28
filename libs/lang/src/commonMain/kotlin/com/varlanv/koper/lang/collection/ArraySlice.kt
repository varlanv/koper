package com.varlanv.koper.lang.collection

class ArraySlice<T>(
    @PublishedApi internal val array: ReadonlyArray<T>,
    internal val offset: Int,
    val size: Int,
) {
    inline fun forEach(block: (T) -> Unit) {
        array.array.forEach(block)
    }

    inline fun forEachIndexed(block: (idx: Int, T) -> Unit) {
        array.array.forEachIndexed(block)
    }
}
