package com.varlanv.koper.lang.ext

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface DynamicDelay {
    fun next(): Duration
}

class JitterDelay(
    private val base: Duration,
    private val jitter: Duration,
    private val random: Random = Random.Default,
) : DynamicDelay {

    init {
        require(jitter.isFinite() && jitter >= Duration.ZERO)
    }

    override fun next(): Duration {
        val offsetMs = jitter.inWholeMilliseconds
        return base + random.nextLong(-offsetMs, offsetMs + 1).milliseconds
    }
}
