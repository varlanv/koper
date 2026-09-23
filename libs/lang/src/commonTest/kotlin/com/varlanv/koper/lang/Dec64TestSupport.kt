package com.varlanv.koper.lang

import kotlin.random.Random

internal fun dec(s: String): Dec64 = Dec64.parseString(string = s)

internal fun randomDec64(random: Random): Dec64 = Dec64.fromLong(
    unscaled = random.nextLong(Dec64.MIN_COEFFICIENT, Dec64.MAX_COEFFICIENT + 1),
    scale = random.nextInt(Dec64.MAX_SCALE + 1),
)
