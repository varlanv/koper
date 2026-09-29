package com.varlanv.koper.lang.ext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ThreadScope<T> actual constructor(initial: () -> T) {
    private var backing = initial()

    actual fun get(): T = backing

    actual fun set(value: T) {
        backing = value
    }
}
