package com.xiaomo.androidforclaw.providers

import com.xiaomo.androidforclaw.providers.llm.Message
import com.xiaomo.androidforclaw.providers.llm.ToolCall
import org.junit.Assert.assertFalse
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
}
