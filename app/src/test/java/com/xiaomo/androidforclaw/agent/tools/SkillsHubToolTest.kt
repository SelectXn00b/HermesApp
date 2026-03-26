package com.xiaomo.androidforclaw.agent.tools

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class SkillsHubToolTest {

    private val context: Context = mockk(relaxed = true)

    // ==================== SkillsSearchTool ====================

    @Test
    fun `search tool has correct name`() {
        val tool = SkillsSearchTool(context)
        assertEquals("skills_search", tool.name)
    }

    @Test
    fun `search tool definition has correct schema`() {
        val def = SkillsSearchTool(context).getToolDefinition()
        assertEquals("skills_search", def.function.name)
        assertEquals("object", def.function.parameters.type)
        assertTrue(def.function.parameters.properties.containsKey("query"))
        assertTrue(def.function.parameters.properties.containsKey("limit"))
        // query is optional (empty = list all)
        assertFalse(def.function.parameters.required.contains("query"))
    }

    @Test
    fun `search tool description mentions ClawHub`() {
        val tool = SkillsSearchTool(context)
        assertTrue(tool.description.contains("ClawHub"))
    }

    @Test
    fun `search tool query param is string type`() {
        val props = SkillsSearchTool(context).getToolDefinition().function.parameters.properties
        assertEquals("string", props["query"]?.type)
    }

    @Test
    fun `search tool limit param is number type`() {
        val props = SkillsSearchTool(context).getToolDefinition().function.parameters.properties
        assertEquals("number", props["limit"]?.type)
    }

}
