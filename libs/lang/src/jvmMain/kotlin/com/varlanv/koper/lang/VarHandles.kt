package com.varlanv.koper.lang

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.nio.ByteOrder

val longViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(LongArray::class.java, ByteOrder.LITTLE_ENDIAN)
val intViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.LITTLE_ENDIAN)
val shortViewHandle: VarHandle = MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, ByteOrder.LITTLE_ENDIAN)
