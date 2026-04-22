package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * Glm45ToolCallParser 测试
 * 格式: <tool_call>{"name": "func", "arguments": {...}}</tool_call>（同 Hermes，但解析逻辑不同）
 */
class Glm45ParserTest {

    private val parser = Glm45ToolCallParser()

    @Test
    fun `hasToolCall detects tool_call tag`() {
        assertTrue(parser.hasToolCall("text <tool_call>{}</tool_call>"))
        assertFalse(parser.hasToolCall("no tags here"))
    }

    @Test
    fun `parse single tool call with arg tags`() {
        // GLM45 格式: <tool_call>funcname\n<arg_key>key</arg_key><arg_value>val</arg_value></tool_call>
        val response = """<tool_call>read
<arg_key>path</arg_key><arg_value>/tmp/test.txt</arg_value></tool_call>"""
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("read", result.toolCalls!![0].name)
        assertEquals("/tmp/test.txt", result.toolCalls!![0].arguments["path"])
    }

    @Test
    fun `parse no tool call`() {
        val result = parser.parse("normal response")
        assertNull(result.toolCalls)
    }

    @Test
    fun `supportedModels includes glm45`() {
        assertTrue(parser.supportedModels.contains("glm45"))
    }
}
