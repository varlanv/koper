package com.varlanv.koper.lang

import kotlin.jvm.JvmInline
import kotlin.math.abs

@JvmInline
value class DataSize internal constructor(val bytes: Int) {

    operator fun plus(bytes: Int): DataSize = DataSize(this.bytes.addExact(bytes))

    operator fun plus(other: DataSize): DataSize = DataSize(this.bytes.addExact(other.bytes))

    override fun toString(): String {
        val magnitude = abs(bytes.toLong())

        if (magnitude < KIB) return "${bytes}B"

        val readable = when {
            magnitude < MIB -> "${format(magnitude, KIB)}KiB"
            magnitude < GIB -> "${format(magnitude, MIB)}MiB"
            else -> "${format(magnitude, GIB)}GiB"
        }

        return "$readable ($bytes B)"
    }

    private fun format(magnitude: Long, unit: Int): String {
        // Round to thousandths, with ties away from zero.
        // Safe in Long for every possible Int byte count.
        val scaled = (magnitude * 1000L + unit / 2) / unit
        val fraction = (scaled % 1000L).toInt()

        return buildString {
            if (bytes < 0) append('-')
            append(scaled / 1000L)

            if (fraction != 0) {
                append('.')
                append('0' + fraction / 100)

                if (fraction % 100 != 0) {
                    append('0' + (fraction / 10) % 10)

                    if (fraction % 10 != 0) {
                        append('0' + fraction % 10)
                    }
                }
            }
        }
    }
}

private const val KIB = 1024
private const val MIB = KIB * 1024
private const val GIB = MIB * 1024

fun Int.bytes() = DataSize(this)

fun Int.kilobytes() = DataSize(this.multiplyExact(KIB))

fun Int.megabytes() = DataSize(this.multiplyExact(MIB))

fun Int.gigabytes() = DataSize(this.multiplyExact(GIB))
