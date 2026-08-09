plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    // Eclipse Angus = implémentation de référence Jakarta Mail (API + impl bundlés)
    implementation(libs.angus.mail)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}
