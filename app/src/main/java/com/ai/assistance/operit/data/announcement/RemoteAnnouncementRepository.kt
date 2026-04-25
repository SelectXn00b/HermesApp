package com.ai.assistance.operit.data.announcement

import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class AnnouncementPointer(
    val schemaVersion: Int = 1,
    val latestVersion: Int = 0,
    val latestFile: String = "",
    val updatedAt: String? = null,
    val notes: String? = null
)

@Serializable
data class RemoteAnnouncementPayload(
    val schemaVersion: Int = 1,
    val id: String = "",
    val version: Int = 0,
    val enabled: Boolean = false,
    val type: String = "modal",
    val priority: String = "normal",
    val countdownSec: Int = 0,
    val target: AnnouncementTarget = AnnouncementTarget(),
    val schedule: AnnouncementSchedule = AnnouncementSchedule(),
    val content: AnnouncementContent = AnnouncementContent(),
    val meta: AnnouncementMeta = AnnouncementMeta()
)

@Serializable
data class AnnouncementTarget(
    val minAppVersionCode: Int = 0,
    val maxAppVersionCode: Int? = null,
    val channels: List<String> = emptyList(),
    val locale: String = "all"
)

@Serializable
data class AnnouncementSchedule(
    val startAt: String? = null,
    val endAt: String? = null
)

@Serializable
data class AnnouncementContent(
    val defaultLocale: String = "zh-CN",
    val locales: Map<String, AnnouncementLocaleContent> = emptyMap()
)

@Serializable
data class AnnouncementLocaleContent(
    val title: String = "",
    val body: String = "",
    val acknowledge: String = "OK"
)

@Serializable
data class AnnouncementMeta(
    val updatedAt: String? = null,
    val source: String? = null
)

data class RemoteAnnouncementDisplay(
    val id: String,
    val version: Int,
    val title: String,
    val body: String,
    val acknowledgeText: String,
    val countdownSec: Int
)

class RemoteAnnouncementRepository(
    private val pointerUrl: String = DEFAULT_POINTER_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetchDisplayableAnnouncement(locale: Locale = Locale.getDefault()): RemoteAnnouncementDisplay? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val pointer = fetchPointer()
                if (pointer.latestFile.isBlank()) return@runCatching null

                val payload = fetchPayload(pointer.latestFile)
                toDisplay(payload, locale)
            }.onFailure { error ->
                AppLogger.w(TAG, "fetchDisplayableAnnouncement failed", error)
            }.getOrNull()
        }
    }

    private fun fetchPointer(): AnnouncementPointer {
        val body = fetchText(addCacheBust(pointerUrl))
        return json.decodeFromString(body)
    }

    private fun fetchPayload(latestFile: String): RemoteAnnouncementPayload {
        val resolved = resolveAnnouncementUrl(latestFile)
        val body = fetchText(addCacheBust(resolved))
        return json.decodeFromString(body)
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cache-Control", "no-cache")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Request failed: ${response.code} $url")
            }
            return response.body?.string() ?: throw IOException("Empty response body: $url")
        }
    }

    private fun toDisplay(payload: RemoteAnnouncementPayload, locale: Locale): RemoteAnnouncementDisplay? {
        if (!payload.enabled) return null
        if (payload.version <= 0) return null
        if (!isVersionInRange(payload.target)) return null
        if (!isChannelMatched(payload.target.channels)) return null
        if (!isLocaleMatched(payload.target.locale, locale)) return null
        if (!isWithinSchedule(payload.schedule)) return null

        val localized = pickLocalizedContent(payload.content, locale) ?: return null
        if (localized.title.isBlank() || localized.body.isBlank()) return null

        return RemoteAnnouncementDisplay(
            id = payload.id.ifBlank { "announcement-${payload.version}" },
            version = payload.version,
            title = localized.title,
            body = localized.body,
            acknowledgeText = localized.acknowledge.ifBlank { "OK" },
            countdownSec = payload.countdownSec.coerceIn(0, 30)
        )
    }

    private fun isVersionInRange(target: AnnouncementTarget): Boolean {
        val appVersionCode = BuildConfig.VERSION_CODE
        val minOk = appVersionCode >= target.minAppVersionCode
        val maxOk = target.maxAppVersionCode?.let { appVersionCode <= it } ?: true
        return minOk && maxOk
    }

    private fun isChannelMatched(channels: List<String>): Boolean {
        if (channels.isEmpty()) return true
        val accepted = currentChannelAliases()
        return channels.any { accepted.contains(it.trim().lowercase(Locale.US)) }
    }

    private fun currentChannelAliases(): Set<String> {
        val buildType = BuildConfig.BUILD_TYPE.lowercase(Locale.US)
        val aliases = mutableSetOf("all", buildType)
        when (buildType) {
            "release" -> aliases += "stable"
            "nightly" -> aliases += "beta"
            "debug" -> {
                aliases += "stable"
                aliases += "beta"
                aliases += "dev"
            }
        }
        return aliases
    }

    private fun isLocaleMatched(targetLocale: String, locale: Locale): Boolean {
        val normalized = targetLocale.trim().lowercase(Locale.US)
        if (normalized.isBlank() || normalized == "all") return true

        val language = locale.language.lowercase(Locale.US)
        val fullTag = locale.toLanguageTag().lowercase(Locale.US)
        return normalized == language || normalized == fullTag
    }

    private fun isWithinSchedule(schedule: AnnouncementSchedule): Boolean {
        val now = Instant.now()
        val start = parseInstant(schedule.startAt)
        val end = parseInstant(schedule.endAt)

        if (start != null && now.isBefore(start)) return false
        if (end != null && now.isAfter(end)) return false
        return true
    }

    private fun pickLocalizedContent(content: AnnouncementContent, locale: Locale): AnnouncementLocaleContent? {
        if (content.locales.isEmpty()) return null

        val normalizedMap = content.locales.mapKeys { it.key.normalizeLocaleKey() }
        val fullTag = locale.toLanguageTag().normalizeLocaleKey()
        val language = locale.language.normalizeLocaleKey()
        val defaultKey = content.defaultLocale.normalizeLocaleKey()

        return normalizedMap[fullTag]
            ?: normalizedMap[language]
            ?: normalizedMap[defaultKey]
            ?: content.locales.values.firstOrNull()
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }
            .getOrElse {
                runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            }
    }

    private fun resolveAnnouncementUrl(latestFile: String): String {
        val trimmed = latestFile.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return runCatching {
            URI(pointerUrl).resolve(trimmed).toString()
        }.getOrElse {
            val base = URI(pointerUrl)
            val origin = buildString {
                append(base.scheme)
                append("://")
                append(base.host)
                if (base.port != -1) {
                    append(":")
                    append(base.port)
                }
            }
            if (trimmed.startsWith("/")) "$origin$trimmed" else "$origin/$trimmed"
        }
    }

    private fun addCacheBust(url: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}ts=${System.currentTimeMillis()}"
    }

    private fun String.normalizeLocaleKey(): String {
        return trim().replace('_', '-').lowercase(Locale.US)
    }

    companion object {
        private const val TAG = "RemoteAnnouncementRepo"
        const val DEFAULT_POINTER_URL = "https://operit.app/announcements/latest.json"
    }
}
