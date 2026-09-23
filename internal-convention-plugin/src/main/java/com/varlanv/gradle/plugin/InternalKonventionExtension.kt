package com.varlanv.gradle.plugin

import org.gradle.api.provider.Property

interface InternalKonventionExtension {

    companion object {
        const val NAME = "internalConvention"
    }

    val internalModule: Property<Boolean>
//    @get:Nested
//    val targets: Targets
}

//interface Targets {
//
//    val jvm: Property<Boolean>
//    val js: Property<Boolean>
//}
