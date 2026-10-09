@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline

@JvmInline
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
value class MutLongs @PublishedApi internal constructor(@PublishedApi internal val impl: LongArray) {
    constructor(size: Int) : this(LongArray(size))

    companion object {
        val empty: MutLongs = LongArray(0).asMut()

        inline operator fun invoke(size: Int, init: (idx: Int) -> Long): MutLongs {
            val res = LongArray(size)
            for (idx in 0 until size) {
                res[idx] = init(idx)
            }
            return MutLongs(res)
        }

        inline operator fun invoke(array: IntArray): MutInts = MutInts(array)
    }
}

fun mutLongsOf(vararg elements: Long): MutLongs = MutLongs(longArrayOf(*elements))

inline fun LongArray.asMut(): MutLongs = MutLongs(this)

inline fun MutLongs.isEmpty(): Boolean = impl.size == 0

inline operator fun MutLongs.get(idx: Int): Long = impl[idx]

inline operator fun MutLongs.set(idx: Int, value: Long) {
    impl[idx] = value
}

inline fun MutLongs.forEach(block: (Long) -> Unit) {
    val sz = impl.size
    for (idx in 0 until sz) {
        block(impl[idx])
    }
}

inline fun MutLongs.forEachIndexed(block: (idx: Int, Long) -> Unit) {
    val sz = impl.size
    for (idx in 0 until sz) {
        block(idx, impl[idx])
    }
}
