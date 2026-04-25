package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarifyToolTest {

    @Test
    fun `MAX_CHOICES constant is 4`() {
        assertEquals(4, MAX_CHOICES)
    }

    @Test
    fun `clarifyTool returns error when question is blank`() {
        val result = clarifyTool(question = "")
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
    }

    @Test
    fun `clarifyTool returns error when question is whitespace only`() {
        val result = clarifyTool(question = "   \t\n  ")
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
    }

    @Test
    fun `clarifyTool returns not-available error when no callback provided`() {
        val result = clarifyTool(question = "What is your name?")
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
        assertTrue("not available" in obj.getString("error"))
    }

    @Test
    fun `clarifyTool invokes callback and returns user response`() {
        val result = clarifyTool(
            question = "Which color?",
            choices = listOf("red", "blue"),
            callback = { q, choices ->
                assertEquals("Which color?", q)
                assertEquals(listOf("red", "blue"), choices)
                "red"
            },
        )
        val obj = JSONObject(result)
        assertEquals("Which color?", obj.getString("question"))
        assertEquals("red", obj.getString("user_response"))
    }

    @Test
    fun `clarifyTool trims question whitespace`() {
        val result = clarifyTool(
            question = "   trimmed?   ",
            callback = { _, _ -> "answered" },
        )
        val obj = JSONObject(result)
        assertEquals("trimmed?", obj.getString("question"))
    }

    @Test
    fun `clarifyTool trims user response`() {
        val result = clarifyTool(
            question = "q?",
            callback = { _, _ -> "  trimmed_response  " },
        )
        val obj = JSONObject(result)
        assertEquals("trimmed_response", obj.getString("user_response"))
    }

    @Test
    fun `clarifyTool truncates choices beyond MAX_CHOICES`() {
        val tooMany = (1..10).map { "choice$it" }
        val captured = mutableListOf<String>()
        clarifyTool(
            question = "pick one",
            choices = tooMany,
            callback = { _, choices ->
                choices?.forEach { captured.add(it) }
                "choice1"
            },
        )
        assertEquals(MAX_CHOICES, captured.size)
        assertEquals("choice1", captured.first())
        assertEquals("choice4", captured.last())
    }

    @Test
    fun `clarifyTool treats empty choice list as no choices`() {
        var observed: List<String>? = listOf("sentinel")
        clarifyTool(
            question = "open ended?",
            choices = emptyList(),
            callback = { _, choices -> observed = choices; "yes" },
        )
        assertNull(observed)
    }

    @Test
    fun `clarifyTool filters empty and whitespace-only choices`() {
        var observed: List<String>? = null
        clarifyTool(
            question = "pick?",
            choices = listOf("", "  ", "real", "   also real  "),
            callback = { _, choices -> observed = choices; "real" },
        )
        assertEquals(listOf("real", "also real"), observed)
    }

    @Test
    fun `clarifyTool returns error when callback throws`() {
        val result = clarifyTool(
            question = "q?",
            callback = { _, _ -> throw RuntimeException("callback failed") },
        )
        val obj = JSONObject(result)
        assertTrue(obj.has("error"))
        assertTrue("callback failed" in obj.getString("error"))
    }

    @Test
    fun `clarifyTool result contains null choices_offered when none supplied`() {
        val result = clarifyTool(
            question = "q?",
            callback = { _, _ -> "answer" },
        )
        val obj = JSONObject(result)
        assertTrue(obj.isNull("choices_offered"))
    }

    @Test
    fun `checkClarifyRequirements always returns true`() {
        assertTrue(checkClarifyRequirements())
    }

    @Test
    fun `CLARIFY_SCHEMA has expected shape`() {
        assertEquals("clarify", CLARIFY_SCHEMA["name"])
        assertNotNull(CLARIFY_SCHEMA["description"])
        @Suppress("UNCHECKED_CAST")
        val params = CLARIFY_SCHEMA["parameters"] as Map<String, Any>
        assertEquals("object", params["type"])
        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("question"), required)
    }

    @Test
    fun `CLARIFY_SCHEMA choices maxItems matches MAX_CHOICES`() {
        @Suppress("UNCHECKED_CAST")
        val params = CLARIFY_SCHEMA["parameters"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val properties = params["properties"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val choices = properties["choices"] as Map<String, Any>
        assertEquals(MAX_CHOICES, choices["maxItems"])
    }
}
