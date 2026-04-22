package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * ToolCallParser 基类、ParseResult、ParsedToolCall 数据类测试
 */
class ToolCallParserTest {

    @Test
    fun `ParseResult with content and no tool calls`() {
        val result = ParseResult("hello", null)
        assertEquals("hello", result.content)
        assertNull(result.toolCalls)
    }

    @Test
    fun `ParseResult with tool calls`() {
        val calls = listOf(
            ParsedToolCall("id1", "func1", mapOf("a" to "b")),
            ParsedToolCall("id2", "func2", emptyMap())
        )
        val result = ParseResult(null, calls)
        assertNull(result.content)
        assertEquals(2, result.toolCalls!!.size)
        assertEquals("func1", result.toolCalls!![0].name)
        assertEquals("b", result.toolCalls!![0].arguments["a"])
    }

    @Test
    fun `ParsedToolCall equality`() {
        val a = ParsedToolCall("id1", "read", mapOf("path" to "/tmp"), "{\"path\":\"/tmp\"}")
        val b = ParsedToolCall("id1", "read", mapOf("path" to "/tmp"), "{\"path\":\"/tmp\"}")
        assertEquals(a, b)
    }

    @Test
    fun `ToolCallParserRegistry createDefault registers built-in parsers`() {
        val registry = ToolCallParserRegistry.createDefault()
        val parserNames = registry.listParsers()
        assertTrue("Should have hermes parser", parserNames.contains("hermes"))
        assertTrue("Should have mistral parser", parserNames.contains("mistral"))
        assertTrue("Should have kimi_k2 parser", parserNames.contains("kimi_k2"))
        assertTrue("Should have glm45 parser", parserNames.contains("glm45"))
        // DeepSeekV3 不在 BUILT_IN_PARSERS 中，需要手动注册
        assertTrue("Should have multiple parsers", parserNames.size >= 5)
    }

    @Test
    fun `ToolCallParserRegistry getParser returns correct parser`() {
        val registry = ToolCallParserRegistry.createDefault()
        val hermesParser = registry.getParser("hermes")
        assertNotNull(hermesParser)
        assertTrue(hermesParser is HermesToolCallParser)
    }

    @Test
    fun `ToolCallParserRegistry getParser returns null for unknown`() {
        val registry = ToolCallParserRegistry.createDefault()
        assertNull(registry.getParser("nonexistent_model"))
    }

    @Test
    fun `ToolCallParserRegistry findParser fuzzy match`() {
        val registry = ToolCallParserRegistry.createDefault()
        // "hermes" 应该模糊匹配到 hermes parser
        val parser = registry.findParser("hermes-3-pro")
        assertNotNull(parser)
    }

    @Test
    fun `ToolCallParserRegistry register custom parser`() {
        val registry = ToolCallParserRegistry()
        val custom = object : ToolCallParser() {
            override val supportedModels = listOf("my_model")
            override fun parse(response: String) = ParseResult(response, null)
            override fun formatToolCalls(toolCalls: List<ParsedToolCall>) = ""
            override fun hasToolCall(response: String) = false
        }
        registry.register(custom)
        assertNotNull(registry.getParser("my_model"))
    }
}
