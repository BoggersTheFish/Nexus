plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.gradle.plugin-publish") version "1.3.1" apply false
}

group = "net.badgersmc"
version = "2.2.1"

/** Root project aggregator; publishable artifacts live in `nexus-*` modules. */

allprojects {
    repositories {
        mavenCentral()
        // Opt-in to a locally-published Nexus snapshot via -PuseMavenLocal=true.
        // Off by default so CI builds never pick up stale local jars.
        if (providers.gradleProperty("useMavenLocal").orNull == "true") {
            mavenLocal()
        }
    }
}

/**
 * Inject the GitHub Packages publish repository into every sub-module that
 * applies `maven-publish`. Sub-modules keep their own `publications` blocks
 * (so they can pin artifactIds), but the repo wiring + credentials live in
 * one place here so the CI workflow only needs one set of secrets.
 */
subprojects {
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/BadgersMC/Nexus")
                    credentials {
                        username = project.findProperty("gpr.user") as String?
                            ?: System.getenv("GITHUB_ACTOR")
                        password = project.findProperty("gpr.token") as String?
                            ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }

    // Detekt runs standalone; harmless on non-Kotlin modules, no-op when there are no sources.
    // The plugin already wires `detekt` into the `check` lifecycle by default.
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = false
        allRules = false
    }
}
