plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(libs.ksp.api)
    testImplementation(libs.ksp.aaEmbeddable)
    testImplementation(libs.ksp.commonDeps)
    testImplementation(projects.libs.serde.serdeFixtures)
    testImplementation(projects.libs.testing.commonTest)
}
