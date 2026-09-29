package com.varlanv.koper.lang.ext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ThreadScope<T> actual constructor(initial: () -> T) {
    private val tl = ThreadLocal.withInitial(initial)

    actual fun get(): T = tl.get()

    actual fun set(value: T) {
        tl.set(value)
    }
}
