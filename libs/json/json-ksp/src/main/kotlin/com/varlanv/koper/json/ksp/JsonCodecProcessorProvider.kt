package com.varlanv.koper.json.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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

private data class ResolvedAnnotations(val isSer: Boolean, val isDe: Boolean)

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

            //            val primaryConstructor = declaration.primaryConstructor
            //            val field = declaration.primaryConstructor?.parameters?.singleOrNull()
            //            val fieldName = field?.name?.asString()
            //            if (declaration.classKind != ClassKind.CLASS ||
            //                declaration.typeParameters.isNotEmpty() ||
            //                declaration.containingFile == null ||
            //                field == null ||
            //                (!field.isVal && !field.isVar) ||
            //                fieldName == null
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
        }
        //        return deferred
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
}
