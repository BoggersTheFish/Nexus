package net.badgersmc.nexus.permissions.gradle

import net.badgersmc.nexus.permissions.PaperPluginYmlMerger
import net.badgersmc.nexus.permissions.PermissionTree
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Reads the consumer's permission tree from [tree] and merges it into
 * the `paper-plugin.yml` already staged under `build/resources/main/`
 * by `processResources`. Re-writes the file in place. See REQ-203.
 *
 * Up-to-date checks are intentionally disabled (everything marked
 * `@Internal`) because the input file is also the output file and the
 * PermissionTree is not Serializable. Cheap to re-run.
 */
abstract class GenerateNexusPermissionsTask : DefaultTask() {

    @get:Internal
    abstract val tree: Property<PermissionTree>

    @get:Internal
    abstract val pluginYml: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = pluginYml.get().asFile
        if (!file.exists()) {
            logger.warn("nexus-permissions: $file does not exist; skipping merge")
            return
        }
        val merged = PaperPluginYmlMerger.merge(file.readText(), tree.get())
        file.writeText(merged)
    }
}
