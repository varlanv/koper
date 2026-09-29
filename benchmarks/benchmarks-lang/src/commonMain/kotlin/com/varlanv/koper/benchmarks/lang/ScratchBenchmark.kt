package com.varlanv.koper.benchmarks.lang

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Scratch benchmark without specific logic for quick and dirty checks.
 * To be cleaned up to current state before committing.
 */
@State(Scope.Benchmark)
class ScratchBenchmark {

    @Setup
    fun setup() {
    }

    @Benchmark
    fun scratch() {

    }
}
