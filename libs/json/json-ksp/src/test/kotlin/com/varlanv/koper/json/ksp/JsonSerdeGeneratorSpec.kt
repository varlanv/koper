package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.varlanv.koper.lang.text.Utf8Str
import com.varlanv.koper.serde.Ser
import com.varlanv.koper.serde.ksp.SerdeKspProcessorProvider
import com.varlanv.koper.serde.ksp.serdeGeneratorsOption
import com.varlanv.koper.testing.BaseSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class JsonSerdeGeneratorSpec : BaseSpec({
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
                    @com.varlanv.koper.serde.Ser class GenericType<T>(val value: kotlin.Int)
                    @com.varlanv.koper.serde.Ser abstract class AbstractType(val value: kotlin.Int)
                    class Outer { @com.varlanv.koper.serde.Ser class Nested(val value: kotlin.Int) }
                    @com.varlanv.koper.serde.Ser private class PrivateType(val value: kotlin.Int)
                    @com.varlanv.koper.serde.Ser class WriteOnly(val value: kotlin.Int)
                    @com.varlanv.koper.serde.Ser class Empty
                    @com.varlanv.koper.serde.Ser class LongPrefix(val abcdefghijklmnopqrstuvwx: kotlin.Int)
                    @com.varlanv.koper.serde.De class ReadOnly(value: kotlin.Int)
                    @com.varlanv.koper.serde.Ser @com.varlanv.koper.serde.De class EscapedName(val `a"b`: kotlin.Int)
                    """
                    .trimIndent() +
                    "\n@com.varlanv.koper.serde.Ser @com.varlanv.koper.serde.De class ManyFields(" +
                    (0..64).joinToString { "val field$it: kotlin.Int" } +
                    ")",
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
                    processorOptions = mapOf(serdeGeneratorsOption to JsonSerdeGenerator::class.qualifiedName!!)
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
                symbolProcessorProviders = listOf(SerdeKspProcessorProvider()),
                logger = logger,
            ).execute() shouldBe KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR

            logger.errors.toMap() shouldBe
                mapOf(
                    "InterfaceOnly" to "@Ser and @De require a primary constructor",
                    "UnsupportedDouble" to "JSON codec does not support field 'value' of type 'kotlin.Double'",
                    "NullableInt" to "JSON codec does not support nullable field 'value' (Int?)",
                    "VarargInts" to "JSON codec does not support vararg field 'value'",
                    "GenericType" to "JSON codec does not support generic classes",
                    "AbstractType" to "JSON codec does not support abstract classes",
                    "Nested" to "JSON codec requires a top-level class",
                    "PrivateType" to "JSON codec requires a public or internal class",
                )
            logger.errors.size shouldBe 8

            val writeOnly = output.resolve("kotlin/WriteOnlyJsonCodec.kt").readText()
            val readOnly = output.resolve("kotlin/ReadOnlyJsonCodec.kt").readText()
            writeOnly.contains("JsonCodec.Write<") shouldBe true
            writeOnly.contains("JsonCodec.Read<") shouldBe false
            writeOnly.contains("override fun write(value: `WriteOnly`, position: Int): Int") shouldBe true
            writeOnly.contains(
                "pos = com.varlanv.koper.json.IntJsonCodec.writePrimitive(value.`value`, position = pos)",
            ) shouldBe true
            writeOnly.contains("writeScope.position") shouldBe false
            writeOnly.contains("return pos + 1") shouldBe true
            val empty = output.resolve("kotlin/EmptyJsonCodec.kt").readText()
            empty.contains("reserve(size = 2, position = position)") shouldBe true
            empty.contains("writeByte(123, position = pos)") shouldBe true
            empty.contains("pos++") shouldBe true
            empty.contains("writeByte(125, position = pos)") shouldBe true
            val longPrefix = output.resolve("kotlin/LongPrefixJsonCodec.kt").readText()
            longPrefix.contains("position = pos + 12") shouldBe true
            longPrefix.contains("position = pos + 24") shouldBe true
            longPrefix.contains("pos += 28") shouldBe true
            readOnly.contains("JsonCodec.Read<") shouldBe true
            readOnly.contains("JsonCodec.Write<") shouldBe false
            val manyFields = output.resolve("kotlin/ManyFieldsJsonCodec.kt").readText()
            manyFields.contains("var _seen0 = 0") shouldBe true
            manyFields.contains("var _seen1 = 0") shouldBe true
            manyFields.contains("var _seen2 = 0") shouldBe true
            manyFields.contains("_seen0 = _seen0 or (1 shl 31)") shouldBe true
            manyFields.contains("_seen1 = _seen1 or (1 shl 31)") shouldBe true
            manyFields.contains("_seen2 = _seen2 or (1 shl 0)") shouldBe true
            manyFields.contains("require(_seen0 == -1)") shouldBe true
            manyFields.contains("require(_seen1 == -1)") shouldBe true
            manyFields.contains("require(_seen2 == 1)") shouldBe true
            manyFields.contains("peekFieldWordTail()") shouldBe true
            manyFields.contains("jsonFieldWordMatches(") shouldBe true
            for (source in listOf(writeOnly, readOnly, manyFields)) {
                source.contains("uL.toLong()") shouldBe false
                source.contains("1L shl") shouldBe false
                Regex("[0-9]L\\b").containsMatchIn(source) shouldBe false
            }
            val supportedTypes = output.resolve("kotlin/SupportedTypesJsonCodec.kt").readText()
            supportedTypes.contains("val length0 = value.`s`.length") shouldBe true
            supportedTypes.contains("val length1 = value.`u`.byteLen") shouldBe true
            val escapedNameSource = output.resolve("kotlin/EscapedNameJsonCodec.kt").readText()
            escapedNameSource.contains("byteArrayOf(97, 34, 98)") shouldBe true
            escapedNameSource.contains("reader.consumeMatchedFieldColon") shouldBe false
            escapedNameSource.contains("reader.fieldMatches") shouldBe false
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
