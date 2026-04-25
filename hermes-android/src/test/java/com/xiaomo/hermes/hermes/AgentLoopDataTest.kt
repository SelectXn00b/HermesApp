package com.xiaomo.hermes.hermes

import org.junit.Assert.*
import org.junit.Test

/**
 * AgentLoop 中的数据类测试
 */
class AgentLoopDataTest {

    @Test
    fun `ToolError construction`() {
        val err = ToolError(
            turn = 1,
            toolName = "read",
            arguments = """{"path":"/tmp"}""",
            error = "file not found",
            toolResult = "error: file not found"
        )
        assertEquals(1, err.turn)
        assertEquals("read", err.toolName)
        assertEquals("file not found", err.error)
    }

    @Test
    fun `AgentResult defaults`() {
        val result = AgentResult(
            messages = listOf(mapOf("role" to "user", "content" to "hello"))
        )
        assertEquals(1, result.messages.size)
        assertNull(result.managedState)
        assertEquals(0, result.turnsUsed)
        assertFalse(result.finishedNaturally)
        assertTrue(result.reasoningPerTurn.isEmpty())
        assertTrue(result.toolErrors.isEmpty())
    }

    @Test
    fun `ChatCompletionResponse structure`() {
        val msg = AssistantMessage(
            content = "test",
            toolCalls = null
        )
        val choice = Choice(message = msg)
        val response = ChatCompletionResponse(choices = listOf(choice))

        assertEquals(1, response.choices.size)
        assertEquals("test", response.choices[0].message.content)
    }

    @Test
    fun `AssistantMessage extractReasoning from reasoningContent`() {
        val msg = AssistantMessage(
            content = "response",
            toolCalls = null,
            reasoningContent = "my reasoning"
        )
        assertEquals("my reasoning", msg.extractReasoning())
    }

    @Test
    fun `AssistantMessage extractReasoning from reasoning field`() {
        val msg = AssistantMessage(
            content = "response",
            toolCalls = null,
            reasoning = "thinking..."
        )
        assertEquals("thinking...", msg.extractReasoning())
    }

    @Test
    fun `AssistantMessage extractReasoning from reasoningDetails`() {
        val msg = AssistantMessage(
            content = "response",
            toolCalls = null,
            reasoningDetails = listOf(ReasoningDetail(text = "detail reasoning"))
        )
        assertEquals("detail reasoning", msg.extractReasoning())
    }

    @Test
    fun `AssistantMessage extractReasoning returns null when no reasoning`() {
        val msg = AssistantMessage(
            content = "no reasoning",
            toolCalls = null
        )
        assertNull(msg.extractReasoning())
    }

    @Test
    fun `ToolCall and ToolCallFunction`() {
        val func = ToolCallFunction(name = "read", arguments = """{"path": "/tmp"}""")
        val tc = ToolCall(id = "call_123", function = func)

        assertEquals("call_123", tc.id)
        assertEquals("function", tc.type)
        assertEquals("read", tc.function.name)
        assertTrue(tc.function.arguments.contains("path"))
    }

    @Test
    fun `ReasoningDetail data class`() {
        val detail = ReasoningDetail(text = "let me think...")
        assertEquals("let me think...", detail.text)

        val empty = ReasoningDetail()
        assertNull(empty.text)
    }
}
