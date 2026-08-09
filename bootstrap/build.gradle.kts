plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    // Le plugin `application` génère des tâches de distribution (startShadowScripts, etc.)
    // qui accèdent à la propriété `mainClassName` supprimée en Gradle 9. On le retire
    // et on inscrit le Main-Class directement dans le manifest du shadow jar.
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    // Bootstrap est le seul endroit qui connaît toute l'architecture.
    // Il instancie ArticleCurator (domain) directement → dépendance explicite requise.
    // `implementation` dans :application ne l'expose pas transitivement aux consommateurs.
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure:rss"))
    implementation(project(":infrastructure:llm"))
    implementation(project(":infrastructure:email"))
    // Bootstrap crée et configure l'HttpClient — il doit déclarer Ktor explicitement.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}

tasks.shadowJar {
    archiveBaseName = "technology-watch-system"
    archiveClassifier = ""
    archiveVersion = ""
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.kyovo.bootstrap.MainKt"
    }
}
