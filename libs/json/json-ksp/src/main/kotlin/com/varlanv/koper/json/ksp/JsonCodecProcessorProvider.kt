package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.varlanv.koper.serde.ksp.SerdeClassShape
import com.varlanv.koper.serde.ksp.SerdeShapeResolver
import com.varlanv.koper.serde.ksp.getSerdeSymbols

class JsonCodecProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor = JsonCodecProcessor(environment.logger)
}

private val knownFieldTypes = setOf(
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.String",
    "com.varlanv.koper.lang.text.Utf8Str",
)

private class JsonCodecProcessor(private val logger: KSPLogger) : SymbolProcessor {
    private val shapeResolver = SerdeShapeResolver(logger)
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        for (symbol in resolver.getSerdeSymbols()) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            val qualifiedName = (symbol as? KSClassDeclaration)?.qualifiedName?.asString()
            if (qualifiedName != null && !generated.add(qualifiedName)) {
                continue
            }
            val shape = shapeResolver.resolve(symbol) ?: continue
            validateJsonFields(shape)
        }
        return deferred
    }

    private fun validateJsonFields(shape: SerdeClassShape) {
        for (field in shape.fields) {
            val name = field.name
            if (field.parameter.isVararg) {
                logger.error(message = "JSON codec does not support vararg field '$name'", symbol = shape.declaration)
                return
            }
            val type = field.type
            if (type.isMarkedNullable) {
                logger.error(
                    message = "JSON codec does not support nullable field '$name' (${type})",
                    symbol = shape.declaration,
                )
                return
            }
            val typeName = type.declaration.qualifiedName?.asString()
            if (typeName !in knownFieldTypes) {
                logger.error(
                    message = "JSON codec does not support field '$name' of type '${typeName ?: type}'",
                    symbol = shape.declaration,
                )
                return
            }
        }
    }
}
