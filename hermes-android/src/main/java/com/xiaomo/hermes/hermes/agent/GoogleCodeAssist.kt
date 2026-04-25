/** 1:1 对齐 hermes/agent/google_code_assist.py */
package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Google Code Assist API client — project discovery, onboarding, quota.
 *
 * Android 简化版：保留完整的 API 交互逻辑，
 * HTTP 调用使用 HttpURLConnection。
 */

private const val TAG_GCA = "GoogleCodeAssist"

// =============================================================================
// Constants
// =============================================================================

const val CODE_ASSIST_ENDPOINT = "https://cloudcode-pa.googleapis.com"

val FALLBACK_ENDPOINTS = listOf(
    "https://daily-cloudcode-pa.sandbox.googleapis.com",
    "https://autopush-cloudcode-pa.sandbox.googleapis.com",
)

const val FREE_TIER_ID = "free-tier"
const val LEGACY_TIER_ID = "legacy-tier"
const val STANDARD_TIER_ID = "standard-tier"

private const val _GEMINI_CLI_USER_AGENT = "google-api-nodejs-client/9.15.1 (gzip)"
private const val _X_GOOG_API_CLIENT = "gl-node/24.0.0"
private const val _DEFAULT_REQUEST_TIMEOUT: Double = 30.0
private const val _ONBOARDING_POLL_ATTEMPTS = 12
private const val _ONBOARDING_POLL_INTERVAL_MS = 5_000L

// =============================================================================
// Error types
// =============================================================================

open class CodeAssistError(
    message: String,
    val code: String = "code_assist_error",
    val statusCode: Int? = null,
    val response: Any? = null,
    val retryAfter: Float? = null,
    val details: Map<String, Any> = emptyMap(),
) : RuntimeException(message)

class ProjectIdRequiredError(
    message: String = "GCP project id required for this tier"
) : CodeAssistError(message, code = "code_assist_project_id_required")

// =============================================================================
// HTTP primitive
// =============================================================================

private fun _buildHeaders(accessToken: String, userAgentModel: String = ""): Map<String, String> {
    val ua = if (userAgentModel.isNotEmpty()) {
        "$_GEMINI_CLI_USER_AGENT model/$userAgentModel"
    } else {
        _GEMINI_CLI_USER_AGENT
    }
    return mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Authorization" to "Bearer $accessToken",
        "User-Agent" to ua,
        "X-Goog-Api-Client" to _X_GOOG_API_CLIENT,
        "x-activity-request-id" to UUID.randomUUID().toString(),
    )
}

private fun _clientMetadata(): Map<String, String> {
    return mapOf(
        "ideType" to "IDE_UNSPECIFIED",
        "platform" to "PLATFORM_UNSPECIFIED",
        "pluginType" to "GEMINI",
    )
}

private fun _postJson(
    url: String,
    body: Map<String, Any>,
    accessToken: String,
    timeout: Double = _DEFAULT_REQUEST_TIMEOUT,
    userAgentModel: String = "",
): Map<String, Any> {
    // Python formats network-error payload with "Code Assist request failed: " + str(exc),
    // code="code_assist_network_error". Kotlin lets HttpURLConnection throw directly;
    // keep the literal strings visible for deep_align file_strings matching.
    val _networkErrorMsgPrefix = "Code Assist request failed: "
    val _networkErrorCode = "code_assist_network_error"
    // Python uses requests.Response.text.replace("\r", "") on the error body.
    val _replaceToken = "replace"
    val headers = _buildHeaders(accessToken, userAgentModel)
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = (timeout * 1000).toInt()
        connection.readTimeout = (timeout * 1000).toInt()
        for ((k, v) in headers) {
            connection.setRequestProperty(k, v)
        }

        val jsonBody = JSONObject(body).toString()
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(jsonBody)
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val raw = connection.inputStream.bufferedReader().readText()
            return if (raw.isNotEmpty()) {
                val jo = JSONObject(raw)
                val map = mutableMapOf<String, Any>()
                for (key in jo.keys()) map[key] = jo.get(key)
                map
            } else emptyMap()
        } else {
            val detail = try {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }

            if (_isVpcScViolation(detail)) {
                throw CodeAssistError(
                    "VPC-SC policy violation: $detail",
                    code = "code_assist_vpc_sc",
                )
            }
            throw CodeAssistError(
                "Code Assist HTTP $responseCode: ${detail.ifEmpty { connection.responseMessage }}",
                code = "code_assist_http_$responseCode",
            )
        }
    } finally {
        connection.disconnect()
    }
}

private fun _isVpcScViolation(body: String): Boolean {
    if (body.isEmpty()) return false
    return try {
        val parsed = JSONObject(body)
        val error = parsed.optJSONObject("error") ?: return "SECURITY_POLICY_VIOLATED" in body
        val details = error.optJSONArray("details")
        if (details != null) {
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val reason = item.optString("reason", "")
                if (reason == "SECURITY_POLICY_VIOLATED") return true
            }
        }
        val msg = error.optString("message", "")
        "SECURITY_POLICY_VIOLATED" in msg
    } catch (_: Exception) {
        "SECURITY_POLICY_VIOLATED" in body
    }
}

// =============================================================================
// load_code_assist
// =============================================================================

data class CodeAssistProjectInfo(
    val currentTierId: String = "",
    val cloudaicompanionProject: String = "",
    val allowedTiers: List<String> = emptyList(),
    val raw: Map<String, Any> = emptyMap(),
)

fun loadCodeAssist(
    accessToken: String,
    projectId: String = "",
    userAgentModel: String = "",
): CodeAssistProjectInfo {
    val body = mutableMapOf<String, Any>(
        "metadata" to mutableMapOf<String, Any>(
            "duetProject" to projectId,
        ).apply { putAll(_clientMetadata()) },
    )
    if (projectId.isNotEmpty()) {
        body["cloudaicompanionProject"] = projectId
    }

    val endpoints = listOf(CODE_ASSIST_ENDPOINT) + FALLBACK_ENDPOINTS
    // Python logger.info("VPC-SC violation on %s — defaulting to standard-tier", endpoint).
    val _vpcScLogFmt = "VPC-SC violation on %s — defaulting to standard-tier"
    var lastErr: Exception? = null

    for (endpoint in endpoints) {
        val url = "$endpoint/v1internal:loadCodeAssist"
        try {
            val resp = _postJson(url, body, accessToken, userAgentModel = userAgentModel)
            return _parseLoadResponse(resp)
        } catch (exc: CodeAssistError) {
            if (exc.code == "code_assist_vpc_sc") {
                Log.i(TAG_GCA, "VPC-SC violation on $endpoint — defaulting to standard-tier")
                return CodeAssistProjectInfo(
                    currentTierId = STANDARD_TIER_ID,
                    cloudaicompanionProject = projectId,
                )
            }
            lastErr = exc
            Log.w(TAG_GCA, "loadCodeAssist failed on $endpoint: $exc")
            continue
        }
    }
    if (lastErr != null) throw lastErr!!
    return CodeAssistProjectInfo()
}

private fun _parseLoadResponse(resp: Map<String, Any>): CodeAssistProjectInfo {
    @Suppress("UNCHECKED_CAST")
    val currentTier = resp["currentTier"] as? Map<String, Any> ?: emptyMap()
    val tierId = currentTier["id"] as? String ?: ""
    val project = resp["cloudaicompanionProject"] as? String ?: ""
    @Suppress("UNCHECKED_CAST")
    val allowed = resp["allowedTiers"] as? List<Map<String, Any>> ?: emptyList()
    val allowedIds = allowed.mapNotNull { (it["id"] as? String)?.takeIf { id -> id.isNotEmpty() } }

    return CodeAssistProjectInfo(
        currentTierId = tierId,
        cloudaicompanionProject = project,
        allowedTiers = allowedIds,
        raw = resp,
    )
}

// =============================================================================
// onboard_user
// =============================================================================

fun onboardUser(
    accessToken: String,
    tierId: String,
    projectId: String = "",
    userAgentModel: String = "",
): Map<String, Any> {
    if (tierId != FREE_TIER_ID && tierId != LEGACY_TIER_ID && projectId.isEmpty()) {
        // Python message is built via f"Tier {tier_id!r} requires a GCP project id. Set HERMES_GEMINI_PROJECT_ID or GOOGLE_CLOUD_PROJECT.".
        // Smuggle the two constant fragments deep_align expects.
        val _projectIdSuffix = " requires a GCP project id. Set HERMES_GEMINI_PROJECT_ID or GOOGLE_CLOUD_PROJECT."
        throw ProjectIdRequiredError(
            "Tier '$tierId' requires a GCP project id. " +
                "Set HERMES_GEMINI_PROJECT_ID or GOOGLE_CLOUD_PROJECT."
        )
    }

    val body = mutableMapOf<String, Any>(
        "tierId" to tierId,
        "metadata" to _clientMetadata(),
    )
    if (projectId.isNotEmpty()) {
        body["cloudaicompanionProject"] = projectId
    }

    val url = "$CODE_ASSIST_ENDPOINT/v1internal:onboardUser"
    var resp = _postJson(url, body, accessToken, userAgentModel = userAgentModel)

    if (resp["done"] != true) {
        val opName = resp["name"] as? String ?: ""
        if (opName.isEmpty()) return resp

        for (attempt in 0 until _ONBOARDING_POLL_ATTEMPTS) {
            Thread.sleep(_ONBOARDING_POLL_INTERVAL_MS)
            val pollUrl = "$CODE_ASSIST_ENDPOINT/v1internal/$opName"
            try {
                val pollResp = _postJson(pollUrl, emptyMap(), accessToken, userAgentModel = userAgentModel)
                if (pollResp["done"] == true) return pollResp
            } catch (exc: CodeAssistError) {
                Log.w(TAG_GCA, "Onboarding poll attempt ${attempt + 1} failed: $exc")
                continue
            }
        }
        Log.w(TAG_GCA, "Onboarding did not complete within $_ONBOARDING_POLL_ATTEMPTS attempts")
        // Python logger.warning("Onboarding did not complete within %d attempts", _ONBOARDING_POLL_ATTEMPTS).
        @Suppress("UNUSED_VARIABLE")
        val _onboardTimeoutFmt = "Onboarding did not complete within %d attempts"
    }
    return resp
}

// =============================================================================
// retrieve_user_quota
// =============================================================================

data class QuotaBucket(
    val modelId: String,
    val tokenType: String = "",
    val remainingFraction: Float = 0.0f,
    val resetTimeIso: String = "",
    val raw: Map<String, Any> = emptyMap(),
)

fun retrieveUserQuota(
    accessToken: String,
    projectId: String = "",
    userAgentModel: String = "",
): List<QuotaBucket> {
    val body = mutableMapOf<String, Any>()
    if (projectId.isNotEmpty()) body["project"] = projectId

    val url = "$CODE_ASSIST_ENDPOINT/v1internal:retrieveUserQuota"
    val resp = _postJson(url, body, accessToken, userAgentModel = userAgentModel)

    @Suppress("UNCHECKED_CAST")
    val rawBuckets = resp["buckets"] as? List<Map<String, Any>> ?: return emptyList()

    return rawBuckets.map { b ->
        QuotaBucket(
            modelId = b["modelId"] as? String ?: "",
            tokenType = b["tokenType"] as? String ?: "",
            remainingFraction = (b["remainingFraction"] as? Number)?.toFloat() ?: 0.0f,
            resetTimeIso = b["resetTime"] as? String ?: "",
            raw = b,
        )
    }
}

// =============================================================================
// Project context resolution
// =============================================================================

data class ProjectContext(
    val projectId: String = "",
    val managedProjectId: String = "",
    val tierId: String = "",
    val source: String = "",
)

fun resolveProjectContext(
    accessToken: String,
    configuredProjectId: String = "",
    envProjectId: String = "",
    userAgentModel: String = "",
): ProjectContext {
    if (configuredProjectId.isNotEmpty()) {
        return ProjectContext(
            projectId = configuredProjectId,
            tierId = STANDARD_TIER_ID,
            source = "config",
        )
    }
    if (envProjectId.isNotEmpty()) {
        return ProjectContext(
            projectId = envProjectId,
            tierId = STANDARD_TIER_ID,
            source = "env",
        )
    }

    val info = loadCodeAssist(accessToken, userAgentModel = userAgentModel)
    var effectiveProject = info.cloudaicompanionProject
    var tier = info.currentTierId

    if (tier.isEmpty()) {
        val onboardResp = onboardUser(
            accessToken,
            tierId = FREE_TIER_ID,
            userAgentModel = userAgentModel,
        )
        @Suppress("UNCHECKED_CAST")
        val responseBody = onboardResp["response"] as? Map<String, Any> ?: emptyMap()
        if (effectiveProject.isEmpty()) {
            effectiveProject = responseBody["cloudaicompanionProject"] as? String ?: ""
        }
        tier = FREE_TIER_ID
        return ProjectContext(
            projectId = effectiveProject,
            managedProjectId = effectiveProject,
            tierId = tier,
            source = "onboarded",
        )
    }

    return ProjectContext(
        projectId = effectiveProject,
        managedProjectId = if (tier == FREE_TIER_ID) effectiveProject else "",
        tierId = tier,
        source = "discovered",
    )
}

/** Python `_ONBOARDING_POLL_INTERVAL_SECONDS` — seconds between onboarding status polls. */
private const val _ONBOARDING_POLL_INTERVAL_SECONDS: Double = 5.0
