package com.xiaomo.hermes.hermes.tools

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetConfigTest {

    private val createdTools = mutableListOf<String>()

    @After
    fun cleanup() {
        for (name in createdTools) registry.deregister(name)
        createdTools.clear()
    }

    private fun registerWithSize(name: String, size: Int) {
        registry.register(ToolEntry(name = name, toolset = "budget_test", maxResultSizeChars = size))
        createdTools.add(name)
    }

    @Test
    fun `default constants match expected tool_result_storage values`() {
        assertEquals(100_000, DEFAULT_RESULT_SIZE_CHARS)
        assertEquals(200_000, DEFAULT_TURN_BUDGET_CHARS)
        assertEquals(1_500, DEFAULT_PREVIEW_SIZE_CHARS)
    }

    @Test
    fun `DEFAULT_BUDGET has expected defaults`() {
        assertEquals(DEFAULT_RESULT_SIZE_CHARS, DEFAULT_BUDGET.defaultResultSize)
        assertEquals(DEFAULT_TURN_BUDGET_CHARS, DEFAULT_BUDGET.turnBudget)
        assertEquals(DEFAULT_PREVIEW_SIZE_CHARS, DEFAULT_BUDGET.previewSize)
        assertTrue(DEFAULT_BUDGET.toolOverrides.isEmpty())
    }

    @Test
    fun `resolveThreshold returns pinned value for read_file regardless of overrides`() {
        val cfg = BudgetConfig(toolOverrides = mapOf("read_file" to 10))
        assertEquals(Double.POSITIVE_INFINITY, cfg.resolveThreshold("read_file"), 0.0)
    }

    @Test
    fun `PINNED_THRESHOLDS pins read_file to positive infinity`() {
        assertEquals(Double.POSITIVE_INFINITY, PINNED_THRESHOLDS["read_file"])
    }

    @Test
    fun `resolveThreshold uses toolOverrides before registry or default`() {
        registerWithSize("bc_test_override_tool", 999)
        val cfg = BudgetConfig(toolOverrides = mapOf("bc_test_override_tool" to 42))
        assertEquals(42.0, cfg.resolveThreshold("bc_test_override_tool"), 0.0)
    }

    @Test
    fun `resolveThreshold falls back to registry when no override`() {
        registerWithSize("bc_test_registry_only", 555)
        val cfg = BudgetConfig()
        assertEquals(555.0, cfg.resolveThreshold("bc_test_registry_only"), 0.0)
    }

    @Test
    fun `resolveThreshold falls back to defaultResultSize for unknown tool`() {
        val cfg = BudgetConfig(defaultResultSize = 123)
        assertEquals(123.0, cfg.resolveThreshold("bc_test_unknown_tool_xyz"), 0.0)
    }

    @Test
    fun `BudgetConfig is a data class with value equality`() {
        val a = BudgetConfig(defaultResultSize = 10, turnBudget = 20, previewSize = 30)
        val b = BudgetConfig(defaultResultSize = 10, turnBudget = 20, previewSize = 30)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `BudgetConfig copy overrides single field`() {
        val a = BudgetConfig()
        val b = a.copy(defaultResultSize = 500)
        assertEquals(500, b.defaultResultSize)
        assertEquals(a.turnBudget, b.turnBudget)
    }
}
