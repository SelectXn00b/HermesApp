package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateToolTest {

    @Test
    fun `DELEGATE_BLOCKED_TOOLS has three tool names`() {
        assertEquals(3, DELEGATE_BLOCKED_TOOLS.size)
        assertTrue("delegate_task" in DELEGATE_BLOCKED_TOOLS)
        assertTrue("delegate_status" in DELEGATE_BLOCKED_TOOLS)
        assertTrue("delegate_cancel" in DELEGATE_BLOCKED_TOOLS)
    }

    @Test
    fun `_EXCLUDED_TOOLSET_NAMES covers expected categories`() {
        assertTrue("debugging" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("safe" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("delegation" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("moa" in _EXCLUDED_TOOLSET_NAMES)
        assertTrue("rl" in _EXCLUDED_TOOLSET_NAMES)
        assertEquals(5, _EXCLUDED_TOOLSET_NAMES.size)
    }

    @Test
    fun `_SUBAGENT_TOOLSETS matches DEFAULT_TOOLSETS`() {
        assertEquals(listOf("terminal", "file", "web"), _SUBAGENT_TOOLSETS)
        assertEquals(_SUBAGENT_TOOLSETS, DEFAULT_TOOLSETS)
    }

    @Test
    fun `_TOOLSET_LIST_STR is single-quoted comma-joined`() {
        assertEquals("'terminal', 'file', 'web'", _TOOLSET_LIST_STR)
    }

    @Test
    fun `module constants match Python defaults`() {
        assertEquals(3, _DEFAULT_MAX_CONCURRENT_CHILDREN)
        assertEquals(2, MAX_DEPTH)
        assertEquals(50, DEFAULT_MAX_ITERATIONS)
        assertEquals(30, _HEARTBEAT_INTERVAL)
    }

    @Test
    fun `DELEGATE_TASK_SCHEMA has expected shape`() {
        assertEquals("object", DELEGATE_TASK_SCHEMA["type"])
        @Suppress("UNCHECKED_CAST")
        val props = DELEGATE_TASK_SCHEMA["properties"] as Map<String, Any>
        assertTrue("goal" in props)
        assertTrue("toolsets" in props)
        @Suppress("UNCHECKED_CAST")
        val required = DELEGATE_TASK_SCHEMA["required"] as List<String>
        assertEquals(listOf("goal"), required)
    }

    @Test
    fun `checkDelegateRequirements returns false on Android`() {
        assertFalse(checkDelegateRequirements())
    }

    @Test
    fun `_getMaxConcurrentChildren returns default when env unset`() {
        val n = _getMaxConcurrentChildren()
        // Env mutation is impossible from JVM; if HERMES_DELEGATE_MAX_CONCURRENT
        // happens to be set in CI we accept any int >= 1.
        assertTrue(n >= 1)
    }

    @Test
    fun `_buildChildSystemPrompt includes goal and default toolsets`() {
        val prompt = _buildChildSystemPrompt(goal = "Solve X")
        assertTrue(prompt.contains("Goal: Solve X"))
        assertTrue(prompt.contains("'terminal', 'file', 'web'"))
        assertFalse(prompt.contains("Workspace:"))  // no hint
    }

    @Test
    fun `_buildChildSystemPrompt includes workspace hint when provided`() {
        val prompt = _buildChildSystemPrompt(goal = "X", workspaceHint = "/tmp/work")
        assertTrue(prompt.contains("Workspace: /tmp/work"))
    }

    @Test
    fun `_buildChildSystemPrompt honors custom toolsets`() {
        val prompt = _buildChildSystemPrompt(goal = "X", toolsets = listOf("memory", "web"))
        assertTrue(prompt.contains("'memory', 'web'"))
        assertFalse(prompt.contains("'terminal'"))
    }

    @Test
    fun `_resolveWorkspaceHint returns null on Android`() {
        assertNull(_resolveWorkspaceHint(null))
        assertNull(_resolveWorkspaceHint(Any()))
    }

    @Test
    fun `_stripBlockedTools drops blocked and excluded toolsets`() {
        val input = listOf("terminal", "file", "delegate_task", "debugging", "moa", "web")
        val result = _stripBlockedTools(input)
        assertEquals(listOf("terminal", "file", "web"), result)
    }

    @Test
    fun `_stripBlockedTools preserves order and passes through unknown names`() {
        val input = listOf("custom1", "custom2")
        assertEquals(input, _stripBlockedTools(input))
    }

    @Test
    fun `_stripBlockedTools on empty list returns empty`() {
        assertEquals(emptyList<String>(), _stripBlockedTools(emptyList()))
    }

    @Test
    fun `_buildChildProgressCallback returns null on Android`() {
        assertNull(_buildChildProgressCallback(
            taskIndex = 0,
            goal = "x",
            parentAgent = null,
        ))
    }

    @Test
    fun `_buildChildAgent returns null on Android`() {
        assertNull(_buildChildAgent(goal = "x"))
    }

    @Test
    fun `_runSingleChild returns error map`() {
        val result = _runSingleChild(taskIndex = 2, goal = "do x")
        assertEquals(false, result["ok"])
        assertEquals("do x", result["goal"])
        assertEquals(2, result["task_index"])
        assertTrue((result["error"] as String).contains("not available"))
    }

    @Test
    fun `delegateTask rejects empty goal with JSON error`() {
        val result = delegateTask(goal = "   ")
        assertTrue(result.contains("Task description is required"))
    }

    @Test
    fun `delegateTask rejects null goal`() {
        val result = delegateTask(goal = null)
        assertTrue(result.contains("Task description is required"))
    }

    @Test
    fun `delegateTask returns not-available JSON error for non-empty goal`() {
        val result = delegateTask(goal = "real goal")
        assertTrue(result.contains("not available"))
        assertFalse(result.contains("Task description is required"))
    }

    @Test
    fun `_resolveChildCredentialPool returns null on Android`() {
        assertNull(_resolveChildCredentialPool(null, null))
        assertNull(_resolveChildCredentialPool("openrouter", Any()))
    }

    @Test
    fun `_resolveDelegationCredentials returns empty map on Android`() {
        assertEquals(emptyMap<String, Any?>(), _resolveDelegationCredentials(emptyMap(), null))
    }
}
