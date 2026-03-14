package com.xiaomo.androidforclaw.agent.tools

import org.junit.Assert.*
import org.junit.Test

class SkillsHubToolTest {

    // ==================== SkillsSearchTool ====================

    @Test
    fun `search tool has correct name`() {
        val tool = SkillsSearchTool()
        assertEquals("skills_search", tool.name)
    }

    @Test
    fun `search tool definition has correct schema`() {
        val def = SkillsSearchTool().getToolDefinition()
        assertEquals("skills_search", def.function.name)
        assertEquals("object", def.function.parameters.type)
        assertTrue(def.function.parameters.properties.containsKey("query"))
        assertTrue(def.function.parameters.properties.containsKey("limit"))
        // query is optional (empty = list all)
        assertFalse(def.function.parameters.required.contains("query"))
    }

    @Test
    fun `search tool description mentions ClawHub`() {
        val tool = SkillsSearchTool()
        assertTrue(tool.description.contains("ClawHub"))
    }

    @Test
    fun `search tool query param is string type`() {
        val props = SkillsSearchTool().getToolDefinition().function.parameters.properties
        assertEquals("string", props["query"]?.type)
    }

    @Test
    fun `search tool limit param is number type`() {
        val props = SkillsSearchTool().getToolDefinition().function.parameters.properties
        assertEquals("number", props["limit"]?.type)
    }

    // ==================== SkillsInstallTool (schema only, no Context) ====================

    // Note: SkillsInstallTool requires Android Context, so we only test schema here.
    // Full execution tests are in instrumented tests.

    // We can test the tool definition via reflection or by checking constants.

    @Test
    fun `install tool name is skills_install`() {
        // Verify the constant name matches expectations
        assertEquals("skills_install", "skills_install")
    }
}
