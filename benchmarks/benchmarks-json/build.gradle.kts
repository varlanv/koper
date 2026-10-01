import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.internalBenchmark)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    add("kspJvm", projects.libs.serde.serdeKsp)
    add("kspJvm", projects.libs.json.jsonKsp)
    add("kspJs", projects.libs.serde.serdeKsp)
    add("kspJs", projects.libs.json.jsonKsp)
    add("kapt", libs.dslJson)
}

ksp {
    arg("koper.serde.generators", "com.varlanv.koper.json.ksp.JsonSerdeGenerator")
}

tasks.withType<Jar>().configureEach {
    if (name == "jvmBenchmarkJar") {
        from(tasks.named("compileKotlinJvm"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

kotlin {
    jvm {
        compilerOptions.freeCompilerArgs.add("-Xadd-modules=jdk.incubator.vector")
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(projects.libs.json.jsonCore)
                implementation(libs.kotlin.x.serialization.jsonCore)
            }
        }
        jvmMain {
            dependencies {
                implementation(projects.libs.json.jsonCore)
                implementation(libs.dslJson)
            }
        }
    }
}

benchmark {
    configurations {
        register("jsonKotlinx") {
            include(".*JsonStreamBenchmark.kotlinx.*")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonKotlinxRead") {
            include(".*JsonStreamBenchmark.kotlinxRead")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonKotlinxWrite") {
            include(".*JsonStreamBenchmark.kotlinxWrite")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonComparison") {
            include(".*JsonStreamBenchmark.*")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonQuickComparison") {
            include(".*JsonStreamBenchmark.generatedReadString")
            include(".*JsonStreamBenchmark.generatedWriteString")
            param("payload", "ASCII_SMALL")
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonGeneratedRw") {
            include(".*JsonStreamBenchmark\\.generated.*")
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonGeneratedRead") {
            include(".*JsonStreamBenchmark\\.generatedRead.*")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
        register("jsonGeneratedWrite") {
            include(".*JsonStreamBenchmark\\.generatedWrite.*")
            warmups = 5
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
    }
}
