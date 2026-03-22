package com.xiaomo.androidforclaw.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExecFacadeTool tests.
 *
 * Note: Since ExecFacadeTool now uses EmbeddedTermuxRuntime (a singleton)
 * for Termux execution, we test the tool definition and internal-only routing.
 * Full integration tests require a device with the runtime installed.
 */
class ExecFacadeToolTest {

    @Test
    fun `tool name is exec`() {
        // ExecFacadeTool constructor requires Context, but we can verify via ExecTool
        val tool = ExecTool()
        assertEquals("exec", tool.name)
    }

    @Test
    fun `tool definition has command parameter`() {
        val tool = ExecTool()
        val def = tool.getToolDefinition()
        assertTrue(def.function.parameters.properties.containsKey("command"))
        assertTrue(def.function.parameters.required.contains("command"))
    }

    @Test
    fun `exec tool rejects dangerous commands`() {
        val tool = ExecTool()
        val result = kotlinx.coroutines.runBlocking {
            tool.execute(mapOf("command" to "rm -rf /"))
        }
        assertTrue(!result.success)
        assertTrue(result.content.contains("blocked") || result.content.contains("safety"))
    }
}
