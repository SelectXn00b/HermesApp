package com.xiaomo.hermes.hermes.acp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for acp/Auth.kt (detectProvider / hasProvider).
 *
 * Under the JUnit unit-test classpath there are no Hermes credential pool files
 * (CredentialPool.loadPool just returns empty pools), so both helpers must
 * report "no provider available".
 *
 * Requirement map: R-ACP-050..052 (see docs/hermes-requirements.md)
 * Test cases:      TC-ACP-050..052 (see docs/hermes-test-cases.md)
 */
class AcpAuthTest {

    @Test
    fun `detectProvider returns null when no credentials configured`() {
        // No pool files on the JUnit classpath -> all candidates miss.
        // If HERMES_INFERENCE_PROVIDER is set in the environment, it's still
        // appended to the same miss-everything fallback, so the result stays null.
        assertNull(detectProvider())
    }

    @Test
    fun `hasProvider mirrors detectProvider null`() {
        assertFalse(hasProvider())
    }

    @Test
    fun `detectProvider stays null across multiple invocations`() {
        // Idempotent / side-effect free from the caller's perspective.
        val a = detectProvider()
        val b = detectProvider()
        assertEquals(a, b)
        assertTrue(a == null)
    }
}
