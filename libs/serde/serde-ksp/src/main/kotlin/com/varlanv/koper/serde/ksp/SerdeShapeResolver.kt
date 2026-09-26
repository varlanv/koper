package com.varlanv.koper.serde.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

const val serQualifiedName = "com.varlanv.koper.serde.Ser"
const val deQualifiedName = "com.varlanv.koper.serde.De"

fun Resolver.getSerdeSymbols(): Sequence<KSAnnotated> = sequenceOf(
    getSymbolsWithAnnotation(annotationName = serQualifiedName),
    getSymbolsWithAnnotation(annotationName = deQualifiedName),
).flatten().distinctBy { (it as? KSClassDeclaration)?.qualifiedName?.asString() ?: it.toString() }

data class SerdeAnnotations(val isSer: Boolean, val isDe: Boolean)

data class SerdeField(
    val name: String,
    val parameter: KSValueParameter,
    val property: KSPropertyDeclaration?,
    val type: KSType,
)

data class SerdeClassShape(
    val declaration: KSClassDeclaration,
    val annotations: SerdeAnnotations,
    val constructor: KSFunctionDeclaration,
    val constructorOrFactory: KSFunctionDeclaration,
    val fields: List<SerdeField>,
)

class SerdeShapeResolver(private val logger: KSPLogger) {
    fun resolve(symbol: KSAnnotated): SerdeClassShape? {
        val declaration = symbol as? KSClassDeclaration
        if (declaration == null) {
            logger.error(message = "@Ser and @De require a class", symbol = symbol)
            return null
        }
        val annotations = resolveAnnotations(declaration)
        if (!annotations.isSer && !annotations.isDe) {
            return null
        }
        val constructor = declaration.primaryConstructor
        if (constructor == null) {
            logger.error(message = "@Ser and @De require a primary constructor", symbol = declaration)
            return null
        }
        val constructorOrFactory = resolveConstructorOrFactory(declaration = declaration, constructor = constructor)
        if (constructorOrFactory == null) {
            logger.error(
                message = "A non-public primary constructor requires a matching companion operator fun invoke",
                symbol = declaration,
            )
            return null
        }
        val fields = resolveFields(
            declaration = declaration,
            parameters = constructor.parameters,
            annotations = annotations,
        ) ?: return null
        return SerdeClassShape(
            declaration = declaration,
            annotations = annotations,
            constructor = constructor,
            constructorOrFactory = constructorOrFactory,
            fields = fields,
        )
    }

    private fun resolveAnnotations(declaration: KSClassDeclaration): SerdeAnnotations {
        var ser = false
        var de = false
        for (annotation in declaration.annotations) {
            val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            if (qualifiedName == serQualifiedName) {
                ser = true
            }
            if (qualifiedName == deQualifiedName) {
                de = true
            }
            if (ser && de) {
                break
            }
        }
        return SerdeAnnotations(isSer = ser, isDe = de)
    }

    private fun resolveConstructorOrFactory(
        declaration: KSClassDeclaration,
        constructor: KSFunctionDeclaration,
    ): KSFunctionDeclaration? {
        if (constructor.getVisibility() == Visibility.PUBLIC) {
            return constructor
        }
        return declaration.declarations
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
                    function.parameters.size == constructor.parameters.size &&
                    constructor.parameters.all { constructorArg ->
                        function.parameters.any { invokeArg ->
                            invokeArg.name?.asString() == constructorArg.name?.asString() &&
                                invokeArg.type.resolve() == constructorArg.type.resolve()
                        }
                    }
            }
    }

    private fun resolveFields(
        declaration: KSClassDeclaration,
        parameters: List<KSValueParameter>,
        annotations: SerdeAnnotations,
    ): List<SerdeField>? {
        val properties = if (annotations.isSer) {
            declaration.getDeclaredProperties().associateBy { it.simpleName.asString() }
        } else {
            emptyMap()
        }
        val fields = ArrayList<SerdeField>(parameters.size)
        for (parameter in parameters) {
            val name = parameter.name?.asString()
            if (name == null) {
                logger.error(message = "Serde requires named primary constructor parameters", symbol = declaration)
                return null
            }
            val type = parameter.type.resolve()
            val property = properties[name]
            if (annotations.isSer) {
                if (property == null || property.extensionReceiver != null) {
                    logger.error(
                        message = "@Ser requires a readable property '$name' matching its primary constructor parameter",
                        symbol = declaration,
                    )
                    return null
                }
                if (!parameter.isVararg && property.type.resolve() != type) {
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
            fields += SerdeField(name = name, parameter = parameter, property = property, type = type)
        }
        return fields
    }
}
