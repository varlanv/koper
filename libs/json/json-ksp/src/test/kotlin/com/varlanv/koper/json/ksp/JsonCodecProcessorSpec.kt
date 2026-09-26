package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.varlanv.koper.serde.Ser
import com.varlanv.koper.test.SerdeSamples
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class JsonCodecProcessorSpec : BaseSpec({
    should("report invalid annotated declarations") {
        val root = Files.createTempDirectory("json-ksp-invalid-").toFile()
        try {
            val input = root.resolve("input/InvalidFixtures.kt")
            input.parentFile.mkdirs()
            input.outputStream().use { output ->
                fun write(shape: SerdeSamples.Shape) {
                    val source = SerdeSamples.buildSerdeSample(shape).bytes
                    output.write(source.unsafeBorrowArray(), source.offset, source.len)
                }

                fun privateClass(name: String, invoke: SerdeSamples.CompanionInvoke? = null) = SerdeSamples.Shape(
                    name = name,
                    parameters = listOf(SerdeSamples.Parameter("value", Int::class)),
                    constructorVisibility = SerdeSamples.Visibility.PRIVATE,
                    companionInvoke = invoke,
                )

                output.write("@com.varlanv.koper.serde.Ser interface InterfaceOnly\n".encodeToByteArray())
                output.write("@com.varlanv.koper.serde.Ser class SecondaryOnly { constructor(value: kotlin.Int) }\n".encodeToByteArray())
                write(privateClass("PrivateNoCompanion"))
                write(privateClass("InternalNoCompanion").copy(constructorVisibility = SerdeSamples.Visibility.INTERNAL))
                write(privateClass("NonOperatorInvoke", SerdeSamples.CompanionInvoke(isOperator = false)))
                write(privateClass("WrongReturn", SerdeSamples.CompanionInvoke(returnType = "kotlin.Int")))
                write(privateClass("NullableReturn", SerdeSamples.CompanionInvoke(returnType = "NullableReturn?")))
                write(privateClass("WrongName", SerdeSamples.CompanionInvoke(parameters = listOf(SerdeSamples.Parameter("other", Int::class)))))
                write(privateClass("WrongType", SerdeSamples.CompanionInvoke(parameters = listOf(SerdeSamples.Parameter("value", Long::class)))))
                write(privateClass("WrongCount", SerdeSamples.CompanionInvoke(parameters = emptyList())))
            }

            val logger = RecordingLogger()
            val output = root.resolve("output")
            val version = "${KotlinVersion.CURRENT.major}.${KotlinVersion.CURRENT.minor}"
            val classpath = listOf(Ser::class.java, Unit::class.java)
                .map { File(it.protectionDomain.codeSource.location.toURI()) }
                .distinct()
            val config = KSPJvmConfig.Builder().apply {
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
            }.build()

            KotlinSymbolProcessing(config, listOf(JsonCodecProcessorProvider()), logger).execute() shouldBe
                KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR

            logger.errors.toMap() shouldBe mapOf(
                "InterfaceOnly" to "@Ser and @De require a primary constructor",
                "SecondaryOnly" to "@Ser and @De require a primary constructor",
                "PrivateNoCompanion" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "InternalNoCompanion" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "NonOperatorInvoke" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "WrongReturn" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "NullableReturn" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "WrongName" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "WrongType" to "A non-public primary constructor requires a matching companion operator fun invoke",
                "WrongCount" to "A non-public primary constructor requires a matching companion operator fun invoke",
            )
            logger.errors.size shouldBe 10
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
