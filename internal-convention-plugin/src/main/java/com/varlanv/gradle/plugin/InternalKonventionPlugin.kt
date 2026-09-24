package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class InternalKonventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        withSharedState(target) {
            fun configureKotlin() {
                configureMultiplatform { kmp ->
                    kmp.compilerOptions {
                        allWarningsAsErrors.set(false)
                        extraWarnings.set(true)
                        progressiveMode.set(true)
                    }
                }
            }

            fun configureTests() {
                tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { test ->
                    test.useJUnitPlatform()
                    test.outputs.upToDateWhen { false }
                    test.testLogging { logging ->
                        logging.showStandardStreams = true
                        logging.showStackTraces = true
                    }
                    val xms = providers.gradleProperty("internalKonventionMs").getOrElse("2g")
                    val xmx = providers.gradleProperty("internalKonventionMx").getOrElse("4g")
                    test.jvmArgs = test.jvmArgs + listOf(
                        "-XX:TieredStopAtLevel=1",
                        "-Xms$xms",
                        "-Xmx$xmx",
                        "-Dfile.encoding=UTF-8",
                    )
                }
            }

            fun configureLinters() {
//            pluginManager.apply("com.varlanv.wrasse")
//            tasks.register("lint") { task ->
//                task.group = "verification"
//                task.dependsOn("wrasseLint")
//            }
//            tasks.register("format") { task ->
//                task.group = "verification"
//                task.dependsOn("wrasseFormat")
//            }
            }

            fun run() {
                configurePrelude()
                pluginManager.apply("org.jetbrains.kotlin.kapt")
                pluginManager.apply(internalProperties.getPlugin("ksp").get().pluginId)
                pluginManager.apply(internalProperties.getPlugin("kotest").get().pluginId)
                project.afterEvaluate {
                    configureKotlin()
                    configureTests()
                    configureLinters()
                }
            }
            run()
        }
    }
}
