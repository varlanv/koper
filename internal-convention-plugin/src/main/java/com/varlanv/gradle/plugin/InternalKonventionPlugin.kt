package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class InternalKonventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        withSharedState(target) {
            fun run() {
                configurePrelude()
                pluginManager.apply(internalProperties.getPlugin("kotlin-jvm").get().pluginId)
                pluginManager.apply("org.jetbrains.kotlin.kapt")
                pluginManager.apply(internalProperties.getPlugin("ksp").get().pluginId)
                pluginManager.apply(internalProperties.getPlugin("kotest").get().pluginId)
                project.afterEvaluate {
                    extensions.configure<KotlinJvmProjectExtension>("kotlin") { kotlin ->
                        kotlin.compilerOptions {
                            jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                            allWarningsAsErrors.set(false)
                            extraWarnings.set(true)
                            progressiveMode.set(true)
                            freeCompilerArgs.addAll(
                                "-Xjsr305=strict",
                            )
                        }
                        kotlin.jvmToolchain { jvmToolchain ->
                            jvmToolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
                            jvmToolchain.vendor.set(jvmVendor)
                        }
                    }
                    configureTests()
                }
            }
            run()
        }
    }
}
