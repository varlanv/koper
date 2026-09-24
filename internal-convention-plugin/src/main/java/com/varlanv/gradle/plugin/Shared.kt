package com.varlanv.gradle.plugin

import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.plugin.use.PluginDependency
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import kotlin.jvm.optionals.getOrNull

internal data class InternalEnvironment(val isCi: Boolean, val isTest: Boolean) {

    companion object {
        const val NAME = "__internal_environment__";
    }

    fun isLocal(): Boolean {
        return !isCi
    }
}

internal class InternalProperties(private val versionCatalogsExtension: VersionCatalogsExtension) {

    companion object {
        const val NAME = "__internal_convention_properties__"
    }

    private val versionCatalog = versionCatalogsExtension.named("libs")

    fun getLib(name: String): Provider<MinimalExternalModuleDependency> {
        return versionCatalog
            .findLibrary(name)
            .getOrNull() ?: error("Unable to find library [${name}]")
    }

    fun getPlugin(name: String): Provider<PluginDependency> {
        return versionCatalog
            .findPlugin(name)
            .orElse(null) ?: error(
            "Unable to find plugin [${name}]"
        )
    }

    fun getVersion(name: String): String {
        return versionCatalog.findVersion(name).getOrNull()?.requiredVersion
            ?: error("Unable to find version [${name}]")
    }
}

internal class SharedState(val project: Project) {

    val projectName = project.name
    val projectLayout = project.layout
    val extensions = project.extensions
    val providers = project.providers
    val pluginManager = project.pluginManager
    val tasks = project.tasks
    val repositories = project.repositories
    val dependencies = project.dependencies
    val rootDir = project.rootDir.toPath()
    val internalEnvironment = extensions.findByName(InternalEnvironment.NAME) as InternalEnvironment?
        ?: InternalEnvironment(providers.environmentVariable("CI").isPresent, false)
    val internalProperties = extensions.findByName(InternalProperties.NAME) as InternalProperties?
        ?: InternalProperties(extensions.getByName("versionCatalogs") as VersionCatalogsExtension)
    val internalKonventionExtension =
        extensions.findByName(InternalKonventionExtension.NAME) as InternalKonventionExtension?
            ?: extensions.create(InternalKonventionExtension.NAME, InternalKonventionExtension::class.java)
    val javaVersion = internalProperties.getVersion("javaVersion")
    val kotlinVersion = internalProperties.getVersion("kotlinVersion")
    val jvmVendor = JvmVendorSpec.ADOPTIUM

    val configurePrelude = {
        fun configureRepositories() {
            if (internalEnvironment.isLocal()) {
                repositories.add(repositories.mavenLocal())
            }
            repositories.add(repositories.mavenCentral())
        }

        fun applyCommonPlugins() {
            if (internalEnvironment.isLocal()) {
                pluginManager.apply(IdeaPlugin::class.java)
                extensions.configure<IdeaModel>("idea") {
                    it.module.isDownloadJavadoc = true
                    it.module.isDownloadSources = true
                }
            }
        }

        fun configureCommonDependencies() {
//            if (projectName != "common-test") {
//                dependencies.add(
//                    "testImplementation",
//                    dependencies.project(mapOf("path" to ":libs:testing:common-test"))
//                )
//            }
        }

        configureRepositories()
        applyCommonPlugins()
        configureCommonDependencies()
        pluginManager.apply(internalProperties.getPlugin("kotlin-serialization").get().pluginId)
        pluginManager.apply(internalProperties.getPlugin("kotlin-multiplatform").get().pluginId)

        project.afterEvaluate {
            pluginManager.apply(KoverGradlePlugin::class.java)
        }
    }
    val applyCommonTargets = {
        extensions.configure<KotlinMultiplatformExtension>("kotlin") { kmp ->
            kmp.jvm()
            kmp.js {
                nodejs()
            }
        }
    }
    val configureMultiplatform = { additional: (KotlinMultiplatformExtension) -> Unit ->
        extensions.configure<KotlinMultiplatformExtension>("kotlin") { kmp ->
            if (providers.gradleProperty("jsLongAsBigInt").map(String::toBoolean).getOrElse(false)) {
                kmp.targets.withType(KotlinJsIrTarget::class.java).configureEach { target ->
                    target.compilerOptions.freeCompilerArgs.add("-Xes-long-as-bigint")
                }
            }

            kmp.targets.withType(KotlinJvmTarget::class.java).configureEach {
                kmp.jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
                        freeCompilerArgs.addAll(
                            "-Xjsr305=strict",
                        )
                    }
                }
                kmp.jvmToolchain { jvmToolchain ->
                    jvmToolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
                    jvmToolchain.vendor.set(jvmVendor)
                }
            }
            kmp.compilerOptions {
                allWarningsAsErrors.set(false)
                extraWarnings.set(true)
                progressiveMode.set(true)
            }
            additional(kmp)
        }
    }

    init {
        internalKonventionExtension.internalModule.convention(false)
    }
}

internal inline fun withSharedState(project: Project, block: SharedState.() -> Unit) {
    block(SharedState(project))
}

internal val log = Logging.getLogger("internal-convention.app")
