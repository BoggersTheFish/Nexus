package net.badgersmc.nexus.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Red test for TDD-204 — DSL recursion + serializer flattening must
 * handle arbitrary depth, not just one level. See REQ-200.
 */
class NestedChildrenTest {

    @Test
    fun `deep nesting flattens to fully-qualified entries with children listing on each parent`() {
        val tree = permissionTree {
            node("foo") {
                child("admin") {
                    child("reload")
                }
            }
        }

        val yaml = PermissionTreeSerializer.toYaml(tree)

        // Every level surfaces as its own top-level key.
        assertTrue("foo:" in yaml, "Expected top-level foo: key")
        assertTrue("foo.admin:" in yaml, "Expected top-level foo.admin: key")
        assertTrue("foo.admin.reload:" in yaml, "Expected top-level foo.admin.reload: key")

        // Each parent declares its direct children, not transitive descendants.
        val expected = """
            foo:
              default: op
              children:
              - foo.admin
            foo.admin:
              default: op
              children:
              - foo.admin.reload
            foo.admin.reload:
              default: op

        """.trimIndent()
        assertEquals(expected, yaml)
    }
}
