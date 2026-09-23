plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kover) apply false
    alias(libs.plugins.wrasse) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.versionCatalogPlugin)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

versionCatalogUpdate {
    keep {
        keepUnusedVersions = true
    }
}
