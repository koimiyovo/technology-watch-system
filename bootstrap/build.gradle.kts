plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    // The `application` plugin generates distribution tasks (startShadowScripts, etc.)
    // that access the `mainClassName` property removed in Gradle 9. Omitting the plugin
    // and writing Main-Class directly into the shadow jar manifest is the workaround.
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    // Bootstrap is the only module that knows the full architecture.
    // It instantiates ArticleCurator (domain) directly — explicit dependency required.
    // `implementation` in :application is not transitive to consumers.
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure:rss"))
    implementation(project(":infrastructure:llm"))
    implementation(project(":infrastructure:email"))
    // Bootstrap creates and configures the HttpClient — Ktor must be declared explicitly.
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
