package net.badgersmc.nexus.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Red tests for TDD-202 — `PaperPluginYmlMerger.merge` must
 * surgically replace the `permissions:` block of an existing
 * paper-plugin.yml (REQ-204) and produce a byte-identical second
 * run on unchanged input (REQ-205).
 */
class PaperPluginYmlMergerTest {

    private fun loadFixture(name: String): String =
        javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
            .bufferedReader().readText()

    @Test
    fun `merge replaces stale permissions block and preserves every other line`() {
        val source = loadFixture("paper-plugin.source.yml")
        val expected = loadFixture("paper-plugin.expected.yml")
        val tree = permissionTree {
            node("foo.admin", default = Default.OP, description = "Admin root") {
                child("reload")
                child("import", default = Default.NOT_OP)
            }
        }

        val merged = PaperPluginYmlMerger.merge(source, tree)

        assertEquals(expected, merged)
    }

    @Test
    fun `merge is idempotent — second run against own output is byte-identical`() {
        val source = loadFixture("paper-plugin.source.yml")
        val tree = permissionTree {
            node("foo.admin", default = Default.OP, description = "Admin root") {
                child("reload")
                child("import", default = Default.NOT_OP)
            }
        }

        val once = PaperPluginYmlMerger.merge(source, tree)
        val twice = PaperPluginYmlMerger.merge(once, tree)

        assertEquals(once, twice)
    }
}
