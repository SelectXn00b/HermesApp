package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/fetch-auth.ts
 *
 * Scoped token provider and fetch-with-bearer-auth-fallback for plugin HTTP calls.
 * Android adaptation: uses OkHttp instead of global fetch().
 */

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// ---------- Types ----------

/**
 * Scope token provider interface.
 * Aligned with TS ScopeTokenProvider.
 */
interface ScopeTokenProvider {
    suspend fun getAccessToken(scope: String): String
}

// ---------- Auth Failure Check ----------

/**
 * Check if an HTTP status indicates auth failure.
 * Aligned with TS isAuthFailureStatus.
 */
fun isAuthFailureStatus(status: Int): Boolean = status == 401 || status == 403

// ---------- Fetch with Auth ----------

/**
 * Retry a fetch with bearer tokens from the provided scopes when the
 * unauthenticated attempt fails.
 * Aligned with TS fetchWithBearerAuthScopeFallback.
 *
 * Android: uses OkHttp; caller provides [client].
 */
suspend fun fetchWithBearerAuthScopeFallback(
    url: String,
    scopes: List<String>,
    tokenProvider: ScopeTokenProvider? = null,
    client: OkHttpClient = OkHttpClient(),
    requestBuilder: Request.Builder = Request.Builder(),
    requireHttps: Boolean = false,
    shouldAttachAuth: ((String) -> Boolean)? = null,
    shouldRetry: ((Response) -> Boolean)? = null,
): Response {
    val parsedUrl = try {
        java.net.URL(url)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid URL: $url")
    }

    if (requireHttps && parsedUrl.protocol != "https") {
        throw IllegalArgumentException("URL must use HTTPS: $url")
    }

    val baseRequest = requestBuilder.url(url).build()

    fun fetchOnce(authHeader: String? = null): Response {
        val req = if (authHeader != null) {
            baseRequest.newBuilder().header("Authorization", authHeader).build()
        } else {
            baseRequest
        }
        return client.newCall(req).execute()
    }

    val firstAttempt = fetchOnce()
    if (firstAttempt.isSuccessful) return firstAttempt
    if (tokenProvider == null) return firstAttempt

    val retryCheck = shouldRetry ?: { response -> isAuthFailureStatus(response.code) }
    if (!retryCheck(firstAttempt)) return firstAttempt
    if (shouldAttachAuth != null && !shouldAttachAuth(url)) return firstAttempt

    for (scope in scopes) {
        try {
            val token = tokenProvider.getAccessToken(scope)
            val authAttempt = fetchOnce("Bearer $token")
            if (authAttempt.isSuccessful) return authAttempt
            if (!retryCheck(authAttempt)) continue
        } catch (_: Exception) {
            // Ignore token/fetch errors and continue trying remaining scopes.
        }
    }

    return firstAttempt
}
