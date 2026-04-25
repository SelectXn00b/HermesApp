package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnvPassthroughTest {

    @Before
    @After
    fun reset() {
        clearEnvPassthrough()
    }

    @Test
    fun `registerEnvPassthrough list adds vars`() {
        registerEnvPassthrough(listOf("FOO_VAR", "BAR_VAR"))
        assertTrue(isEnvPassthrough("FOO_VAR"))
        assertTrue(isEnvPassthrough("BAR_VAR"))
    }

    @Test
    fun `registerEnvPassthrough single-arg overload works`() {
        registerEnvPassthrough("SINGLE_VAR")
        assertTrue(isEnvPassthrough("SINGLE_VAR"))
    }

    @Test
    fun `registerEnvPassthrough trims whitespace and skips empty`() {
        registerEnvPassthrough(listOf("  SPACED  ", "", "   "))
        assertTrue(isEnvPassthrough("SPACED"))
        assertFalse(isEnvPassthrough(""))
        assertFalse(isEnvPassthrough("   "))
    }

    @Test
    fun `registerEnvPassthrough refuses HERMES provider credentials with _KEY`() {
        registerEnvPassthrough("HERMES_OPENAI_KEY")
        assertFalse(isEnvPassthrough("HERMES_OPENAI_KEY"))
    }

    @Test
    fun `registerEnvPassthrough refuses HERMES provider credentials with _TOKEN`() {
        registerEnvPassthrough("HERMES_SOME_TOKEN")
        assertFalse(isEnvPassthrough("HERMES_SOME_TOKEN"))
    }

    @Test
    fun `registerEnvPassthrough allows HERMES vars that aren't credentials`() {
        registerEnvPassthrough("HERMES_LOG_LEVEL")
        assertTrue(isEnvPassthrough("HERMES_LOG_LEVEL"))
    }

    @Test
    fun `clearEnvPassthrough removes all registered vars`() {
        registerEnvPassthrough(listOf("A", "B"))
        clearEnvPassthrough()
        assertFalse(isEnvPassthrough("A"))
        assertFalse(isEnvPassthrough("B"))
    }

    @Test
    fun `isEnvPassthrough returns false for unregistered var`() {
        assertFalse(isEnvPassthrough("NEVER_REGISTERED_VAR_XYZ"))
    }

    @Test
    fun `getAllPassthrough returns registered vars`() {
        registerEnvPassthrough(listOf("ALPHA", "BETA"))
        val all = getAllPassthrough()
        assertTrue("ALPHA" in all)
        assertTrue("BETA" in all)
    }
}
