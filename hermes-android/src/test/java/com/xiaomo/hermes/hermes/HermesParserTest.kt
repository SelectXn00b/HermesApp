package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * HermesToolCallParser 测试
 * 格式: <tool_call>{"name": "func", "arguments": {...}}</tool_call>
 */
class HermesParserTest {

    private val parser = HermesToolCallParser()

    @Test
    fun `hasToolCall detects tool call tag`() {
        assertTrue(parser.hasToolCall("some text <tool_call>{}</tool_call>"))
        assertFalse(parser.hasToolCall("no tool calls here"))
    }

    @Test
    fun `parse simple tool call`() {
        val response = """Let me read that file.
<tool_call>{"name": "read", "arguments": {"path": "/tmp/test.txt"}}</tool_call>"""

        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)

        val tc = result.toolCalls!![0]
        assertEquals("read", tc.name)
        assertEquals("/tmp/test.txt", tc.arguments["path"])
        assertTrue(tc.id.startsWith("call_"))
    }

    @Test
    fun `parse extracts content before tool call`() {
        val response = """I'll check that for you.
<tool_call>{"name": "exec", "arguments": {"command": "ls"}}</tool_call>"""

        val result = parser.parse(response)
        assertEquals("I'll check that for you.", result.content)
    }

    @Test
    fun `parse multiple tool calls`() {
        val response = """<tool_call>{"name": "read", "arguments": {"path": "a.txt"}}</tool_call>
<tool_call>{"name": "read", "arguments": {"path": "b.txt"}}</tool_call>"""

        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals(2, result.toolCalls!!.size)
        assertEquals("a.txt", result.toolCalls!![0].arguments["path"])
        assertEquals("b.txt", result.toolCalls!![1].arguments["path"])
    }

    @Test
    fun `parse no tool call returns content only`() {
        val result = parser.parse("Just a normal response")
        assertEquals("Just a normal response", result.content)
        assertNull(result.toolCalls)
    }

    @Test
    fun `parse unclosed tool call tag`() {
        // 未闭合的 <tool_call> 也应尝试解析
        val response = """<tool_call>{"name": "test", "arguments": {}}"""
        val result = parser.parse(response)
        // 根据正则的 |<tool_call>\s*(.*) 分支，应能匹配
        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("test", result.toolCalls!![0].name)
    }

    @Test
    fun `parse empty tool call tag ignored`() {
        val response = "<tool_call></tool_call>"
        val result = parser.parse(response)
        // 空的 tool call 被跳过
        assertNull(result.toolCalls)
    }

    @Test
    fun `parse invalid JSON falls back to content`() {
        val response = "<tool_call>not json at all</tool_call>"
        val result = parser.parse(response)
        // JSON 解析失败，整体回退
        assertNull(result.toolCalls)
    }

    @Test
    fun `formatToolCalls produces correct format`() {
        val calls = listOf(
            ParsedToolCall("id1", "read", mapOf("path" to "/tmp")),
            ParsedToolCall("id2", "exec", mapOf("command" to "ls"))
        )
        val formatted = parser.formatToolCalls(calls)
        assertTrue(formatted.contains("<tool_call>"))
        assertTrue(formatted.contains("</tool_call>"))
        assertTrue(formatted.contains("\"name\":\"read\""))
        assertTrue(formatted.contains("\"name\":\"exec\""))
    }

    @Test
    fun `parseToolCalls convenience method`() {
        val response = """<tool_call>{"name": "test", "arguments": {"x": 1}}</tool_call>"""
        val calls = parser.parseToolCalls(response)
        assertEquals(1, calls.size)
        assertEquals("test", calls[0].name)
    }

    @Test
    fun `parse tool call with nested arguments`() {
        val response = """<tool_call>{"name": "write", "arguments": {"path": "/tmp/f.txt", "content": "hello\nworld"}}</tool_call>"""
        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals("write", result.toolCalls!![0].name)
        assertEquals("/tmp/f.txt", result.toolCalls!![0].arguments["path"])
    }
}
