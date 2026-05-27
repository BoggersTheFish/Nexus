plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal()
    }
}

dependencies {
    // The DSL + serializer + merger live in the sibling module. The
    // Gradle plugin is intentionally thin — it just wires the task into
    // the consumer's build lifecycle.
    implementation(project(":nexus-permissions"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("nexusPermissions") {
            id = "net.badgersmc.nexus.permissions"
            implementationClass = "net.badgersmc.nexus.permissions.gradle.NexusPermissionsPlugin"
            displayName = "Nexus Permissions"
            description = "Generates the permissions: block of paper-plugin.yml from a Kotlin DSL during processResources."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        // java-gradle-plugin auto-creates `pluginMaven` + a marker
        // publication per declared plugin. Override the coordinates so
        // they slot into the JitPack/GHP naming scheme used by every
        // other Nexus module. configureEach runs lazily as each
        // publication is registered, which avoids touching pluginMaven
        // before the gradle-plugin plugin has had a chance to create it.
        withType<MavenPublication>().configureEach {
            groupId = "net.badgersmc"
            version = rootProject.version.toString()
            if (name == "pluginMaven") {
                artifactId = "nexus-permissions-gradle"
            }
        }
    }
}
