plugins {
    alias(libs.plugins.internalMultiplatform)
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
