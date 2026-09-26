import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.internalBenchmark)
    alias(libs.plugins.ksp)
}

dependencies {
    add("kspJvm", projects.libs.serde.serdeKsp)
    add("kspJvm", projects.libs.json.jsonKsp)
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
    sourceSets {
        jvmMain {
            dependencies {
                implementation(projects.libs.json.jsonCore)
            }
        }
    }
}

benchmark {
    configurations {
        register("jsonComparison") {
            include(".*JsonStreamBenchmark.*")
            warmups = 2
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 1)
        }
    }
}
