package com.xiaomo.hermes.hermes.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for error_classifier.kt — priority-ordered pipeline that maps API
 * errors to ClassifiedError recovery recommendations.
 *
 * Tests synthesize exceptions with getStatusCode()/getBody() reflection-exposed
 * accessors (matching how the production code peeks at OkHttp-wrapped errors).
 *
 * Requirement map: R-AGENT-001..061 (see docs/hermes-requirements.md)
 * Test cases:      TC-AGENT-001..061 (see docs/hermes-test-cases.md)
 */
class ErrorClassifierTest {

    // ---------------------------------------------------------------------
    // Helper exception types — mirror what OkHttp SDKs expose via Kotlin vals
    // ---------------------------------------------------------------------

    /** Exposes status code + body; reflection picks up getStatusCode()/getBody(). */
    private class FakeApiException(
        message: String,
        val statusCode: Int? = null,
        val body: JSONObject = JSONObject(),
    ) : RuntimeException(message)

    private fun errBody(messageField: String? = null, code: String? = null): JSONObject {
        val root = JSONObject()
        val inner = JSONObject()
        if (messageField != null) inner.put("message", messageField)
        if (code != null) inner.put("code", code)
        root.put("error", inner)
        return root
    }

    // ---------------------------------------------------------------------
    // Status-code branches
    // ---------------------------------------------------------------------

    @Test
    fun `401 routes to auth with rotate and fallback`() {
        val e = FakeApiException("unauthorized", statusCode = 401)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.auth, r.reason)
        assertEquals(401, r.statusCode)
        assertFalse(r.retryable)
        assertTrue(r.shouldRotateCredential)
        assertTrue(r.shouldFallback)
        assertTrue(r.isAuth())
    }

    @Test
    fun `403 plain routes to auth without rotate`() {
        val e = FakeApiException("forbidden", statusCode = 403)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.auth, r.reason)
        assertFalse(r.shouldRotateCredential)
        assertTrue(r.shouldFallback)
    }

    @Test
    fun `403 with key limit text routes to billing`() {
        val e = FakeApiException("key limit exceeded for your plan", statusCode = 403)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.billing, r.reason)
        assertTrue(r.shouldRotateCredential)
    }

    @Test
    fun `402 generic routes to billing`() {
        val e = FakeApiException("insufficient credits", statusCode = 402)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.billing, r.reason)
        assertFalse(r.retryable)
    }

    @Test
    fun `402 with transient usage limit text routes to rate_limit`() {
        // "quota" is a usage-limit pattern; "try again" is the transient signal
        val e = FakeApiException("quota exceeded, try again later", statusCode = 402)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.rate_limit, r.reason)
        assertTrue(r.retryable)
    }

    @Test
    fun `404 is model_not_found`() {
        val e = FakeApiException("not found", statusCode = 404)
        val r = classifyApiError(e, provider = "openai", model = "bogus")
        assertEquals(FailoverReason.model_not_found, r.reason)
        assertFalse(r.retryable)
        assertTrue(r.shouldFallback)
    }

    @Test
    fun `413 is payload_too_large with compress`() {
        val e = FakeApiException("request entity too large", statusCode = 413)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.payload_too_large, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `429 is rate_limit with rotate and fallback`() {
        val e = FakeApiException("too many requests", statusCode = 429)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.rate_limit, r.reason)
        assertTrue(r.retryable)
        assertTrue(r.shouldRotateCredential)
        assertTrue(r.shouldFallback)
    }

    @Test
    fun `500 is server_error retryable`() {
        val e = FakeApiException("internal", statusCode = 500)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.server_error, r.reason)
        assertTrue(r.retryable)
    }

    @Test
    fun `503 is overloaded retryable`() {
        val e = FakeApiException("service unavailable", statusCode = 503)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.overloaded, r.reason)
        assertTrue(r.retryable)
    }

    @Test
    fun `529 is overloaded (Anthropic-style)`() {
        val e = FakeApiException("overloaded_error", statusCode = 529)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.overloaded, r.reason)
    }

    @Test
    fun `other 4xx falls into format_error`() {
        val e = FakeApiException("conflict", statusCode = 409)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.format_error, r.reason)
        assertFalse(r.retryable)
    }

    @Test
    fun `other 5xx falls into server_error retryable`() {
        val e = FakeApiException("gateway timeout", statusCode = 504)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.server_error, r.reason)
        assertTrue(r.retryable)
    }

    // ---------------------------------------------------------------------
    // 400 branch — context overflow, model-not-found, format_error
    // ---------------------------------------------------------------------

    @Test
    fun `400 with context_length msg routes to context_overflow`() {
        val e = FakeApiException("context length exceeded", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.context_overflow, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `400 with invalid model msg routes to model_not_found`() {
        val e = FakeApiException("is not a valid model id", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.model_not_found, r.reason)
    }

    @Test
    fun `400 rate-limit text beats billing pattern order`() {
        val e = FakeApiException("rate limit reached", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.rate_limit, r.reason)
    }

    @Test
    fun `400 billing pattern routes to billing`() {
        val e = FakeApiException("insufficient_quota", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.billing, r.reason)
    }

    @Test
    fun `400 with generic short body + large session infers context_overflow`() {
        val e = FakeApiException("error", statusCode = 400)
        val r = classifyApiError(e, approxTokens = 100000)
        assertEquals(FailoverReason.context_overflow, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `400 with unknown text falls to format_error`() {
        val e = FakeApiException("some random client-side problem entirely", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.format_error, r.reason)
    }

    // ---------------------------------------------------------------------
    // Special-case provider patterns (priority 1)
    // ---------------------------------------------------------------------

    @Test
    fun `thinking signature trips before generic 400 classification`() {
        val e = FakeApiException("invalid signature in thinking block", statusCode = 400)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.thinking_signature, r.reason)
        assertTrue(r.retryable)
        assertFalse(r.shouldCompress)
    }

    @Test
    fun `long context tier trips before 429 rate_limit`() {
        val e = FakeApiException(
            "extra usage required for long context window",
            statusCode = 429,
        )
        val r = classifyApiError(e)
        assertEquals(FailoverReason.long_context_tier, r.reason)
        assertTrue(r.shouldCompress)
    }

    // ---------------------------------------------------------------------
    // Error-code (body-level) classification
    // ---------------------------------------------------------------------

    @Test
    fun `error code resource_exhausted maps to rate_limit`() {
        val body = errBody(messageField = "some limit msg", code = "resource_exhausted")
        val e = FakeApiException("failed", body = body)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.rate_limit, r.reason)
    }

    @Test
    fun `error code insufficient_quota maps to billing`() {
        val body = errBody(code = "insufficient_quota")
        val e = FakeApiException("", body = body)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.billing, r.reason)
    }

    @Test
    fun `error code context_length_exceeded maps to context_overflow`() {
        val body = errBody(code = "context_length_exceeded")
        val e = FakeApiException("", body = body)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.context_overflow, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `error code model_not_found maps to model_not_found`() {
        val body = errBody(code = "model_not_found")
        val e = FakeApiException("", body = body)
        val r = classifyApiError(e)
        assertEquals(FailoverReason.model_not_found, r.reason)
    }

    // ---------------------------------------------------------------------
    // Message-only classification (no status code)
    // ---------------------------------------------------------------------

    @Test
    fun `msg-only payload too large routes to payload_too_large`() {
        val e = RuntimeException("error code: 413 payload too large")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.payload_too_large, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `msg-only billing pattern routes to billing`() {
        val e = RuntimeException("credit balance is too low")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.billing, r.reason)
    }

    @Test
    fun `msg-only rate_limit pattern routes to rate_limit`() {
        val e = RuntimeException("too many requests, please slow down")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.rate_limit, r.reason)
    }

    @Test
    fun `msg-only context overflow pattern routes to context_overflow`() {
        val e = RuntimeException("maximum context length exceeded by the prompt")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.context_overflow, r.reason)
    }

    @Test
    fun `msg-only auth pattern routes to auth`() {
        val e = RuntimeException("invalid api key supplied")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.auth, r.reason)
    }

    @Test
    fun `msg-only model not found pattern routes to model_not_found`() {
        val e = RuntimeException("unsupported model: foo-v9")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.model_not_found, r.reason)
    }

    // ---------------------------------------------------------------------
    // Transport / disconnect heuristics
    // ---------------------------------------------------------------------

    @Test
    fun `IOException is classified as timeout`() {
        val e = java.io.IOException("socket closed")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.timeout, r.reason)
        assertTrue(r.retryable)
    }

    @Test
    fun `SocketTimeoutException is classified as timeout`() {
        val e = java.net.SocketTimeoutException("read timeout")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.timeout, r.reason)
    }

    @Test
    fun `disconnect plus large session infers context_overflow`() {
        val e = RuntimeException("server disconnected without sending a response")
        val r = classifyApiError(e, approxTokens = 150000)
        assertEquals(FailoverReason.context_overflow, r.reason)
        assertTrue(r.shouldCompress)
    }

    @Test
    fun `disconnect alone routes to timeout`() {
        val e = RuntimeException("server disconnected without sending a response")
        val r = classifyApiError(e, approxTokens = 100)
        assertEquals(FailoverReason.timeout, r.reason)
    }

    // ---------------------------------------------------------------------
    // Fallback
    // ---------------------------------------------------------------------

    @Test
    fun `totally unknown error falls through to unknown`() {
        val e = RuntimeException("something entirely novel happened here")
        val r = classifyApiError(e)
        assertEquals(FailoverReason.unknown, r.reason)
        assertTrue(r.retryable)
    }

    // ---------------------------------------------------------------------
    // Body / code / message extraction helpers
    // ---------------------------------------------------------------------

    @Test
    fun `_extractErrorCode reads error dot code`() {
        val body = errBody(code = "resource_exhausted")
        assertEquals("resource_exhausted", _extractErrorCode(body))
    }

    @Test
    fun `_extractErrorCode falls back to top-level code`() {
        val body = JSONObject().put("code", "top_level")
        assertEquals("top_level", _extractErrorCode(body))
    }

    @Test
    fun `_extractErrorCode reads integer code as string`() {
        val body = JSONObject().put("error_code", 502)
        assertEquals("502", _extractErrorCode(body))
    }

    @Test
    fun `_extractErrorCode returns empty for empty body`() {
        assertEquals("", _extractErrorCode(JSONObject()))
    }

    @Test
    fun `_extractMessage prefers body error message over throwable`() {
        val body = errBody(messageField = "server-side reason")
        val e = RuntimeException("throw-side reason")
        assertEquals("server-side reason", _extractMessage(e, body))
    }

    @Test
    fun `_extractMessage trims and caps at 500 chars`() {
        val longMsg = "x".repeat(800)
        val body = errBody(messageField = longMsg)
        val e = RuntimeException("ignored")
        assertEquals(500, _extractMessage(e, body).length)
    }

    @Test
    fun `_extractMessage falls back to throwable when body empty`() {
        val e = RuntimeException("no body here")
        assertEquals("no body here", _extractMessage(e, JSONObject()))
    }

    @Test
    fun `_extractStatusCode returns null when absent`() {
        assertNull(_extractStatusCode(RuntimeException("plain")))
    }

    @Test
    fun `_extractStatusCode reads reflective getStatusCode`() {
        val e = FakeApiException("boom", statusCode = 418)
        assertEquals(418, _extractStatusCode(e))
    }

    @Test
    fun `_extractErrorBody parses body on the exception`() {
        val body = errBody(code = "x")
        val e = FakeApiException("boom", body = body)
        val parsed = _extractErrorBody(e)
        assertEquals("x", parsed.getJSONObject("error").getString("code"))
    }

    // ---------------------------------------------------------------------
    // ClassifiedError.isAuth
    // ---------------------------------------------------------------------

    @Test
    fun `isAuth true for auth and auth_permanent only`() {
        assertTrue(ClassifiedError(FailoverReason.auth).isAuth())
        assertTrue(ClassifiedError(FailoverReason.auth_permanent).isAuth())
        assertFalse(ClassifiedError(FailoverReason.rate_limit).isAuth())
        assertFalse(ClassifiedError(FailoverReason.unknown).isAuth())
    }

    @Test
    fun `FailoverReason enum values match python names`() {
        // Sanity: string values are used for wire compatibility with Python.
        assertEquals("auth", FailoverReason.auth.value)
        assertEquals("rate_limit", FailoverReason.rate_limit.value)
        assertEquals("context_overflow", FailoverReason.context_overflow.value)
        assertEquals("unknown", FailoverReason.unknown.value)
    }
}
