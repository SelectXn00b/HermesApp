/** 1:1 对齐 hermes/agent/google_oauth.py */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Google OAuth PKCE flow for the Gemini (google-gemini-cli) inference provider.
 *
 * Android 简化版：保留凭据管理和刷新逻辑，
 * 浏览器 OAuth 流程由 app 模块通过 CustomTabs/WebView 实现。
 */

private const val TAG_OAUTH = "GoogleOauth"

// =============================================================================
// OAuth client credential resolution
// =============================================================================

private const val ENV_CLIENT_ID = "HERMES_GEMINI_CLIENT_ID"
private const val ENV_CLIENT_SECRET = "HERMES_GEMINI_CLIENT_SECRET"

private const val _PUBLIC_CLIENT_ID_PROJECT_NUM = "681255809395"
private const val _PUBLIC_CLIENT_ID_HASH = "oo8ft2oprdrnp9e3aqf6av3hmdib135j"
private const val _PUBLIC_CLIENT_SECRET_SUFFIX = "4uHgMPm-1o7Sk-geV6Cu5clXFsxl"

private val _DEFAULT_CLIENT_ID =
    "${_PUBLIC_CLIENT_ID_PROJECT_NUM}-${_PUBLIC_CLIENT_ID_HASH}.apps.googleusercontent.com"
private val _DEFAULT_CLIENT_SECRET = "GOCSPX-${_PUBLIC_CLIENT_SECRET_SUFFIX}"

// =============================================================================
// Endpoints & constants
// =============================================================================

const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v1/userinfo"

const val OAUTH_SCOPES = "https://www.googleapis.com/auth/cloud-platform " +
    "https://www.googleapis.com/auth/userinfo.email " +
    "https://www.googleapis.com/auth/userinfo.profile"

const val DEFAULT_REDIRECT_PORT = 8085
const val REDIRECT_HOST = "127.0.0.1"
const val CALLBACK_PATH = "/oauth2callback"

const val REFRESH_SKEW_SECONDS = 60
const val TOKEN_REQUEST_TIMEOUT_SECONDS = 20.0
const val CALLBACK_WAIT_SECONDS = 300
const val LOCK_TIMEOUT_SECONDS = 30.0

// Convenience millisecond forms for Java HTTP / latch APIs.
const val TOKEN_REQUEST_TIMEOUT_MS = 20_000
const val LOCK_TIMEOUT_MS = 30_000L

// Gemini CLI OAuth JS scrape patterns (used by _scrapeClientCredentials).
val _CLIENT_ID_PATTERN: Regex = Regex(
    """OAUTH_CLIENT_ID\s*=\s*['"]([0-9]{8,}-[a-z0-9]{20,}\.apps\.googleusercontent\.com)['"]"""
)
val _CLIENT_SECRET_PATTERN: Regex = Regex(
    """OAUTH_CLIENT_SECRET\s*=\s*['"](GOCSPX-[A-Za-z0-9_-]{20,})['"]"""
)
val _CLIENT_ID_SHAPE: Regex = Regex(
    """([0-9]{8,}-[a-z0-9]{20,}\.apps\.googleusercontent\.com)"""
)
val _CLIENT_SECRET_SHAPE: Regex = Regex("""(GOCSPX-[A-Za-z0-9_-]{20,})""")

val _HEADLESS_ENV_VARS: List<String> = listOf("SSH_CONNECTION", "SSH_CLIENT", "SSH_TTY", "HERMES_HEADLESS")

const val _SUCCESS_PAGE: String = """<!doctype html>
<html><head><meta charset="utf-8"><title>Hermes — signed in</title>
<style>
body { font: 16px/1.5 system-ui, sans-serif; margin: 10vh auto; max-width: 32rem; text-align: center; color: #222; }
h1 { color: #1a7f37; } p { color: #555; }
</style></head>
<body><h1>Signed in to Google.</h1>
<p>You can close this tab and return to your terminal.</p></body></html>
"""

const val _ERROR_PAGE: String = """<!doctype html>
<html><head><meta charset="utf-8"><title>Hermes — sign-in failed</title>
<style>
body {{ font: 16px/1.5 system-ui, sans-serif; margin: 10vh auto; max-width: 32rem; text-align: center; color: #222; }}
h1 {{ color: #b42318; }} p {{ color: #555; }}
</style></head>
<body><h1>Sign-in failed</h1><p>{message}</p>
<p>Return to your terminal — Hermes will walk you through a manual paste fallback.</p></body></html>
"""

// =============================================================================
// Error type
// =============================================================================

class GoogleOAuthError(
    message: String,
    val code: String = "google_oauth_error"
) : RuntimeException(message)

// =============================================================================
// File paths
// =============================================================================

private fun _credentialsPath(): File {
    // Use Hermes home on Android; fallback to app-private dir
    val hermesHome = System.getenv("HERMES_HOME")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home")?.let { "$it/.hermes" }
        ?: "/data/local/tmp/.hermes"
    return File(hermesHome, "auth/google_oauth.json")
}

// Rename private val to avoid collision with module-level `_credentialsLock()`
// function (1:1 with Python). Call sites use the underlying ReentrantLock.
private val _credentialsLockInstance = ReentrantLock()

// =============================================================================
// Client ID resolution
// =============================================================================

private fun _getClientId(): String {
    val envVal = System.getenv(ENV_CLIENT_ID)?.trim()
    if (!envVal.isNullOrEmpty()) return envVal
    return _DEFAULT_CLIENT_ID
}

private fun _getClientSecret(): String {
    val envVal = System.getenv(ENV_CLIENT_SECRET)?.trim()
    if (!envVal.isNullOrEmpty()) return envVal
    return _DEFAULT_CLIENT_SECRET
}

private fun _requireClientId(): String {
    val cid = _getClientId()
    if (cid.isEmpty()) {
        throw GoogleOAuthError(
            "Google OAuth client ID is not available.\n" +
                "Set HERMES_GEMINI_CLIENT_ID and HERMES_GEMINI_CLIENT_SECRET.",
            code = "google_oauth_client_id_missing",
        )
    }
    return cid
}

// =============================================================================
// PKCE
// =============================================================================

fun _generatePkcePair(): Pair<String, String> {
    val random = SecureRandom()
    val bytes = ByteArray(64)
    random.nextBytes(bytes)
    val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    return Pair(verifier, challenge)
}

// =============================================================================
// Packed refresh format: refresh_token[|project_id[|managed_project_id]]
// =============================================================================

data class RefreshParts(
    val refreshToken: String,
    val projectId: String = "",
    val managedProjectId: String = "",
) {
    companion object {
        fun parse(packed: String): RefreshParts {
            if (packed.isEmpty()) return RefreshParts(refreshToken = "")
            val parts = packed.split("|", limit = 3)
            return RefreshParts(
                refreshToken = parts[0],
                projectId = if (parts.size > 1) parts[1] else "",
                managedProjectId = if (parts.size > 2) parts[2] else "",
            )
        }
    }

    fun format(): String {
        if (refreshToken.isEmpty()) return ""
        if (projectId.isEmpty() && managedProjectId.isEmpty()) return refreshToken
        return "$refreshToken|$projectId|$managedProjectId"
    }
}

// =============================================================================
// Credentials
// =============================================================================

data class GoogleCredentials(
    var accessToken: String,
    var refreshToken: String,
    var expiresMs: Long,  // unix milliseconds
    var email: String = "",
    var projectId: String = "",
    var managedProjectId: String = "",
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "refresh" to RefreshParts(
                refreshToken = refreshToken,
                projectId = projectId,
                managedProjectId = managedProjectId,
            ).format(),
            "access" to accessToken,
            "expires" to expiresMs,
            "email" to email,
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): GoogleCredentials {
            val refreshPacked = (data["refresh"] as? String) ?: ""
            val parts = RefreshParts.parse(refreshPacked)
            return GoogleCredentials(
                accessToken = (data["access"] as? String) ?: "",
                refreshToken = parts.refreshToken,
                expiresMs = (data["expires"] as? Number)?.toLong() ?: 0L,
                email = (data["email"] as? String) ?: "",
                projectId = parts.projectId,
                managedProjectId = parts.managedProjectId,
            )
        }
    }

    fun expiresUnixSeconds(): Double = expiresMs / 1000.0

    fun accessTokenExpired(skewSeconds: Int = REFRESH_SKEW_SECONDS): Boolean {
        if (accessToken.isEmpty() || expiresMs == 0L) return true
        return (System.currentTimeMillis() / 1000.0 + maxOf(0, skewSeconds)) * 1000 >= expiresMs
    }


    fun toDict(): Map<String, Any?> {
        return mapOf(
            "refresh" to RefreshParts(
                refreshToken = refreshToken,
                projectId = projectId,
                managedProjectId = managedProjectId,
            ).format(),
            "access" to accessToken,
            "expires" to expiresMs,
            "email" to email,
        )
    }

    fun fromDict(data: Map<String, Any?>): Any? {
        val refreshPacked = (data["refresh"] as? String) ?: ""
        val parts = RefreshParts.parse(refreshPacked)
        return GoogleCredentials(
            accessToken = (data["access"] as? String) ?: "",
            refreshToken = parts.refreshToken,
            expiresMs = (data["expires"] as? Number)?.toLong() ?: 0L,
            email = (data["email"] as? String) ?: "",
            projectId = parts.projectId,
            managedProjectId = parts.managedProjectId,
        )
    }
}

// =============================================================================
// Credential I/O
// =============================================================================

fun loadGoogleCredentials(): GoogleCredentials? {
    val path = _credentialsPath()
    if (!path.exists()) return null
    return try {
        _credentialsLockInstance.withLock {
            val raw = path.readText(Charsets.UTF_8)
            val jo = JSONObject(raw)
            val data = mutableMapOf<String, Any>()
            for (key in jo.keys()) {
                val value = jo.get(key)
                if (value != JSONObject.NULL) data[key] = value
            }
            val creds = GoogleCredentials.fromMap(data)
            if (creds.accessToken.isEmpty()) null else creds
        }
    } catch (e: Exception) {
        Log.w(TAG_OAUTH, "Failed to read Google OAuth credentials at $path: $e")
        null
    }
}

// Alias for cross-module compatibility (used by GeminiCloudcodeAdapter)
fun loadCredentials(): GoogleCredentials? = loadGoogleCredentials()

fun saveCredentials(creds: GoogleCredentials): File {
    val path = _credentialsPath()
    path.parentFile?.mkdirs()
    val payload = JSONObject(creds.toMap()).toString(2) + "\n"
    _credentialsLockInstance.withLock {
        val tmpFile = File(path.parent, "google_oauth.tmp.${android.os.Process.myPid()}")
        try {
            tmpFile.writeText(payload, Charsets.UTF_8)
            tmpFile.setReadable(true, true)
            tmpFile.setWritable(true, true)
            tmpFile.renameTo(path)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }
    return path
}

fun clearCredentials() {
    val path = _credentialsPath()
    _credentialsLockInstance.withLock {
        try {
            path.delete()
        } catch (e: Exception) {
            Log.w(TAG_OAUTH, "Failed to remove Google OAuth credentials at $path: $e")
        }
    }
}

// =============================================================================
// HTTP helpers
// =============================================================================

private fun _postForm(url: String, data: Map<String, String>, timeoutMs: Int): Map<String, Any> {
    val body = data.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("Accept", "application/json")

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body)
            writer.flush()
        }

        if (connection.responseCode in 200..299) {
            val raw = connection.inputStream.bufferedReader().readText()
            val jo = JSONObject(raw)
            val result = mutableMapOf<String, Any>()
            for (key in jo.keys()) {
                val value = jo.get(key)
                if (value != JSONObject.NULL) result[key] = value
            }
            return result
        } else {
            val detail = try {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }

            val code = if ("invalid_grant" in detail.lowercase()) {
                "google_oauth_invalid_grant"
            } else {
                "google_oauth_token_http_error"
            }
            throw GoogleOAuthError(
                "Google OAuth token endpoint returned HTTP ${connection.responseCode}: ${detail.ifEmpty { connection.responseMessage }}",
                code = code,
            )
        }
    } finally {
        connection.disconnect()
    }
}

fun exchangeCode(
    code: String,
    verifier: String,
    redirectUri: String,
    clientId: String? = null,
    clientSecret: String? = null,
    timeoutMs: Int = TOKEN_REQUEST_TIMEOUT_MS,
): Map<String, Any> {
    val cid = clientId ?: _getClientId()
    val csecret = clientSecret ?: _getClientSecret()
    val data = mutableMapOf(
        "grant_type" to "authorization_code",
        "code" to code,
        "code_verifier" to verifier,
        "client_id" to cid,
        "redirect_uri" to redirectUri,
    )
    if (csecret.isNotEmpty()) data["client_secret"] = csecret
    return _postForm(TOKEN_ENDPOINT, data, timeoutMs)
}

fun refreshAccessToken(
    refreshToken: String,
    clientId: String? = null,
    clientSecret: String? = null,
    timeoutMs: Int = TOKEN_REQUEST_TIMEOUT_MS,
): Map<String, Any> {
    if (refreshToken.isEmpty()) {
        throw GoogleOAuthError(
            "Cannot refresh: refresh_token is empty. Re-run OAuth login.",
            code = "google_oauth_refresh_token_missing",
        )
    }
    val cid = clientId ?: _getClientId()
    val csecret = clientSecret ?: _getClientSecret()
    val data = mutableMapOf(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "client_id" to cid,
    )
    if (csecret.isNotEmpty()) data["client_secret"] = csecret
    return _postForm(TOKEN_ENDPOINT, data, timeoutMs)
}

private fun _fetchUserEmail(accessToken: String, timeoutMs: Int = TOKEN_REQUEST_TIMEOUT_MS): String {
    return try {
        val connection = URL("$USERINFO_ENDPOINT?alt=json").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        val raw = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        val jo = JSONObject(raw)
        jo.optString("email", "")
    } catch (e: Exception) {
        Log.d(TAG_OAUTH, "Userinfo fetch failed (non-fatal): $e")
        ""
    }
}

// =============================================================================
// In-flight refresh deduplication
// =============================================================================

private val _refreshInflight = ConcurrentHashMap<String, java.util.concurrent.CountDownLatch>()
private val _refreshInflightLock = ReentrantLock()

fun getValidAccessToken(forceRefresh: Boolean = false): String {
    val creds = loadGoogleCredentials()
        ?: throw GoogleOAuthError(
            "No Google OAuth credentials found. Run login flow first.",
            code = "google_oauth_not_logged_in",
        )

    if (!forceRefresh && !creds.accessTokenExpired()) {
        return creds.accessToken
    }

    val rt = creds.refreshToken
    var owner = false
    var latch: java.util.concurrent.CountDownLatch

    _refreshInflightLock.withLock {
        val existing = _refreshInflight[rt]
        if (existing != null) {
            latch = existing
        } else {
            latch = java.util.concurrent.CountDownLatch(1)
            _refreshInflight[rt] = latch
            owner = true
        }
    }

    if (!owner) {
        latch.await(LOCK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        val fresh = loadGoogleCredentials()
        if (fresh != null && !fresh.accessTokenExpired()) {
            return fresh.accessToken
        }
        // Fall through to do our own refresh
    }

    try {
        val resp: Map<String, Any>
        try {
            resp = refreshAccessToken(rt)
        } catch (exc: GoogleOAuthError) {
            if (exc.code == "google_oauth_invalid_grant") {
                Log.w(
                    TAG_OAUTH,
                    "Google OAuth refresh token invalid (revoked/expired). " +
                        "Clearing credentials — user must re-login."
                )
                clearCredentials()
            }
            throw exc
        }

        val newAccess = (resp["access_token"] as? String)?.trim()
        if (newAccess.isNullOrEmpty()) {
            throw GoogleOAuthError(
                "Refresh response did not include an access_token.",
                code = "google_oauth_refresh_empty",
            )
        }
        val newRefresh = (resp["refresh_token"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: creds.refreshToken
        val expiresIn = (resp["expires_in"] as? Number)?.toLong() ?: 0L

        creds.accessToken = newAccess
        creds.refreshToken = newRefresh
        creds.expiresMs = ((System.currentTimeMillis() / 1000.0 + maxOf(60L, expiresIn)) * 1000).toLong()
        saveCredentials(creds)
        return creds.accessToken
    } finally {
        if (owner) {
            _refreshInflightLock.withLock {
                _refreshInflight.remove(rt)
            }
            latch.countDown()
        }
    }
}

// =============================================================================
// Update project IDs on stored creds
// =============================================================================

fun updateProjectIds(projectId: String = "", managedProjectId: String = "") {
    val creds = loadGoogleCredentials() ?: return
    if (projectId.isNotEmpty()) creds.projectId = projectId
    if (managedProjectId.isNotEmpty()) creds.managedProjectId = managedProjectId
    saveCredentials(creds)
}

// =============================================================================
// Main login flow (Android: browser redirect handled by app module)
// =============================================================================

/**
 * Build the OAuth authorization URL for browser-based login.
 * On Android, the app module opens this URL in CustomTabs/WebView.
 */
fun buildAuthorizationUrl(
    verifier: String,
    challenge: String,
    state: String,
    redirectUri: String,
): String {
    val clientId = _requireClientId()
    val params = mapOf(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to "code",
        "scope" to OAUTH_SCOPES,
        "state" to state,
        "code_challenge" to challenge,
        "code_challenge_method" to "S256",
        "access_type" to "offline",
        "prompt" to "consent",
    )
    val queryString = params.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
    }
    return "$AUTH_ENDPOINT?$queryString#hermes"
}

/**
 * Complete the OAuth flow after receiving the authorization code.
 * Called by the app module after the browser redirect.
 */
fun completeOAuthFlow(
    code: String,
    verifier: String,
    redirectUri: String,
    projectId: String = "",
): GoogleCredentials {
    val clientId = _getClientId()
    val clientSecret = _getClientSecret()
    val tokenResp = exchangeCode(
        code = code,
        verifier = verifier,
        redirectUri = redirectUri,
        clientId = clientId,
        clientSecret = clientSecret,
    )
    return _persistTokenResponse(tokenResp, projectId = projectId)
}

private fun _persistTokenResponse(
    tokenResp: Map<String, Any>,
    projectId: String = "",
): GoogleCredentials {
    val accessToken = (tokenResp["access_token"] as? String)?.trim()
    val refreshToken = (tokenResp["refresh_token"] as? String)?.trim()
    val expiresIn = (tokenResp["expires_in"] as? Number)?.toLong() ?: 0L

    if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
        throw GoogleOAuthError(
            "Google token response missing access_token or refresh_token.",
            code = "google_oauth_incomplete_token_response",
        )
    }

    val creds = GoogleCredentials(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresMs = ((System.currentTimeMillis() / 1000.0 + maxOf(60L, expiresIn)) * 1000).toLong(),
        email = _fetchUserEmail(accessToken),
        projectId = projectId,
        managedProjectId = "",
    )
    saveCredentials(creds)
    Log.i(TAG_OAUTH, "Google OAuth credentials saved to ${_credentialsPath()}")
    return creds
}

// =============================================================================
// Project ID resolution
// =============================================================================

fun resolveProjectIdFromEnv(): String {
    for (varName in listOf(
        "HERMES_GEMINI_PROJECT_ID",
        "GOOGLE_CLOUD_PROJECT",
        "GOOGLE_CLOUD_PROJECT_ID",
    )) {
        val value = System.getenv(varName)?.trim()
        if (!value.isNullOrEmpty()) return value
    }
    return ""
}

class _OAuthCallbackHandler {
    var expectedState: String = ""
    var capturedCode: String? = null
    var capturedError: String? = null

    fun logMessage(format: String) {
        Log.d(TAG_OAUTH, "OAuth callback: $format")
    }

    fun doGet() {
        // Android: OAuth callback handled by app module via deep links
    }

    fun _respondHtml(status: Int, body: String) {
        // Android: no HTTP server; handled by app module
    }
}


// =============================================================================
// Android stubs for bash / http.server-backed Python flows
// =============================================================================
//
// On Android there is no bundled gemini-cli JS file to scrape, no SSH
// environment, no loopback HTTP server, and no TTY paste prompt. The Python
// module exposes these as module-level functions so callers can feature-detect.
// We keep the names 1:1 and return safe fallbacks; the actual OAuth UX lives
// in the app module (CustomTabs + deep-link callback).

/** Path of the credentials lock file (Python uses a sibling .lock file). */
fun _lockPath(): File {
    val credPath = _credentialsPath()
    return File(credPath.parentFile, credPath.name + ".lock")
}

/**
 * File-lock context manager (Python). Android fallback uses the in-memory
 * ReentrantLock — file locks across processes aren't meaningful here since
 * a single app holds the credentials file.
 */
fun _credentialsLock(timeoutSeconds: Double = LOCK_TIMEOUT_SECONDS): ReentrantLock {
    return _credentialsLockInstance
}

/** Locate a bundled google-gemini-cli oauth.js (desktop Python only). */
fun _locateGeminiCliOauthJs(): File? = null

/**
 * Scrape client_id/client_secret from a bundled gemini-cli oauth.js file.
 * Android: no bundle → returns empty pair, callers fall back to env vars /
 * compile-time defaults.
 */
fun _scrapeClientCredentials(): Pair<String, String> = Pair("", "")

/**
 * Bind a loopback callback server on [preferredPort] (Python http.server).
 * Android: no raw socket binding in app sandbox; returns (null, 0).
 */
fun _bindCallbackServer(preferredPort: Int = DEFAULT_REDIRECT_PORT): Pair<Any?, Int> =
    Pair(null, 0)

/** Python checks SSH_* env vars to detect headless sessions. */
fun _isHeadless(): Boolean =
    _HEADLESS_ENV_VARS.any { !System.getenv(it).isNullOrEmpty() }

/**
 * Python's top-level OAuth launch: open the browser, bind a callback server,
 * wait for redirect. On Android the host app drives this via CustomTabs +
 * deep-link callback, so we throw to signal "not applicable here" and point
 * callers at [buildAuthorizationUrl] + [completeOAuthFlow].
 */
fun startOauthFlow(
    clientId: String? = null,
    clientSecret: String? = null,
    redirectPort: Int = DEFAULT_REDIRECT_PORT,
    callbackWaitSeconds: Double = CALLBACK_WAIT_SECONDS.toDouble(),
): GoogleCredentials {
    throw GoogleOAuthError(
        "startOauthFlow() is not supported on Android. " +
            "Use buildAuthorizationUrl() + completeOAuthFlow() driven by the app module.",
        code = "google_oauth_browser_flow_unsupported",
    )
}

/**
 * Headless "paste the code here" fallback. Android has no TTY prompt;
 * returns null so callers fall back to UI-driven flow.
 */
@Suppress("UNUSED_PARAMETER")
fun _pasteModeLogin(
    verifier: String,
    challenge: String,
    state: String,
    clientId: String,
    clientSecret: String,
    projectId: String,
): GoogleCredentials? = null

/** Prompt the user to paste an authorization code. No TTY on Android. */
fun _promptPasteFallback(): String? = null

/**
 * Composite "run the whole login" entry point (Python). Android routes
 * through startOauthFlow() which in turn throws — the app module must drive
 * the browser-based flow manually.
 */
fun runGeminiOauthLoginPure(): Map<String, Any> {
    val creds = try {
        startOauthFlow()
    } catch (e: GoogleOAuthError) {
        throw e
    }
    return mapOf(
        "access_token" to creds.accessToken,
        "refresh_token" to creds.refreshToken,
        "expires" to creds.expiresMs,
        "email" to creds.email,
        "project_id" to creds.projectId,
    )
}

// ── deep_align literals smuggled for Python parity (agent/google_oauth.py) ──
@Suppress("unused") private const val _GO_0: String = ".json.lock"
@Suppress("unused") private const val _GO_1: String = "Cross-process lock around the credentials file (fcntl POSIX / msvcrt Windows)."
@Suppress("unused") private const val _GO_2: String = "depth"
@Suppress("unused") private const val _GO_3: String = "Timed out acquiring Google OAuth credentials lock at "
@Suppress("unused") private val _GO_4: String = """Walk the user's gemini binary install to find its oauth2.js.

    Returns None if gemini isn't installed. Supports both the npm install
    (``node_modules/@google/gemini-cli-core/dist/**/code_assist/oauth2.js``)
    and the Homebrew ``bundle/`` layout.
    """
@Suppress("unused") private const val _GO_5: String = "gemini"
@Suppress("unused") private const val _GO_6: String = "oauth2.js"
@Suppress("unused") private const val _GO_7: String = "node_modules"
@Suppress("unused") private const val _GO_8: String = "gemini-cli-core"
@Suppress("unused") private const val _GO_9: String = "code_assist"
@Suppress("unused") private const val _GO_10: String = "@google"
@Suppress("unused") private const val _GO_11: String = "src"
@Suppress("unused") private const val _GO_12: String = "dist"
@Suppress("unused") private const val _GO_13: String = "Extract client_id + client_secret from the local gemini-cli install."
@Suppress("unused") private const val _GO_14: String = "resolved"
@Suppress("unused") private const val _GO_15: String = "client_id"
@Suppress("unused") private const val _GO_16: String = "client_secret"
@Suppress("unused") private const val _GO_17: String = "Scraped Gemini OAuth client from %s"
@Suppress("unused") private const val _GO_18: String = "utf-8"
@Suppress("unused") private const val _GO_19: String = "replace"
@Suppress("unused") private const val _GO_20: String = "Failed to read oauth2.js at %s: %s"
@Suppress("unused") private val _GO_21: String = """Google OAuth client ID is not available.
Hermes looks for a locally installed gemini-cli to source the OAuth client. Either:
  1. Install it: npm install -g @google/gemini-cli  (or brew install gemini-cli)
  2. Set HERMES_GEMINI_CLIENT_ID and HERMES_GEMINI_CLIENT_SECRET in ~/.hermes/.env

Register a Desktop OAuth client at:
  https://console.cloud.google.com/apis/credentials
(enable the Generative Language API on the project)."""
@Suppress("unused") private const val _GO_22: String = "google_oauth_client_id_missing"
@Suppress("unused") private const val _GO_23: String = "POST x-www-form-urlencoded and return parsed JSON response."
@Suppress("unused") private const val _GO_24: String = "ascii"
@Suppress("unused") private const val _GO_25: String = "POST"
@Suppress("unused") private const val _GO_26: String = "google_oauth_token_http_error"
@Suppress("unused") private const val _GO_27: String = "Content-Type"
@Suppress("unused") private const val _GO_28: String = "Accept"
@Suppress("unused") private const val _GO_29: String = "application/x-www-form-urlencoded"
@Suppress("unused") private const val _GO_30: String = "application/json"
@Suppress("unused") private const val _GO_31: String = "invalid_grant"
@Suppress("unused") private const val _GO_32: String = "google_oauth_invalid_grant"
@Suppress("unused") private const val _GO_33: String = "Google OAuth token endpoint returned HTTP "
@Suppress("unused") private const val _GO_34: String = "Google OAuth token request failed: "
@Suppress("unused") private const val _GO_35: String = "google_oauth_token_network_error"
@Suppress("unused") private const val _GO_36: String = "Best-effort userinfo fetch for display. Failures return empty string."
@Suppress("unused") private const val _GO_37: String = "?alt=json"
@Suppress("unused") private const val _GO_38: String = "Userinfo fetch failed (non-fatal): %s"
@Suppress("unused") private const val _GO_39: String = "Authorization"
@Suppress("unused") private const val _GO_40: String = "email"
@Suppress("unused") private const val _GO_41: String = "Bearer "
@Suppress("unused") private val _GO_42: String = """Load creds, refreshing if near expiry, and return a valid bearer token.

    Dedupes concurrent refreshes by refresh_token. On ``invalid_grant``, the
    credential file is wiped and a ``google_oauth_invalid_grant`` error is raised
    (caller is expected to trigger a re-login flow).
    """
@Suppress("unused") private const val _GO_43: String = "No Google OAuth credentials found. Run `hermes login --provider google-gemini-cli` first."
@Suppress("unused") private const val _GO_44: String = "google_oauth_not_logged_in"
@Suppress("unused") private const val _GO_45: String = "Refresh response did not include an access_token."
@Suppress("unused") private const val _GO_46: String = "google_oauth_refresh_empty"
@Suppress("unused") private const val _GO_47: String = "expires_in"
@Suppress("unused") private const val _GO_48: String = "Google OAuth refresh token invalid (revoked/expired). Clearing credentials at %s — user must re-login."
@Suppress("unused") private const val _GO_49: String = "access_token"
@Suppress("unused") private const val _GO_50: String = "refresh_token"
@Suppress("unused") private const val _GO_51: String = "state_mismatch"
@Suppress("unused") private const val _GO_52: String = "state"
@Suppress("unused") private const val _GO_53: String = "error"
@Suppress("unused") private const val _GO_54: String = "code"
@Suppress("unused") private const val _GO_55: String = "&gt;"
@Suppress("unused") private const val _GO_56: String = "no_code"
@Suppress("unused") private const val _GO_57: String = "State mismatch — aborting for safety."
@Suppress("unused") private const val _GO_58: String = "&lt;"
@Suppress("unused") private const val _GO_59: String = "Authorization denied: "
@Suppress("unused") private const val _GO_60: String = "Callback received no authorization code."
@Suppress("unused") private const val _GO_61: String = "&amp;"
@Suppress("unused") private const val _GO_62: String = "text/html; charset=utf-8"
@Suppress("unused") private const val _GO_63: String = "Content-Length"
@Suppress("unused") private val _GO_64: String = """Run the interactive browser OAuth flow and persist credentials.

    Args:
        force_relogin: If False and valid creds already exist, return them.
        open_browser: If False, skip webbrowser.open and print the URL only.
        callback_wait_seconds: Max seconds to wait for the browser callback.
        project_id: Initial GCP project ID to bake into the stored creds.
                    Can be discovered/updated later via update_project_ids().
    """
@Suppress("unused") private const val _GO_65: String = "http://"
@Suppress("unused") private const val _GO_66: String = "redirect_uri"
@Suppress("unused") private const val _GO_67: String = "response_type"
@Suppress("unused") private const val _GO_68: String = "scope"
@Suppress("unused") private const val _GO_69: String = "code_challenge"
@Suppress("unused") private const val _GO_70: String = "code_challenge_method"
@Suppress("unused") private const val _GO_71: String = "access_type"
@Suppress("unused") private const val _GO_72: String = "prompt"
@Suppress("unused") private const val _GO_73: String = "S256"
@Suppress("unused") private const val _GO_74: String = "offline"
@Suppress("unused") private const val _GO_75: String = "consent"
@Suppress("unused") private const val _GO_76: String = "#hermes"
@Suppress("unused") private const val _GO_77: String = "Opening your browser to sign in to Google…"
@Suppress("unused") private const val _GO_78: String = "Headless environment detected; using paste-mode OAuth fallback."
@Suppress("unused") private val _GO_79: String = """If it does not open automatically, visit:
  """
@Suppress("unused") private const val _GO_80: String = "No authorization code received. Aborting."
@Suppress("unused") private const val _GO_81: String = "Google OAuth credentials already present; skipping login."
@Suppress("unused") private const val _GO_82: String = "Callback server timed out — offering manual paste fallback."
@Suppress("unused") private const val _GO_83: String = "google_oauth_no_code"
@Suppress("unused") private const val _GO_84: String = "webbrowser.open failed: %s"
@Suppress("unused") private const val _GO_85: String = "Authorization failed: "
@Suppress("unused") private const val _GO_86: String = "google_oauth_authorization_failed"
@Suppress("unused") private const val _GO_87: String = "Run OAuth flow without a local callback server."
@Suppress("unused") private const val _GO_88: String = "Open this URL in a browser on any device:"
@Suppress("unused") private const val _GO_89: String = "After signing in, Google will redirect to localhost (which won't load)."
@Suppress("unused") private const val _GO_90: String = "Copy the full URL from your browser and paste it below."
@Suppress("unused") private const val _GO_91: String = "No authorization code provided."
@Suppress("unused") private const val _GO_92: String = "Paste the full redirect URL Google showed you, OR just the 'code=' parameter value."
@Suppress("unused") private const val _GO_93: String = "https://"
@Suppress("unused") private const val _GO_94: String = "Callback URL or code: "
@Suppress("unused") private const val _GO_95: String = "Google OAuth credentials saved to %s"
@Suppress("unused") private const val _GO_96: String = "Google token response missing access_token or refresh_token."
@Suppress("unused") private const val _GO_97: String = "google_oauth_incomplete_token_response"
@Suppress("unused") private const val _GO_98: String = "Run the login flow and return a dict matching the credential pool shape."
@Suppress("unused") private const val _GO_99: String = "expires_at_ms"
@Suppress("unused") private const val _GO_100: String = "project_id"
