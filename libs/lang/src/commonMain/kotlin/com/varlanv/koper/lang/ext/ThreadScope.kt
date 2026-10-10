package com.varlanv.koper.lang.ext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ThreadScope<T>(initial: () -> T) {
    fun get(): T

    fun set(value: T)
}

class SameThreadScope<T>(initial: () -> T) {
    private var backing = initial()

    fun get(): T = backing

    fun set(value: T) {
        backing = value
    }
}
