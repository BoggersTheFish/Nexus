package net.badgersmc.nexus.permissions.gradle

import net.badgersmc.nexus.permissions.PermissionTree
import net.badgersmc.nexus.permissions.PermissionTreeBuilder
import net.badgersmc.nexus.permissions.permissionTree

/**
 * Configuration block for the Nexus permissions plugin. Holds the
 * consumer-declared permission tree until the generate task fires.
 *
 * Usage:
 * ```
 * nexusPermissions {
 *     tree {
 *         node("foo.admin", default = Default.OP) {
 *             child("reload")
 *         }
 *     }
 * }
 * ```
 */
open class NexusPermissionsExtension {

    internal var tree: PermissionTree = permissionTree { }

    fun tree(block: PermissionTreeBuilder.() -> Unit) {
        tree = permissionTree(block)
    }
}
