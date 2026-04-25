package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TirithSecurityTest {

    @Test
    fun `checkCommandSecurity returns allow shape when tirith disabled via env`() {
        // TIRITH_ENABLED env isn't easy to flip in-process; cfg defaults tirith_enabled=true.
        // The fail_open default is true, so on Android (no tirith binary) we always get allow + message.
        val result = checkCommandSecurity("ls -la")
        assertEquals("allow", result["action"])
        @Suppress("UNCHECKED_CAST")
        val findings = result["findings"] as List<Any?>
        assertEquals(0, findings.size)
    }

    @Test
    fun `checkCommandSecurity summary explains tirith unavailable on Android`() {
        val result = checkCommandSecurity("echo hi")
        assertEquals("tirith unavailable on Android", result["summary"])
    }

    @Test
    fun `checkCommandSecurity preserves action findings summary keys`() {
        val result = checkCommandSecurity("whatever")
        assertNotNull(result["action"])
        assertNotNull(result["findings"])
        assertNotNull(result["summary"])
    }

    @Test
    fun `ensureInstalled returns null on Android`() {
        // Android path has no tirith binary; ensureInstalled always returns null.
        assertEquals(null, ensureInstalled())
    }

    @Test
    fun `ensureInstalled respects logFailures parameter without throwing`() {
        // Just exercise both paths of the parameter
        assertEquals(null, ensureInstalled(logFailures = true))
        assertEquals(null, ensureInstalled(logFailures = false))
    }

    @Test
    fun `checkCommandSecurity handles empty command gracefully`() {
        val result = checkCommandSecurity("")
        assertEquals("allow", result["action"])
    }
}
