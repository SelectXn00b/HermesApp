package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: remote skill hub client.
 */


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ClawHub HTTP API Client
 *
 * Interfaces with https://clawhub.ai API
 * Provides skill search, download, and details query functions
 */
class ClawHubClient {
    companion object {
        private const val TAG = "ClawHubClient"
        private const val BASE_URL = "https://clawhub.ai"
        private const val API_BASE = "$BASE_URL/api/v1"  // 使用 v1 API
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Search skills
     *
     * ClawHub API v1: GET /api/v1/search?q=query&limit=20
     */
    suspend fun searchSkills(
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<SkillSearchResult> = withContext(Dispatchers.IO) {
        try {
            // ClawHub API v1: GET /api/v1/search?q=query&limit=20
            val url = "$API_BASE/search?q=$query&limit=$limit"
            Log.d(TAG, "Searching skills: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                Log.e(TAG, "Search failed: ${response.code} - ${response.message}")
                return@withContext Result.failure(
                    Exception("Search failed: ${response.code} - ${response.message}")
                )
            }

            val json = JsonParser.parseString(body).asJsonObject
            val resultsArray = json.getAsJsonArray("results")

            val skills = resultsArray.map { element ->
                val obj = element.asJsonObject
                SkillSearchEntry(
                    slug = obj.get("slug")?.asString ?: "",
                    name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString
                        ?: obj.get("slug")?.asString ?: "",
                    description = obj.get("summary")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    version = obj.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "latest",
                    author = null,  // v1 API 不返回 author
                    downloads = 0,  // v1 API 不返回 downloads
                    rating = obj.get("score")?.takeIf { !it.isJsonNull }?.asFloat
                )
            }

            Result.success(
                SkillSearchResult(
                    skills = skills,
                    total = json.get("total")?.asInt ?: skills.size,
                    limit = limit,
                    offset = offset
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get skill details
     *
     * GET /api/skills/:slug
     */
    suspend fun getSkillDetails(slug: String): Result<SkillDetails> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/skills/$slug"
            Log.d(TAG, "Getting skill details: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    Exception("Get details failed: ${response.code} - ${response.message}")
                )
            }

            val json = JsonParser.parseString(body).asJsonObject
            val skill = json.getAsJsonObject("skill")
            val latestVersion = json.getAsJsonObject("latestVersion")
            val owner = json.getAsJsonObject("owner")
            val stats = skill.getAsJsonObject("stats")

            // ClawHub API v1 field mapping:
            // - displayName -> name
            // - summary -> description
            // - tags.latest -> version (from latestVersion)
            // - owner.displayName -> author
            // - stats.downloads -> downloads

            Result.success(
                SkillDetails(
                    slug = skill.get("slug")?.asString ?: "",
                    name = skill.get("displayName")?.takeIf { !it.isJsonNull }?.asString
                        ?: skill.get("slug")?.asString ?: "",
                    description = skill.get("summary")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    version = latestVersion?.get("version")?.takeIf { !it.isJsonNull }?.asString ?: "latest",
                    author = owner?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    homepage = null,  // v1 API 不返回 homepage
                    repository = null,  // v1 API 不返回 repository
                    downloads = stats?.get("downloads")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    rating = null,  // v1 API 不返回 rating
                    readme = null,  // v1 API 不返回 readme
                    metadata = skill.getAsJsonObject("metadata")
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Get details failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get skill version list
     *
     * GET /api/skills/:slug/versions
     */
    suspend fun getSkillVersions(slug: String): Result<List<SkillVersion>> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/skills/$slug/versions"
            Log.d(TAG, "Getting skill versions: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    Exception("Get versions failed: ${response.code} - ${response.message}")
                )
            }

            val json = JsonParser.parseString(body).asJsonObject
            val versions = json.getAsJsonArray("versions").map { element ->
                val obj = element.asJsonObject
                SkillVersion(
                    version = obj.get("version").asString,
                    publishedAt = obj.get("publishedAt")?.asString,
                    changelog = obj.get("changelog")?.asString,
                    hash = obj.get("hash")?.asString
                )
            }

            Result.success(versions)

        } catch (e: Exception) {
            Log.e(TAG, "Get versions failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download skill package
     *
     * ClawHub API v1: GET /api/v1/download?slug=x-twitter&version=latest
     *
     * @param slug Skill slug
     * @param version Version number (default "latest")
     * @param targetFile Download target file
     * @param progressCallback Download progress callback (downloaded bytes, total bytes)
     */
    suspend fun downloadSkill(
        slug: String,
        version: String = "latest",
        targetFile: File,
        progressCallback: ((Long, Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/download?slug=$slug&version=$version"
            Log.d(TAG, "Downloading skill: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Download failed: ${response.code} - ${response.message}")
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val contentLength = body.contentLength()
            Log.d(TAG, "Content length: $contentLength bytes")

            // Ensure target directory exists
            targetFile.parentFile?.mkdirs()

            // Download to temporary file
            val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        progressCallback?.invoke(totalBytesRead, contentLength)
                    }
                }
            }

            // Move to target file
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "✅ Downloaded skill to ${targetFile.absolutePath}")
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Failed to rename temp file"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
}

/**
 * Skill Search Result
 */
data class SkillSearchResult(
    val skills: List<SkillSearchEntry>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

/**
 * Skill Search Entry
 */
data class SkillSearchEntry(
    val slug: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String? = null,
    val downloads: Int,
    val rating: Float? = null
)

/**
 * Skill Details
 */
data class SkillDetails(
    val slug: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String? = null,
    val homepage: String? = null,
    val repository: String? = null,
    val downloads: Int,
    val rating: Float? = null,
    val readme: String? = null,
    val metadata: JsonObject? = null
)

/**
 * Skill Version
 */
data class SkillVersion(
    val version: String,
    val publishedAt: String? = null,
    val changelog: String? = null,
    val hash: String? = null
)
