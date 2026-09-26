package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.validate

const val serQualifiedName = "com.varlanv.koper.serde.Ser"
const val deQualifiedPath = "com.varlanv.koper.serde.De"

class JsonCodecProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor = JsonCodecProcessor(codeGenerator = environment.codeGenerator, logger = environment.logger)
}

private data class ResolvedAnnotations(val isSer: Boolean, val isDe: Boolean) {
    fun hasSerde(): Boolean = isSer || isDe
}

private enum class FieldCodec(
    val typeName: String,
    val writeMethod: String,
    val readMethod: String,
    val fixedMaximumValueBytes: Int?,
) {
    INT("kotlin.Int", "writeInt", "readInt", 11),
    LONG("kotlin.Long", "writeLong", "readLong", 20),
    BOOLEAN("kotlin.Boolean", "writeBoolean", "readBoolean", 5),
    STRING("kotlin.String", "writeString", "readString", null),
    UTF8_STRING("com.varlanv.koper.lang.text.Utf8Str", "writeUtf8", "readUtf8", null),
}

private val fieldCodecs = FieldCodec.entries.associateBy(FieldCodec::typeName)

private data class ResolvedField(
    val name: String,
    val parameter: KSValueParameter,
    val property: KSPropertyDeclaration?,
    val codec: FieldCodec,
)

private class JsonCodecProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = sequenceOf(
            resolver.getSymbolsWithAnnotation(annotationName = serQualifiedName),
            resolver.getSymbolsWithAnnotation(annotationName = deQualifiedPath),
        ).flatten().distinctBy { (it as? KSClassDeclaration)?.qualifiedName?.asString() ?: it.toString() }
        val deferred = mutableListOf<KSAnnotated>()
        for (symbol in symbols) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            val declaration = symbol as? KSClassDeclaration
            if (declaration == null) {
                logger.error(message = "@Ser and @De require a class", symbol = symbol)
                continue
            }
            val qualifiedName = declaration.qualifiedName?.asString() ?: continue
            if (!generated.add(qualifiedName)) {
                continue
            }
            val resolvedAnnotations = resolveAnnotations(declaration)
            if (!resolvedAnnotations.hasSerde()) {
                continue
            }
            val primaryConstructor = declaration.primaryConstructor
            if (primaryConstructor == null) {
                logger.error(message = "@Ser and @De require a primary constructor", symbol = declaration)
                continue
            }
            val constructorArgs = primaryConstructor.parameters
            val constructorOrInvoke = if (primaryConstructor.getVisibility() == Visibility.PUBLIC) {
                primaryConstructor
            } else {
                declaration.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .firstOrNull { it.isCompanionObject }
                    ?.declarations
                    ?.filterIsInstance<KSFunctionDeclaration>()
                    ?.firstOrNull { function ->
                        function.simpleName.asString() == "invoke" &&
                            Modifier.OPERATOR in function.modifiers &&
                            function.returnType?.resolve()?.let { type ->
                                type.declaration == declaration && !type.isMarkedNullable
                            } == true &&
                            function.parameters.size == constructorArgs.size &&
                            constructorArgs.all { constructorArg ->
                                function.parameters.any { invokeArg ->
                                    invokeArg.name?.asString() == constructorArg.name?.asString() &&
                                        invokeArg.type.resolve() == constructorArg.type.resolve()
                                }
                            }
                    }
            }
            if (constructorOrInvoke == null) {
                logger.error(
                    message = "A non-public primary constructor requires a matching companion operator fun invoke",
                    symbol = declaration,
                )
                continue
            }
            if (resolveFields(
                declaration = declaration,
                parameters = constructorArgs,
                annotations = resolvedAnnotations,
            ) == null) {
                continue
            }
        }
        return listOf()
    }

    private fun resolveAnnotations(declaration: KSClassDeclaration): ResolvedAnnotations {
        var ser = false
        var de = false
        for (ann in declaration.annotations) {
            val qualifiedName = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            if (!ser && qualifiedName == serQualifiedName) {
                ser = true
            }
            if (!de && qualifiedName == deQualifiedPath) {
                de = true
            }
            if (ser && de) {
                break
            }
        }
        return ResolvedAnnotations(isSer = ser, isDe = de)
    }

    private fun resolveFields(
        declaration: KSClassDeclaration,
        parameters: List<KSValueParameter>,
        annotations: ResolvedAnnotations,
    ): List<ResolvedField>? {
        val properties = if (annotations.isSer) {
            declaration.getDeclaredProperties().associateBy { it.simpleName.asString() }
        } else {
            emptyMap()
        }
        val fields = ArrayList<ResolvedField>(parameters.size)
        for (parameter in parameters) {
            val name = parameter.name?.asString()
            if (name == null) {
                logger.error(message = "JSON codec requires named primary constructor parameters", symbol = declaration)
                return null
            }
            if (parameter.isVararg) {
                logger.error(message = "JSON codec does not support vararg field '$name'", symbol = declaration)
                return null
            }
            val type = parameter.type.resolve()
            if (type.isMarkedNullable) {
                logger.error(
                    message = "JSON codec does not support nullable field '$name' (${type})",
                    symbol = declaration,
                )
                return null
            }
            val typeName = type.declaration.qualifiedName?.asString()
            val codec = fieldCodecs[typeName]
            if (codec == null) {
                logger.error(
                    message = "JSON codec does not support field '$name' of type '${typeName ?: type}'",
                    symbol = declaration,
                )
                return null
            }
            val property = properties[name]
            if (annotations.isSer) {
                if (property == null || property.extensionReceiver != null) {
                    logger.error(
                        message = "@Ser requires a readable property '$name' matching its primary constructor parameter",
                        symbol = declaration,
                    )
                    return null
                }
                if (property.type.resolve() != type) {
                    logger.error(
                        message = "@Ser property '$name' must have the same type as its primary constructor parameter",
                        symbol = declaration,
                    )
                    return null
                }
                val visibility = property.getVisibility()
                if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
                    logger.error(message = "@Ser property '$name' must be public or internal", symbol = declaration)
                    return null
                }
            }
            fields += ResolvedField(name = name, parameter = parameter, property = property, codec = codec)
        }
        return fields
    }
}
