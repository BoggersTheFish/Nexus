package net.badgersmc.nexus.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Red test for TDD-201 — verifies [PermissionTreeSerializer.toYaml]
 * emits a flat-keyed YAML block matching Paper's `paper-plugin.yml`
 * permissions schema (REQ-202). Output is compared against a fixture
 * stored under `src/test/resources/fixtures/permissions.yml`.
 */
class PermissionTreeSerializerTest {

    @Test
    fun `toYaml flattens nodes and emits Paper schema with deterministic key ordering`() {
        val tree = permissionTree {
            node("foo.admin", default = Default.OP, description = "Admin root") {
                child("reload")
                child("import", default = Default.NOT_OP)
            }
        }

        val expected = javaClass.classLoader
            .getResourceAsStream("fixtures/permissions.yml")!!
            .bufferedReader()
            .readText()

        val actual = PermissionTreeSerializer.toYaml(tree)

        assertEquals(expected, actual)
    }
}
