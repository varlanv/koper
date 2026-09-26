## Project structure

- [internal-convention-plugin](internal-convention-plugin): Gradle convention plugins.
- [libs/lang](libs/lang): shared Kotlin Multiplatform utilities.
- [libs/testing/common-test](libs/testing/common-test): shared test utilities.
- [libs/serde/serde-core](libs/serde/serde-core): serde annotations.
- [libs/serde/serde-fixtures](libs/serde/serde-fixtures): serde sample builders for tests.
- [libs/serde/serde-ksp](libs/serde/serde-ksp): reusable class-shape analysis for format processors; not a KSP
  processor.
- [libs/json/json-core](libs/json/json-core): JSON runtime. See [JSON instructions](libs/json/AGENTS.md).
- [libs/json/json-ksp](libs/json/json-ksp): JSON KSP processor.
- [benchmarks/benchmarks-lang](benchmarks/benchmarks-lang) and [benchmarks/benchmarks-json](benchmarks/benchmarks-json):
  benchmarks.

## Gradle patterns

- Register modules in [settings.gradle.kts](settings.gradle.kts). Versions and external dependencies live
  in [gradle/libs.versions.toml](gradle/libs.versions.toml); use `projects.libs.*` for module dependencies.
- Use `alias(libs.plugins.internalMultiplatform)` for JVM/JS libraries, `internalConvention` for JVM tools and fixtures,
  and `internalBenchmark` for benchmarks. Put Multiplatform dependencies in the relevant `kotlin.sourceSets` block.
- The convention plugins provide `lint` and `format`. Run targeted tests with `./gradlew :libs:serde:serde-ksp:test`
  (JVM) or `./gradlew :libs:json:json-core:jvmTest` (Multiplatform).
