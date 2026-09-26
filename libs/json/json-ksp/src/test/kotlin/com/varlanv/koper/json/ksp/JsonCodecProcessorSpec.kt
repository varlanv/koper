package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.serde.Ser
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class JsonCodecProcessorSpec : BaseSpec({
    should("apply serde shape analysis and reject unsupported JSON fields") {
        val root = Files.createTempDirectory("json-ksp-invalid-").toFile()
        try {
            val input = root.resolve("input/InvalidFixtures.kt")
            input.parentFile.mkdirs()
            input.writeText(
                """
                    @com.varlanv.koper.serde.Ser interface InterfaceOnly
                    @com.varlanv.koper.serde.Ser class UnsupportedDouble(val value: kotlin.Double)
                    @com.varlanv.koper.serde.Ser class NullableInt(val value: kotlin.Int?)
                    @com.varlanv.koper.serde.Ser class VarargInts(vararg val value: kotlin.Int)
                    @com.varlanv.koper.serde.Ser class SupportedTypes(val i: kotlin.Int, val l: kotlin.Long, val b: kotlin.Boolean, val s: kotlin.String, val u: com.varlanv.koper.lang.text.Utf8Str)
                    """
                    .trimIndent(),
            )

            val logger = RecordingLogger()
            val output = root.resolve("output")
            val version = "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}"
            val classpath = listOf(
                Ser::class.java,
                Utf8Str::class.java,
                Unit::class.java,
            ).map { File(it.protectionDomain.codeSource.location.toURI()) }.distinct()
            val config = KSPJvmConfig
                .Builder()
                .apply {
                    moduleName = "json-ksp-invalid"
                    sourceRoots = listOf(input.parentFile)
                    libraries = classpath
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

            KotlinSymbolProcessing(
                kspConfig = config,
                symbolProcessorProviders = listOf(JsonCodecProcessorProvider()),
                logger = logger,
            ).execute() shouldBe KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR

            logger.errors.toMap() shouldBe
                mapOf(
                    "InterfaceOnly" to "@Ser and @De require a primary constructor",
                    "UnsupportedDouble" to "JSON codec does not support field 'value' of type 'kotlin.Double'",
                    "NullableInt" to "JSON codec does not support nullable field 'value' (Int?)",
                    "VarargInts" to "JSON codec does not support vararg field 'value'",
                )
            logger.errors.size shouldBe 4
        } finally {
            root.deleteRecursively()
        }
    }
})

private class RecordingLogger : KSPLogger {
    val errors = mutableListOf<Pair<String?, String>>()

    override fun logging(message: String, symbol: KSNode?) = Unit

    override fun info(message: String, symbol: KSNode?) = Unit

    override fun warn(message: String, symbol: KSNode?) = Unit

    override fun error(message: String, symbol: KSNode?) {
        errors += (symbol as? KSDeclaration)?.simpleName?.asString() to message
    }

    override fun exception(e: Throwable): Unit = throw e
}
