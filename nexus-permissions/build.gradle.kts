plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal() // opt-in via -PuseMavenLocal=true — never on CI
    }
}

dependencies {
    // Pure-Kotlin DSL module. No Paper, no Gradle API — keeps the
    // permission tree model testable standalone and re-usable from
    // the Gradle plugin in nexus-permissions-gradle without dragging
    // the server runtime onto the build classpath.
    api("org.yaml:snakeyaml:2.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "net.badgersmc"
            artifactId = "nexus-permissions"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}
