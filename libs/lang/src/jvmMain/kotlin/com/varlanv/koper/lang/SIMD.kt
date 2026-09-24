package com.varlanv.koper.lang

import kotlin.reflect.KClass

/**
 * SIMD activation utilities. This class should NEVER load any Vector modules directly.
 * In most Vector paths, the point is to have 2 interface implementations for specific logic: one Vector and one Scalar.
 * If Vector is not enabled, the "Vector" implementation should never load. This give opportunity for JVM to devirtualize
 * interface call because only one class is loaded. Additionally, it may protect from errors if Vector api changes and
 * fails to load against current incubator version.
 */
object SIMD {

    val enabled = java.lang.Boolean.getBoolean("koper.lang.utf8.vector") &&
            ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent


    inline fun <T> tryLoad(block: () -> T): T? {
        return if (enabled) {
            // don't hide exception. Fail loudly in case Vector is enabled but load is failing.
            block()
        } else {
            null
        }
    }

    fun ensureEnabled(type: KClass<*>) {
        if (!enabled) {
            error("Class $type should not be loaded by JVM when SIMD is disabled")
        }
    }
}
