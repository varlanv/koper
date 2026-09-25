package com.varlanv.koper.lang

import kotlin.reflect.KClass

/**
 * SIMD activation utilities. This class should NEVER load any Vector modules directly.
 * In most Vector paths, the point is to have 2 interface implementations for specific logic: one Vector and one Scalar.
 * If Vector is not enabled, the "Vector" implementation should never load. This give opportunity for JVM to devirtualize
 * interface call because only one class is loaded. Additionally, it may protect from errors if Vector api changes and
 * fails to load against current incubator version.
 */
interface VectorApi {
    /**
     * Smoke test that current vectorized API is actually working on current JVM version.
     * Implementations are supposed to do a quick vectorized operation on a small throwaway dataset.
     * If exception is thrown during initialization - fallback Scalar Api should be used.
     * Implementations should not do deep, long validation - they are just supposed to verify that all Vector API signatures
     * match to what code was compiled against, and return expected result.
     *
     * Additionally, if `false` is returned, it is considered as smoke test fail, but will not fail the build.
     * `false` might indicate that all signatures were loaded, but return unexpected result, so Vectorized version
     * will be disabled.
     */
    fun smokeTest(): Any? = anyMarker

    companion object {
        /**
         * Marker that is checked against `smokeTest()` return.
         * Serves as a small safety net to ensure Vector implementation did not forget to override `smokeTest()`,
         * but does not ensure that `smokeTest()` implementation is sound by itself.
         */
        private val anyMarker = Any()
        val enabled = java.lang.Boolean.getBoolean("koper.lang.utf8.vector.enabled") &&
            ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent
        private val isDebug = java.lang.Boolean.getBoolean("koper.lang.utf8.vector.debug")

        fun <T : VectorApi> tryLoad(block: () -> T): T? {
            if (!enabled) {
                return null
            }
            val api: T
            val smokeTestRes: Any?
            try {
                api = block()
                smokeTestRes = api.smokeTest()
            } catch (e: LinkageError) {
                if (isDebug) {
                    e.printStackTrace(System.err)
                }
                return null
            } catch (e: Exception) {
                if (isDebug) {
                    e.printStackTrace(System.err)
                }
                return null
            }
            if (smokeTestRes === anyMarker) {
                error("Initialization error - [ ${api::class} ] did not override `smokeTest()`")
            }
            if (smokeTestRes == false) {
                if (isDebug) {
                    System.err.println(
                        "[ ${api::class} ] returned `false` from smoke test - Vector API will be disabled",
                    )
                }
                return null
            }
            return api
        }

        fun ensureEnabled(type: KClass<*>) {
            if (!enabled) {
                error("Class $type should not be loaded by JVM when Vector API is disabled")
            }
        }
    }
}
