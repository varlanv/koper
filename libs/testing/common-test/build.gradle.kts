plugins {
    alias(libs.plugins.internalConvention)
}

kotlin {
    jvm()
    js {
        nodejs()
    }
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.kotlin.kotest.junit5Runner)
                api(libs.kotlin.kotest.assertionsJvm)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.kotest.junit5Runner)
            }
        }
        commonMain {
            dependencies {
                api(libs.kotlin.kotest.assertionsCore)
                api(libs.kotlin.kotest.frameworkEngine)
            }
        }
    }
}
