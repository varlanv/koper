@file:Suppress("NOTHING_TO_INLINE")

package com.varlanv.koper.lang.collection

import kotlin.jvm.JvmInline

@JvmInline
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
value class MutInts @PublishedApi internal constructor(@PublishedApi internal val impl: IntArray) {
    constructor(size: Int) : this(IntArray(size))

    companion object {
        val empty: MutInts = IntArray(0).asMut()

        inline operator fun invoke(size: Int, init: (idx: Int) -> Int): MutInts {
            val res = IntArray(size)
            for (idx in 0 until size) {
                res[idx] = init(idx)
            }
            return MutInts(res)
        }

        inline operator fun invoke(array: IntArray): MutInts = MutInts(array)
    }
}

fun mutIntsOf(vararg elements: Int): MutInts = MutInts(intArrayOf(*elements))

inline fun IntArray.asMut(): MutInts = MutInts(this)

inline fun MutInts.isEmpty(): Boolean = impl.size == 0

inline operator fun MutInts.get(idx: Int): Int = impl[idx]

inline operator fun MutInts.set(idx: Int, value: Int) {
    impl[idx] = value
}

inline fun MutInts.forEach(block: (Int) -> Unit) {
    val sz = impl.size
    for (idx in 0 until sz) {
        block(impl[idx])
    }
}

inline fun MutInts.forEachIndexed(block: (idx: Int, Int) -> Unit) {
    val sz = impl.size
    for (idx in 0 until sz) {
        block(idx, impl[idx])
    }
}
