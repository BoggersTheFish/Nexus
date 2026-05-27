package net.badgersmc.nexus.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Red test for TDD-200 — verifies the `permissionTree { ... }` DSL builds
 * an in-memory tree where children inherit their parent's dotted-name
 * prefix and each node carries its declared Default.
 *
 * Backed by REQ-200 (DSL grammar) and REQ-201 (Default enum mapping).
 */
class PermissionTreeDslTest {

    @Test
    fun `node builds child names with inherited dotted prefix and defaults map through`() {
        val tree = permissionTree {
            node("foo.admin", default = Default.OP, description = "Admin root") {
                child("reload")
                child("import", default = Default.NOT_OP)
            }
        }

        // Top-level node exists with the declared default + description.
        val admin = tree.find("foo.admin")
        assertNotNull(admin, "Expected node foo.admin to exist")
        assertEquals(Default.OP, admin.default)
        assertEquals("Admin root", admin.description)

        // Children are fully-qualified — prefix inherited from parent name.
        assertEquals(
            setOf("foo.admin.reload", "foo.admin.import"),
            admin.children.map { it.name }.toSet(),
            "Children must inherit foo.admin. prefix"
        )

        // child("reload") with no default falls back to OP (Bukkit default).
        val reload = tree.find("foo.admin.reload")
        assertNotNull(reload)
        assertEquals(Default.OP, reload.default)

        // child("import", default = NOT_OP) preserves the override.
        val importNode = tree.find("foo.admin.import")
        assertNotNull(importNode)
        assertEquals(Default.NOT_OP, importNode.default)
    }
}
