// Déclare les plugins ici pour verrouiller leurs versions via le catalog,
// sans les appliquer à la racine (apply false). Chaque sous-module les applique
// lui-même, ce qui lui laisse le contrôle explicite.
plugins {
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow)               apply false
}

// Config partagée minimale : dépôt + runner de tests.
// On évite subprojects { apply(plugin=…) } parce que les accesseurs Kotlin DSL
// (kotlin {}, dependencies { testImplementation(…) }) ne sont pas générés dans
// un bloc subprojects — on perdrait le typage. Chaque module déclare donc son
// propre plugin block, et seule la version est centralisée dans le catalog.
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
