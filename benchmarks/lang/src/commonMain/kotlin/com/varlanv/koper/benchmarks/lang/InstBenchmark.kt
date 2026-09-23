package com.varlanv.koper.benchmarks.lang

import com.varlanv.koper.lang.Inst
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@State(Scope.Benchmark)
class InstBenchmark {
    private var epochMillis = 0L
    private var duration = Duration.ZERO
    private var inst = Inst.EPOCH
    private var laterInst = Inst.EPOCH
    private var instant = Instant.fromEpochMilliseconds(0L)
    private var laterInstant = Instant.fromEpochMilliseconds(0L)

    @Setup
    fun setup() {
        epochMillis = 1_700_000_000_123L
        duration = 1_234.milliseconds
        inst = Inst.fromMillis(epochMillis)
        laterInst = Inst.fromMillis(epochMillis + 3_600_000L)
        instant = Instant.fromEpochMilliseconds(epochMillis)
        laterInstant = Instant.fromEpochMilliseconds(epochMillis + 3_600_000L)
    }

    @Benchmark
    fun instConstructBoxed(): Any = Inst.fromMillis(epochMillis)

    @Benchmark
    fun instantConstructBoxed(): Any = Instant.fromEpochMilliseconds(epochMillis)

    @Benchmark
    fun instConstructAndReadMillis(): Long = Inst.fromMillis(epochMillis).millis

    @Benchmark
    fun instantConstructAndReadMillis(): Long = Instant.fromEpochMilliseconds(epochMillis).toEpochMilliseconds()

    @Benchmark
    fun instPlusAndReadMillis(): Long = (inst + duration).millis

    @Benchmark
    fun instantPlusAndReadMillis(): Long = (instant + duration).toEpochMilliseconds()

    @Benchmark
    fun instCompare(): Int = inst.compareTo(laterInst)

    @Benchmark
    fun instantCompare(): Int = instant.compareTo(laterInstant)

    @Benchmark
    fun instEpochSeconds(): Long = inst.seconds

    @Benchmark
    fun instantEpochSeconds(): Long = instant.epochSeconds

    @Benchmark
    fun instMillisOfSecond(): Int = inst.millisOfSecond

    @Benchmark
    fun instantMillisOfSecond(): Int = instant.nanosecondsOfSecond / 1_000_000

    @Benchmark
    fun instEpochDay(): Long = inst.epochDay

    @Benchmark
    fun instantEpochDay(): Long = instant.epochSeconds.floorDiv(86_400L)

    @Benchmark
    fun instYear(): Int = inst.year

    @Benchmark
    fun instantYear(): Int = instant.toLocalDateTime(TimeZone.UTC).year
}
