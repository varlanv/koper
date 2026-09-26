package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.varlanv.koper.serde.ksp.model.SerdeClassShape
import com.varlanv.koper.serde.ksp.model.SerdeGenerator

private val knownFieldTypes = setOf(
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.String",
    "com.varlanv.koper.lang.text.Utf8Str",
)

class JsonSerdeGenerator : SerdeGenerator {
    override fun generate(shape: SerdeClassShape, environment: SymbolProcessorEnvironment) {
        for (field in shape.fields) {
            val name = field.name
            if (field.parameter.isVararg) {
                environment.logger.error(
                    message = "JSON codec does not support vararg field '$name'",
                    symbol = shape.declaration,
                )
                return
            }
            val type = field.type
            if (type.isMarkedNullable) {
                environment.logger.error(
                    message = "JSON codec does not support nullable field '$name' (${type})",
                    symbol = shape.declaration,
                )
                return
            }
            val typeName = type.declaration.qualifiedName?.asString()
            if (typeName !in knownFieldTypes) {
                environment.logger.error(
                    message = "JSON codec does not support field '$name' of type '${typeName ?: type}'",
                    symbol = shape.declaration,
                )
                return
            }
        }
    }
}
