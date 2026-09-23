import kotlin.jvm.optionals.getOrNull

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

val isCiBuild = providers.environmentVariable("CI").orNull != null

kotlin {
    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(versionCatalogs.named("libs").findVersion("javaVersion").getOrNull()?.requiredVersion!!))
    }
}

if (!isCiBuild) {
    pluginManager.apply(IdeaPlugin::class.java)
    val idea = extensions.getByName("idea") as org.gradle.plugins.ide.idea.model.IdeaModel
    idea.module.isDownloadJavadoc = true
    idea.module.isDownloadSources = true
}

repositories {
    if (!isCiBuild) {
        mavenLocal()
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.allopen)
    implementation(libs.kotlin.gradle.benchmark)
    implementation(libs.kotlin.gradle.serialization)
    implementation(libs.kotlin.gradle.kover)
    implementation(libs.kotlin.gradle.main)
    implementation(libs.wrasse.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("internalBenchmarkConventionPlugin") {
            id = libs.plugins.internalBenchmark.get().pluginId
            implementationClass = "com.varlanv.gradle.plugin.InternalBenchmarkPlugin"
        }
        create("internalGradleConventionPlugin") {
            id = libs.plugins.internalConvention.get().pluginId
            implementationClass = "com.varlanv.gradle.plugin.InternalKonventionPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
