package com.xiaomo.androidforclaw.providers

import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiAdapterToolCallFormatTest {

    @Test
    fun `assistant tool call turn uses null content when text is empty`() {
        val message = Message(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                ToolCall(
                    id = "call_function_ein6jzgsgxfe_1",
                    name = "read",
                    arguments = "{\"path\":\"/tmp/a.txt\"}"
                )
            )
        )

        assertTrue(ApiAdapter.shouldUseNullContentForAssistantToolCall(message))
    }

    @Test
    fun `assistant plain text does not use null content`() {
        val message = Message(
            role = "assistant",
            content = "hello",
            toolCalls = null
        )

        assertFalse(ApiAdapter.shouldUseNullContentForAssistantToolCall(message))
    }

    @Test
    fun `assistant tool call with visible text keeps string content`() {
        val message = Message(
            role = "assistant",
            content = "我先帮你查一下",
            toolCalls = listOf(
                ToolCall(
                    id = "call_123",
                    name = "read",
                    arguments = "{}"
                )
            )
        )

        assertFalse(ApiAdapter.shouldUseNullContentForAssistantToolCall(message))
    }

    @Test
    fun `responses api builds function call spec with call id`() {
        val message = Message(
            role = "assistant",
            content = "",
            toolCalls = listOf(
                ToolCall(
                    id = "call_function_5qpgmzyic4xr_1",
                    name = "read",
                    arguments = "{\"path\":\"/tmp/a.txt\"}"
                )
            )
        )

        val items = ApiAdapter.buildResponsesFunctionCallItemsSpec(message)

        assertEquals(1, items.size)
        assertEquals("function_call", items[0].type)
        assertEquals("call_function_5qpgmzyic4xr_1", items[0].callId)
        assertEquals("read", items[0].name)
    }

    @Test
    fun `responses api builds function call output spec with matching call id`() {
        val message = Message(
            role = "tool",
            content = "hello",
            toolCallId = "call_function_5qpgmzyic4xr_1",
            name = "read"
        )

        val item = ApiAdapter.buildResponsesFunctionCallOutputItemSpec(message)

        assertNotNull(item)
        assertEquals("function_call_output", item!!.type)
        assertEquals("call_function_5qpgmzyic4xr_1", item.callId)
        assertEquals("hello", item.output)
    }

    @Test
    fun `responses api skips output spec when tool call id missing`() {
        val message = Message(
            role = "tool",
            content = "hello",
            toolCallId = null,
            name = "read"
        )

        val item = ApiAdapter.buildResponsesFunctionCallOutputItemSpec(message)

        assertNull(item)
    }
}
