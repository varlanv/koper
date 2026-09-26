package com.varlanv.koper.serde.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.varlanv.koper.serde.ksp.model.SerdeGenerator

const val serdeGeneratorsOption = "koper.serde.generators"

class SerdeKspProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val names = environment.options[serdeGeneratorsOption]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            .orEmpty()
        if (names.isEmpty()) {
            environment.logger.warn(message = "No serde generators configured; serde processing is disabled")
        }
        val generators = names.map(::loadGenerator)
        return SerdeKspProcessor(environment = environment, generators = generators)
    }

    private fun loadGenerator(name: String): SerdeGenerator {
        val implementation = try {
            Class.forName(name, true, javaClass.classLoader)
        } catch (cause: ClassNotFoundException) {
            throw IllegalStateException("Serde generator class '$name' was not found", cause)
        }
        if (!SerdeGenerator::class.java.isAssignableFrom(implementation)) {
            error("Serde generator class '$name' does not implement ${SerdeGenerator::class.qualifiedName}")
        }
        return try {
            implementation.getConstructor().newInstance() as SerdeGenerator
        } catch (cause: ReflectiveOperationException) {
            throw IllegalStateException("Could not initialize serde generator class '$name'", cause)
        }
    }
}

private class SerdeKspProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val generators: List<SerdeGenerator>,
) : SymbolProcessor {
    private val shapeResolver = SerdeShapeResolver(environment.logger)
    private val processed = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generators.isEmpty()) {
            return emptyList()
        }
        val deferred = mutableListOf<KSAnnotated>()
        for (symbol in resolver.getSerdeSymbols()) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            val qualifiedName = (symbol as? KSClassDeclaration)?.qualifiedName?.asString()
            if (qualifiedName != null && !processed.add(qualifiedName)) {
                continue
            }
            val shape = shapeResolver.resolve(symbol) ?: continue
            for (generator in generators) {
                generator.generate(shape = shape, environment = environment)
            }
        }
        return deferred
    }
}
