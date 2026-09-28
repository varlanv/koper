package com.varlanv.koper.test

import com.varlanv.koper.lang.bin.Bytes
import com.varlanv.koper.lang.bin.kilobytes
import com.varlanv.koper.lang.text.Charset
import com.varlanv.koper.lang.text.Str
import kotlin.reflect.KClass

private object SampleBuffer {
    private val bytes = 10.kilobytes().allocate()
    private var size = 0

    fun reset() {
        size = 0
    }

    fun append(value: String) {
        Charset.Utf8.encodeInline(value) { put(it.toInt()) }
    }

    fun result(): Str = Str.wrapBytes(
        bytes = Bytes(bytes),
        offset = 0,
        len = size,
    )

    private fun put(value: Int) {
        check(size < bytes.size) { "Fixture source exceeds the 10 KiB sample buffer" }
        bytes[size++] = value.toByte()
    }
}

object SerdeSamples {
    enum class Kind { CLASS, DATA_CLASS, OBJECT, DATA_OBJECT }

    enum class Visibility { PUBLIC, INTERNAL, PROTECTED, PRIVATE }

    enum class Property { NONE, VAL, VAR }

    data class Annotations(
        val includeSer: Boolean = true,
        val includeDe: Boolean = true,
    )

    data class Parameter(
        val name: String,
        val type: String,
        val property: Property = Property.VAL,
    ) {
        constructor(name: String, type: KClass<*>, property: Property = Property.VAL) :
                this(
                    name,
                    requireNotNull(type.qualifiedName) { "Fixture type must have a qualified name" },
                    property,
                )
    }

    data class CompanionInvoke(
        val visibility: Visibility = Visibility.PUBLIC,
        val isOperator: Boolean = true,
        val parameters: List<Parameter>? = null,
        val returnType: String? = null,
    )

    data class Shape(
        val name: String,
        val kind: Kind = Kind.CLASS,
        val annotations: Annotations = Annotations(),
        val parameters: List<Parameter> = emptyList(),
        val constructorVisibility: Visibility = Visibility.PUBLIC,
        val companionInvoke: CompanionInvoke? = null,
    )

    data class Capabilities(
        val annotations: Set<Annotations> = setOf(Annotations()),
        val kinds: Set<Kind> = setOf(Kind.CLASS),
        val constructorVisibilities: Set<Visibility> = setOf(Visibility.PUBLIC),
        val parameterProperties: Set<Property> = setOf(Property.VAL),
        val companionInvokes: Set<CompanionInvoke?> = setOf(null),
    )

    data class MatrixSettings(
        val includeMixOfArgumentsPosition: Boolean = false,
        val parametersRepeating: Int = 0,
        val namePrefix: String = "SerdeFixture",
        val maxSamples: Int = 100_000,
    )

    fun buildSerdeSample(shape: Shape): Str {
        validate(shape)
        SampleBuffer.reset()
        if (shape.annotations.includeSer) {
            SampleBuffer.append("@com.varlanv.koper.serde.Ser\n")
        }
        if (shape.annotations.includeDe) {
            SampleBuffer.append("@com.varlanv.koper.serde.De\n")
        }
        when (shape.kind) {
            Kind.OBJECT -> {
                SampleBuffer.append("object ")
                SampleBuffer.append(shape.name)
            }

            Kind.DATA_OBJECT -> {
                SampleBuffer.append("data object ")
                SampleBuffer.append(shape.name)
            }

            Kind.CLASS, Kind.DATA_CLASS -> {
                renderClass(shape)
            }
        }
        SampleBuffer.append("\n")
        return SampleBuffer.result()
    }

    fun buildSerdeSample(types: List<KClass<*>>, capabilities: Capabilities): Str {
        val shape = Shape(
            name = "SerdeFixture",
            kind = capabilities.kinds.single(),
            annotations = capabilities.annotations.single(),
            parameters = types.mapIndexed { index, type ->
                Parameter("arg$index", type, capabilities.parameterProperties.single())
            },
            constructorVisibility = capabilities.constructorVisibilities.single(),
            companionInvoke = capabilities.companionInvokes.single(),
        )
        return buildSerdeSample(shape)
    }

    fun buildSerdeSamplesMatrix(
        types: Set<KClass<*>>,
        capabilities: Capabilities,
        matrixSettings: MatrixSettings = MatrixSettings(),
        block: (shape: Shape, source: Str) -> Unit,
    ) {
        require(types.isNotEmpty()) { "Matrix needs at least one parameter type" }
        require(matrixSettings.parametersRepeating >= 0) { "parametersRepeating must not be negative" }
        require(matrixSettings.maxSamples > 0) { "maxSamples must be positive" }
        require(capabilities.annotations.isNotEmpty())
        require(capabilities.kinds.isNotEmpty() && capabilities.kinds.all { it == Kind.CLASS || it == Kind.DATA_CLASS })
        require(capabilities.constructorVisibilities.isNotEmpty())
        require(capabilities.parameterProperties.isNotEmpty())
        require(capabilities.companionInvokes.isNotEmpty())

        val typeNames = types
            .map { requireNotNull(it.qualifiedName) { "Fixture type must have a qualified name" } }
            .sorted()
        val orderedTypes = typeNames.flatMap { type -> List(matrixSettings.parametersRepeating + 1) { type } }
        val annotations = capabilities.annotations.sortedWith(compareBy({ it.includeSer }, { it.includeDe }))
        val kinds = capabilities.kinds.sortedBy { it.ordinal }
        val visibilities = capabilities.constructorVisibilities.sortedBy { it.ordinal }
        val properties = capabilities.parameterProperties.sortedBy { it.ordinal }
        val invokes = capabilities.companionInvokes.sortedBy { it.toString() }
        var count = 0

        fun emit(typesInOrder: List<String>) {
            val selectedProperties = Array(typesInOrder.size) { Property.VAL }

            fun emitProperties(index: Int) {
                if (index < typesInOrder.size) {
                    for (property in properties) {
                        selectedProperties[index] = property
                        emitProperties(index + 1)
                    }
                    return
                }
                val parameters = typesInOrder.mapIndexed { position, type ->
                    Parameter("arg$position", type, selectedProperties[position])
                }
                for (kind in kinds) {
                    if (kind == Kind.DATA_CLASS && parameters.any { it.property == Property.NONE }) {
                        continue
                    }
                    for (annotation in annotations) {
                        for (visibility in visibilities) {
                            for (invoke in invokes) {
                                check(count < matrixSettings.maxSamples) { "Fixture matrix exceeds maxSamples" }
                                val shape = Shape(
                                    name = "${matrixSettings.namePrefix}${count++}",
                                    kind = kind,
                                    annotations = annotation,
                                    parameters = parameters,
                                    constructorVisibility = visibility,
                                    companionInvoke = invoke,
                                )
                                block(
                                    shape,
                                    buildSerdeSample(shape),
                                )
                            }
                        }
                    }
                }
            }

            emitProperties(0)
        }

        if (!matrixSettings.includeMixOfArgumentsPosition) {
            emit(orderedTypes)
            return
        }
        val distinctTypes = orderedTypes.distinct()
        val remaining = distinctTypes.map { type -> orderedTypes.count { it == type } }.toIntArray()
        val permutation = Array(orderedTypes.size) { "" }

        fun emitPermutations(index: Int) {
            if (index == permutation.size) {
                emit(permutation.asList())
                return
            }
            for (typeIndex in distinctTypes.indices) {
                if (remaining[typeIndex] == 0) {
                    continue
                }
                remaining[typeIndex]--
                permutation[index] = distinctTypes[typeIndex]
                emitPermutations(index + 1)
                remaining[typeIndex]++
            }
        }

        emitPermutations(0)
    }

    private fun validate(shape: Shape) {
        require(shape.name.isNotBlank()) { "Fixture name must not be blank" }
        require(shape.parameters.all { it.name.isNotBlank() && it.type.isNotBlank() })
        require(
            shape.parameters.map { it.name }.distinct().size == shape.parameters.size,
        ) { "Fixture parameter names must be unique" }
        if (shape.kind == Kind.OBJECT || shape.kind == Kind.DATA_OBJECT) {
            require(
                shape.parameters.isEmpty() &&
                        shape.constructorVisibility == Visibility.PUBLIC &&
                        shape.companionInvoke == null,
            ) {
                "Object fixtures cannot have constructor parameters, constructor visibility, or a companion invoke"
            }
        }
        if (shape.kind == Kind.DATA_CLASS) {
            require(shape.parameters.isNotEmpty() && shape.parameters.all { it.property != Property.NONE }) {
                "Data class fixtures need at least one constructor property"
            }
        }
        require(shape.companionInvoke?.visibility != Visibility.PROTECTED) { "Companion invoke cannot be protected" }
        shape.companionInvoke?.let { invoke ->
            require(
                invoke.returnType == null || invoke.returnType.isNotBlank(),
            ) { "Companion invoke return type must not be blank" }
            require(
                invoke.parameters == null || invoke.parameters.all { it.name.isNotBlank() && it.type.isNotBlank() },
            ) {
                "Companion invoke parameters must have names and types"
            }
            require(
                invoke.parameters == null ||
                        invoke.parameters.map { it.name }.distinct().size == invoke.parameters.size,
            ) {
                "Companion invoke parameter names must be unique"
            }
        }
    }

    private fun renderClass(shape: Shape) {
        SampleBuffer.append(
            if (shape.kind == Kind.DATA_CLASS) {
                "data class "
            } else {
                "class "
            },
        )
        SampleBuffer.append(shape.name)
        if (shape.parameters.isNotEmpty() ||
            shape.constructorVisibility != Visibility.PUBLIC ||
            shape.kind == Kind.DATA_CLASS
        ) {
            if (shape.constructorVisibility != Visibility.PUBLIC) {
                SampleBuffer.append(" ")
                SampleBuffer.append(shape.constructorVisibility.name.lowercase())
                SampleBuffer.append(" constructor")
            }
            SampleBuffer.append("(")
            shape.parameters.forEachIndexed { index, parameter ->
                if (index > 0) {
                    SampleBuffer.append(", ")
                }
                when (parameter.property) {
                    Property.NONE -> Unit
                    Property.VAL -> SampleBuffer.append("val ")
                    Property.VAR -> SampleBuffer.append("var ")
                }
                SampleBuffer.append(parameter.name)
                SampleBuffer.append(": ")
                SampleBuffer.append(parameter.type)
            }
            SampleBuffer.append(")")
        }
        val invoke = shape.companionInvoke ?: return
        val invokeParameters = invoke.parameters ?: shape.parameters
        SampleBuffer.append(" {\n    companion object {\n        ")
        if (invoke.visibility != Visibility.PUBLIC) {
            SampleBuffer.append(invoke.visibility.name.lowercase())
            SampleBuffer.append(" ")
        }
        if (invoke.isOperator) {
            SampleBuffer.append("operator ")
        }
        SampleBuffer.append("fun invoke(")
        invokeParameters.forEachIndexed { index, parameter ->
            if (index > 0) {
                SampleBuffer.append(", ")
            }
            SampleBuffer.append(parameter.name)
            SampleBuffer.append(": ")
            SampleBuffer.append(parameter.type)
        }
        SampleBuffer.append("): ")
        SampleBuffer.append(invoke.returnType ?: shape.name)
        SampleBuffer.append(" = ")
        val matchingParameters = invokeParameters.size == shape.parameters.size &&
                shape.parameters.all { constructorParameter ->
                    invokeParameters.any { it.name == constructorParameter.name && it.type == constructorParameter.type }
                }
        if ((invoke.returnType == null || invoke.returnType == shape.name) && matchingParameters) {
            SampleBuffer.append(shape.name)
            SampleBuffer.append("(")
            shape.parameters.forEachIndexed { index, parameter ->
                if (index > 0) {
                    SampleBuffer.append(", ")
                }
                SampleBuffer.append(parameter.name)
                SampleBuffer.append(" = ")
                SampleBuffer.append(parameter.name)
            }
            SampleBuffer.append(")")
        } else {
            SampleBuffer.append("error(\"fixture\")")
        }
        SampleBuffer.append("\n    }\n}")
    }
}
