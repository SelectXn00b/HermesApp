package com.xiaomo.hermes.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the parsers that previously had no dedicated test:
 *   LongcatToolCallParser, Qwen3CoderToolCallParser, LlamaToolCallParser,
 *   Glm47ToolCallParser, QwenToolCallParser, DeepSeekV31ToolCallParser.
 *
 * Requirement map: R-PARSER-001..053 (see docs/hermes-requirements.md)
 * Test cases:      TC-PARSER-001..053 (see docs/hermes-test-cases.md)
 */
class MissingParsersTest {

    // ---- Longcat ----

    @Test
    fun `Longcat parses single tool call`() {
        val p = LongcatToolCallParser()
        val resp = """<longcat_tool_call>{"name":"read","arguments":{"path":"/tmp/a"}}</longcat_tool_call>"""
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals(1, r.toolCalls!!.size)
        assertEquals("read", r.toolCalls!![0].name)
        assertEquals("/tmp/a", r.toolCalls!![0].arguments["path"])
    }

    @Test
    fun `Longcat returns null when no tag`() {
        val p = LongcatToolCallParser()
        val r = p.parse("no tool call here")
        assertNull(r.toolCalls)
    }

    @Test
    fun `Longcat supportedModels includes longcat`() {
        assertTrue(LongcatToolCallParser().supportedModels.contains("longcat"))
    }

    @Test
    fun `Longcat skips entries with empty name`() {
        val p = LongcatToolCallParser()
        val resp = """<longcat_tool_call>{"arguments":{"a":1}}</longcat_tool_call>"""
        val r = p.parse(resp)
        assertNull(r.toolCalls)
    }

    // ---- Qwen3 Coder ----

    @Test
    fun `Qwen3Coder parses function with parameters`() {
        val p = Qwen3CoderToolCallParser()
        val resp = """<tool_call><function=read><parameter=path>/tmp/x</parameter></function></tool_call>"""
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals("read", r.toolCalls!![0].name)
        assertEquals("/tmp/x", r.toolCalls!![0].arguments["path"])
    }

    @Test
    fun `Qwen3Coder converts boolean parameter`() {
        val p = Qwen3CoderToolCallParser()
        val resp = """<tool_call><function=fn><parameter=flag>true</parameter></function></tool_call>"""
        val r = p.parse(resp)
        assertEquals(true, r.toolCalls!![0].arguments["flag"])
    }

    @Test
    fun `Qwen3Coder no tool_call returns null`() {
        val p = Qwen3CoderToolCallParser()
        assertNull(p.parse("hello world").toolCalls)
    }

    @Test
    fun `Qwen3Coder _parseFunctionCall returns null when no gt`() {
        val p = Qwen3CoderToolCallParser()
        assertNull(p._parseFunctionCall("no_gt_here"))
    }

    @Test
    fun `Qwen3Coder supportedModels`() {
        assertTrue(Qwen3CoderToolCallParser().supportedModels.contains("qwen3_coder"))
    }

    // ---- Llama ----

    @Test
    fun `Llama parses arguments object`() {
        val p = LlamaToolCallParser()
        val resp = """<|python_tag|>{"name":"read","arguments":{"path":"/a"}}"""
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals("read", r.toolCalls!![0].name)
        assertEquals("/a", r.toolCalls!![0].arguments["path"])
    }

    @Test
    fun `Llama accepts parameters key as synonym`() {
        val p = LlamaToolCallParser()
        val resp = """{"name":"fn","parameters":{"k":"v"}}"""
        val r = p.parse(resp)
        assertEquals("v", r.toolCalls!![0].arguments["k"])
    }

    @Test
    fun `Llama no json and no token returns null`() {
        val p = LlamaToolCallParser()
        assertNull(p.parse("plain text only").toolCalls)
    }

    @Test
    fun `Llama invalid json returns null`() {
        val p = LlamaToolCallParser()
        assertNull(p.parse("{not valid json").toolCalls)
    }

    @Test
    fun `Llama supportedModels`() {
        assertTrue(LlamaToolCallParser().supportedModels.contains("llama3_json"))
        assertTrue(LlamaToolCallParser().supportedModels.contains("llama4_json"))
    }

    // ---- Glm 4.7 ----

    @Test
    fun `Glm47 supportedModels`() {
        assertEquals(listOf("glm47"), Glm47ToolCallParser().supportedModels)
    }

    @Test
    fun `Glm47 uses arg_key arg_value syntax`() {
        val p = Glm47ToolCallParser()
        val resp = """<tool_call>read
<arg_key>path</arg_key>
<arg_value>/etc/hosts</arg_value></tool_call>"""
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals("read", r.toolCalls!![0].name)
        assertEquals("/etc/hosts", r.toolCalls!![0].arguments["path"])
    }

    @Test
    fun `Glm47 no tool call returns null`() {
        assertNull(Glm47ToolCallParser().parse("hello").toolCalls)
    }

    // ---- Qwen 2.5 (inherits Hermes) ----

    @Test
    fun `Qwen supportedModels contains qwen`() {
        assertEquals(listOf("qwen"), QwenToolCallParser().supportedModels)
    }

    @Test
    fun `Qwen parses Hermes-format tool call`() {
        val p = QwenToolCallParser()
        val resp = """<tool_call>{"name":"read","arguments":{"path":"/t"}}</tool_call>"""
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals("read", r.toolCalls!![0].name)
    }

    // ---- DeepseekV3.1 (heart-emoji delimiter) ----

    @Test
    fun `DeepseekV31 supportedModels`() {
        val models = DeepSeekV31ToolCallParser().supportedModels
        assertTrue(models.contains("deepseek_v3_1"))
        assertTrue(models.contains("deepseek_v31"))
    }

    @Test
    fun `DeepseekV31 parses heart-emoji delimited call`() {
        val p = DeepSeekV31ToolCallParser()
        val resp = "\u2764\ufe0fmyFunction\u2764\ufe0f"
        val r = p.parse(resp)
        assertNotNull(r.toolCalls)
        assertEquals(1, r.toolCalls!!.size)
        assertEquals("myFunction", r.toolCalls!![0].name)
        assertTrue(r.toolCalls!![0].arguments.isEmpty())
    }

    @Test
    fun `DeepseekV31 no delimiter returns empty list`() {
        val p = DeepSeekV31ToolCallParser()
        val r = p.parse("plain text")
        assertNotNull(r.toolCalls)
        assertTrue(r.toolCalls!!.isEmpty())
    }

    @Test
    fun `DeepseekV31 parses multiple calls`() {
        val p = DeepSeekV31ToolCallParser()
        val resp = "\u2764\ufe0ffn1\u2764\ufe0f some text \u2764\ufe0ffn2\u2764\ufe0f"
        val r = p.parse(resp)
        assertEquals(2, r.toolCalls!!.size)
        assertEquals("fn1", r.toolCalls!![0].name)
        assertEquals("fn2", r.toolCalls!![1].name)
    }
}
