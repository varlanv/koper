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
        register("decoderCandidates") {
            include(".*DecoderCandidatesJsBenchmark.*")
            warmups = 2
            iterations = 3
            iterationTime = 250
            iterationTimeUnit = "ms"
        }
        register("latin1DecoderHybrid") {
            include(".*Latin1DecoderCandidatesJsBenchmark.current.*")
            warmups = 2
            iterations = 3
            iterationTime = 250
            iterationTimeUnit = "ms"
        }
        register("asciiDecoderValidated") {
            include(".*AsciiValidatedDecoderJsBenchmark.*")
            warmups = 2
            iterations = 3
            iterationTime = 250
            iterationTimeUnit = "ms"
        }
        register("charsets") {
            include(".*CharsetsJsBenchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
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
        register("inst") {
            include(".*InstBenchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            advanced("jvmForks", 2)
        }
        register("kotlinInstant") {
            include(".*KotlinInstantBenchmark.*")
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            advanced("jvmForks", 2)
        }
    }
}
