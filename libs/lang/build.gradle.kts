plugins {
    alias(libs.plugins.internalConvention)
}

kotlin {
    jvm {
        compilerOptions.freeCompilerArgs.add("-Xadd-modules=jdk.incubator.vector")
    }
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.x.serialization.core)
                implementation(libs.kotlin.x.io.core)
                implementation(libs.kotlin.x.datetime)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.x.io.coreJvm)
            }
        }

        commonTest {
            dependencies {
                implementation(projects.libs.testing.commonTest)
                implementation(libs.kotlin.x.serialization.jsonCore)
            }
        }
    }
}
