package com.varlanv.koper.lang

import java.lang.invoke.MethodHandles
import java.nio.ByteOrder

internal val intViewHandle = MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.LITTLE_ENDIAN)
internal val longViewHandle = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.LITTLE_ENDIAN)
