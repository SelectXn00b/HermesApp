package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessRegistryTest {

    @Test
    fun `cleanShellNoise strips leading job-control noise lines`() {
        val input = listOf(
            "bash: cannot set terminal process group (1234): Inappropriate ioctl for device",
            "bash: no job control in this shell",
            "actual output line 1",
            "actual output line 2",
        ).joinToString("\n")
        val result = ProcessRegistry.cleanShellNoise(input)
        assertEquals("actual output line 1\nactual output line 2", result)
    }

    @Test
    fun `cleanShellNoise leaves clean output unchanged`() {
        val input = "hello\nworld"
        assertEquals(input, ProcessRegistry.cleanShellNoise(input))
    }

    @Test
    fun `cleanShellNoise keeps noise substrings that appear mid-output intact`() {
        // Only leading noise lines are stripped; noise inside the body is kept.
        val input = "real line\nbash: no job control in this shell\nafter"
        assertEquals(input, ProcessRegistry.cleanShellNoise(input))
    }

    @Test
    fun `cleanShellNoise returns empty string for empty input`() {
        assertEquals("", ProcessRegistry.cleanShellNoise(""))
    }

    @Test
    fun `cleanShellNoise handles all-noise input`() {
        val input = listOf(
            "bash: cannot set terminal process group",
            "bash: no job control in this shell",
        ).joinToString("\n")
        assertEquals("", ProcessRegistry.cleanShellNoise(input))
    }

    @Test
    fun `cleanShellNoise handles tcsetattr noise variant`() {
        val input = "tcsetattr: Inappropriate ioctl for device\nkept"
        assertEquals("kept", ProcessRegistry.cleanShellNoise(input))
    }

    @Test
    fun `formatUptimeShort under 60s renders as seconds`() {
        assertEquals("0s", formatUptimeShort(0.0))
        assertEquals("1s", formatUptimeShort(1.9))  // toInt truncates
        assertEquals("59s", formatUptimeShort(59.9))
    }

    @Test
    fun `formatUptimeShort 60s to 3599s renders as minutes`() {
        assertEquals("1m", formatUptimeShort(60.0))
        assertEquals("30m", formatUptimeShort(1800.0))
        assertEquals("59m", formatUptimeShort(3599.0))
    }

    @Test
    fun `formatUptimeShort 3600s to 86399s renders as hours`() {
        assertEquals("1h", formatUptimeShort(3600.0))
        assertEquals("12h", formatUptimeShort(43200.0))
        assertEquals("23h", formatUptimeShort(86399.0))
    }

    @Test
    fun `formatUptimeShort 86400s and above renders as days`() {
        assertEquals("1d", formatUptimeShort(86400.0))
        assertEquals("7d", formatUptimeShort(604800.0))
    }

    @Test
    fun `isHostPidAlive returns false for null pid`() {
        assertFalse(ProcessRegistry.isHostPidAlive(null))
    }

    @Test
    fun `envTempDir returns Android tmp path`() {
        assertEquals("/data/local/tmp", ProcessRegistry.envTempDir())
    }

    @Test
    fun `get returns null for unknown session id`() {
        assertEquals(null, ProcessRegistry.get("does_not_exist_${System.nanoTime()}"))
    }

    @Test
    fun `isCompletionConsumed returns false for fresh unknown session`() {
        assertFalse(ProcessRegistry.isCompletionConsumed("unknown_${System.nanoTime()}"))
    }

    @Test
    fun `hasActiveForSession returns false when no sessions`() {
        assertFalse(ProcessRegistry.hasActiveForSession("no_such_session_key_${System.nanoTime()}"))
    }

    @Test
    fun `hasActiveProcesses returns false for unknown task`() {
        assertFalse(ProcessRegistry.hasActiveProcesses("no_such_task_${System.nanoTime()}"))
    }

    @Test
    fun `listSessions returns a list (possibly empty) without throwing`() {
        val all = ProcessRegistry.listSessions(taskId = "no_such_task_${System.nanoTime()}")
        assertTrue(all.isEmpty())
    }
}
