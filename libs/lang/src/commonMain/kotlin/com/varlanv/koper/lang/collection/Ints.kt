@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline

@JvmInline
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
value class Ints(@PublishedApi internal val ints: MutInts) {
    companion object {
        val empty: Ints = Ints(MutInts.empty)

        inline operator fun invoke(size: Int, init: (idx: Int) -> Int): Ints {
            val res = IntArray(size)
            for (idx in 0 until size) {
                res[idx] = init(idx)
            }
            return Ints(MutInts(res))
        }
    }
}

// todo check performance of this - I think it doubles the work
fun intsOf(vararg elements: Int): Ints = intArrayOf(*elements).asReadonly()

inline fun Ints.size(): Int = ints.impl.size

inline operator fun Ints.get(idx: Int): Int = ints.impl[idx]

inline fun Ints.forEachIndex(block: (idx: Int) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(idx)
    }
}

inline fun Ints.forEach(block: (Int) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(ints.impl[idx])
    }
}

inline fun Ints.forEachIndexed(block: (index: Int, Int) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(idx, ints.impl[idx])
    }
}

inline fun Ints.isEmpty(): Boolean = ints.impl.size == 0

inline fun IntArray.asReadonly(): Ints = Ints(MutInts(this))
