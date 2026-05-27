package net.badgersmc.nexus.permissions.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin entry point for the Nexus permissions DSL. Registers
 * the [NexusPermissionsExtension] under `nexusPermissions { }` and
 * wires [GenerateNexusPermissionsTask] into the consumer's build so
 * the merged paper-plugin.yml is in place before the jar / shadowJar
 * is assembled (REQ-203).
 */
class NexusPermissionsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create(
            "nexusPermissions",
            NexusPermissionsExtension::class.java,
        )

        val generate = target.tasks.register(
            "generateNexusPermissions",
            GenerateNexusPermissionsTask::class.java,
        ) { task ->
            task.tree.set(target.provider { extension.tree })
            task.pluginYml.set(
                target.layout.buildDirectory.file("resources/main/paper-plugin.yml")
            )
        }

        // Must run after processResources has staged the source yml and
        // before classes/jar pack it. Wire both edges defensively so any
        // task graph that reaches `classes` picks the generation up.
        target.plugins.withId("java") {
            generate.configure { it.dependsOn("processResources") }
            target.tasks.named("classes").configure { it.dependsOn(generate) }
        }

        // Optional: if shadow is on the consumer's classpath, fence
        // shadowJar behind the generation too. Both modern and legacy
        // plugin ids are handled because the Paper plugin ecosystem
        // still has the old com.github.johnrengelman.shadow id in use.
        listOf("com.gradleup.shadow", "com.github.johnrengelman.shadow").forEach { id ->
            target.plugins.withId(id) {
                target.tasks.named("shadowJar").configure { it.dependsOn(generate) }
            }
        }
    }
}
