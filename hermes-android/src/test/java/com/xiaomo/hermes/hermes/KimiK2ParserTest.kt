package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * KimiK2ToolCallParser 测试
 */
class KimiK2ParserTest {

    private val parser = KimiK2ToolCallParser()

    @Test
    fun `parse single tool call`() {
        val response = """I'll check the weather.
<|tool_calls_section_begin|>
<|tool_call_begin|>functions.get_weather:0<|tool_call_argument_begin|>{"city": "Shanghai"}<|tool_call_end|>
<|tool_calls_section_end|>"""

        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("get_weather", result.toolCalls!![0].name)
        assertEquals("Shanghai", result.toolCalls!![0].arguments["city"])
        assertEquals("I'll check the weather.", result.content)
    }

    @Test
    fun `parse multiple tool calls`() {
        val response = """<|tool_calls_section_begin|>
<|tool_call_begin|>functions.read:0<|tool_call_argument_begin|>{"path": "a.txt"}<|tool_call_end|>
<|tool_call_begin|>functions.read:1<|tool_call_argument_begin|>{"path": "b.txt"}<|tool_call_end|>
<|tool_calls_section_end|>"""

        val result = parser.parse(response)
        assertNotNull(result.toolCalls)
        assertEquals(2, result.toolCalls!!.size)
        assertEquals("a.txt", result.toolCalls!![0].arguments["path"])
        assertEquals("b.txt", result.toolCalls!![1].arguments["path"])
    }

    @Test
    fun `parse no tool calls`() {
        val result = parser.parse("normal response without special tokens")
        assertNull(result.toolCalls)
        assertEquals("normal response without special tokens", result.content)
    }
}
