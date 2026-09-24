package com.varlanv.koper.benchmarks.lang.math

import kotlin.math.pow
import kotlin.random.Random

internal fun doubleNumbersData(distribution: String): DoubleArray {
    val random = Random(42)
    val values = DoubleArray(4096) { index ->
        when (distribution) {
            "small" -> (index and 255) / 100.0
            "mixed" -> when (index % 4) {
                0 -> random.nextInt(-1_000_000, 1_000_000).toDouble()
                1 -> random.nextInt(-1_000_000, 1_000_000) / 100.0
                2 -> random.nextDouble(-1.0, 1.0)
                else -> random.nextDouble(-1.0, 1.0) * 10.0.pow(random.nextInt(-300, 301))
            }
            "random" -> {
                var value: Double
                do {
                    value = Double.fromBits(random.nextLong())
                } while (!value.isFinite())
                value
            }
            else -> error("Unknown distribution: $distribution")
        }
    }
    values.shuffle(random)
    return values
}
