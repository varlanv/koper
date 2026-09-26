package com.varlanv.koper.serde.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSNode
import com.varlanv.koper.serde.Ser
import com.varlanv.koper.serde.ksp.model.SerdeClassShape
import com.varlanv.koper.serde.ksp.model.SerdeGenerator
import com.varlanv.koper.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class SerdeKspProcessorSpec : BaseSpec({
    should("warn and skip processing when no generators are configured") {
        val logger = RecordingProcessorLogger()
        processSource(
            source = "@com.varlanv.koper.serde.Ser interface InvalidShape",
            options = emptyMap(),
            logger = logger,
        ) shouldBe KotlinSymbolProcessing.ExitCode.OK
        logger.warnings shouldBe listOf("No serde generators configured; serde processing is disabled")
        logger.errors shouldBe emptyList()
    }

    should("fail when a configured generator cannot be loaded") {
        shouldThrow<IllegalStateException> {
            processSource(
                source = "@com.varlanv.koper.serde.Ser class Sample(val value: kotlin.Int)",
                options = mapOf(serdeGeneratorsOption to "missing.Generator"),
                logger = RecordingProcessorLogger(),
            )
        }.message shouldBe "Serde generator class 'missing.Generator' was not found"

        shouldThrow<IllegalStateException> {
            processSource(
                source = "@com.varlanv.koper.serde.Ser class Sample(val value: kotlin.Int)",
                options = mapOf(serdeGeneratorsOption to "java.lang.String"),
                logger = RecordingProcessorLogger(),
            )
        }.message shouldBe
            "Serde generator class 'java.lang.String' does not implement ${SerdeGenerator::class.qualifiedName}"
    }

    should("instantiate each configured generator once and dispatch every shape") {
        RecordingSerdeGenerator.constructions = 0
        RecordingSerdeGenerator.names.clear()
        SecondaryRecordingSerdeGenerator.names.clear()
        val name = RecordingSerdeGenerator::class.qualifiedName!!
        val secondaryName = SecondaryRecordingSerdeGenerator::class.qualifiedName!!
        val logger = RecordingProcessorLogger()
        processSource(
            source = """
                @com.varlanv.koper.serde.Ser class First(val value: kotlin.Int)
                @com.varlanv.koper.serde.De class Second(value: kotlin.Long)
                """
                .trimIndent(),
            options = mapOf(serdeGeneratorsOption to "$name, $name, $secondaryName"),
            logger = logger,
        ) shouldBe KotlinSymbolProcessing.ExitCode.OK
        RecordingSerdeGenerator.constructions shouldBe 1
        RecordingSerdeGenerator.names shouldBe listOf("First", "Second")
        SecondaryRecordingSerdeGenerator.names shouldBe listOf("First", "Second")
        logger.errors shouldBe emptyList()
    }
})

class RecordingSerdeGenerator : SerdeGenerator {
    init {
        constructions++
    }

    override fun generate(shape: SerdeClassShape, environment: SymbolProcessorEnvironment) {
        names += shape.declaration.simpleName.asString()
    }

    companion object {
        var constructions = 0
        val names = mutableListOf<String>()
    }
}

class SecondaryRecordingSerdeGenerator : SerdeGenerator {
    override fun generate(shape: SerdeClassShape, environment: SymbolProcessorEnvironment) {
        names += shape.declaration.simpleName.asString()
    }

    companion object {
        val names = mutableListOf<String>()
    }
}

private fun processSource(
    source: String,
    options: Map<String, String>,
    logger: RecordingProcessorLogger,
): KotlinSymbolProcessing.ExitCode {
    val root = Files.createTempDirectory("serde-ksp-processor-").toFile()
    try {
        val input = root.resolve("input/Fixtures.kt")
        input.parentFile.mkdirs()
        input.writeText(source)
        val output = root.resolve("output")
        val version = "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}"
        val classpath = listOf(
            Ser::class.java,
            Unit::class.java,
        ).map { File(it.protectionDomain.codeSource.location.toURI()) }.distinct()
        val config = KSPJvmConfig
            .Builder()
            .apply {
                moduleName = "serde-ksp-processor"
                sourceRoots = listOf(input.parentFile)
                libraries = classpath
                processorOptions = options
                jdkHome = File(System.getProperty("java.home"))
                jvmTarget = Runtime.version().feature().toString()
                languageVersion = version
                apiVersion = version
                projectBaseDir = root
                outputBaseDir = output
                cachesDir = output.resolve("cache")
                classOutputDir = output.resolve("classes")
                kotlinOutputDir = output.resolve("kotlin")
                javaOutputDir = output.resolve("java")
                resourceOutputDir = output.resolve("resources")
            }
            .build()
        return KotlinSymbolProcessing(
            kspConfig = config,
            symbolProcessorProviders = listOf(SerdeKspProcessorProvider()),
            logger = logger,
        ).execute()
    } finally {
        root.deleteRecursively()
    }
}

private class RecordingProcessorLogger : KSPLogger {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun logging(message: String, symbol: KSNode?) = Unit

    override fun info(message: String, symbol: KSNode?) = Unit

    override fun warn(message: String, symbol: KSNode?) {
        warnings += message
    }

    override fun error(message: String, symbol: KSNode?) {
        errors += message
    }

    override fun exception(e: Throwable): Unit = throw e
}
