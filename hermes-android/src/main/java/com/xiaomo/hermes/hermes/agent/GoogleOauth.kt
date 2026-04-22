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
const val TOKEN_REQUEST_TIMEOUT_MS = 20_000
const val LOCK_TIMEOUT_MS = 30_000L

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

private val _credentialsLock = ReentrantLock()

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
        _credentialsLock.withLock {
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
    _credentialsLock.withLock {
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
    _credentialsLock.withLock {
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
