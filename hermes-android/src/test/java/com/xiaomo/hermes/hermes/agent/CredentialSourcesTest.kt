package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CredentialSources — removal-step registry + per-source cleanup contracts.
 *
 * Requirement: R-AGENT-008 (credential pool / rotation — multi-source merge)
 * Test cases:
 *   TC-AGENT-222-a — multi-source merge: registry matches the right step for
 *                    each (provider, source) combination so cleanup is
 *                    source-specific.
 *
 * The actual "seeding" from env / claude_code / hermes_pkce is largely no-op
 * on Android (those paths don't exist at runtime), so we test the removal
 * contract instead — that's the piece that actually ships.
 */
class CredentialSourcesTest {

    /** TC-AGENT-222-a — env source matches the wildcard env:* step. */
    @Test
    fun `env source routes to env removal step`() {
        val step = findRemovalStep("anthropic", "env:ANTHROPIC_API_KEY")
        assertNotNull(step)
        assertEquals("*", step!!.provider)
        assertTrue(step.description.contains("env", ignoreCase = true))
    }

    /** claude_code only matches for anthropic provider. */
    @Test
    fun `claude_code source routes to anthropic step`() {
        val step = findRemovalStep("anthropic", "claude_code")
        assertNotNull(step)
        assertEquals("anthropic", step!!.provider)
        assertEquals("claude_code", step.sourceId)
    }

    /** hermes_pkce is anthropic-specific. */
    @Test
    fun `hermes_pkce routes to anthropic step`() {
        val step = findRemovalStep("anthropic", "hermes_pkce")
        assertNotNull(step)
        assertEquals("anthropic", step!!.provider)
        assertEquals("hermes_pkce", step.sourceId)
    }

    /** device_code for nous matches the nous-specific step. */
    @Test
    fun `device_code routes to nous step for nous`() {
        val step = findRemovalStep("nous", "device_code")
        assertNotNull(step)
        assertEquals("nous", step!!.provider)
    }

    /** openai-codex device_code still matches even with a colon prefix label. */
    @Test
    fun `openai-codex device_code with colon prefix still matches`() {
        val step = findRemovalStep("openai-codex", "primary:device_code")
        assertNotNull(step)
        assertEquals("openai-codex", step!!.provider)
    }

    /** Copilot matches gh_cli source and env:COPILOT_GITHUB_TOKEN. */
    @Test
    fun `copilot gh_cli routes to copilot step`() {
        val step = findRemovalStep("copilot", "gh_cli")
        assertNotNull(step)
        assertEquals("copilot", step!!.provider)
    }

    /** config:* wildcard falls through to the custom-provider step. */
    @Test
    fun `custom config routes to custom removal step`() {
        val step = findRemovalStep("xai", "config:xai")
        assertNotNull(step)
        assertEquals("*", step!!.provider)
        assertTrue(step.description.contains("config", ignoreCase = true))
    }

    /** model_config is an alias for custom config source. */
    @Test
    fun `model_config routes to custom removal step`() {
        val step = findRemovalStep("xai", "model_config")
        assertNotNull(step)
        assertEquals("*", step!!.provider)
    }

    /** Unknown source ID returns null — caller falls through to default path. */
    @Test
    fun `unknown source returns null`() {
        assertNull(findRemovalStep("whoami", "made-up-source"))
    }

    /** RemovalStep.matches wildcard provider + custom matchFn. */
    @Test
    fun `removal step matches wildcard provider`() {
        val step = RemovalStep(
            provider = "*",
            sourceId = "foo",
            removeFn = { _, _ -> RemovalResult() },
        )
        assertTrue(step.matches("anthropic", "foo"))
        assertTrue(step.matches("openai", "foo"))
        assertFalse(step.matches("anthropic", "bar"))
    }

    /** RemovalStep.matches respects a custom matchFn. */
    @Test
    fun `removal step honours custom matchFn`() {
        val step = RemovalStep(
            provider = "anthropic",
            sourceId = "prefix:",
            matchFn = { src -> src.startsWith("prefix:") },
            removeFn = { _, _ -> RemovalResult() },
        )
        assertTrue(step.matches("anthropic", "prefix:whatever"))
        assertFalse(step.matches("anthropic", "other"))
        assertFalse(step.matches("openai", "prefix:match-src-but-wrong-provider"))
    }

    /** RemovalResult default state: empty lists + suppress=true. */
    @Test
    fun `removal result defaults are empty plus suppress`() {
        val r = RemovalResult()
        assertTrue(r.cleaned.isEmpty())
        assertTrue(r.hints.isEmpty())
        assertTrue(r.suppress)
    }
}
