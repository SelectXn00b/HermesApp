package com.xiaomo.androidforclaw.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecFacadeToolTest {

    private class FakeTool(
        override val name: String,
        private val result: ToolResult
    ) : Tool {
        var callCount: Int = 0
            private set
        var lastArgs: Map<String, Any?>? = null
            private set

        override val description: String = name

        override fun getToolDefinition() = com.xiaomo.androidforclaw.providers.ToolDefinition(
            type = "function",
            function = com.xiaomo.androidforclaw.providers.FunctionDefinition(
                name = name,
                description = description,
                parameters = com.xiaomo.androidforclaw.providers.ParametersSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )

        override suspend fun execute(args: Map<String, Any?>): ToolResult {
            callCount++
            lastArgs = args
            return result
        }
    }

    @Test
    fun `auto routes to termux when available`() {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = true)

        val result = kotlinx.coroutines.runBlocking {
            facade.execute(mapOf("command" to "echo hi"))
        }

        assertTrue(result.success)
        assertEquals("termux", result.content)
        assertEquals(0, internal.callCount)
        assertEquals(1, termux.callCount)
    }

    @Test
    fun `auto falls back to internal when termux unavailable`() {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = false)

        val result = kotlinx.coroutines.runBlocking {
            facade.execute(mapOf("command" to "echo hi"))
        }

        assertTrue(result.success)
        assertEquals("internal", result.content)
        assertEquals(1, internal.callCount)
        assertEquals(0, termux.callCount)
    }

    @Test
    fun `backend internal forces internal exec`() {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = true)

        val result = kotlinx.coroutines.runBlocking {
            facade.execute(mapOf("command" to "echo hi", "backend" to "internal"))
        }

        assertEquals("internal", result.content)
        assertEquals(1, internal.callCount)
        assertEquals(0, termux.callCount)
    }

    @Test
    fun `backend termux forces termux exec`() {
        val internal = FakeTool("exec", ToolResult.success("internal"))
        val termux = FakeTool("exec", ToolResult.success("termux"))
        val facade = ExecFacadeTool(internal, termux, termuxAvailable = false)

        val result = kotlinx.coroutines.runBlocking {
            facade.execute(mapOf("command" to "echo hi", "backend" to "termux"))
        }

        assertEquals("termux", result.content)
        assertEquals(0, internal.callCount)
        assertEquals(1, termux.callCount)
    }
}
