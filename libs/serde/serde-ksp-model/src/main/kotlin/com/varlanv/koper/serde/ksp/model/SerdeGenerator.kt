package com.varlanv.koper.serde.ksp.model

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

/** Which operations the class requests: serialization, deserialization, or both. */
data class SerdeAnnotations(val isSer: Boolean, val isDe: Boolean)

/** A primary constructor parameter and its corresponding property, if serialization is requested. */
data class SerdeField(
    val name: String,
    val parameter: KSValueParameter,
    /** Null for classes that only request deserialization. */
    val property: KSPropertyDeclaration?,
    /** Resolved type of [parameter]. */
    val type: KSType,
)

/** A class validated by the serde processor and passed to protocol generators. */
data class SerdeClassShape(
    val declaration: KSClassDeclaration,
    val annotations: SerdeAnnotations,
    val constructor: KSFunctionDeclaration,
    /** The public primary constructor, or a matching companion `invoke` for a non-public constructor. */
    val constructorOrFactory: KSFunctionDeclaration,
    /** Fields in primary constructor parameter order. */
    val fields: List<SerdeField>,
)

/** Generates protocol-specific code for resolved serde classes. */
interface SerdeGenerator {
    /** Handles one validated class; [environment] provides diagnostics and generated-file output. */
    fun generate(shape: SerdeClassShape, environment: SymbolProcessorEnvironment)
}
