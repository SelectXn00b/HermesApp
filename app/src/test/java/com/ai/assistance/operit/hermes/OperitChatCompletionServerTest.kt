package com.ai.assistance.operit.hermes

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.xiaomo.hermes.hermes.ChatCompletionServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Pure-JVM tests for the reverse bridge that lets HermesAgentLoop drive an
 * Operit AIService — the parts that don't touch Android runtime:
 *   - extractToolCalls: XML <tool name=…><param name=…>…</param></tool> → ToolCall list
 *   - buildToolCallIdToNameMap: round-trip tool_call_id → tool name for later tool/role messages
 *   - toPromptTurn: wrap role="tool" content as <tool_result> XML the provider recognizes
 *   - detectToolResultStatus: JSON {"success":…} → "success"/"error"
 */
class OperitChatCompletionServerTest {

    private lateinit var server: OperitChatCompletionServer

    @Before fun setup() {
        // Mock Context + AIService because the methods we're testing don't touch
        // them — we only need a valid instance to access internal members.
        val ctx = mock(Context::class.java)
        val service = mock(AIService::class.java)
        server = OperitChatCompletionServer(context = ctx, service = service)
        // Sanity: bridge is a ChatCompletionServer.
        assertTrue(server is ChatCompletionServer)
    }

    // ---------- extractToolCalls ----------

    @Test fun extractToolCalls_noXml_returnsNull() {
        assertNull(server.extractToolCalls("hello world, no tool tags here"))
    }

    @Test fun extractToolCalls_singleToolWithParams_synthesizesToolCall() {
        val text = """
            thinking…
            <tool name="read_file"><param name="path">/tmp/x</param><param name="limit">10</param></tool>
            done.
        """.trimIndent()
        val calls = server.extractToolCalls(text)
        assertNotNull(calls)
        assertEquals(1, calls!!.size)
        val call = calls[0]
        assertEquals("function", call.type)
        assertEquals("read_file", call.function.name)
        assertTrue(call.id.startsWith("call_"))
        val args = JSONObject(call.function.arguments)
        assertEquals("/tmp/x", args.getString("path"))
        assertEquals("10", args.getString("limit"))
    }

    @Test fun extractToolCalls_multipleTools_preservesOrder() {
        val text = """
            <tool name="a"><param name="x">1</param></tool>
            <tool name="b"><param name="y">2</param></tool>
        """.trimIndent()
        val calls = server.extractToolCalls(text)
        assertNotNull(calls)
        assertEquals(listOf("a", "b"), calls!!.map { it.function.name })
    }

    @Test fun extractToolCalls_emptyBody_producesEmptyArgs() {
        val calls = server.extractToolCalls("""<tool name="noop"></tool>""")
        assertNotNull(calls)
        assertEquals("{}", calls!![0].function.arguments)
    }

    @Test fun extractToolCalls_generatesDistinctIds() {
        val text = """
            <tool name="a"><param name="k">1</param></tool>
            <tool name="a"><param name="k">2</param></tool>
        """.trimIndent()
        val calls = server.extractToolCalls(text)!!
        assertEquals(2, calls.size)
        assertTrue(calls[0].id != calls[1].id)
    }

    // ---------- buildToolCallIdToNameMap ----------

    @Test fun buildToolCallIdToNameMap_assistantToolCalls_areMapped() {
        val msgs: List<Map<String, Any?>> = listOf(
            mapOf("role" to "user", "content" to "hi"),
            mapOf(
                "role" to "assistant",
                "content" to "",
                "tool_calls" to listOf(
                    mapOf(
                        "id" to "call_abc",
                        "type" to "function",
                        "function" to mapOf("name" to "read_file", "arguments" to "{}")
                    ),
                    mapOf(
                        "id" to "call_def",
                        "type" to "function",
                        "function" to mapOf("name" to "search", "arguments" to "{}")
                    )
                )
            )
        )
        val map = server.buildToolCallIdToNameMap(msgs)
        assertEquals(mapOf("call_abc" to "read_file", "call_def" to "search"), map)
    }

    @Test fun buildToolCallIdToNameMap_ignoresNonAssistantRoles() {
        val msgs: List<Map<String, Any?>> = listOf(
            mapOf(
                "role" to "user",
                "tool_calls" to listOf(
                    mapOf("id" to "x", "function" to mapOf("name" to "y"))
                )
            )
        )
        assertTrue(server.buildToolCallIdToNameMap(msgs).isEmpty())
    }

    @Test fun buildToolCallIdToNameMap_skipsMalformedEntries() {
        val msgs: List<Map<String, Any?>> = listOf(
            mapOf(
                "role" to "assistant",
                "tool_calls" to listOf(
                    mapOf("id" to null),                              // missing id
                    mapOf("id" to "ok", "function" to "notAMap"),     // bad function
                    mapOf("id" to "good", "function" to mapOf("name" to "yes"))
                )
            )
        )
        assertEquals(mapOf("good" to "yes"), server.buildToolCallIdToNameMap(msgs))
    }

    @Test fun buildToolCallIdToNameMap_noToolCalls_returnsEmpty() {
        val msgs: List<Map<String, Any?>> = listOf(
            mapOf("role" to "assistant", "content" to "just text")
        )
        assertTrue(server.buildToolCallIdToNameMap(msgs).isEmpty())
    }

    // ---------- toPromptTurn ----------

    @Test fun toPromptTurn_systemRole_producesSystemKind() {
        val turn = with(server) {
            mapOf<String, Any?>("role" to "system", "content" to "sys").toPromptTurn(emptyMap())
        }
        assertEquals(PromptTurnKind.SYSTEM, turn.kind)
        assertEquals("sys", turn.content)
    }

    @Test fun toPromptTurn_userRole_producesUserKind() {
        val turn = with(server) {
            mapOf<String, Any?>("role" to "user", "content" to "hi").toPromptTurn(emptyMap())
        }
        assertEquals(PromptTurnKind.USER, turn.kind)
        assertEquals("hi", turn.content)
    }

    @Test fun toPromptTurn_toolRole_wrapsContentAsToolResultXml() {
        val msg = mapOf<String, Any?>(
            "role" to "tool",
            "tool_call_id" to "call_abc",
            "content" to """{"success":true,"result":"ok","error":null}"""
        )
        val turn = with(server) { msg.toPromptTurn(mapOf("call_abc" to "read_file")) }
        assertEquals(PromptTurnKind.TOOL_RESULT, turn.kind)
        assertEquals("read_file", turn.toolName)
        val content = turn.content
        assertTrue("should start with <tool_result>", content.startsWith("<tool_result"))
        assertTrue("should tag name", content.contains("name=\"read_file\""))
        assertTrue("should tag status success", content.contains("status=\"success\""))
        assertTrue("should wrap body in <content>", content.contains("<content>"))
    }

    @Test fun toPromptTurn_toolRole_errorJson_marksStatusError() {
        val msg = mapOf<String, Any?>(
            "role" to "tool",
            "tool_call_id" to "c1",
            "content" to """{"success":false,"error":"boom"}"""
        )
        val turn = with(server) { msg.toPromptTurn(mapOf("c1" to "t")) }
        assertTrue(turn.content.contains("status=\"error\""))
    }

    @Test fun toPromptTurn_toolRole_unknownIdFallsBackToLiteralTool() {
        val msg = mapOf<String, Any?>(
            "role" to "tool",
            "tool_call_id" to "unknown",
            "content" to "{}"
        )
        val turn = with(server) { msg.toPromptTurn(emptyMap()) }
        assertEquals("tool", turn.toolName)
    }

    @Test fun toPromptTurn_toolRole_alreadyWrapped_passesThrough() {
        val xml = "<tool_result name=\"x\" status=\"success\"><content>already</content></tool_result>"
        val msg = mapOf<String, Any?>(
            "role" to "tool",
            "tool_call_id" to "call_x",
            "content" to xml
        )
        val turn = with(server) { msg.toPromptTurn(mapOf("call_x" to "x")) }
        assertEquals(xml, turn.content)
    }

    @Test fun toPromptTurn_contentAsList_joinsTextFields() {
        val msg = mapOf<String, Any?>(
            "role" to "user",
            "content" to listOf(
                mapOf("text" to "line1"),
                mapOf("text" to "line2")
            )
        )
        val turn = with(server) { msg.toPromptTurn(emptyMap()) }
        assertEquals("line1\nline2", turn.content)
    }

    @Test fun toPromptTurn_nullContent_becomesEmpty() {
        val msg = mapOf<String, Any?>("role" to "user", "content" to null)
        val turn = with(server) { msg.toPromptTurn(emptyMap()) }
        assertEquals("", turn.content)
    }

    // ---------- detectToolResultStatus ----------

    @Test fun detectToolResultStatus_success_true() {
        assertEquals("success", server.detectToolResultStatus("""{"success":true}"""))
    }

    @Test fun detectToolResultStatus_success_falseIsError() {
        assertEquals("error", server.detectToolResultStatus("""{"success":false,"error":"nope"}"""))
    }

    @Test fun detectToolResultStatus_errorFieldPresent_isError() {
        assertEquals(
            "error",
            server.detectToolResultStatus("""{"success":true,"error":"warn"}""")
        )
    }

    @Test fun detectToolResultStatus_malformedJson_defaultsToSuccess() {
        assertEquals("success", server.detectToolResultStatus("not json at all"))
    }
}
