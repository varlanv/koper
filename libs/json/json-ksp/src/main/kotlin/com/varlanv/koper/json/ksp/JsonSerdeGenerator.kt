package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import com.varlanv.koper.serde.ksp.model.SerdeClassShape
import com.varlanv.koper.serde.ksp.model.SerdeGenerator

private enum class FieldType(
    val codec: String,
    val fixedMaximum: Int,
    val boxedByGeneric: Boolean,
) {
    INT("IntJsonCodec", 11, true),
    LONG("LongJsonCodec", 20, true),
    BOOLEAN("BooleanJsonCodec", 5, true),
    STRING("StringJsonCodec", 2, false),
    UTF8STR("Utf8StrJsonCodec", 2, true),
}

private val knownFieldTypes = mapOf(
    "kotlin.Int" to FieldType.INT,
    "kotlin.Long" to FieldType.LONG,
    "kotlin.Boolean" to FieldType.BOOLEAN,
    "kotlin.String" to FieldType.STRING,
    "com.varlanv.koper.lang.text.Utf8Str" to FieldType.UTF8STR,
)

private data class JsonField(
    val name: String,
    val type: FieldType,
    val nameBytes: ByteArray,
    val prefix: ByteArray,
    val hash: Int,
) {
    val fastMatchable: Boolean = nameBytes.none { it.toInt() == 34 || it.toInt() == 92 || it.toInt() in 0..31 }
}

class JsonSerdeGenerator : SerdeGenerator {
    override fun generate(shape: SerdeClassShape, environment: SymbolProcessorEnvironment) {
        val declaration = shape.declaration
        if (declaration.parentDeclaration != null || declaration.classKind != ClassKind.CLASS) {
            environment.logger.error(message = "JSON codec requires a top-level class", symbol = declaration)
            return
        }
        if (declaration.typeParameters.isNotEmpty()) {
            environment.logger.error(message = "JSON codec does not support generic classes", symbol = declaration)
            return
        }
        if (Modifier.ABSTRACT in declaration.modifiers) {
            environment.logger.error(message = "JSON codec does not support abstract classes", symbol = declaration)
            return
        }
        val visibility = declaration.getVisibility()
        if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
            environment.logger.error(message = "JSON codec requires a public or internal class", symbol = declaration)
            return
        }
        if (shape.constructorOrFactory != shape.constructor) {
            val factoryVisibility = shape.constructorOrFactory.getVisibility()
            if (factoryVisibility != Visibility.PUBLIC && factoryVisibility != Visibility.INTERNAL) {
                environment.logger.error(
                    message = "JSON codec requires a public or internal companion factory",
                    symbol = declaration,
                )
                return
            }
        }

        val fields = ArrayList<JsonField>(shape.fields.size)
        for ((index, field) in shape.fields.withIndex()) {
            val name = field.name
            if (field.parameter.isVararg) {
                environment.logger.error(
                    message = "JSON codec does not support vararg field '$name'",
                    symbol = declaration,
                )
                return
            }
            val type = field.type
            if (type.isMarkedNullable) {
                environment.logger.error(
                    message = "JSON codec does not support nullable field '$name' ($type)",
                    symbol = declaration,
                )
                return
            }
            val typeName = type.declaration.qualifiedName?.asString()
            val fieldType = knownFieldTypes[typeName]
            if (fieldType == null) {
                environment.logger.error(
                    message = "JSON codec does not support field '$name' of type '${typeName ?: type}'",
                    symbol = declaration,
                )
                return
            }
            val nameBytes = name.encodeToByteArray()
            fields += JsonField(
                name = name,
                type = fieldType,
                nameBytes = nameBytes,
                prefix = jsonFieldPrefix(
                    name = name,
                    first = index == 0,
                ),
                hash = nameBytes.fold(0) { hash, byte -> 31 * hash + (byte.toInt() and 255) },
            )
        }

        val sourceFile = declaration.containingFile ?: return
        val packageName = declaration.packageName.asString()
        val className = declaration.simpleName.asString()
        val codecName = "${className}JsonCodec"
        val source = generateSource(
            shape = shape,
            fields = fields,
            packageName = packageName,
            className = className,
            codecName = codecName,
            visibility = visibility,
        )
        environment.codeGenerator
            .createNewFile(
                dependencies = Dependencies(
                    aggregating = false,
                    sourceFile,
                ),
                packageName = packageName,
                fileName = codecName,
            )
            .bufferedWriter(Charsets.UTF_8)
            .use { it.write(source) }
    }
}

private fun generateSource(
    shape: SerdeClassShape,
    fields: List<JsonField>,
    packageName: String,
    className: String,
    codecName: String,
    visibility: Visibility,
): String = buildString {
    val classType = if (packageName.isEmpty()) {
        identifier(className)
    } else {
        "$packageName.${identifier(className)}"
    }
    val codecType = "com.varlanv.koper.json.JsonCodec"
    val jsonType = "com.varlanv.koper.json"
    val binType = "com.varlanv.koper.lang.bin"
    val readProtocol = "$jsonType.JsonReadProtocol"
    val writeProtocol = "$jsonType.JsonWriteProtocol"
    val write = shape.annotations.isSer
    val read = shape.annotations.isDe
    val interfaces = buildList {
        if (write) {
            add("$codecType.Write<$classType>")
        }
        if (read) {
            add("$codecType.Read<$classType>")
        }
    }.joinToString(", ")
    val fixedMaximum = 1L + fields.sumOf { it.prefix.size.toLong() + it.type.fixedMaximum } + if (fields.isEmpty()) {
        1L
    } else {
        0L
    }
    val variableFields = fields.filter { it.type == FieldType.STRING || it.type == FieldType.UTF8STR }
    val fastFields = fields.withIndex().filter { it.value.fastMatchable }

    fun appendFieldRead(index: Int, indent: String) {
        val field = fields[index]
        val method = if (field.type.boxedByGeneric) {
            "readPrimitive"
        } else {
            "read"
        }
        appendLine("${indent}_field$index = $jsonType.${field.type.codec}.$method()")
        appendLine("${indent}_seen${index / 32} = _seen${index / 32} or (1 shl ${index % 32})")
    }

    if (packageName.isNotEmpty()) {
        appendLine("package $packageName")
        appendLine()
    }
    appendLine(
        "${if (visibility == Visibility.INTERNAL) {
            "internal "
        } else {
            ""
        }}object ${identifier(codecName)} : $interfaces {",
    )
    if (read) {
        fields.forEachIndexed { index, field ->
            appendLine(
                "    private val _fieldName$index = byteArrayOf(${field.nameBytes.joinToString { it.toString() }})",
            )
        }
        if (fields.isNotEmpty()) {
            appendLine()
        }
    }
    if (write) {
        val size = if (variableFields.isEmpty()) {
            "$jsonType.JsonValueSize.Static(${fixedMaximum}L)"
        } else {
            "$jsonType.JsonValueSize.FromValue<$classType> { value -> maximumBytes(value) }"
        }
        appendLine("    override val hints: $codecType.Hints<$classType> = $codecType.Hints($size)")
    } else {
        appendLine("    override val hints: $codecType.Hints<$classType> = $codecType.Hints()")
    }
    if (write) {
        appendLine()
        appendLine("    context(sink: $binType.ByteSink, writeScope: $jsonType.JsonWriteScope)")
        appendLine("    override fun write(value: $classType) {")
        appendLine(
            "        $writeProtocol.reserve(${if (variableFields.isEmpty()) {
                "${fixedMaximum}L"
            } else {
                "maximumBytes(value)"
            }})",
        )
        if (fields.isEmpty()) {
            appendLine("        $writeProtocol.writeByte(123)")
        }
        for (field in fields) {
            appendPackedWrites(bytes = field.prefix, indent = "        ", writeProtocol = writeProtocol)
            val method = if (field.type.boxedByGeneric) {
                "writePrimitive"
            } else {
                "write"
            }
            appendLine("        $jsonType.${field.type.codec}.$method(value.${identifier(field.name)})")
        }
        appendLine("        $writeProtocol.writeByte(125)")
        appendLine("    }")
        if (variableFields.isNotEmpty()) {
            appendLine()
            val extra = variableFields.joinToString(" + ") { field ->
                val size = if (field.type == FieldType.STRING) {
                    "value.${identifier(field.name)}.length"
                } else {
                    "value.${identifier(field.name)}.byteLen"
                }
                "$size.toLong() * 6L"
            }
            appendLine("    private fun maximumBytes(value: $classType): Long = ${fixedMaximum}L + $extra")
        }
    }
    if (read) {
        appendLine()
        appendLine("    context(input: $binType.ByteSource, parseScope: $jsonType.JsonReadScope)")
        appendLine("    override fun read(): $classType {")
        appendLine("        require(parseScope.last == 123) { \"Expected JSON object\" }")
        fields.forEachIndexed { index, field ->
            val initial = when (field.type) {
                FieldType.INT -> "Int = 0"
                FieldType.LONG -> "Long = 0L"
                FieldType.BOOLEAN -> "Boolean = false"
                FieldType.STRING -> "String? = null"
                FieldType.UTF8STR -> "com.varlanv.koper.lang.text.Utf8Str? = null"
            }
            appendLine("        var _field$index: $initial")
        }
        val groupCount = (fields.size + 31) / 32
        repeat(groupCount) { appendLine("        var _seen$it = 0") }
        appendLine("        var _token = $readProtocol.nextToken()")
        appendLine("        if (_token != 125) {")
        appendLine("            while (true) {")
        appendLine("                require(_token == 34) { \"Expected JSON field name\" }")
        if (fastFields.isNotEmpty()) {
            appendLine("                val _word = $readProtocol.peekFieldWord()")
            if (fastFields.any { it.value.nameBytes.size > 2 }) {
                appendLine("                val _wordTail = $readProtocol.peekFieldWord(4)")
            }
        }
        appendLine("                when {")
        fastFields.forEach { (index, field) ->
            val nameLength = field.nameBytes.size
            val compactMatch = if (nameLength <= 6) {
                packedWordMatch(field.nameBytes + byteArrayOf(34, 58))
            } else if (nameLength == 7) {
                "${packedWordMatch(field.nameBytes + byteArrayOf(34))} && $readProtocol.consumeFieldColon($nameLength)"
            } else if (nameLength == 8) {
                "${packedWordMatch(field.nameBytes)} && $readProtocol.consumeFieldColon($nameLength)"
            } else {
                "${packedWordMatch(field.nameBytes.copyOfRange(0, 8))} && " +
                    "$readProtocol.fieldMatches(_fieldName$index) && $readProtocol.consumeFieldColon($nameLength)"
            }
            appendLine("                    $compactMatch -> {")
            if (nameLength <= 6) {
                appendLine("                        $readProtocol.consumeMatchedFieldColon($nameLength)")
            }
            appendFieldRead(index = index, indent = "                        ")
            appendLine("                    }")
        }
        fastFields.forEach { (index, field) ->
            val nameLength = field.nameBytes.size
            val nameMatch = if (nameLength <= 7) {
                packedWordMatch(field.nameBytes + byteArrayOf(34))
            } else if (nameLength == 8) {
                packedWordMatch(field.nameBytes)
            } else {
                "${packedWordMatch(field.nameBytes.copyOfRange(0, 8))} && $readProtocol.fieldMatches(_fieldName$index)"
            }
            appendLine("                    $nameMatch && $readProtocol.consumeField($nameLength) -> {")
            appendLine("                        $readProtocol.nextFieldValue()")
            appendFieldRead(index = index, indent = "                        ")
            appendLine("                    }")
        }
        appendLine("                    else -> {")
        appendLine("                        val _hash = $readProtocol.readField()")
        appendLine("                        when (_hash) {")
        fields.forEachIndexed { index, field ->
            appendLine("                            ${field.hash} if $readProtocol.fieldEquals(_fieldName$index) -> {")
            appendLine("                                $readProtocol.nextFieldValue()")
            appendFieldRead(index = index, indent = "                                ")
            appendLine("                            }")
        }
        appendLine("                            else -> {")
        appendLine("                                $readProtocol.nextFieldValue()")
        appendLine("                                $readProtocol.skipValue()")
        appendLine("                            }")
        appendLine("                        }")
        appendLine("                    }")
        appendLine("                }")
        appendLine("                _token = $readProtocol.nextFieldOrEnd()")
        appendLine("                if (_token == 125) break")
        appendLine("            }")
        appendLine("        }")
        repeat(groupCount) { group ->
            val bits = minOf(32, fields.size - group * 32)
            val expected = if (bits == 32) {
                -1
            } else {
                (1 shl bits) - 1
            }
            appendLine("        require(_seen$group == $expected) { \"Missing required JSON field\" }")
        }
        val constructor = if (shape.constructorOrFactory == shape.constructor) {
            classType
        } else {
            val companion = shape.constructorOrFactory.parentDeclaration?.simpleName?.asString() ?: "Companion"
            "$classType.${identifier(companion)}.invoke"
        }
        if (fields.isEmpty()) {
            appendLine("        return $constructor()")
        } else {
            appendLine("        return $constructor(")
            fields.forEachIndexed { index, field ->
                val value = if (field.type == FieldType.STRING || field.type == FieldType.UTF8STR) {
                    "_field$index ?: error(\"Missing required JSON field\")"
                } else {
                    "_field$index"
                }
                appendLine(
                    "            ${identifier(field.name)} = $value${if (index == fields.lastIndex) {
                        ""
                    } else {
                        ","
                    }}",
                )
            }
            appendLine("        )")
        }
        appendLine("    }")
    }
    appendLine("}")
}

private fun StringBuilder.appendPackedWrites(
    bytes: ByteArray,
    indent: String,
    writeProtocol: String,
) {
    var index = 0
    while (index < bytes.size) {
        val remaining = bytes.size - index
        when {
            remaining >= 12 -> {
                appendLine(
                    "${indent}$writeProtocol.writeRaw(first = ${packedInt(bytes = bytes, start = index)}, " +
                        "second = ${packedInt(
                            bytes = bytes,
                            start = index + 4,
                        )}, third = ${packedInt(bytes = bytes, start = index + 8)})",
                )
                index += 12
            }
            remaining >= 10 -> {
                appendLine(
                    "${indent}$writeProtocol.writeRaw(first = ${packedInt(bytes = bytes, start = index)}, " +
                        "second = ${packedInt(
                            bytes = bytes,
                            start = index + 4,
                        )}, third = ${packedShort(bytes = bytes, start = index + 8)})",
                )
                index += 10
            }
            remaining >= 8 -> {
                appendLine(
                    "${indent}$writeProtocol.writeRaw(first = ${packedInt(bytes = bytes, start = index)}, " +
                        "second = ${packedInt(bytes = bytes, start = index + 4)})",
                )
                index += 8
            }
            remaining >= 6 -> {
                appendLine(
                    "${indent}$writeProtocol.writeRaw(first = ${packedInt(bytes = bytes, start = index)}, " +
                        "second = ${packedShort(bytes = bytes, start = index + 4)})",
                )
                index += 6
            }
            remaining >= 4 -> {
                appendLine("${indent}$writeProtocol.writeRaw(value = ${packedInt(bytes = bytes, start = index)})")
                index += 4
            }
            remaining >= 2 -> {
                appendLine("${indent}$writeProtocol.writeRaw(value = ${packedShort(bytes = bytes, start = index)})")
                index += 2
            }
            else -> {
                appendLine("${indent}$writeProtocol.writeByte(${bytes[index].toInt() and 255})")
                index++
            }
        }
    }
}

private fun packedShort(bytes: ByteArray, start: Int): String =
    "${packed(bytes = bytes, start = start, count = 2)}.toShort()"

private fun packedInt(bytes: ByteArray, start: Int): String =
    "0x${packed(bytes = bytes, start = start, count = 4).toUInt().toString(16)}u.toInt()"

private fun packedWordMatch(bytes: ByteArray): String {
    fun match(
        word: String,
        start: Int,
        count: Int,
    ): String {
        val value = packed(bytes = bytes, start = start, count = count)
        return if (count == 4) {
            "$word == 0x${value.toUInt().toString(16)}u.toInt()"
        } else {
            val mask = (1 shl (count * 8)) - 1
            "($word and 0x${mask.toString(16)}) == 0x${value.toString(16)}"
        }
    }
    val first = match(
        word = "_word",
        start = 0,
        count = minOf(
            4,
            bytes.size,
        ),
    )
    return if (bytes.size <= 4) {
        first
    } else {
        "$first && ${match(word = "_wordTail", start = 4, count = bytes.size - 4)}"
    }
}

private fun packed(
    bytes: ByteArray,
    start: Int,
    count: Int,
): Int {
    var result = 0
    repeat(count) { result = result or ((bytes[start + it].toInt() and 255) shl (it * 8)) }
    return result
}

private fun identifier(value: String): String = "`$value`"

private fun jsonFieldPrefix(name: String, first: Boolean): ByteArray = buildString {
    append(
        if (first) {
            '{'
        } else {
            ','
        },
    )
    append('"')
    for (char in name) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append("\":")
}.encodeToByteArray()
