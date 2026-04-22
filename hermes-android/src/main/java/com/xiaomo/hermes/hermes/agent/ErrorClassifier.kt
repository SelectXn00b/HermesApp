/**
 * API error classification for smart failover and recovery.
 *
 * 1:1 对齐 hermes/agent/error_classifier.py (Python 原始)
 *
 * Provides a structured taxonomy of API errors and a priority-ordered
 * classification pipeline that determines the correct recovery action
 * (retry, rotate credential, fallback to another provider, compress
 * context, or abort).
 */
package com.xiaomo.hermes.hermes.agent

import org.json.JSONObject


// ── Error taxonomy ──────────────────────────────────────────────────────

enum class FailoverReason(val value: String) {
    // Authentication / authorization
    auth("auth"),                        // Transient auth (401/403) — refresh/rotate
    auth_permanent("auth_permanent"),    // Auth failed after refresh — abort

    // Billing / quota
    billing("billing"),                  // 402 or confirmed credit exhaustion
    rate_limit("rate_limit"),            // 429 or quota-based throttling

    // Server-side
    overloaded("overloaded"),            // 503/529 — provider overloaded
    server_error("server_error"),        // 500/502 — internal server error

    // Transport
    timeout("timeout"),                  // Connection/read timeout

    // Context / payload
    context_overflow("context_overflow"),    // Context too large — compress
    payload_too_large("payload_too_large"),  // 413 — compress payload

    // Model
    model_not_found("model_not_found"),  // 404 or invalid model

    // Request format
    format_error("format_error"),        // 400 bad request

    // Provider-specific
    thinking_signature("thinking_signature"),  // Anthropic thinking sig invalid
    long_context_tier("long_context_tier"),    // Anthropic "extra usage" gate

    // Catch-all
    unknown("unknown"),
}


// ── Classification result ───────────────────────────────────────────────

data class ClassifiedError(
    val reason: FailoverReason,
    val statusCode: Int? = null,
    val provider: String? = null,
    val model: String? = null,
    val message: String = "",
    val errorContext: Map<String, Any?> = emptyMap(),
    val retryable: Boolean = true,
    val shouldCompress: Boolean = false,
    val shouldRotateCredential: Boolean = false,
    val shouldFallback: Boolean = false,
) {
    fun isAuth(): Boolean = reason == FailoverReason.auth || reason == FailoverReason.auth_permanent
}


// ── Provider-specific patterns ──────────────────────────────────────────

// Patterns that indicate billing exhaustion (not transient rate limit)
val _BILLING_PATTERNS: List<String> = listOf(
    "insufficient credits",
    "insufficient_quota",
    "credit balance",
    "credits have been exhausted",
    "top up your credits",
    "payment required",
    "billing hard limit",
    "exceeded your current quota",
    "account is deactivated",
    "plan does not include",
)

// Patterns that indicate rate limiting (transient, will resolve)
val _RATE_LIMIT_PATTERNS: List<String> = listOf(
    "rate limit",
    "rate_limit",
    "too many requests",
    "throttled",
    "requests per minute",
    "tokens per minute",
    "requests per day",
    "try again in",
    "please retry after",
    "resource_exhausted",
    "rate increased too quickly",
    // AWS Bedrock throttling
    "throttlingexception",
    "too many concurrent requests",
    "servicequotaexceededexception",
)

// Usage-limit patterns that need disambiguation (could be billing OR rate_limit)
val _USAGE_LIMIT_PATTERNS: List<String> = listOf(
    "usage limit",
    "quota",
    "limit exceeded",
    "key limit exceeded",
)

// Patterns confirming usage limit is transient (not billing)
val _USAGE_LIMIT_TRANSIENT_SIGNALS: List<String> = listOf(
    "try again",
    "retry",
    "resets at",
    "reset in",
    "wait",
    "requests remaining",
    "periodic",
    "window",
)

// Payload-too-large patterns detected from message text (no status_code attr).
val _PAYLOAD_TOO_LARGE_PATTERNS: List<String> = listOf(
    "request entity too large",
    "payload too large",
    "error code: 413",
)

// Context overflow patterns
val _CONTEXT_OVERFLOW_PATTERNS: List<String> = listOf(
    "context length",
    "context size",
    "maximum context",
    "token limit",
    "too many tokens",
    "reduce the length",
    "exceeds the limit",
    "context window",
    "prompt is too long",
    "prompt exceeds max length",
    "max_tokens",
    "maximum number of tokens",
    // vLLM / local inference server patterns
    "exceeds the max_model_len",
    "max_model_len",
    "prompt length",
    "input is too long",
    "maximum model length",
    // Ollama patterns
    "context length exceeded",
    "truncating input",
    // llama.cpp / llama-server patterns
    "slot context",
    "n_ctx_slot",
    // Chinese error messages
    "超过最大长度",
    "上下文长度",
    // AWS Bedrock Converse API error patterns
    "input is too long",
    "max input token",
    "input token",
    "exceeds the maximum number of input tokens",
)

// Model not found patterns
val _MODEL_NOT_FOUND_PATTERNS: List<String> = listOf(
    "is not a valid model",
    "invalid model",
    "model not found",
    "model_not_found",
    "does not exist",
    "no such model",
    "unknown model",
    "unsupported model",
)

// Auth patterns (non-status-code signals)
val _AUTH_PATTERNS: List<String> = listOf(
    "invalid api key",
    "invalid_api_key",
    "authentication",
    "unauthorized",
    "forbidden",
    "invalid token",
    "token expired",
    "token revoked",
    "access denied",
)

// Anthropic thinking block signature patterns
val _THINKING_SIG_PATTERNS: List<String> = listOf(
    "signature",
)

// Transport error type names
val _TRANSPORT_ERROR_TYPES: Set<String> = setOf(
    "ReadTimeout", "ConnectTimeout", "PoolTimeout",
    "ConnectError", "RemoteProtocolError",
    "ConnectionError", "ConnectionResetError",
    "ConnectionAbortedError", "BrokenPipeError",
    "TimeoutError", "ReadError",
    "ServerDisconnectedError",
    // OpenAI SDK errors
    "APIConnectionError",
    "APITimeoutError",
    // JVM-side common IO/transport
    "SocketTimeoutException",
    "InterruptedIOException",
    "UnknownHostException",
    "SocketException",
    "SSLHandshakeException",
)

// Server disconnect patterns
val _SERVER_DISCONNECT_PATTERNS: List<String> = listOf(
    "server disconnected",
    "peer closed connection",
    "connection reset by peer",
    "connection was closed",
    "network connection lost",
    "unexpected eof",
    "incomplete chunked read",
)


// ── Classification pipeline ─────────────────────────────────────────────

/**
 * Classify an API error into a structured recovery recommendation.
 *
 * Priority-ordered pipeline:
 *   1. Special-case provider-specific patterns (thinking sigs, tier gates)
 *   2. HTTP status code + message-aware refinement
 *   3. Error code classification (from body)
 *   4. Message pattern matching (billing vs rate_limit vs context vs auth)
 *   5. Transport error heuristics
 *   6. Server disconnect + large session → context overflow
 *   7. Fallback: unknown (retryable with backoff)
 */
fun classifyApiError(
    error: Throwable,
    provider: String = "",
    model: String = "",
    approxTokens: Int = 0,
    contextLength: Int = 200000,
    numMessages: Int = 0,
): ClassifiedError {
    val statusCode = _extractStatusCode(error)
    val errorType = error.javaClass.simpleName
    val body = _extractErrorBody(error)
    val errorCode = _extractErrorCode(body)

    val rawMsg = (error.message ?: "").lowercase()
    var bodyMsg = ""
    var metadataMsg = ""
    val errObj = body.opt("error")
    if (errObj is JSONObject) {
        bodyMsg = (errObj.opt("message") as? String)?.lowercase() ?: ""
        val metadata = errObj.opt("metadata")
        if (metadata is JSONObject) {
            val rawJson = metadata.opt("raw") as? String
            if (!rawJson.isNullOrBlank()) {
                try {
                    val inner = JSONObject(rawJson)
                    val innerErr = inner.opt("error")
                    if (innerErr is JSONObject) {
                        metadataMsg = (innerErr.opt("message") as? String)?.lowercase() ?: ""
                    }
                } catch (_: Exception) {
                    // ignore malformed nested JSON
                }
            }
        }
    }
    if (bodyMsg.isEmpty()) {
        bodyMsg = (body.opt("message") as? String)?.lowercase() ?: ""
    }

    val parts = mutableListOf(rawMsg)
    if (bodyMsg.isNotEmpty() && bodyMsg !in rawMsg) parts.add(bodyMsg)
    if (metadataMsg.isNotEmpty() && metadataMsg !in rawMsg && metadataMsg !in bodyMsg) parts.add(metadataMsg)
    val errorMsg = parts.joinToString(" ")
    val providerLower = provider.trim().lowercase()
    val modelLower = model.trim().lowercase()

    fun _result(
        reason: FailoverReason,
        retryable: Boolean = true,
        shouldCompress: Boolean = false,
        shouldRotateCredential: Boolean = false,
        shouldFallback: Boolean = false,
    ): ClassifiedError = ClassifiedError(
        reason = reason,
        statusCode = statusCode,
        provider = provider,
        model = model,
        message = _extractMessage(error, body),
        retryable = retryable,
        shouldCompress = shouldCompress,
        shouldRotateCredential = shouldRotateCredential,
        shouldFallback = shouldFallback,
    )

    // ── 1. Provider-specific patterns (highest priority) ────────────
    if (statusCode == 400 && "signature" in errorMsg && "thinking" in errorMsg) {
        return _result(
            FailoverReason.thinking_signature,
            retryable = true,
            shouldCompress = false,
        )
    }
    if (statusCode == 429 && "extra usage" in errorMsg && "long context" in errorMsg) {
        return _result(
            FailoverReason.long_context_tier,
            retryable = true,
            shouldCompress = true,
        )
    }

    // ── 2. HTTP status code classification ──────────────────────────
    if (statusCode != null) {
        val classified = _classifyByStatus(
            statusCode, errorMsg, errorCode, body,
            provider = providerLower, model = modelLower,
            approxTokens = approxTokens, contextLength = contextLength,
            numMessages = numMessages,
            resultFn = ::_result,
        )
        if (classified != null) return classified
    }

    // ── 3. Error code classification ────────────────────────────────
    if (errorCode.isNotEmpty()) {
        val classified = _classifyByErrorCode(errorCode, errorMsg, ::_result)
        if (classified != null) return classified
    }

    // ── 4. Message pattern matching (no status code) ────────────────
    val classified = _classifyByMessage(
        errorMsg, errorType,
        approxTokens = approxTokens,
        contextLength = contextLength,
        resultFn = ::_result,
    )
    if (classified != null) return classified

    // ── 5. Server disconnect + large session → context overflow ─────
    val isDisconnect = _SERVER_DISCONNECT_PATTERNS.any { it in errorMsg }
    if (isDisconnect && statusCode == null) {
        val isLarge = approxTokens > contextLength * 0.6 ||
            approxTokens > 120000 ||
            numMessages > 200
        if (isLarge) {
            return _result(
                FailoverReason.context_overflow,
                retryable = true,
                shouldCompress = true,
            )
        }
        return _result(FailoverReason.timeout, retryable = true)
    }

    // ── 6. Transport / timeout heuristics ───────────────────────────
    if (errorType in _TRANSPORT_ERROR_TYPES ||
        error is java.util.concurrent.TimeoutException ||
        error is java.io.IOException
    ) {
        return _result(FailoverReason.timeout, retryable = true)
    }

    // ── 7. Fallback: unknown ────────────────────────────────────────
    return _result(FailoverReason.unknown, retryable = true)
}


// ── Status code classification ──────────────────────────────────────────

fun _classifyByStatus(
    statusCode: Int,
    errorMsg: String,
    errorCode: String,
    body: JSONObject,
    provider: String,
    model: String,
    approxTokens: Int,
    contextLength: Int,
    numMessages: Int = 0,
    resultFn: (FailoverReason, Boolean, Boolean, Boolean, Boolean) -> ClassifiedError,
): ClassifiedError? {

    if (statusCode == 401) {
        return resultFn(
            FailoverReason.auth,
            /*retryable=*/false,
            /*shouldCompress=*/false,
            /*shouldRotateCredential=*/true,
            /*shouldFallback=*/true,
        )
    }

    if (statusCode == 403) {
        if ("key limit exceeded" in errorMsg || "spending limit" in errorMsg) {
            return resultFn(
                FailoverReason.billing,
                false, false, true, true,
            )
        }
        return resultFn(
            FailoverReason.auth,
            false, false, false, true,
        )
    }

    if (statusCode == 402) {
        return _classify402(errorMsg, resultFn)
    }

    if (statusCode == 404) {
        // 404 is always model-not-found (caller chose provider+model)
        return resultFn(
            FailoverReason.model_not_found,
            false, false, false, true,
        )
    }

    if (statusCode == 413) {
        return resultFn(
            FailoverReason.payload_too_large,
            true, true, false, false,
        )
    }

    if (statusCode == 429) {
        return resultFn(
            FailoverReason.rate_limit,
            true, false, true, true,
        )
    }

    if (statusCode == 400) {
        return _classify400(
            errorMsg, errorCode, body,
            provider = provider, model = model,
            approxTokens = approxTokens,
            contextLength = contextLength,
            numMessages = numMessages,
            resultFn = resultFn,
        )
    }

    if (statusCode == 500 || statusCode == 502) {
        return resultFn(FailoverReason.server_error, true, false, false, false)
    }

    if (statusCode == 503 || statusCode == 529) {
        return resultFn(FailoverReason.overloaded, true, false, false, false)
    }

    // Other 4xx — non-retryable
    if (statusCode in 400..499) {
        return resultFn(
            FailoverReason.format_error,
            false, false, false, true,
        )
    }

    // Other 5xx — retryable
    if (statusCode in 500..599) {
        return resultFn(FailoverReason.server_error, true, false, false, false)
    }

    return null
}


fun _classify402(
    errorMsg: String,
    resultFn: (FailoverReason, Boolean, Boolean, Boolean, Boolean) -> ClassifiedError,
): ClassifiedError {
    val hasUsageLimit = _USAGE_LIMIT_PATTERNS.any { it in errorMsg }
    val hasTransientSignal = _USAGE_LIMIT_TRANSIENT_SIGNALS.any { it in errorMsg }

    if (hasUsageLimit && hasTransientSignal) {
        return resultFn(
            FailoverReason.rate_limit,
            true, false, true, true,
        )
    }

    return resultFn(
        FailoverReason.billing,
        false, false, true, true,
    )
}


fun _classify400(
    errorMsg: String,
    errorCode: String,
    body: JSONObject,
    provider: String,
    model: String,
    approxTokens: Int,
    contextLength: Int,
    numMessages: Int = 0,
    resultFn: (FailoverReason, Boolean, Boolean, Boolean, Boolean) -> ClassifiedError,
): ClassifiedError {

    if (_CONTEXT_OVERFLOW_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.context_overflow,
            true, true, false, false,
        )
    }

    if (_MODEL_NOT_FOUND_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.model_not_found,
            false, false, false, true,
        )
    }

    if (_RATE_LIMIT_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.rate_limit,
            true, false, true, true,
        )
    }
    if (_BILLING_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.billing,
            false, false, true, true,
        )
    }

    // Generic 400 + large session → probable context overflow
    var errBodyMsg = ""
    val errObj = body.opt("error")
    if (errObj is JSONObject) {
        errBodyMsg = ((errObj.opt("message") as? String) ?: "").trim().lowercase()
    }
    if (errBodyMsg.isEmpty()) {
        errBodyMsg = ((body.opt("message") as? String) ?: "").trim().lowercase()
    }
    val isGeneric = errBodyMsg.length < 30 || errBodyMsg == "error" || errBodyMsg.isEmpty()
    val isLarge = approxTokens > contextLength * 0.4 ||
        approxTokens > 80000 ||
        numMessages > 80

    if (isGeneric && isLarge) {
        return resultFn(
            FailoverReason.context_overflow,
            true, true, false, false,
        )
    }

    return resultFn(
        FailoverReason.format_error,
        false, false, false, true,
    )
}


// ── Error code classification ───────────────────────────────────────────

fun _classifyByErrorCode(
    errorCode: String,
    errorMsg: String,
    resultFn: (FailoverReason, Boolean, Boolean, Boolean, Boolean) -> ClassifiedError,
): ClassifiedError? {
    val codeLower = errorCode.lowercase()

    if (codeLower in setOf("resource_exhausted", "throttled", "rate_limit_exceeded")) {
        return resultFn(
            FailoverReason.rate_limit,
            true, false, true, false,
        )
    }
    if (codeLower in setOf("insufficient_quota", "billing_not_active", "payment_required")) {
        return resultFn(
            FailoverReason.billing,
            false, false, true, true,
        )
    }
    if (codeLower in setOf("model_not_found", "model_not_available", "invalid_model")) {
        return resultFn(
            FailoverReason.model_not_found,
            false, false, false, true,
        )
    }
    if (codeLower in setOf("context_length_exceeded", "max_tokens_exceeded")) {
        return resultFn(
            FailoverReason.context_overflow,
            true, true, false, false,
        )
    }
    return null
}


// ── Message pattern classification ──────────────────────────────────────

fun _classifyByMessage(
    errorMsg: String,
    errorType: String,
    approxTokens: Int,
    contextLength: Int,
    resultFn: (FailoverReason, Boolean, Boolean, Boolean, Boolean) -> ClassifiedError,
): ClassifiedError? {

    if (_PAYLOAD_TOO_LARGE_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.payload_too_large,
            true, true, false, false,
        )
    }

    val hasUsageLimit = _USAGE_LIMIT_PATTERNS.any { it in errorMsg }
    if (hasUsageLimit) {
        val hasTransientSignal = _USAGE_LIMIT_TRANSIENT_SIGNALS.any { it in errorMsg }
        if (hasTransientSignal) {
            return resultFn(
                FailoverReason.rate_limit,
                true, false, true, true,
            )
        }
        return resultFn(
            FailoverReason.billing,
            false, false, true, true,
        )
    }

    if (_BILLING_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.billing,
            false, false, true, true,
        )
    }

    if (_RATE_LIMIT_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.rate_limit,
            true, false, true, true,
        )
    }

    if (_CONTEXT_OVERFLOW_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.context_overflow,
            true, true, false, false,
        )
    }

    if (_AUTH_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.auth,
            false, false, true, true,
        )
    }

    if (_MODEL_NOT_FOUND_PATTERNS.any { it in errorMsg }) {
        return resultFn(
            FailoverReason.model_not_found,
            false, false, false, true,
        )
    }

    return null
}


// ── Helpers ─────────────────────────────────────────────────────────────

/** Walk the error and its cause chain to find an HTTP status code. */
fun _extractStatusCode(error: Throwable): Int? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        val c: Any? = try {
            current.javaClass.getMethod("getStatusCode").invoke(current)
        } catch (_: Throwable) { null }
        if (c is Int) return c
        // Some SDKs use .status instead of .status_code
        val s: Any? = try {
            current.javaClass.getMethod("getStatus").invoke(current)
        } catch (_: Throwable) { null }
        if (s is Int && s in 100..599) return s
        // Walk cause chain
        val cause = current.cause
        if (cause == null || cause === current) break
        current = cause
        depth++
    }
    return null
}


/** Extract the structured error body from an SDK exception. */
fun _extractErrorBody(error: Throwable): JSONObject {
    val body: Any? = try {
        error.javaClass.getMethod("getBody").invoke(error)
    } catch (_: Throwable) { null }
    if (body is JSONObject) return body
    if (body is Map<*, *>) {
        return try { JSONObject(body as Map<*, *>) } catch (_: Exception) { JSONObject() }
    }
    if (body is String && body.isNotBlank()) {
        return try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }
    return JSONObject()
}


/** Extract an error code string from the response body. */
fun _extractErrorCode(body: JSONObject): String {
    if (body.length() == 0) return ""
    val errorObj = body.opt("error")
    if (errorObj is JSONObject) {
        val code = errorObj.opt("code") ?: errorObj.opt("type")
        if (code is String && code.isNotBlank()) return code.trim()
    }
    val topCode = body.opt("code") ?: body.opt("error_code")
    if (topCode is String) return topCode.trim()
    if (topCode is Int) return topCode.toString()
    return ""
}


/** Extract the most informative error message. */
fun _extractMessage(error: Throwable, body: JSONObject): String {
    if (body.length() > 0) {
        val errorObj = body.opt("error")
        if (errorObj is JSONObject) {
            val msg = errorObj.opt("message") as? String
            if (!msg.isNullOrBlank()) return msg.trim().take(500)
        }
        val msg = body.opt("message") as? String
        if (!msg.isNullOrBlank()) return msg.trim().take(500)
    }
    return (error.message ?: error.toString()).take(500)
}
