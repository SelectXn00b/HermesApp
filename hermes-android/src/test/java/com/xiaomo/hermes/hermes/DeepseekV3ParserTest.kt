package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * DeepSeekV3ToolCallParser 测试。
 *
 * 格式参考 `environments/tool_call_parsers/deepseek_v3_parser.py` 和
 * 上游 `tests/tools/test_tool_call_parsers.py::TestDeepSeekV3Parser`：
 *
 *     <｜tool▁calls▁begin｜>
 *     <｜tool▁call▁begin｜>function<｜tool▁sep｜>get_weather
 *     ```json
 *     {"city": "London"}
 *     ```<｜tool▁call▁end｜>
 *     <｜tool▁calls▁end｜>
 */
class DeepseekV3ParserTest {

    private val parser = DeepSeekV3ToolCallParser()

    @Test
    fun `parse no tool call returns original content and null`() {
        val text = "Hello, how can I help you?"
        val result = parser.parse(text)
        assertEquals(text, result.content)
        assertNull(result.toolCalls)
    }

    @Test
    fun `parse single tool call`() {
        val text =
            "<｜tool▁calls▁begin｜><｜tool▁call▁begin｜>function<｜tool▁sep｜>get_weather\n" +
            "```json\n{\"city\": \"London\"}\n```<｜tool▁call▁end｜><｜tool▁calls▁end｜>"
        val result = parser.parse(text)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("get_weather", result.toolCalls!![0].name)
        assertEquals("{\"city\": \"London\"}", result.toolCalls!![0].rawArguments)
    }

    @Test
    fun `parse multiple tool calls`() {
        val text =
            "<｜tool▁calls▁begin｜>" +
            "<｜tool▁call▁begin｜>function<｜tool▁sep｜>get_weather\n" +
            "```json\n{\"city\": \"London\"}\n```<｜tool▁call▁end｜>" +
            "<｜tool▁call▁begin｜>function<｜tool▁sep｜>get_time\n" +
            "```json\n{\"timezone\": \"UTC\"}\n```<｜tool▁call▁end｜>" +
            "<｜tool▁calls▁end｜>"
        val result = parser.parse(text)

        assertNotNull(result.toolCalls)
        assertEquals(2, result.toolCalls!!.size)
        val names = result.toolCalls!!.map { it.name }
        assertTrue("expected get_weather in $names", "get_weather" in names)
        assertTrue("expected get_time in $names", "get_time" in names)
    }

    @Test
    fun `parse tool call with preceding text`() {
        val text =
            "Let me check that for you.\n" +
            "<｜tool▁calls▁begin｜><｜tool▁call▁begin｜>function<｜tool▁sep｜>terminal\n" +
            "```json\n{\"command\": \"ls\"}\n```<｜tool▁call▁end｜><｜tool▁calls▁end｜>"
        val result = parser.parse(text)

        assertNotNull(result.toolCalls)
        assertEquals(1, result.toolCalls!!.size)
        assertEquals("terminal", result.toolCalls!![0].name)
        assertEquals("Let me check that for you.", result.content)
    }
}
