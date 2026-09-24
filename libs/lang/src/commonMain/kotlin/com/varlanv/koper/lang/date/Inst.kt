package com.varlanv.koper.lang.date

import com.varlanv.koper.lang.math.addExact
import com.varlanv.koper.lang.math.multiplyExact
import com.varlanv.koper.lang.math.subtractExact
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_DAY = 86_400_000L

private fun finiteMillis(duration: Duration): Long {
    if (duration.isInfinite()) {
        throw ArithmeticException("Infinite duration")
    }
    return duration.inWholeMilliseconds
}

@JvmInline
@Serializable(with = InstSerializer::class)
value class Inst internal constructor(val millis: Long) : Comparable<Inst> {
    val seconds: Long
        get() = if (millis >= 0L) {
            millis / MILLIS_PER_SECOND
        } else {
            millis.floorDiv(MILLIS_PER_SECOND)
        }

    val millisOfSecond: Int get() = millis.mod(MILLIS_PER_SECOND).toInt()

    val epochDay: Long get() = millis.floorDiv(MILLIS_PER_DAY)

    val year: Int
        get() {
            val z = epochDay + 719_468L
            val era = z.floorDiv(146_097L)
            val doe = z - era * 146_097L
            val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
            val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
            val mp = (5L * doy + 2L) / 153L
            val y = yoe + era * 400L
            return (if (mp < 10L) {
                y
            } else {
                y + 1L
            }).toInt()
        }

    fun atStartOfDay(): Inst {
        return Inst(millis.subtractExact(millis.mod(MILLIS_PER_DAY)))
    }

    fun atEndOfDay(): Inst {
        return Inst(millis.addExact(MILLIS_PER_DAY - 1 - millis.mod(MILLIS_PER_DAY)))
    }

    override operator fun compareTo(other: Inst): Int {
        return millis.compareTo(other.millis)
    }

    operator fun plus(duration: Duration): Inst {
        return Inst(
            millis.addExact(
                finiteMillis(duration),
            ),
        )
    }

    operator fun minus(duration: Duration): Inst {
        return Inst(
            millis.subtractExact(
                finiteMillis(duration),
            ),
        )
    }

    operator fun minus(other: Inst): Duration {
        val result = millis.subtractExact(other.millis).milliseconds
        if (result.isInfinite()) {
            throw ArithmeticException("Difference between $this and $other exceeds Duration range")
        }
        return result
    }

    fun toInstant(): Instant {
        return Instant.fromEpochMilliseconds(millis)
    }

    override fun toString(): String {
        return toInstant().toString()
    }

    companion object {
        val EPOCH = Inst(0L)

        fun fromMillis(millis: Long): Inst {
            return Inst(millis)
        }

        fun fromSeconds(seconds: Long): Inst {
            return Inst(seconds.multiplyExact(MILLIS_PER_SECOND))
        }

        fun from(instant: Instant): Inst {
            return Inst(instant.toEpochMilliseconds())
        }

        fun parse(iso8601: String): Inst {
            return from(Instant.parse(iso8601))
        }
    }
}

fun interface CurrentTime {
    operator fun invoke(): Inst
}

object InstSerializer : KSerializer<Inst> {
    override val descriptor = PrimitiveSerialDescriptor("Inst", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Inst) = encoder.encodeLong(value.millis)

    override fun deserialize(decoder: Decoder): Inst {
        return Inst(decoder.decodeLong())
    }
}

typealias InstSeconds = @Serializable(with = InstSecondsSerializer::class) Inst

object InstSecondsSerializer : KSerializer<Inst> {
    override val descriptor = PrimitiveSerialDescriptor("InstSeconds", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Inst) = encoder.encodeLong(value.seconds)

    override fun deserialize(decoder: Decoder): Inst {
        return Inst.fromSeconds(decoder.decodeLong())
    }
}

typealias InstStringMillis = @Serializable(with = InstStringMillisSerializer::class) Inst

object InstStringMillisSerializer : KSerializer<Inst> {
    override val descriptor = PrimitiveSerialDescriptor("InstStringMillis", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Inst) = encoder.encodeString(value.millis.toString())

    override fun deserialize(decoder: Decoder): Inst {
        return Inst(decoder.decodeString().toLong())
    }
}

typealias InstStringSeconds = @Serializable(with = InstStringSecondsSerializer::class) Inst

object InstStringSecondsSerializer : KSerializer<Inst> {
    override val descriptor = PrimitiveSerialDescriptor("InstStringSeconds", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Inst) = encoder.encodeString(value.seconds.toString())

    override fun deserialize(decoder: Decoder): Inst {
        return Inst.fromSeconds(decoder.decodeString().toLong())
    }
}
