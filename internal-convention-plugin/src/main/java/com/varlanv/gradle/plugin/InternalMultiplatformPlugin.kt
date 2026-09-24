package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class InternalMultiplatformPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        withSharedState(target) {
            configurePrelude()
            pluginManager.apply(internalProperties.getPlugin("kotlin-multiplatform").get().pluginId)
            pluginManager.apply("org.jetbrains.kotlin.kapt")
            pluginManager.apply(internalProperties.getPlugin("ksp").get().pluginId)
            pluginManager.apply(internalProperties.getPlugin("kotest").get().pluginId)
            project.afterEvaluate {
                configureMultiplatform { kmp ->
                    kmp.compilerOptions {
                        configureCommonCompilerOptions(this)
                    }
                }
                configureTests()
            }

        }
    }
}
