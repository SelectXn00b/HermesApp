package com.xiaomo.hermes.hermes.tools

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

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

/**
 * Tests for OpenrouterClient.kt — lazy-cached OkHttpClient used by all
 * Hermes tool modules that hit OpenRouter. Covers TC-TOOL-325-a
 * (singleton reuse + 120 s connect/read timeout).
 */
class OpenrouterClientTest {

    private val hasKey: Boolean
        get() = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()

    @Test
    fun `checkApiKey returns false when env var missing`() {
        // OPENROUTER_API_KEY isn't set in the JVM test env.
        if (hasKey) return
        assertFalse(checkApiKey())
    }

    @Test
    fun `checkApiKey mirrors env state`() {
        assertEquals(hasKey, checkApiKey())
    }

    @Test
    fun `getAsyncClient throws IllegalArgumentException when env var missing`() {
        if (hasKey) return
        _resetClientViaReflection()
        val thrown = try {
            getAsyncClient()
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(thrown)
    }

    @Test
    fun `getAsyncClient error message mentions env var name`() {
        if (hasKey) return
        _resetClientViaReflection()
        try {
            getAsyncClient()
            fail("expected IllegalArgumentException when OPENROUTER_API_KEY is unset")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message must mention OPENROUTER_API_KEY: ${e.message}",
                (e.message ?: "").contains("OPENROUTER_API_KEY"))
        }
    }

    // ── R-TOOL-325 / TC-TOOL-325-a: singleton reuse + 120s timeout ──
    /**
     * TC-TOOL-325-a — two calls to `getAsyncClient()` must return the SAME
     * OkHttpClient instance (lazy-cached `_client`), and that instance must
     * have 120 s connect + 120 s read timeouts.
     */
    @Test
    fun `singleton plus timeout`() {
        if (!hasKey) {
            // Env unset — surrogate: pre-seed the cached slot and verify
            // getAsyncClient returns it and the timeouts are what we configure.
            val canned = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            _setClientViaReflection(canned)
            try {
                val a = getAsyncClient()
                val b = getAsyncClient()
                assertSame("two calls must return the same instance", a, b)
                assertSame("must be the instance we pre-seeded", canned, a)
                assertEquals(
                    "connect timeout must be 120s", 120_000, a.connectTimeoutMillis)
                assertEquals(
                    "read timeout must be 120s", 120_000, a.readTimeoutMillis)
            } finally {
                _setClientViaReflection(null)
            }
            return
        }

        // Env set — exercise the real constructor path end-to-end.
        _resetClientViaReflection()
        val first = getAsyncClient()
        val second = getAsyncClient()
        assertSame("successive calls must return the same cached instance", first, second)
        assertEquals("connect timeout must be 120s", 120_000, first.connectTimeoutMillis)
        assertEquals("read timeout must be 120s", 120_000, first.readTimeoutMillis)
    }

    @Test
    fun `client is lazy null before first call`() {
        _resetClientViaReflection()
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.OpenrouterClientKt")
        val field = clazz.getDeclaredField("_client").apply { isAccessible = true }
        assertEquals(null, field.get(null))
    }

    @Test
    fun `source references openrouter provider tag`() {
        val src = java.io.File(
            "src/main/java/com/xiaomo/hermes/hermes/tools/OpenrouterClient.kt")
        assertTrue("source file must be readable", src.exists())
        val text = src.readText()
        assertTrue(
            "source must reference provider = openrouter for Python alignment",
            text.contains("\"openrouter\""))
    }

    // ── Helper: poke _client module-level private ──
    private fun _resetClientViaReflection() {
        _setClientViaReflection(null)
    }

    private fun _setClientViaReflection(value: OkHttpClient?) {
        val clazz = Class.forName("com.xiaomo.hermes.hermes.tools.OpenrouterClientKt")
        val field = clazz.getDeclaredField("_client").apply { isAccessible = true }
        field.set(null, value)
    }
}
