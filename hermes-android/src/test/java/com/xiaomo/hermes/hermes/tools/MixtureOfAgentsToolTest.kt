package com.xiaomo.hermes.hermes.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MixtureOfAgentsTool.kt — MoA methodology that routes a hard
 * problem through multiple frontier LLMs. Android is a stub; the public
 * call returns an error JSON, but the configuration constants
 * (REFERENCE_MODELS, AGGREGATOR_MODEL, temperatures, MIN_SUCCESSFUL_REFERENCES)
 * must match the Python upstream exactly.
 *
 * Covers TC-TOOL-329-a (4 reference + 1 aggregator model defaults).
 */
class MixtureOfAgentsToolTest {

    // ── R-TOOL-329 / TC-TOOL-329-a: reference/aggregator model defaults ──
    /**
     * TC-TOOL-329-a — Python upstream ships 4 reference models + 1 aggregator:
     *   - reference: anthropic/claude-opus-4.6, google/gemini-3-pro-preview,
     *                openai/gpt-5.4-pro, deepseek/deepseek-v3.2
     *   - aggregator: anthropic/claude-opus-4.6
     *   - reference_temperature: 0.6, aggregator_temperature: 0.4
     *   - min_successful_references: 1
     */
    @Test
    fun `4+1 defaults`() {
        assertEquals(4, REFERENCE_MODELS.size)
        assertEquals(
            listOf(
                "anthropic/claude-opus-4.6",
                "google/gemini-3-pro-preview",
                "openai/gpt-5.4-pro",
                "deepseek/deepseek-v3.2"),
            REFERENCE_MODELS)
        assertEquals("anthropic/claude-opus-4.6", AGGREGATOR_MODEL)
        assertEquals(0.6, REFERENCE_TEMPERATURE, 0.0)
        assertEquals(0.4, AGGREGATOR_TEMPERATURE, 0.0)
        assertEquals(1, MIN_SUCCESSFUL_REFERENCES)
    }

    // ── Aggregator temperature < reference temperature (synthesis = focused) ──
    @Test
    fun `aggregator temperature below reference temperature`() {
        assertTrue(
            "aggregator should be more focused than references",
            AGGREGATOR_TEMPERATURE < REFERENCE_TEMPERATURE)
    }

    // ── Aggregator is in the reference pool (acts as both) ──
    @Test
    fun `aggregator model is in reference pool`() {
        assertTrue(
            "aggregator model should appear in reference_models",
            AGGREGATOR_MODEL in REFERENCE_MODELS)
    }

    // ── getMoaConfiguration exposes all five fields ──
    @Test
    fun `getMoaConfiguration exposes all constants`() {
        val cfg = getMoaConfiguration()
        assertEquals(REFERENCE_MODELS, cfg["reference_models"])
        assertEquals(AGGREGATOR_MODEL, cfg["aggregator_model"])
        assertEquals(REFERENCE_TEMPERATURE, cfg["reference_temperature"])
        assertEquals(AGGREGATOR_TEMPERATURE, cfg["aggregator_temperature"])
        assertEquals(MIN_SUCCESSFUL_REFERENCES, cfg["min_successful_references"])
        assertEquals(5, cfg.size)
    }

    // ── AGGREGATOR_SYSTEM_PROMPT is non-empty and mentions synthesis ──
    @Test
    fun `AGGREGATOR_SYSTEM_PROMPT mentions synthesize`() {
        assertTrue(AGGREGATOR_SYSTEM_PROMPT.isNotEmpty())
        assertTrue(
            "prompt should describe synthesis task",
            AGGREGATOR_SYSTEM_PROMPT.contains("synthesize", ignoreCase = true))
    }

    // ── MOA_SCHEMA shape ──
    @Test
    fun `MOA_SCHEMA name and required`() {
        assertEquals("mixture_of_agents", MOA_SCHEMA["name"])
        @Suppress("UNCHECKED_CAST")
        val params = MOA_SCHEMA["parameters"] as Map<String, Any?>
        assertEquals("object", params["type"])
        assertEquals(listOf("user_prompt"), params["required"])
    }

    @Test
    fun `MOA_SCHEMA properties include user_prompt`() {
        @Suppress("UNCHECKED_CAST")
        val params = MOA_SCHEMA["parameters"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val props = params["properties"] as Map<String, Any?>
        assertTrue("user_prompt" in props.keys)
    }

    // ── checkMoaRequirements reflects env state ──
    @Test
    fun `checkMoaRequirements mirrors OPENROUTER_API_KEY presence`() {
        val hasKey = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
        assertEquals(hasKey, checkMoaRequirements())
    }

    @Test
    fun `checkMoaRequirements false without env`() {
        if (!System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) return
        assertFalse(checkMoaRequirements())
    }

    // ── mixtureOfAgentsTool Android stub returns error JSON ──
    @Test
    fun `mixtureOfAgentsTool returns error on Android`() {
        val result = mixtureOfAgentsTool("solve fermat's last theorem")
        val json = JSONObject(result)
        assertTrue("must have error key: $result", json.has("error"))
        val err = json.getString("error")
        assertTrue(
            "must mention Android unavailability: $err",
            err.contains("Android") || err.contains("OpenRouter"))
    }

    @Test
    fun `mixtureOfAgentsTool accepts reference model override without throwing`() {
        // Override parameters exist in the signature — verify they don't explode.
        val result = mixtureOfAgentsTool(
            userPrompt = "x",
            referenceModels = listOf("model-a", "model-b"),
            aggregatorModel = "model-c")
        val json = JSONObject(result)
        assertTrue(json.has("error"))
    }
}
