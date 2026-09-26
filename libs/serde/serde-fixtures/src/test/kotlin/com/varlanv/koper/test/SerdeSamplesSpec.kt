package com.varlanv.koper.test

import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class SerdeSamplesSpec : BaseSpec({
    should("render an exact shape with a reordered companion invoke") {
        val shape = SerdeSamples.Shape(
            name = "Sample",
            kind = SerdeSamples.Kind.DATA_CLASS,
            parameters = listOf(
                SerdeSamples.Parameter(
                    "first",
                    Int::class,
                ),
                SerdeSamples.Parameter(
                    "second",
                    Long::class,
                ),
            ),
            constructorVisibility = SerdeSamples.Visibility.PRIVATE,
            companionInvoke = SerdeSamples.CompanionInvoke(
                parameters = listOf(
                    SerdeSamples.Parameter(
                        "second",
                        Long::class,
                    ),
                    SerdeSamples.Parameter(
                        "first",
                        Int::class,
                    ),
                ),
            ),
        )

        SerdeSamples.buildSerdeSample(shape).allocateString() shouldBe """
            @com.varlanv.koper.serde.Ser
            @com.varlanv.koper.serde.De
            data class Sample private constructor(val first: kotlin.Int, val second: kotlin.Long) {
                companion object {
                    operator fun invoke(second: kotlin.Long, first: kotlin.Int): Sample = Sample(first = first, second = second)
                }
            }

            """
            .trimIndent()
    }

    should("vary only requested matrix options and reuse the byte buffer") {
        val shapes = mutableListOf<SerdeSamples.Shape>()
        val sources = mutableListOf<String>()
        val buffers = mutableSetOf<ByteArray>()

        SerdeSamples.buildSerdeSamplesMatrix(
            types = setOf(
                Int::class,
                Long::class,
            ),
            capabilities = SerdeSamples.Capabilities(
                kinds = setOf(
                    SerdeSamples.Kind.CLASS,
                    SerdeSamples.Kind.DATA_CLASS,
                ),
                constructorVisibilities = setOf(
                    SerdeSamples.Visibility.PUBLIC,
                    SerdeSamples.Visibility.PRIVATE,
                ),
                companionInvokes = setOf(
                    null,
                    SerdeSamples.CompanionInvoke(),
                ),
            ),
            matrixSettings = SerdeSamples.MatrixSettings(includeMixOfArgumentsPosition = true),
        ) { shape, source ->
            shapes += shape
            sources += source.allocateString()
            buffers += source.bytes.unsafeBorrowArray()
        }

        shapes.size shouldBe 16
        shapes.map { it.name }.distinct().size shouldBe 16
        shapes.map { it.parameters.map { parameter -> parameter.type } }.distinct().size shouldBe 2
        shapes.map { it.kind }.toSet() shouldBe setOf(SerdeSamples.Kind.CLASS, SerdeSamples.Kind.DATA_CLASS)
        buffers.size shouldBe 1
        sources.all { it.contains("@com.varlanv.koper.serde.Ser") } shouldBe true
    }

    should("fail when one fixture exceeds the fixed buffer and recover for the next fixture") {
        shouldThrow<IllegalStateException> {
            SerdeSamples.buildSerdeSample(
                SerdeSamples.Shape(
                    name = "Oversized",
                    parameters = listOf(
                        SerdeSamples.Parameter(
                            "value",
                            "X".repeat(10_240),
                        ),
                    ),
                ),
            )
        }.message shouldBe "Fixture source exceeds the 10 KiB sample buffer"

        SerdeSamples
            .buildSerdeSample(SerdeSamples.Shape(name = "Small", kind = SerdeSamples.Kind.OBJECT))
            .allocateString() shouldBe "@com.varlanv.koper.serde.Ser\n@com.varlanv.koper.serde.De\nobject Small\n"

        SerdeSamples
            .buildSerdeSample(
                SerdeSamples.Shape(
                    name = "Éclair",
                    kind = SerdeSamples.Kind.OBJECT,
                    annotations = SerdeSamples.Annotations(
                        includeSer = false,
                        includeDe = false,
                    ),
                ),
            )
            .allocateString() shouldBe "object Éclair\n"
    }
})
