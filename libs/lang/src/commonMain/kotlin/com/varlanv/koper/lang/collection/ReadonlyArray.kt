package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline

@JvmInline
value class ReadonlyArray<T>(@PublishedApi internal val array: Array<out T>) {
    val size get(): Int = array.size

    inline fun forEach(block: (T) -> Unit) {
        array.forEach(block)
    }

    fun asList(): List<T> = array.asList()

    inline fun <R> associateBy(block: (T) -> R): Map<R, T> = array.associateBy(block)
}

inline fun <reified T> emptyReadonlyArray(): ReadonlyArray<T> = ReadonlyArray(emptyArray())

fun <T> readonlyArrayOf(vararg elements: T): ReadonlyArray<T> = ReadonlyArray(elements)
