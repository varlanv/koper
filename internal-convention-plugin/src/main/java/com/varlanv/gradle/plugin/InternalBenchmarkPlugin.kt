package com.varlanv.gradle.plugin

import kotlinx.benchmark.gradle.BenchmarksExtension
import kotlinx.benchmark.gradle.JsBenchmarkTarget
import kotlinx.benchmark.gradle.JsBenchmarksExecutor
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

class InternalBenchmarkPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        withSharedState(target) {
            configurePrelude()
            project.pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
            project.pluginManager.apply("org.jetbrains.kotlinx.benchmark")
            applyCommonTargets()

            // JMH uses sun.misc.Unsafe on JDK 24+, and kotlinx-benchmark 0.5.0 renders
            // the fork's warning bytes as decimal integers in the console.
            if (javaVersion.toInt() >= 24) {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    if (task.name.startsWith("jvm") && task.name.endsWith("Benchmark")) {
                        task.jvmArgs("--sun-misc-unsafe-memory-access=allow")
                    }
                }
            }

            project.extensions.configure(AllOpenExtension::class.java) { allOpen ->
                allOpen.annotation("org.openjdk.jmh.annotations.State")
            }

            configureMultiplatform { kmp ->
                kmp.sourceSets.getByName("commonMain").dependencies { dependencies ->
                    dependencies.implementation(internalProperties.getLib("kotlin-x-benchmark"))
                }
            }

            project.extensions.configure(BenchmarksExtension::class.java) { benchmark ->
                benchmark.targets.register("jvm") { target ->
                    (target as JvmBenchmarkTarget).jmhVersion = internalProperties.getVersion("jmhVersion")
                }
                benchmark.targets.register("js") { target ->
                    (target as JsBenchmarkTarget).jsBenchmarksExecutor = JsBenchmarksExecutor.BuiltIn
                }
                benchmark.configurations.configureEach { configuration ->
                    configuration.mode = "avgt"
                    configuration.outputTimeUnit = "ns"
                    configuration.reportFormat = "json"
                }
                benchmark.configurations.named("main") { configuration ->
                    configuration.warmups = 5
                    configuration.iterations = 5
                    configuration.iterationTime = 1
                    configuration.iterationTimeUnit = "s"
                    configuration.advanced("jvmForks", 2)
                }
                benchmark.configurations.register("smoke") { configuration ->
                    configuration.warmups = 1
                    configuration.iterations = 1
                    configuration.iterationTime = 100
                    configuration.iterationTimeUnit = "ms"
                    configuration.advanced("jvmForks", 1)
                }
                benchmark.configurations.register("quick") { configuration ->
                    configuration.warmups = 3
                    configuration.iterations = 5
                    configuration.iterationTime = 500
                    configuration.iterationTimeUnit = "ms"
                    configuration.advanced("jvmForks", 1)
                }
            }
        }
    }
}
