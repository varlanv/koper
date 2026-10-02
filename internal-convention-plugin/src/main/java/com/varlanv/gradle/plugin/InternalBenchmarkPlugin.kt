package com.varlanv.gradle.plugin

import kotlinx.benchmark.gradle.BenchmarksExtension
import kotlinx.benchmark.gradle.JsBenchmarkTarget
import kotlinx.benchmark.gradle.JsBenchmarksExecutor
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec

class InternalBenchmarkPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        withSharedState(target) {
            configurePrelude()
            pluginManager.apply(internalProperties.getPlugin("kotlin-multiplatform").get().pluginId)
            project.pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
            project.pluginManager.apply("org.jetbrains.kotlinx.benchmark")
            applyCommonTargets()

            if (javaVersion.toInt() >= 24) {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    if (task.name.startsWith("jvm") && task.name.endsWith("Benchmark")) {
                        task.jvmArgs("--sun-misc-unsafe-memory-access=allow")
                    }
                }
            }

            project.afterEvaluate {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    if (task.name.endsWith("Benchmark") && task.mainClass.orNull == "kotlinx.benchmark.jvm.JvmBenchmarkRunnerKt") {
                        configureBenchmarkReporting(task, project.layout.buildDirectory.get().asFile)
                    }
                }
                project.tasks.withType(NodeJsExec::class.java).configureEach { task ->
                    if (task.name.endsWith("Benchmark") && task.args.singleOrNull()?.let { java.io.File(it).name.startsWith("benchmarks") } == true) {
                        configureBenchmarkReporting(task, project.layout.buildDirectory.get().asFile)
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
