package com.xiaomo.hermes.hermes.agent

/**
 * Account-usage fetchers for cloud providers (Codex / Anthropic / OpenRouter).
 *
 * Ported from agent/account_usage.py
 */

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

private fun _utcNow(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

data class AccountUsageWindow(
    val label: String,
    val usedPercent: Double? = null,
    val resetAt: OffsetDateTime? = null,
    val detail: String? = null)

data class AccountUsageSnapshot(
    val provider: String,
    val source: String,
    val fetchedAt: OffsetDateTime,
    val title: String = "Account limits",
    val plan: String? = null,
    val windows: List<AccountUsageWindow> = emptyList(),
    val details: List<String> = emptyList(),
    val unavailableReason: String? = null) {

    fun available(): Boolean =
        (windows.isNotEmpty() || details.isNotEmpty()) && unavailableReason == null
}

private fun _titleCaseSlug(value: String?): String? {
    val cleaned = (value ?: "").trim()
    if (cleaned.isEmpty()) return null
    return cleaned.replace("_", " ").replace("-", " ")
        .split(" ")
        .joinToString(" ") { part ->
            if (part.isEmpty()) part
            else part.substring(0, 1).uppercase() + part.substring(1).lowercase()
        }
}

private fun _parseDt(value: Any?): OffsetDateTime? {
    if (value == null || value == "") return null
    if (value is Number) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.toLong()), ZoneOffset.UTC)
    }
    if (value is String) {
        var text = value.trim()
        if (text.isEmpty()) return null
        if (text.endsWith("Z")) text = text.dropLast(1) + "+00:00"
        return try {
            val dt = OffsetDateTime.parse(text)
            dt
        } catch (_: Exception) {
            null
        }
    }
    return null
}

private fun _formatReset(dt: OffsetDateTime?): String {
    if (dt == null) return "unknown"
    val localDt = dt.atZoneSameInstant(ZoneId.systemDefault())
    val totalSeconds = (dt.toEpochSecond() - _utcNow().toEpochSecond()).toInt()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz")
    if (totalSeconds <= 0) return "now (${localDt.format(fmt)})"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val rel = when {
        hours >= 24 -> {
            val days = hours / 24
            val remHours = hours % 24
            "in ${days}d ${remHours}h"
        }
        hours > 0 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes}m"
    }
    return "$rel (${localDt.format(fmt)})"
}

fun renderAccountUsageLines(snapshot: AccountUsageSnapshot?, markdown: Boolean = false): List<String> {
    if (snapshot == null) return emptyList()
    val bold = if (markdown) "**" else ""
    val lines = mutableListOf<String>()
    lines.add("📈 $bold${snapshot.title}$bold")
    if (snapshot.plan != null) {
        lines.add("Provider: ${snapshot.provider} (${snapshot.plan})")
    } else {
        lines.add("Provider: ${snapshot.provider}")
    }
    for (window in snapshot.windows) {
        var base = if (window.usedPercent == null) {
            "${window.label}: unavailable"
        } else {
            val remaining = max(0, (100 - window.usedPercent).roundToInt())
            val used = max(0, window.usedPercent.roundToInt())
            "${window.label}: ${remaining}% remaining (${used}% used)"
        }
        if (window.resetAt != null) {
            base += " • resets ${_formatReset(window.resetAt)}"
        } else if (window.detail != null) {
            base += " • ${window.detail}"
        }
        lines.add(base)
    }
    for (detail in snapshot.details) {
        lines.add(detail)
    }
    if (snapshot.unavailableReason != null) {
        lines.add("Unavailable: ${snapshot.unavailableReason}")
    }
    return lines
}

private fun _resolveCodexUsageUrl(baseUrl: String): String {
    var normalized = (baseUrl).trim().trimEnd('/')
    if (normalized.isEmpty()) {
        normalized = "https://chatgpt.com/backend-api/codex"
    }
    if (normalized.endsWith("/codex")) {
        normalized = normalized.dropLast("/codex".length)
    }
    return if ("/backend-api" in normalized) "$normalized/wham/usage"
    else "$normalized/api/codex/usage"
}

private val _ACCOUNT_USAGE_HTTP: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private val _ACCOUNT_USAGE_GSON = Gson()

@Suppress("UNCHECKED_CAST")
private fun _parseJsonObject(body: String): Map<String, Any?> {
    if (body.isBlank()) return emptyMap()
    val type = object : TypeToken<Map<String, Any?>>() {}.type
    return try {
        _ACCOUNT_USAGE_GSON.fromJson<Map<String, Any?>>(body, type) ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun _fetchCodexAccountUsage(): AccountUsageSnapshot? {
    // hermes_cli.auth is Python-only; Android runtime cannot refresh Codex
    // OAuth tokens. Report usage as unavailable rather than silently returning
    // null so the caller surfaces the reason to the user.
    return AccountUsageSnapshot(
        provider = "openai-codex",
        source = "usage_api",
        fetchedAt = _utcNow(),
        unavailableReason = "Codex account usage requires the hermes_cli OAuth runtime, which is not available on Android."
    )
}

private fun _fetchAnthropicAccountUsage(): AccountUsageSnapshot? {
    val token = (resolveAnthropicToken() ?: "").trim()
    if (token.isEmpty()) return null
    if (!_isOauthToken(token)) {
        return AccountUsageSnapshot(
            provider = "anthropic",
            source = "oauth_usage_api",
            fetchedAt = _utcNow(),
            unavailableReason = "Anthropic account limits are only available for OAuth-backed Claude accounts."
        )
    }
    val request = Request.Builder()
        .url("https://api.anthropic.com/api/oauth/usage")
        .get()
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("anthropic-beta", "oauth-2025-04-20")
        .header("User-Agent", "claude-code/2.1.0")
        .build()
    val payload: Map<String, Any?> = _ACCOUNT_USAGE_HTTP.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return null
        _parseJsonObject(resp.body?.string() ?: "")
    }
    val windows = mutableListOf<AccountUsageWindow>()
    val mapping = listOf(
        "five_hour" to "Current session",
        "seven_day" to "Current week",
        "seven_day_opus" to "Opus week",
        "seven_day_sonnet" to "Sonnet week"
    )
    for ((key, label) in mapping) {
        @Suppress("UNCHECKED_CAST")
        val window = (payload[key] as? Map<String, Any?>) ?: continue
        val util = (window["utilization"] as? Number)?.toDouble() ?: continue
        val used = if (util <= 1.0) util * 100.0 else util
        windows.add(
            AccountUsageWindow(
                label = label,
                usedPercent = used,
                resetAt = _parseDt(window["resets_at"])
            )
        )
    }
    val details = mutableListOf<String>()
    @Suppress("UNCHECKED_CAST")
    val extra = (payload["extra_usage"] as? Map<String, Any?>) ?: emptyMap()
    if (extra["is_enabled"] == true) {
        val usedCredits = (extra["used_credits"] as? Number)?.toDouble()
        val monthlyLimit = (extra["monthly_limit"] as? Number)?.toDouble()
        val currency = (extra["currency"] as? String) ?: "USD"
        if (usedCredits != null && monthlyLimit != null) {
            details.add("Extra usage: %.2f / %.2f %s".format(usedCredits, monthlyLimit, currency))
        }
    }
    return AccountUsageSnapshot(
        provider = "anthropic",
        source = "oauth_usage_api",
        fetchedAt = _utcNow(),
        windows = windows.toList(),
        details = details.toList()
    )
}

private fun _fetchOpenrouterAccountUsage(baseUrl: String?, apiKey: String?): AccountUsageSnapshot? {
    val resolvedKey = (apiKey
        ?: System.getenv("OPENROUTER_API_KEY")
        ?: System.getenv("OPEN_ROUTER_API_KEY")
        ?: "").trim()
    if (resolvedKey.isEmpty()) return null
    val normalized = (baseUrl
        ?: System.getenv("OPENROUTER_BASE_URL")
        ?: "https://openrouter.ai/api/v1").trim().trimEnd('/')
    val creditsReq = Request.Builder()
        .url("$normalized/credits")
        .get()
        .header("Authorization", "Bearer $resolvedKey")
        .header("Accept", "application/json")
        .build()
    @Suppress("UNCHECKED_CAST")
    val credits: Map<String, Any?> = _ACCOUNT_USAGE_HTTP.newCall(creditsReq).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val body = _parseJsonObject(resp.body?.string() ?: "")
        (body["data"] as? Map<String, Any?>) ?: emptyMap()
    }
    @Suppress("UNCHECKED_CAST")
    val keyData: Map<String, Any?> = try {
        val keyReq = Request.Builder()
            .url("$normalized/key")
            .get()
            .header("Authorization", "Bearer $resolvedKey")
            .header("Accept", "application/json")
            .build()
        _ACCOUNT_USAGE_HTTP.newCall(keyReq).execute().use { resp ->
            if (!resp.isSuccessful) emptyMap()
            else ((_parseJsonObject(resp.body?.string() ?: "")["data"] as? Map<String, Any?>) ?: emptyMap())
        }
    } catch (_: Exception) {
        emptyMap()
    }
    val totalCredits = (credits["total_credits"] as? Number)?.toDouble() ?: 0.0
    val totalUsage = (credits["total_usage"] as? Number)?.toDouble() ?: 0.0
    val details = mutableListOf("Credits balance: $%.2f".format(max(0.0, totalCredits - totalUsage)))
    val windows = mutableListOf<AccountUsageWindow>()
    val limit = (keyData["limit"] as? Number)?.toDouble()
    val limitRemaining = (keyData["limit_remaining"] as? Number)?.toDouble()
    val limitReset = ((keyData["limit_reset"] as? String) ?: "").trim()
    val usage = (keyData["usage"] as? Number)?.toDouble()
    if (limit != null && limit > 0 && limitRemaining != null && limitRemaining in 0.0..limit) {
        val usedPercent = ((limit - limitRemaining) / limit) * 100.0
        val detailParts = mutableListOf("$%.2f of $%.2f remaining".format(limitRemaining, limit))
        if (limitReset.isNotEmpty()) detailParts.add("resets $limitReset")
        windows.add(
            AccountUsageWindow(
                label = "API key quota",
                usedPercent = usedPercent,
                detail = detailParts.joinToString(" • ")
            )
        )
    }
    if (usage != null) {
        val usageParts = mutableListOf("API key usage: $%.2f total".format(usage))
        for ((value, label) in listOf(
            (keyData["usage_daily"] as? Number)?.toDouble() to "today",
            (keyData["usage_weekly"] as? Number)?.toDouble() to "this week",
            (keyData["usage_monthly"] as? Number)?.toDouble() to "this month"
        )) {
            if (value != null && value > 0) {
                usageParts.add("$%.2f %s".format(value, label))
            }
        }
        details.add(usageParts.joinToString(" • "))
    }
    return AccountUsageSnapshot(
        provider = "openrouter",
        source = "credits_api",
        fetchedAt = _utcNow(),
        windows = windows.toList(),
        details = details.toList()
    )
}

fun fetchAccountUsage(
    provider: String?,
    baseUrl: String? = null,
    apiKey: String? = null): AccountUsageSnapshot? {
    val normalized = (provider ?: "").trim().lowercase()
    if (normalized in setOf("", "auto", "custom")) return null
    return try {
        when (normalized) {
            "openai-codex" -> _fetchCodexAccountUsage()
            "anthropic" -> _fetchAnthropicAccountUsage()
            "openrouter" -> _fetchOpenrouterAccountUsage(baseUrl, apiKey)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
