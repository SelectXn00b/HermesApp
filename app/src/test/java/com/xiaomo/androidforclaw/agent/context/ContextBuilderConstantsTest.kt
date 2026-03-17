package com.xiaomo.androidforclaw.agent.context

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verify ContextBuilder constants are aligned with OpenClaw source values.
 *
 * OpenClaw source references:
 * - tokens-T07bZqlD.js: SILENT_REPLY_TOKEN = "NO_REPLY", HEARTBEAT_TOKEN = "HEARTBEAT_OK"
 * - auth-profiles-MKCH-k1W.js: MIN_BOOTSTRAP_FILE_BUDGET_CHARS = 64, BOOTSTRAP_TAIL_RATIO = 0.2
 * - skills-*.js: DEFAULT_MAX_SKILLS_IN_PROMPT = 150, DEFAULT_MAX_SKILLS_PROMPT_CHARS = 30000
 * - redact-snapshot-*.js: bootstrapMaxChars default = 20000, bootstrapTotalMaxChars default = 150000
 */
class ContextBuilderConstantsTest {

    @Test
    fun `SILENT_REPLY_TOKEN matches OpenClaw`() {
        assertEquals("NO_REPLY", ContextBuilder.Companion.SILENT_REPLY_TOKEN)
    }

    @Test
    fun `MIN_BOOTSTRAP_FILE_BUDGET_CHARS matches OpenClaw value of 64`() {
        // const val in companion object compiles to a static field on the Companion class
        val companionClass = Class.forName("com.xiaomo.androidforclaw.agent.context.ContextBuilder\$Companion")
        // Try companion first, then outer class (Kotlin compiler may place it in either)
        val value = try {
            val f = companionClass.getDeclaredField("MIN_BOOTSTRAP_FILE_BUDGET_CHARS")
            f.isAccessible = true
            f.getInt(ContextBuilder.Companion)
        } catch (_: NoSuchFieldException) {
            val f = ContextBuilder::class.java.getDeclaredField("MIN_BOOTSTRAP_FILE_BUDGET_CHARS")
            f.isAccessible = true
            f.getInt(null)
        }
        assertEquals(64, value)
    }

    @Test
    fun `DEFAULT_BOOTSTRAP_MAX_CHARS matches OpenClaw default of 20000`() {
        val field = ContextBuilder::class.java.getDeclaredField("DEFAULT_BOOTSTRAP_MAX_CHARS")
            .apply { isAccessible = true }
        assertEquals(20_000, field.getInt(null))
    }

    @Test
    fun `DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS matches OpenClaw default of 150000`() {
        val field = ContextBuilder::class.java.getDeclaredField("DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS")
            .apply { isAccessible = true }
        assertEquals(150_000, field.getInt(null))
    }

    @Test
    fun `BOOTSTRAP_TAIL_RATIO matches OpenClaw value of 0_2`() {
        val field = ContextBuilder::class.java.getDeclaredField("BOOTSTRAP_TAIL_RATIO")
            .apply { isAccessible = true }
        assertEquals(0.2, field.getDouble(null), 0.001)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `BOOTSTRAP_FILES order matches OpenClaw loadWorkspaceBootstrapFiles`() {
        // OpenClaw agent-scope-Cwa3GjIC.js: loadWorkspaceBootstrapFiles() loads:
        // AGENTS → SOUL → TOOLS → IDENTITY → USER → HEARTBEAT → BOOTSTRAP → memory/*
        // MEMORY.md is resolved dynamically in OpenClaw but statically appended here.
        val field = ContextBuilder::class.java.getDeclaredField("BOOTSTRAP_FILES")
            .apply { isAccessible = true }
        val files = field.get(null) as List<String>

        val expectedOrder = listOf(
            "AGENTS.md",
            "SOUL.md",
            "TOOLS.md",
            "IDENTITY.md",
            "USER.md",
            "HEARTBEAT.md",
            "BOOTSTRAP.md",
            "MEMORY.md"
        )
        assertEquals(expectedOrder, files)
    }
}
