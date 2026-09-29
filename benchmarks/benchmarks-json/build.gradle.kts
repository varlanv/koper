import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.internalBenchmark)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    add("kspJvm", projects.libs.serde.serdeKsp)
    add("kspJvm", projects.libs.json.jsonKsp)
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
        register("jsonComparison") {
            include(".*JsonStreamBenchmark.*")
            warmups = 3
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
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
    }
}
