// Declare plugins here to lock their versions via the version catalog,
// without applying them to the root (apply false). Each submodule applies
// them explicitly, keeping full control over which plugins it uses.
plugins {
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow)               apply false
}

// Minimal shared config: repository and test runner only.
// Avoid subprojects { apply(plugin=…) } because Kotlin DSL accessors
// (kotlin {}, dependencies { testImplementation(…) }) are not generated inside
// a subprojects block — type safety would be lost. Each module therefore declares
// its own plugin block; only versions are centralised in the catalog.
subprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

allprojects {
    group   = "com.kyovo"
    version = "1.0-SNAPSHOT"
}
