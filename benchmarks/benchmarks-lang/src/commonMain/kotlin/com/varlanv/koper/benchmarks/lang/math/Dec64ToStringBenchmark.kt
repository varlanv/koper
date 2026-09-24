package com.varlanv.koper.benchmarks.lang.math

import com.varlanv.koper.lang.math.Dec64
import com.varlanv.koper.lang.math.Dec64Array
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.random.Random

@State(Scope.Benchmark)
class Dec64ToStringBenchmark {
    private var values = Dec64Array(4096)
    private var index = 0

    @Setup
    fun setup() {
        val random = Random(43)
        for (position in 0 until values.size) {
            values[position] = when (position) {
                0 -> Dec64.ZERO
                1 -> Dec64.MAX_VALUE
                2 -> Dec64.MIN_VALUE
                3 -> Dec64.fromLong(1, Dec64.MAX_SCALE)
                4 -> Dec64.fromLong(-1, Dec64.MAX_SCALE)
                else -> Dec64.fromLong(
                    unscaled = random.nextLong(Dec64.MIN_COEFFICIENT, Dec64.MAX_COEFFICIENT + 1),
                    scale = random.nextInt(Dec64.MAX_SCALE + 1),
                )
            }
        }
        index = 0
    }

    @Benchmark
    fun dec64ToString(): String {
        val value = values[index]
        index = (index + 1) and 4095
        return value.toString()
    }
}
