plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "technology-watch-system"

include(
    "domain",
    "application",
    "infrastructure:rss",
    "infrastructure:llm",
    "infrastructure:email",
    "bootstrap"
)
