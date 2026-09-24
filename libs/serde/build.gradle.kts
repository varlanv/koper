plugins {
    alias(libs.plugins.internalMultiplatform)
    `maven-publish`
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
