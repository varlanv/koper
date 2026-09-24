package com.varlanv.koper.benchmarks.lang.date

import com.varlanv.koper.lang.date.Inst
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@State(Scope.Benchmark)
class InstBenchmark {
    private var epochMillis = 0L
    private var duration = Duration.ZERO
    private var inst = Inst.EPOCH
    private var laterInst = Inst.EPOCH

    @Setup
    fun setup() {
        epochMillis = 1_700_000_000_123L
        duration = 1_234.milliseconds
        inst = Inst.fromMillis(epochMillis)
        laterInst = Inst.fromMillis(epochMillis + 3_600_000L)
    }

    @Benchmark
    fun instConstructBoxed(): Any = Inst.fromMillis(epochMillis)

    @Benchmark
    fun instConstructAndReadMillis(): Long = Inst.fromMillis(epochMillis).millis

    @Benchmark
    fun instPlusAndReadMillis(): Long = (inst + duration).millis

    @Benchmark
    fun instCompare(): Int = inst.compareTo(laterInst)

    @Benchmark
    fun instEpochSeconds(): Long = inst.seconds

    @Benchmark
    fun instMillisOfSecond(): Int = inst.millisOfSecond

    @Benchmark
    fun instEpochDay(): Long = inst.epochDay

    @Benchmark
    fun instYear(): Int = inst.year
}
