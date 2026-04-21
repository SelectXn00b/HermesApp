package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.core.application.OperitApplication
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
data class MarketStatsEntryResponse(
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MarketTypeStatsResponse(
    val updatedAt: String? = null,
    val items: Map<String, MarketStatsEntryResponse> = emptyMap()
)

@Serializable
data class MarketRankIssueEntryResponse(
    val id: String,
    val downloads: Int = 0,
    val lastDownloadAt: String? = null,
    val updatedAt: String? = null,
    val statsUpdatedAt: String? = null,
    val displayTitle: String = "",
    val summaryDescription: String = "",
    val authorLogin: String = "",
    val authorAvatarUrl: String = "",
    val metadata: JsonElement? = null,
    val issue: GitHubIssue
)

@Serializable
data class MarketRankPageResponse(
    val updatedAt: String? = null,
    val type: String = "",
    val metric: String = "",
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
    val items: List<MarketRankIssueEntryResponse> = emptyList()
)

class MarketStatsApiService {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val staticClient = STATIC_CLIENT

    private val noRedirectTrackingClient = NO_REDIRECT_TRACKING_CLIENT

    suspend fun getStats(type: String): Result<MarketTypeStatsResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    STATIC_BASE_URL.toHttpUrl()
                        .newBuilder()
                        .addPathSegment("stats")
                        .addPathSegment("$type.json")
                        .build()

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                staticClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error(buildHttpErrorMessage(response, body))
                    }
                    json.decodeFromString(MarketTypeStatsResponse.serializer(), body)
                }
            }
        }

    suspend fun getRankPage(
        type: String,
        metric: String,
        page: Int
    ): Result<MarketRankPageResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    STATIC_BASE_URL.toHttpUrl()
                        .newBuilder()
                        .addPathSegment("rank")
                        .addPathSegment("${type}-${metric}-page-${page}.json")
                        .build()

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                staticClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error(buildHttpErrorMessage(response, body))
                    }
                    json.decodeFromString(MarketRankPageResponse.serializer(), body)
                }
            }
        }

    suspend fun trackDownload(
        type: String,
        id: String,
        targetUrl: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    TRACK_BASE_URL.toHttpUrl()
                        .newBuilder()
                        .addPathSegment("download")
                        .addQueryParameter("type", type)
                        .addQueryParameter("id", id)
                        .addQueryParameter("target", targetUrl)
                        .build()

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                noRedirectTrackingClient.newCall(request).execute().use { response ->
                    if (response.code in 300..399 || response.isSuccessful) {
                        Unit
                    } else {
                        val body = response.body?.string().orEmpty()
                        error(buildHttpErrorMessage(response, body))
                    }
                }
            }
        }

    private fun buildHttpErrorMessage(
        response: Response,
        body: String
    ): String {
        val requestPath = response.request.url.encodedPath
        val summary =
            when {
                body.isBlank() -> ""
                body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE html", ignoreCase = true) ->
                    " [html body omitted]"
                else -> " ${body.lineSequence().firstOrNull().orEmpty().trim().take(180)}"
            }

        return "HTTP ${response.code}: ${response.message} ($requestPath)$summary"
    }

    companion object {
        const val STATIC_BASE_URL = "https://static.operit.app/market-stats"
        const val TRACK_BASE_URL = "https://api.operit.app/market-stats"
        private const val USER_AGENT = "Operit-Market-Stats"
        private const val TIMEOUT_SECONDS = 15L
        private const val STATIC_CACHE_SIZE_BYTES = 8L * 1024L * 1024L

        private val STATIC_CACHE by lazy {
            Cache(
                directory = File(OperitApplication.instance.cacheDir, "market_stats_http_cache"),
                maxSize = STATIC_CACHE_SIZE_BYTES,
            )
        }

        private val STATIC_CLIENT by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .cache(STATIC_CACHE)
                .build()
        }

        private val TRACKING_CLIENT by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private val NO_REDIRECT_TRACKING_CLIENT by lazy {
            TRACKING_CLIENT.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
