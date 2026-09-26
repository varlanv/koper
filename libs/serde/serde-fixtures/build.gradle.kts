plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(projects.libs.lang)
    api(projects.libs.serde.serdeCore)
    testImplementation(projects.libs.testing.commonTest)
}
