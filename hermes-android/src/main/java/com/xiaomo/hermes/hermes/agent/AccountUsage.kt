package com.xiaomo.hermes.hermes.agent

/**
 * Account-usage fetchers for cloud providers (Codex / Anthropic / OpenRouter).
 *
 * Ported from agent/account_usage.py
 */

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    val available: Boolean
        get() = (windows.isNotEmpty() || details.isNotEmpty()) && unavailableReason == null
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

private fun _fetchCodexAccountUsage(): AccountUsageSnapshot? {
    // TODO: port hermes_cli.auth credentials resolution + httpx call
    return null
}

private fun _fetchAnthropicAccountUsage(): AccountUsageSnapshot? {
    // TODO: port anthropic_adapter.resolveAnthropicToken + httpx call
    return null
}

private fun _fetchOpenrouterAccountUsage(baseUrl: String?, apiKey: String?): AccountUsageSnapshot? {
    // TODO: port hermes_cli.runtime_provider.resolveRuntimeProvider + httpx call
    return null
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
