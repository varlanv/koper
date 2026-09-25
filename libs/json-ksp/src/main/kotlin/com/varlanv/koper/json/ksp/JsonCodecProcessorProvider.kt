package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

class JsonCodecProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor = JsonCodecProcessor(codeGenerator = environment.codeGenerator, logger = environment.logger)
}

private class JsonCodecProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        return listOf()
        //        val symbols = sequenceOf(
        //            resolver.getSymbolsWithAnnotation(annotationName = "com.varlanv.koper.serde.Ser"),
        //            resolver.getSymbolsWithAnnotation(annotationName = "com.varlanv.koper.serde.De"),
        //        ).flatten().distinctBy { (it as? KSClassDeclaration)?.qualifiedName?.asString() ?: it.toString() }
        //        val deferred = mutableListOf<KSAnnotated>()
        //        for (symbol in symbols) {
        //            if (!symbol.validate()) {
        //                deferred += symbol
        //                continue
        //            }
        //            val declaration = symbol as? KSClassDeclaration
        //            if (declaration == null) {
        //                logger.error(message = "@Ser and @De require a class", symbol = symbol)
        //                continue
        //            }
        //            val qualifiedName = declaration.qualifiedName?.asString() ?: continue
        //            if (!generated.add(qualifiedName)) {
        //                continue
        //            }
        //            val annotations = declaration.annotations
        //                .mapNotNull {
        //                    it.annotationType.resolve().declaration.qualifiedName?.asString()
        //                }
        //                .toSet()
        //            val ser = "com.varlanv.koper.serde.Ser" in annotations
        //            val de = "com.varlanv.koper.serde.De" in annotations
        //            val field = declaration.primaryConstructor?.parameters?.singleOrNull()
        //            val fieldName = field?.name?.asString()
        //            if (declaration.classKind != ClassKind.CLASS ||
        //                declaration.typeParameters.isNotEmpty() ||
        //                field == null ||
        //                (!field.isVal && !field.isVar) ||
        //                field.type.resolve().declaration.qualifiedName?.asString() != "kotlin.Int" ||
        //                fieldName == null ||
        //                !Regex("[A-Za-z_][A-Za-z_0-9]*").matches(fieldName) ||
        //                declaration.containingFile == null
        //            ) {
        //                logger.error(
        //                    message = "V1 JSON generation requires a class with one Int constructor property",
        //                    symbol = declaration,
        //                )
        //                continue
        //            }
        //            val packageName = declaration.packageName.asString()
        //            val typeName = declaration.simpleName.asString()
        //            val codecName = typeName + "GeneratedJsonCodec"
        //            val source = render(
        //                packageName = packageName,
        //                typeName = typeName,
        //                codecName = codecName,
        //                fieldName = fieldName,
        //                ser = ser,
        //                de = de,
        //            )
        //            codeGenerator
        //                .createNewFile(
        //                    dependencies = Dependencies(
        //                        aggregating = false,
        //                        declaration.containingFile!!,
        //                    ),
        //                    packageName = packageName,
        //                    fileName = codecName,
        //                )
        //                .use { it.write(source.encodeToByteArray()) }
        //        }
        //        return deferred
    }

    private fun render(
        packageName: String,
        typeName: String,
        codecName: String,
        fieldName: String,
        ser: Boolean,
        de: Boolean,
    ): String = buildString {
        appendLine("package " + packageName)
        appendLine()
        appendLine("import com.varlanv.koper.json.IdealJsonReader")
        appendLine("import com.varlanv.koper.json.IdealJsonWriter")
        appendLine("import com.varlanv.koper.json.JsonInput")
        appendLine("import com.varlanv.koper.json.JsonOutput")
        appendLine()
        appendLine("object " + codecName + " {")
        if (ser || de) {
            appendLine("    private val fieldName = \"" + fieldName + "\".encodeToByteArray()")
        }
        if (ser) {
            appendLine()
            appendLine("    fun write(writer: IdealJsonWriter, value: " + typeName + ", output: JsonOutput) {")
            appendLine("        writer.reset(output)")
            appendLine("        writer.writeByte('{'.code)")
            appendLine("        writer.writeByte('\"'.code)")
            appendLine("        writer.writeRaw(fieldName)")
            appendLine("        writer.writeByte('\"'.code)")
            appendLine("        writer.writeByte(':'.code)")
            appendLine("        writer.writeInt(value." + fieldName + ")")
            appendLine("        writer.writeByte('}'.code)")
            appendLine("        writer.flush()")
            appendLine("    }")
        }
        if (de) {
            appendLine()
            appendLine("    fun read(reader: IdealJsonReader, input: JsonInput): " + typeName + " {")
            appendLine("        reader.reset(input)")
            appendLine("        require(reader.nextToken() == '{'.code) { \"Expected JSON object\" }")
            appendLine("        var fieldValue = 0")
            appendLine("        var seen = false")
            appendLine("        var token = reader.nextToken()")
            appendLine("        if (token != '}'.code) {")
            appendLine("            while (true) {")
            appendLine("                require(token == '\"'.code) { \"Expected JSON field name\" }")
            appendLine("                reader.readField()")
            appendLine("                reader.nextFieldValue()")
            appendLine("                if (reader.fieldEquals(fieldName)) {")
            appendLine("                    fieldValue = reader.readInt()")
            appendLine("                    seen = true")
            appendLine("                } else {")
            appendLine("                    reader.skipValue()")
            appendLine("                }")
            appendLine("                token = reader.nextToken()")
            appendLine("                if (token == '}'.code) break")
            appendLine("                require(token == ','.code) { \"Expected comma or closing brace\" }")
            appendLine("                token = reader.nextToken()")
            appendLine("            }")
            appendLine("        }")
            appendLine("        require(seen) { \"Missing required JSON field\" }")
            appendLine("        require(reader.nextToken() == -1) { \"Unexpected trailing JSON content\" }")
            appendLine("        return " + typeName + "(fieldValue)")
            appendLine("    }")
        }
        appendLine("}")
    }
}
