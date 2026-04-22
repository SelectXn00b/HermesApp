package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * DeepSeekV3ToolCallParser 测试
 * 格式: ❤️tool_name❤️
 */
class DeepseekV3ParserTest {

    private val parser = DeepSeekV3ToolCallParser()

    @Test
    fun `parse single tool call`() {
        val response = "Let me ❤️read_file❤️ that"
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("read_file", result.toolCalls!![0].name)
        assertTrue(result.toolCalls!![0].arguments.isEmpty())
    }

    @Test
    fun `parse multiple tool calls`() {
        val response = "❤️read❤️ then ❤️write❤️"
        val result = parser.parse(response)

        assertNotNull(result.toolCalls)
        assertEquals(2, result.toolCalls!!.size)
        assertEquals("read", result.toolCalls!![0].name)
        assertEquals("write", result.toolCalls!![1].name)
    }

    @Test
    fun `parse no tool calls`() {
        val result = parser.parse("normal text")
        assertNotNull(result.toolCalls)
        assertTrue(result.toolCalls!!.isEmpty())
    }
}
