@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline

@JvmInline
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
value class Longs(@PublishedApi internal val longs: MutLongs) {
    companion object {
        val empty: Longs = Longs(MutLongs.empty)

        inline operator fun invoke(size: Int, init: (idx: Int) -> Long): Longs {
            val res = LongArray(size)
            for (idx in 0 until size) {
                res[idx] = init(idx)
            }
            return Longs(MutLongs(res))
        }
    }
}

// todo check performance of this - I think it doubles the work
fun longsOf(vararg elements: Long): Longs = longArrayOf(*elements).asReadonly()

inline fun Longs.size(): Int = longs.impl.size

inline operator fun Longs.get(idx: Int): Long = longs.impl[idx]

inline fun Longs.forEachIndex(block: (idx: Int) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(idx)
    }
}

inline fun Longs.forEach(block: (Long) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(longs.impl[idx])
    }
}

inline fun Longs.forEachIndexed(block: (index: Int, Long) -> Unit) {
    val size = size()
    for (idx in 0 until size) {
        block(idx, longs.impl[idx])
    }
}

inline fun Longs.isEmpty(): Boolean = longs.impl.size == 0

inline fun LongArray.asReadonly(): Longs = Longs(MutLongs(this))
