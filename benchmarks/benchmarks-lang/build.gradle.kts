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

private fun kotlinx.benchmark.gradle.BenchmarkConfiguration.benchConf(block: kotlinx.benchmark.gradle.BenchmarkConfiguration.() -> Unit) {
    block(this)
}

private fun kotlinx.benchmark.gradle.BenchmarkConfiguration.configureFast() = benchConf {
    warmups = 2
    iterations = 3
    iterationTime = 250
    iterationTimeUnit = "ms"
}


private fun kotlinx.benchmark.gradle.BenchmarkConfiguration.configureSlow() = benchConf {
    warmups = 3
    iterations = 5
    iterationTime = 500
    iterationTimeUnit = "ms"
}


benchmark {
    configurations {
        register("charsetEncoding") {
            include(".*CharsetsJsBenchmark.encode.*")
            configureFast()
        }
        register("utf8Validation") {
            include(".*Utf8ValidationBenchmark.*")
            configureSlow()
            advanced("jvmForks", 1)
        }
        register("decoderCandidates") {
            include(".*DecoderCandidatesJsBenchmark.*")
            configureFast()
        }
        register("latin1DecoderHybrid") {
            include(".*Latin1DecoderCandidatesJsBenchmark.current.*")
            configureFast()
        }
        register("asciiDecoderValidated") {
            include(".*AsciiValidatedDecoderJsBenchmark.*")
            configureFast()
        }
        register("charsets") {
            include(".*CharsetsJsBenchmark.*")
            configureSlow()
        }
        register("doubles") {
            include(".*DoubleNumbers.*Benchmark.*")
            configureSlow()
            advanced("jvmForks", 2)
        }
        register("numbers") {
            include(".*Numbers.*Benchmark.*")
            configureSlow()
            advanced("jvmForks", 2)
        }
        register("dec64ToString") {
            include(".*Dec64ToStringBenchmark.*")
            configureSlow()
            advanced("jvmForks", 2)
        }
        register("inst") {
            include(".*InstBenchmark.*")
            configureSlow()
            advanced("jvmForks", 2)
        }
        register("kotlinInstant") {
            include(".*KotlinInstantBenchmark.*")
            configureSlow()
            advanced("jvmForks", 2)
        }
    }
}
