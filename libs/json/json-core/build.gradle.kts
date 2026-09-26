plugins {
    alias(libs.plugins.internalMultiplatform)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector", "-Dkoper.lang.utf8.vector.enabled=true")
}

dependencies {
    add("kspCommonMainMetadata", projects.libs.serde.serdeKsp)
    add("kspCommonMainMetadata", projects.libs.json.jsonKsp)
}

ksp {
    arg("koper.serde.generators", "com.varlanv.koper.json.ksp.JsonSerdeGenerator")
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
            kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
            dependencies {
                api(projects.libs.lang)
                api(projects.libs.serde.serdeCore)
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

tasks.matching {
    it.name == "compileKotlinJvm" || it.name == "compileKotlinJs" || it.name.startsWith("kaptGenerateStubs")
}.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
