plugins {
    alias(libs.plugins.internalConvention)
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.libs.lang)
            }
        }
    }
}
