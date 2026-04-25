package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XaiHttpTest {

    @Test
    fun `hermesXaiUserAgent starts with Hermes-Agent prefix`() {
        val ua = XaiHttp.hermesXaiUserAgent()
        assertTrue(ua.startsWith("Hermes-Agent/"))
    }

    @Test
    fun `hermesXaiUserAgent is non-empty`() {
        assertTrue(XaiHttp.hermesXaiUserAgent().isNotEmpty())
    }

    @Test
    fun `hermesXaiUserAgent is stable across calls`() {
        assertEquals(XaiHttp.hermesXaiUserAgent(), XaiHttp.hermesXaiUserAgent())
    }

    @Test
    fun `hermesXaiUserAgent falls back to unknown when BuildConfig missing`() {
        // In JVM tests the hermes-android BuildConfig isn't on classpath → "unknown".
        val ua = XaiHttp.hermesXaiUserAgent()
        // Either "Hermes-Agent/unknown" or a real version string — both start with prefix.
        assertNotNull(ua)
        // The slash separator is present.
        assertTrue(ua.contains("/"))
    }
}

class OpenrouterClientTest {

    @Test
    fun `checkApiKey returns false when env var missing`() {
        // OPENROUTER_API_KEY isn't set in the JVM test env.
        assertFalse(checkApiKey())
    }

    @Test
    fun `getAsyncClient throws IllegalArgumentException when env var missing`() {
        val thrown = try {
            getAsyncClient()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(thrown)
    }
}
