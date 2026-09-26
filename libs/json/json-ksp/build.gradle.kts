plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    implementation(libs.ksp.api)
    api(projects.libs.serde.serdeKspModel)
    testImplementation(projects.libs.serde.serdeKsp)
    testImplementation(libs.ksp.aaEmbeddable)
    testImplementation(libs.ksp.commonDeps)
    testImplementation(projects.libs.serde.serdeFixtures)
    testImplementation(projects.libs.testing.commonTest)
}
