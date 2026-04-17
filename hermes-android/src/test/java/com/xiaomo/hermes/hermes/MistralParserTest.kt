package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * MistralToolCallParser 测试
 * 支持两种格式：
 * - Pre-v11: content[TOOL_CALLS] [{"name": ..., "arguments": {...}}]
 * - v11+:    content[TOOL_CALLS]tool_name{"arg": "val"}
 */
class MistralParserTest {

    private val parser = MistralToolCallParser()

    @Test
    fun `hasToolCall detects BOT_TOKEN`() {
        assertTrue(parser.hasToolCall("hello[TOOL_CALLS]something"))
        assertFalse(parser.hasToolCall("no tool calls here"))
    }

    @Test
    fun `parse v11 format - single tool call`() {
        val response = """Let me check.[TOOL_CALLS]read_file{"path": "/tmp/test.txt"}"""
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("read_file", result.toolCalls!![0].name)
        assertEquals("/tmp/test.txt", result.toolCalls!![0].arguments["path"])
        assertEquals("Let me check.", result.content)
    }

    @Test
    fun `parse v11 format - multiple tool calls`() {
        val response = """[TOOL_CALLS]read{"path": "a.txt"}[TOOL_CALLS]read{"path": "b.txt"}"""
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(2, result.toolCalls!!.size)
        assertEquals("a.txt", result.toolCalls!![0].arguments["path"])
        assertEquals("b.txt", result.toolCalls!![1].arguments["path"])
    }

    @Test
    fun `parse pre-v11 format - JSON array`() {
        val response = """[TOOL_CALLS] [{"name": "exec", "arguments": {"command": "ls"}}]"""
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("exec", result.toolCalls!![0].name)
        assertEquals("ls", result.toolCalls!![0].arguments["command"])
    }

    @Test
    fun `parse pre-v11 format - single JSON object`() {
        val response = """[TOOL_CALLS] {"name": "write", "arguments": {"path": "/tmp/f"}}"""
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("write", result.toolCalls!![0].name)
    }

    @Test
    fun `parse no tool calls`() {
        val result = parser.parse("Just normal text")
        assertEquals("Just normal text", result.content)
        assertNull(result.toolCalls)
    }

    @Test
    fun `tool call IDs are 9 chars`() {
        val response = """[TOOL_CALLS]test{"x": 1}"""
        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals(9, result.toolCalls!![0].id.length)
    }

    @Test
    fun `formatToolCalls produces v11 format`() {
        val calls = listOf(
            ParsedToolCall("abc123456", "read", mapOf("path" to "/tmp"), """{"path":"/tmp"}""")
        )
        val formatted = parser.formatToolCalls(calls)
        assertTrue(formatted.contains("[TOOL_CALLS]"))
        assertTrue(formatted.contains("read"))
    }
}
