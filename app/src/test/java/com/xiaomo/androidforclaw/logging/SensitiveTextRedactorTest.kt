package com.xiaomo.androidforclaw.logging

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SensitiveTextRedactor.
 * Verifies alignment with OpenClaw logging/redact.ts:
 * - 16 redact patterns cover all documented secret formats
 * - maskToken partial reveal behavior
 * - Bounded chunked replacement for large texts
 * - Truncation at TEXT_MAX_CHARS
 */
class SensitiveTextRedactorTest {

    // ── maskToken ──────────────────────────────────────────────

    @Test
    fun `maskToken short token returns stars`() {
        assertEquals("***", SensitiveTextRedactor.maskToken("abc"))
        assertEquals("***", SensitiveTextRedactor.maskToken("12345678901234567")) // 17 chars
    }

    @Test
    fun `maskToken long token reveals first 6 and last 4`() {
        // 18 chars exactly
        val token = "abcdefghijklmnopqr"
        assertEquals("abcdef...opqr", SensitiveTextRedactor.maskToken(token))
    }

    @Test
    fun `maskToken very long token`() {
        val token = "sk-abcdefghijklmnopqrstuvwxyz1234567890"
        assertEquals("sk-abc...7890", SensitiveTextRedactor.maskToken(token))
    }

    // ── ENV-style patterns ─────────────────────────────────────

    @Test
    fun `redact ENV-style API_KEY=value`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("API_KEY=super_secret_key_12345678")
        assertTrue(redacted)
        assertTrue(result.contains("API_KEY="))
        assertFalse(result.contains("super_secret_key_12345678"))
    }

    @Test
    fun `redact ENV-style TOKEN colon value`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("TOKEN: my_long_secret_token_value_here")
        assertTrue(redacted)
        assertFalse(result.contains("my_long_secret_token_value_here"))
    }

    @Test
    fun `redact ENV-style SECRET=value`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("SECRET=abcdef1234567890abcdef")
        assertTrue(redacted)
        assertFalse(result.contains("abcdef1234567890abcdef"))
    }

    @Test
    fun `redact ENV-style PASSWORD=value`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("PASSWORD=hunter2_is_not_a_good_password")
        assertTrue(redacted)
        assertFalse(result.contains("hunter2_is_not_a_good_password"))
    }

    // ── JSON field patterns ────────────────────────────────────

    @Test
    fun `redact JSON apiKey field`() {
        val json = """{"apiKey": "sk-proj-1234567890abcdef1234567890"}"""
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(json)
        assertTrue(redacted)
        assertFalse(result.contains("sk-proj-1234567890abcdef1234567890"))
    }

    @Test
    fun `redact JSON token field`() {
        val json = """{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"}"""
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(json)
        assertTrue(redacted)
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"))
    }

    // ── CLI flag patterns ──────────────────────────────────────

    @Test
    fun `redact CLI --api-key flag`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("curl --api-key sk-abcdefghij1234567890")
        assertTrue(redacted)
        assertFalse(result.contains("sk-abcdefghij1234567890"))
    }

    @Test
    fun `redact CLI --token flag`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("--token ghp_abcdefghijklmnopqrstuvwxyz")
        assertTrue(redacted)
        assertFalse(result.contains("ghp_abcdefghijklmnopqrstuvwxyz"))
    }

    // ── Authorization header ───────────────────────────────────

    @Test
    fun `redact Authorization Bearer header`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"
        )
        assertTrue(redacted)
        assertFalse(result.contains("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    @Test
    fun `redact bare Bearer token`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"
        )
        assertTrue(redacted)
        assertFalse(result.contains("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"))
    }

    // ── PEM private key ────────────────────────────────────────

    @Test
    fun `redact PEM private key`() {
        val pem = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF8PbnGy5AH...
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(pem)
        assertTrue(redacted)
        assertTrue(result.contains("...redacted..."))
        assertFalse(result.contains("MIIEpAIBAAKCAQEA0Z3VS5JJcds3xfn"))
    }

    // ── Provider-specific tokens ───────────────────────────────

    @Test
    fun `redact OpenAI sk- key`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "Using key sk-abcdefghij1234567890abcdef"
        )
        assertTrue(redacted)
        assertFalse(result.contains("sk-abcdefghij1234567890abcdef"))
    }

    @Test
    fun `redact GitHub PAT ghp_`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "ghp_abcdefghijklmnopqrstuvwxyz1234"
        )
        assertTrue(redacted)
        assertFalse(result.contains("ghp_abcdefghijklmnopqrstuvwxyz1234"))
    }

    @Test
    fun `redact GitHub PAT github_pat_`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "github_pat_abcdefghijklmnopqrstuvwxyz1234"
        )
        assertTrue(redacted)
        assertFalse(result.contains("github_pat_abcdefghijklmnopqrstuvwxyz1234"))
    }

    @Test
    fun `redact Slack xoxb- token`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "xoxb-1234-5678-abcdefghij"
        )
        assertTrue(redacted)
        assertFalse(result.contains("xoxb-1234-5678-abcdefghij"))
    }

    @Test
    fun `redact Slack xapp- token`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "xapp-1-ABCD-1234567890-abcdefghij"
        )
        assertTrue(redacted)
        assertFalse(result.contains("xapp-1-ABCD-1234567890-abcdefghij"))
    }

    @Test
    fun `redact Groq gsk_ key`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "gsk_abcdefghij1234567890"
        )
        assertTrue(redacted)
        assertFalse(result.contains("gsk_abcdefghij1234567890"))
    }

    @Test
    fun `redact Google AI AIza key`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "AIzaSyAabcdefghijklmnopqrstuvwx"
        )
        assertTrue(redacted)
        assertFalse(result.contains("AIzaSyAabcdefghijklmnopqrstuvwx"))
    }

    @Test
    fun `redact Perplexity pplx- key`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "pplx-abcdefghij1234567890"
        )
        assertTrue(redacted)
        assertFalse(result.contains("pplx-abcdefghij1234567890"))
    }

    @Test
    fun `redact npm token`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "npm_abcdefghij1234567890"
        )
        assertTrue(redacted)
        assertFalse(result.contains("npm_abcdefghij1234567890"))
    }

    @Test
    fun `redact Telegram bot token`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(
            "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg"
        )
        assertTrue(redacted)
        assertFalse(result.contains("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg"))
    }

    // ── No false positives ─────────────────────────────────────

    @Test
    fun `normal text is not redacted`() {
        val text = "Hello world, this is a normal message with no secrets."
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(text)
        assertFalse(redacted)
        assertEquals(text, result)
    }

    @Test
    fun `empty text returns empty`() {
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText("")
        assertFalse(redacted)
        assertEquals("", result)
    }

    // ── Bounded replacement ────────────────────────────────────

    @Test
    fun `replacePatternBounded handles large text`() {
        // Build text larger than REGEX_CHUNK_SIZE * 2
        val filler = "a".repeat(SensitiveTextRedactor.REGEX_CHUNK_SIZE)
        val secret = "API_KEY=super_secret_key_12345678"
        val largeText = filler + secret + filler

        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(largeText)
        assertTrue(redacted)
        assertFalse(result.contains("super_secret_key_12345678"))
    }

    // ── truncateHistoryText ────────────────────────────────────

    @Test
    fun `truncateHistoryText truncates long text`() {
        val longText = "x".repeat(SensitiveTextRedactor.TEXT_MAX_CHARS + 500)
        val (result, truncated, _) = SensitiveTextRedactor.truncateHistoryText(longText)
        assertTrue(truncated)
        assertTrue(result.endsWith("...(truncated)..."))
        assertTrue(result.length < longText.length)
    }

    @Test
    fun `truncateHistoryText does not truncate short text`() {
        val shortText = "short message"
        val (result, truncated, redacted) = SensitiveTextRedactor.truncateHistoryText(shortText)
        assertFalse(truncated)
        assertFalse(redacted)
        assertEquals(shortText, result)
    }

    @Test
    fun `truncateHistoryText both truncates and redacts`() {
        val secret = "sk-abcdefghij1234567890abcdef "
        val longText = secret + "x".repeat(SensitiveTextRedactor.TEXT_MAX_CHARS)
        val (result, truncated, redacted) = SensitiveTextRedactor.truncateHistoryText(longText)
        assertTrue(truncated)
        assertTrue(redacted)
        assertFalse(result.contains("sk-abcdefghij1234567890abcdef"))
    }

    // ── Constants alignment ────────────────────────────────────

    @Test
    fun `constants align with OpenClaw`() {
        assertEquals(4000, SensitiveTextRedactor.TEXT_MAX_CHARS)
        assertEquals(80 * 1024, SensitiveTextRedactor.MAX_BYTES)
        assertEquals(16_384, SensitiveTextRedactor.REGEX_CHUNK_SIZE)
        assertEquals(16, SensitiveTextRedactor.REDACT_PATTERNS.size)
    }

    // ── Multiple secrets in one text ───────────────────────────

    @Test
    fun `redacts multiple secrets in one text`() {
        val text = "key1: sk-abcdefghij1234567890 and key2: ghp_abcdefghijklmnopqrstuvwxyz1234"
        val (result, redacted) = SensitiveTextRedactor.redactSensitiveText(text)
        assertTrue(redacted)
        assertFalse(result.contains("sk-abcdefghij1234567890"))
        assertFalse(result.contains("ghp_abcdefghijklmnopqrstuvwxyz1234"))
    }
}
