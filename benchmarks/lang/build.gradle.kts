plugins {
    alias(libs.plugins.internalBenchmark)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.libs.lang)
                implementation(libs.kotlin.x.datetime)
            }
        }
    }
}

benchmark {
    configurations {
        register("doubles") {
            include(".*DoubleNumbers.*Benchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            advanced("jvmForks", 2)
        }
        register("numbers") {
            include(".*Numbers.*Benchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            advanced("jvmForks", 2)
        }
        register("dec64ToString") {
            include(".*Dec64ToStringBenchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            advanced("jvmForks", 2)
        }
    }
}
