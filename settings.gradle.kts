pluginManagement {
    repositories {
        if (providers.environmentVariable("CI").getOrNull() == null) {
            mavenLocal()
        }
        gradlePluginPortal()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention").version(
            providers.gradleProperty("foojayToolchainPluginVersion").get()
        )
    }
    includeBuild("internal-convention-plugin")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "koper"

val isCi = providers.environmentVariable("CI").getOrNull() == null

buildCache {
    local {
        isEnabled = !isCi
        isPush = !isCi
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    listOf(
        "benchmarks:benchmarks-lang",
        "libs:testing:common-test",
        "libs:lang",
        "libs:serde",
        "libs:json",
    )
)
