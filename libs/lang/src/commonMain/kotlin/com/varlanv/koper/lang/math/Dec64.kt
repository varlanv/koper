package com.varlanv.koper.lang.math

import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.allocateString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.math.roundToLong
import kotlin.math.sign

enum class Rounding {
    UNNECESSARY,
    DOWN,
    UP,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
}

data class Dec64Context(val scale: Int, val rounding: Rounding) {
    companion object {
        val DECIMAL64 = Dec64Context(scale = Dec64.MAX_SCALE, rounding = Rounding.HALF_EVEN)
    }
}

private const val SCALE_BITS = 5
private const val SCALE_MASK = 0x1FL
private const val INVALID_BITS = Long.MIN_VALUE
private const val DOUBLE_EXACT_LIMIT = 1L shl 53

private val POW10 = longArrayOf(
    1L,
    10L,
    100L,
    1_000L,
    10_000L,
    100_000L,
    1_000_000L,
    10_000_000L,
    100_000_000L,
    1_000_000_000L,
    10_000_000_000L,
    100_000_000_000L,
    1_000_000_000_000L,
    10_000_000_000_000L,
    100_000_000_000_000L,
    1_000_000_000_000_000L,
    10_000_000_000_000_000L,
    100_000_000_000_000_000L,
    1_000_000_000_000_000_000L,
)

private val POW10_DOUBLE = DoubleArray(Dec64.MAX_SCALE + 1) { POW10[it].toDouble() }
private const val INSERTION_SORT_THRESHOLD = 16

@JvmInline
@Serializable(with = Dec64Serializer::class)
value class Dec64 private constructor(val bits: Long) : Comparable<Dec64> {
    val coefficient: Long get() = bits shr SCALE_BITS

    val scale: Int get() = (bits and SCALE_MASK).toInt()

    val isZero: Boolean get() = bits == 0L

    fun signum(): Int {
        return bits.sign
    }

    operator fun unaryMinus(): Dec64 {
        return Dec64(((-coefficient) shl SCALE_BITS) or (bits and SCALE_MASK))
    }

    fun abs(): Dec64 {
        return if (bits < 0L) {
            -this
        } else {
            this
        }
    }

    override operator fun compareTo(other: Dec64): Int {
        val a = bits
        val b = other.bits
        if (a == b) {
            return 0
        }
        if ((a xor b) < 0L) {
            return if (a < 0L) {
                -1
            } else {
                1
            }
        }
        val sa = (a and SCALE_MASK).toInt()
        val sb = (b and SCALE_MASK).toInt()
        val ca = a shr SCALE_BITS
        val cb = b shr SCALE_BITS
        return when {
            sa == sb -> ca.compareTo(cb)
            sa < sb -> compareScaled(c = ca, p = POW10[sb - sa], other = cb)
            else -> -compareScaled(c = cb, p = POW10[sa - sb], other = ca)
        }
    }

    operator fun plus(other: Dec64): Dec64 {
        val a = bits
        val b = other.bits
        val sa = (a and SCALE_MASK).toInt()
        val sb = (b and SCALE_MASK).toInt()
        val ca = a shr SCALE_BITS
        val cb = b shr SCALE_BITS
        if (sa == sb) {
            val r = buildBits(coefficient = ca + cb, scale = sa)
            if (r == INVALID_BITS) {
                throw overflow(op = "+", other = other)
            }
            return Dec64(r)
        }
        val r = if (sa < sb) {
            addScaled(c = ca, p = POW10[sb - sa], other = cb, scale = sb)
        } else {
            addScaled(c = cb, p = POW10[sa - sb], other = ca, scale = sa)
        }
        if (r == INVALID_BITS) {
            throw overflow(op = "+", other = other)
        }
        return Dec64(r)
    }

    operator fun minus(other: Dec64): Dec64 {
        val a = bits
        val b = other.bits
        val sa = (a and SCALE_MASK).toInt()
        val sb = (b and SCALE_MASK).toInt()
        val ca = a shr SCALE_BITS
        val cb = -(b shr SCALE_BITS)
        if (sa == sb) {
            val r = buildBits(coefficient = ca + cb, scale = sa)
            if (r == INVALID_BITS) {
                throw overflow(op = "-", other = other)
            }
            return Dec64(r)
        }
        val r = if (sa < sb) {
            addScaled(c = ca, p = POW10[sb - sa], other = cb, scale = sb)
        } else {
            addScaled(c = cb, p = POW10[sa - sb], other = ca, scale = sa)
        }
        if (r == INVALID_BITS) {
            throw overflow(op = "-", other = other)
        }
        return Dec64(r)
    }

    operator fun times(other: Dec64): Dec64 {
        val a = bits
        val b = other.bits
        if (a == 0L || b == 0L) {
            return ZERO
        }
        val ca = a shr SCALE_BITS
        val cb = b shr SCALE_BITS
        var s = (a and SCALE_MASK).toInt() + (b and SCALE_MASK).toInt()
        val lo = ca * cb
        val hi = ca.multiplyHigh( cb)
        if (hi == (lo shr 63)) {
            val r = buildBits(coefficient = lo, scale = s)
            if (r == INVALID_BITS) {
                throw overflow(op = "*", other = other)
            }
            return Dec64(r)
        }
        val negative = (ca < 0L) != (cb < 0L)
        val ua = kotlin.math.abs(ca)
        val ub = kotlin.math.abs(cb)
        var mlo = ua * ub
        var mhi = ua.multiplyHigh(ub)
        while (s > 0 && !fits64(hi = mhi, lo = mlo) && rem10(hi = mhi, lo = mlo) == 0L) {
            mlo = div10Lo(hi = mhi, lo = mlo)
            mhi /= 10L
            s--
        }
        if (!fits64(hi = mhi, lo = mlo)) {
            throw overflow(op = "*", other = other)
        }
        val r = buildBits(
            coefficient = if (negative) {
                -mlo
            } else {
                mlo
            },
            scale = s,
        )
        if (r == INVALID_BITS) {
            throw overflow(op = "*", other = other)
        }
        return Dec64(r)
    }

    fun div(other: Dec64, ctx: Dec64Context): Dec64 {
        val divScale = ctx.scale
        val roundingMode = ctx.rounding
        require(divScale in 0..MAX_SCALE) { "Scale $divScale is outside 0..$MAX_SCALE" }
        val a = bits
        val b = other.bits
        if (b == 0L) {
            throw divisionByZero(other)
        }
        if (a == 0L) {
            return ZERO
        }
        val ca = a shr SCALE_BITS
        val cb = b shr SCALE_BITS
        val negative = (ca < 0L) != (cb < 0L)
        val ua = kotlin.math.abs(ca)
        val ub = kotlin.math.abs(cb)
        val shift = divScale + (b and SCALE_MASK).toInt() - (a and SCALE_MASK).toInt()
        var qh = 0L
        var ql: Long
        var resultScale = divScale
        val remainderNonZero: Boolean
        val halfComparison: Int
        if (shift >= 0) {
            var r: Long
            var i: Int
            if (shift < POW10.size && ua <= Long.MAX_VALUE / POW10[shift]) {
                val numerator = ua * POW10[shift]
                ql = numerator / ub
                r = numerator % ub
                i = shift
            } else {
                ql = ua / ub
                r = ua % ub
                i = 0
            }
            while (i < shift && r != 0L) {
                if (qh > Long.MAX_VALUE / 10L) {
                    throw overflow(op = "/", other = other)
                }
                val r10 = r * 10L
                val d = r10 / ub
                r = r10 % ub
                val nl10 = ql * 10L
                val nl = nl10 + d
                val carry = if (nl.compareUnsigned(nl10) < 0) {
                    1L
                } else {
                    0L
                }
                qh = qh * 10L + ql.unsignedMultiplyHigh( 10L) + carry
                ql = nl
                i++
            }
            resultScale -= shift - i
            if (resultScale < 0) {
                if (qh != 0L) {
                    throw overflow(op = "/", other = other)
                }
                ql = mulPow10(value = ql, exponent = -resultScale)
                if (ql == INVALID_BITS) {
                    throw overflow(op = "/", other = other)
                }
                resultScale = 0
            }
            remainderNonZero = r != 0L
            halfComparison = r.compareTo(ub - r)
        } else {
            val p = POW10[-shift]
            val dlo = ub * p
            val dhi = ub.multiplyHigh( p)
            if (dhi == 0L && dlo >= 0L) {
                ql = ua / dlo
                val r = ua % dlo
                remainderNonZero = r != 0L
                halfComparison = r.compareTo(dlo - r)
            } else {
                ql = 0L
                remainderNonZero = true
                halfComparison = -1
            }
        }
        if (remainderNonZero) {
            val increment = when (roundingMode) {
                Rounding.UNNECESSARY -> throw ArithmeticException(
                    "Rounding necessary: $this / $other at scale $divScale",
                )

                Rounding.DOWN -> false
                Rounding.UP -> true
                Rounding.CEILING -> !negative
                Rounding.FLOOR -> negative
                Rounding.HALF_UP -> halfComparison >= 0
                Rounding.HALF_DOWN -> halfComparison > 0
                Rounding.HALF_EVEN -> halfComparison > 0 || (halfComparison == 0 && (ql and 1L) == 1L)
            }
            if (increment) {
                ql++
                if (ql == 0L) {
                    qh++
                }
            }
        }
        while (resultScale > 0 && !fits64(hi = qh, lo = ql) && rem10(hi = qh, lo = ql) == 0L) {
            ql = div10Lo(hi = qh, lo = ql)
            qh /= 10L
            resultScale--
        }
        if (!fits64(hi = qh, lo = ql)) {
            throw overflow(op = "/", other = other)
        }
        val bitsResult = buildBits(
            coefficient = if (negative) {
                -ql
            } else {
                ql
            },
            scale = resultScale,
        )
        if (bitsResult == INVALID_BITS) {
            throw overflow(op = "/", other = other)
        }
        return Dec64(bitsResult)
    }

    fun toDouble(): Double {
        val c = coefficient
        if (c > -DOUBLE_EXACT_LIMIT && c < DOUBLE_EXACT_LIMIT) {
            return c.toDouble() / POW10_DOUBLE[scale]
        }
        return toString().toDouble()
    }


    fun toLongExact(): Long {
        if (scale != 0) {
            throw ArithmeticException("$this is not a whole number")
        }
        return coefficient
    }

    fun writeTo(buf: ByteArray, offset: Int): Int {
        var c = coefficient
        val s = scale
        val negative = c < 0L
        if (negative) {
            c = -c
        }
        var digitCount = 1
        var t = c
        while (t >= 10L) {
            t /= 10L
            digitCount++
        }
        val len = (if (negative) {
            1
        } else {
            0
        }) + (if (digitCount > s) {
            digitCount + (if (s > 0) {
                1
            } else {
                0
            })
        } else {
            s + 2
        })
        var pos = offset + len
        var i = 0
        while (i < s) {
            pos--
            buf[pos] = ('0'.code + (c % 10L)).toByte()
            c /= 10L
            i++
        }
        if (s > 0) {
            pos--
            buf[pos] = '.'.code.toByte()
        }
        if (c == 0L) {
            pos--
            buf[pos] = '0'.code.toByte()
        } else {
            while (c > 0L) {
                pos--
                buf[pos] = ('0'.code + (c % 10L)).toByte()
                c /= 10L
            }
        }
        if (negative) {
            pos--
            buf[pos] = '-'.code.toByte()
        }
        return offset + len
    }

    override fun toString(): String = allocateString()

    private fun overflow(op: String, other: Dec64) = ArithmeticException("Dec64 overflow: $this $op $other")

    private fun divisionByZero(other: Dec64) = ArithmeticException("Dec64 division by zero: $this / $other")

    companion object {
        const val MAX_SCALE = 18
        const val MAX_COEFFICIENT = (1L shl 58) - 1
        const val MIN_COEFFICIENT = -MAX_COEFFICIENT
        const val MAX_CHARS = 21

        val ZERO = Dec64(0L)
        val ONE = Dec64(1L shl SCALE_BITS)
        val MAX_VALUE = Dec64(MAX_COEFFICIENT shl SCALE_BITS)
        val MIN_VALUE = Dec64(MIN_COEFFICIENT shl SCALE_BITS)

        operator fun invoke(value: Long): Dec64 {
            if (value !in MIN_COEFFICIENT..MAX_COEFFICIENT) {
                throw ArithmeticException("Dec64 overflow: $value is outside $MIN_COEFFICIENT..$MAX_COEFFICIENT")
            }
            return Dec64(value shl SCALE_BITS)
        }

        fun fromLong(unscaled: Long, scale: Int): Dec64 {
            var c = unscaled
            var s = scale
            if (s < 0) {
                if (c !in MIN_COEFFICIENT..MAX_COEFFICIENT) {
                    throw ofOverflow(unscaled = unscaled, scale = scale)
                }
                c = mulPow10(value = c, exponent = -s)
                if (c == INVALID_BITS) {
                    throw ofOverflow(unscaled = unscaled, scale = scale)
                }
                s = 0
            }
            val r = buildBits(coefficient = c, scale = s)
            if (r == INVALID_BITS) {
                throw ofOverflow(unscaled = unscaled, scale = scale)
            }
            return Dec64(r)
        }

        fun fromDouble(value: Double): Dec64 {
            if (value == 0.0) {
                return ZERO
            }
            if (!value.isFinite()) {
                throw ArithmeticException("Dec64: non-finite $value")
            }
            for (s in 0..MAX_SCALE) {
                val scaled = value * POW10_DOUBLE[s]
                val rounded = scaled.roundToLong()
                if (rounded in MIN_COEFFICIENT..MAX_COEFFICIENT && rounded.toDouble() == scaled) {
                    val bits = buildBits(coefficient = rounded, scale = s)
                    if (bits != INVALID_BITS) {
                        return Dec64(bits)
                    }
                }
            }
            return parseString(string = value.toString())
        }

        fun parseString(
            string: String,
            from: Int = 0,
            to: Int = string.length,
        ): Dec64 {
            return Dec64(
                parseBits(
                    from = from,
                    to = to,
                    charAt = { string[it].code },
                    text = { string.substring(from, to) },
                ),
            )
        }

        fun parseBytes(
            bytes: ByteArray,
            from: Int = 0,
            to: Int = bytes.size,
        ): Dec64 {
            return Dec64(
                parseBits(
                    from = from,
                    to = to,
                    charAt = { bytes[it].toInt() },
                    text = { Charset.Latin1.allocateString(bytes, from, to - from) },
                ),
            )
        }

        fun min(a: Dec64, b: Dec64): Dec64 {
            return if (a <= b) {
                a
            } else {
                b
            }
        }

        fun max(a: Dec64, b: Dec64): Dec64 {
            return if (a >= b) {
                a
            } else {
                b
            }
        }

        @PublishedApi
        internal fun fromBits(bits: Long) = Dec64(bits)

        private fun ofOverflow(
            unscaled: Long,
            scale: Int,
        ) = ArithmeticException("Dec64 overflow: unscaled=$unscaled scale=$scale")

        private inline fun parseBits(
            from: Int,
            to: Int,
            charAt: (Int) -> Int,
            text: () -> String,
        ): Long {
            var i = from
            if (i >= to) {
                throw NumberFormatException("Empty Dec64 input")
            }
            var ch = charAt(i)
            var negative = false
            if (ch == '-'.code) {
                negative = true
                i++
            } else if (ch == '+'.code) {
                i++
            }
            var coef = 0L
            var pendingZeros = 0
            var digits = 0
            var fractionDigits = 0
            var seenDot = false
            while (i < to) {
                ch = charAt(i)
                val d = ch - '0'.code
                if (d in 0..9) {
                    digits++
                    if (seenDot) {
                        fractionDigits++
                    }
                    if (d == 0) {
                        pendingZeros++
                    } else {
                        if (pendingZeros > 0) {
                            coef = mulPow10(value = coef, exponent = pendingZeros)
                            if (coef == INVALID_BITS) {
                                throw parseOverflow(text())
                            }
                            pendingZeros = 0
                        }
                        coef = coef * 10L + d
                        if (coef > MAX_COEFFICIENT) {
                            throw parseOverflow(text())
                        }
                    }
                } else if (ch == '.'.code && !seenDot) {
                    seenDot = true
                } else if (ch == 'e'.code || ch == 'E'.code) {
                    break
                } else {
                    throw NumberFormatException("Invalid Dec64 input: '${text()}'")
                }
                i++
            }
            if (digits == 0) {
                throw NumberFormatException("Invalid Dec64 input: '${text()}'")
            }
            var s = fractionDigits - pendingZeros
            if (i < to) {
                i++
                if (i >= to) {
                    throw NumberFormatException("Invalid Dec64 input: '${text()}'")
                }
                ch = charAt(i)
                var expNegative = false
                if (ch == '-'.code) {
                    expNegative = true
                    i++
                } else if (ch == '+'.code) {
                    i++
                }
                if (i >= to) {
                    throw NumberFormatException("Invalid Dec64 input: '${text()}'")
                }
                var exp = 0
                while (i < to) {
                    val d = charAt(i) - '0'.code
                    if (d !in 0..9) {
                        throw NumberFormatException("Invalid Dec64 input: '${text()}'")
                    }
                    exp = exp * 10 + d
                    if (exp > 10_000) {
                        throw parseOverflow(text())
                    }
                    i++
                }
                s += if (expNegative) {
                    exp
                } else {
                    -exp
                }
            }
            if (s < 0) {
                coef = mulPow10(value = coef, exponent = -s)
                if (coef == INVALID_BITS) {
                    throw parseOverflow(text())
                }
                s = 0
            }
            val r = buildBits(
                coefficient = if (negative) {
                    -coef
                } else {
                    coef
                },
                scale = s,
            )
            if (r == INVALID_BITS) {
                throw parseOverflow(text())
            }
            return r
        }

        private fun parseOverflow(text: String) = ArithmeticException("Dec64 overflow: '$text' does not fit")
    }
}

internal expect fun Dec64.allocateString(): String

private fun buildBits(coefficient: Long, scale: Int): Long {
    var c = coefficient
    var s = scale
    while (s > 0 && c % 10L == 0L) {
        c /= 10L
        s--
    }
    if (c == 0L) {
        return 0L
    }
    if (c > Dec64.MAX_COEFFICIENT || c < Dec64.MIN_COEFFICIENT || s > Dec64.MAX_SCALE) {
        return INVALID_BITS
    }
    return (c shl SCALE_BITS) or s.toLong()
}

private fun mulPow10(value: Long, exponent: Int): Long {
    if (value == 0L) {
        return 0L
    }
    if (exponent >= POW10.size) {
        return INVALID_BITS
    }
    val p = POW10[exponent]
    if (value > Dec64.MAX_COEFFICIENT / p || value < Dec64.MIN_COEFFICIENT / p) {
        return INVALID_BITS
    }
    return value * p
}

private fun addScaled(
    c: Long,
    p: Long,
    other: Long,
    scale: Int,
): Long {
    val lo0 = c * p
    val hi0 = c.multiplyHigh( p)
    val lo = lo0 + other
    val carry = if (lo.compareUnsigned( lo0) < 0) {
        1L
    } else {
        0L
    }
    val hi = hi0 + (other shr 63) + carry
    if (hi != (lo shr 63)) {
        return INVALID_BITS
    }
    return buildBits(coefficient = lo, scale = scale)
}

private fun fits64(hi: Long, lo: Long): Boolean {
    return hi == 0L && lo >= 0L && lo <= Dec64.MAX_COEFFICIENT
}

private fun rem10(hi: Long, lo: Long): Long {
    return ((hi % 10L) * 6L + lo.remainderUnsigned(10L)) % 10L
}

private fun div10Lo(hi: Long, lo: Long): Long {
    val t1 = ((hi % 10L) shl 32) or (lo ushr 32)
    val t2 = ((t1 % 10L) shl 32) or (lo and 0xFFFF_FFFFL)
    return ((t1 / 10L) shl 32) or (t2 / 10L)
}

private fun compareScaled(
    c: Long,
    p: Long,
    other: Long,
): Int {
    val lo = c * p
    val hi = c.multiplyHigh( p)
    val otherHi = other shr 63
    return if (hi != otherHi) {
        hi.compareTo(otherHi)
    } else {
        lo.compareUnsigned(other)
    }
}

object Dec64Serializer : KSerializer<Dec64> {
    override val descriptor = PrimitiveSerialDescriptor("Dec64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Dec64) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Dec64 {
        return Dec64.parseString(string = decoder.decodeString())
    }
}

@JvmInline
value class Dec64Array private constructor(@PublishedApi internal val values: LongArray) {
    val size: Int get() = values.size

    fun isEmpty(): Boolean {
        return values.isEmpty()
    }

    operator fun get(index: Int): Dec64 {
        if (index !in values.indices) {
            throw IndexOutOfBoundsException("Index $index is outside 0 until $size")
        }
        return Dec64.fromBits(values[index])
    }

    operator fun set(index: Int, value: Dec64) {
        if (index !in values.indices) {
            throw IndexOutOfBoundsException("Index $index is outside 0 until $size")
        }
        values[index] = value.bits
    }

    fun fill(
        value: Dec64,
        from: Int = 0,
        to: Int = values.size,
    ) {
        values.fill(value.bits, from, to)
    }

    fun copyOf(): Dec64Array {
        return Dec64Array(values.copyOf())
    }

    fun copyOfRange(from: Int, to: Int): Dec64Array {
        return Dec64Array(values.copyOfRange(from, to))
    }

    fun contentEquals(other: Dec64Array): Boolean {
        return values.contentEquals(other.values)
    }

    fun sum(
        from: Int = 0,
        to: Int = values.size,
    ): Dec64 {
        var acc = Dec64.ZERO
        for (i in from until to) {
            acc += Dec64.fromBits(values[i])
        }
        return acc
    }

    fun min(
        from: Int = 0,
        to: Int = values.size,
    ): Dec64 {
        require(from < to) { "Empty range $from..$to" }
        var m = Dec64.fromBits(values[from])
        for (i in from + 1 until to) {
            val v = Dec64.fromBits(values[i])
            if (v < m) {
                m = v
            }
        }
        return m
    }

    fun max(
        from: Int = 0,
        to: Int = values.size,
    ): Dec64 {
        require(from < to) { "Empty range $from..$to" }
        var m = Dec64.fromBits(values[from])
        for (i in from + 1 until to) {
            val v = Dec64.fromBits(values[i])
            if (v > m) {
                m = v
            }
        }
        return m
    }

    fun sortAsc(
        from: Int = 0,
        to: Int = values.size,
    ) {
        if (to - from > 1) {
            quickSort(
                a = values,
                low = from,
                high = to - 1,
                depthLimit = 2 * (32 - (to - from).numberOfLeadingZeros()),
            )
        }
    }

    fun sortDesc(
        from: Int = 0,
        to: Int = values.size,
    ) {
        sortAsc(from = from, to = to)
        values.reverse(from, to)
    }

    inline fun forEach(block: (value: Dec64) -> Unit) {
        for (value in values) {
            block(Dec64.fromBits(value))
        }
    }

    inline fun forEachIndexed(block: (index: Int, value: Dec64) -> Unit) {
        for (i in values.indices) {
            block(
                i,
                Dec64.fromBits(values[i]),
            )
        }
    }

    override fun toString(): String {
        val sb = StringBuilder(values.size * 8 + 2)
        sb.append('[')
        for (i in values.indices) {
            if (i > 0) {
                sb.append(", ")
            }
            sb.append(Dec64.fromBits(values[i]).toString())
        }
        return sb.append(']').toString()
    }

    companion object {
        operator fun invoke(size: Int): Dec64Array {
            return Dec64Array(LongArray(size))
        }

        inline operator fun invoke(size: Int, init: (index: Int) -> Dec64): Dec64Array {
            val values = LongArray(size)
            for (i in 0 until size) {
                values[i] = init(i).bits
            }
            return wrap(values)
        }

        @PublishedApi
        internal fun wrap(values: LongArray): Dec64Array {
            return Dec64Array(values)
        }
    }
}

private fun quickSort(
    a: LongArray,
    low: Int,
    high: Int,
    depthLimit: Int,
) {
    var lo = low
    var hi = high
    var depth = depthLimit
    while (hi - lo >= INSERTION_SORT_THRESHOLD) {
        if (depth == 0) {
            heapSort(a = a, lo = lo, hi = hi)
            return
        }
        depth--
        val mid = (lo + hi) ushr 1
        if (less(x = a[mid], y = a[lo])) {
            swap(a = a, i = mid, j = lo)
        }
        if (less(x = a[hi], y = a[lo])) {
            swap(a = a, i = hi, j = lo)
        }
        if (less(x = a[hi], y = a[mid])) {
            swap(a = a, i = hi, j = mid)
        }
        val pivot = a[mid]
        var i = lo
        var j = hi
        while (i <= j) {
            while (less(x = a[i], y = pivot)) i++
            while (less(x = pivot, y = a[j])) j--
            if (i <= j) {
                swap(a = a, i = i, j = j)
                i++
                j--
            }
        }
        if (j - lo < hi - i) {
            quickSort(a = a, low = lo, high = j, depthLimit = depth)
            lo = i
        } else {
            quickSort(a = a, low = i, high = hi, depthLimit = depth)
            hi = j
        }
    }
    insertionSort(a = a, lo = lo, hi = hi)
}

private fun insertionSort(
    a: LongArray,
    lo: Int,
    hi: Int,
) {
    var i = lo + 1
    while (i <= hi) {
        val v = a[i]
        var j = i - 1
        while (j >= lo && less(x = v, y = a[j])) {
            a[j + 1] = a[j]
            j--
        }
        a[j + 1] = v
        i++
    }
}

private fun heapSort(
    a: LongArray,
    lo: Int,
    hi: Int,
) {
    val n = hi - lo + 1
    var i = n / 2 - 1
    while (i >= 0) {
        siftDown(a = a, base = lo, start = i, size = n)
        i--
    }
    var end = n - 1
    while (end > 0) {
        swap(a = a, i = lo, j = lo + end)
        siftDown(a = a, base = lo, start = 0, size = end)
        end--
    }
}

private fun siftDown(
    a: LongArray,
    base: Int,
    start: Int,
    size: Int,
) {
    var root = start
    while (true) {
        val left = 2 * root + 1
        if (left >= size) {
            return
        }
        var child = left
        val right = left + 1
        if (right < size && less(x = a[base + child], y = a[base + right])) {
            child = right
        }
        if (!less(x = a[base + root], y = a[base + child])) {
            return
        }
        swap(a = a, i = base + root, j = base + child)
        root = child
    }
}

private fun less(x: Long, y: Long): Boolean {
    return Dec64.fromBits(x) < Dec64.fromBits(y)
}

private fun swap(
    a: LongArray,
    i: Int,
    j: Int,
) {
    val t = a[i]
    a[i] = a[j]
    a[j] = t
}
