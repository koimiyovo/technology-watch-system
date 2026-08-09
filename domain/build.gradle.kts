plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    // Les ports du domaine utilisent `suspend` → coroutines sur le classpath.
    // Aucune autre dépendance infra : c'est la garantie de l'isolation hexagonale.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}
