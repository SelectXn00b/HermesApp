package com.xiaomo.androidforclaw.hermes.tools

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * Skills Hub — manages skill discovery and loading.
 * Ported from skills_hub.py
 */
object SkillsHub {
    private const val TAG = "SkillsHub"

    data class SkillInfo(
        val name: String,
        val description: String = "",
        val path: String = "",
        val category: String = "general",
        val enabled: Boolean = true)

    private val _skills = mutableMapOf<String, SkillInfo>()

    /**
     * Discover skills in a directory.
     */
    fun discoverSkills(skillsDir: File): List<SkillInfo> {
        if (!skillsDir.exists()) return emptyList()
        val skills = mutableListOf<SkillInfo>()
        for (dir in skillsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            val skillMd = File(dir, "SKILL.md")
            if (skillMd.exists()) {
                val name = readSkillName(skillMd) ?: dir.name
                val desc = readSkillDescription(skillMd)
                val skill = SkillInfo(name = name, description = desc, path = dir.absolutePath)
                skills.add(skill)
                _skills[name] = skill
            }
        }
        return skills
    }

    /**
     * Get a skill by name.
     */
    fun getSkill(name: String): SkillInfo? = _skills[name]

    /**
     * Get all discovered skills.
     */
    fun getAllSkills(): Map<String, SkillInfo> = _skills.toMap()

    private fun readSkillName(skillMd: File): String? {
        return try {
            val content = skillMd.readText(Charsets.UTF_8).take(2000)
            var inFrontmatter = false
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed == "---") {
                    if (inFrontmatter) break
                    inFrontmatter = true
                    continue
                }
                if (inFrontmatter && trimmed.startsWith("name:")) {
                    return trimmed.substringAfter(":").trim().trim('"', '\'')
                }
            }
            null
        } catch (_unused: Exception) { null }
    }

    private fun readSkillDescription(skillMd: File): String {
        return try {
            val content = skillMd.readText(Charsets.UTF_8).take(2000)
            var inFrontmatter = false
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed == "---") {
                    if (inFrontmatter) { inFrontmatter = false; continue }
                    inFrontmatter = true
                    continue
                }
                if (inFrontmatter && trimmed.startsWith("description:")) {
                    return trimmed.substringAfter(":").trim().trim('"', '\'')
                }
            }
            ""
        } catch (_unused: Exception) { "" }
    }


    // === Path constants ===
    val HERMES_HOME: String get() = _hermesHome().absolutePath
    val SKILLS_DIR: String get() = File(_hermesHome(), "skills").absolutePath
    val HUB_DIR: String get() = File(SKILLS_DIR, ".hub").absolutePath
    val LOCK_FILE: String get() = File(HUB_DIR, "lock.json").absolutePath
    val QUARANTINE_DIR: String get() = File(HUB_DIR, "quarantine").absolutePath
    val AUDIT_LOG: String get() = File(HUB_DIR, "audit.log").absolutePath
    val TAPS_FILE: String get() = File(HUB_DIR, "taps.json").absolutePath
    val INDEX_CACHE_DIR: String get() = File(HUB_DIR, "index-cache").absolutePath
    val INDEX_CACHE_TTL: Long = 3600L  // 1 hour in seconds
    val HERMES_INDEX_URL: String = "https://hermes-agent.nousresearch.com/docs/api/skills-index.json"
    val HERMES_INDEX_CACHE_FILE: String get() = File(INDEX_CACHE_DIR, "hermes-index.json").absolutePath
    val HERMES_INDEX_TTL: Long = 6 * 3600L  // 6 hours

    // Trusted repos set
    private val TRUSTED_REPOS = setOf(
        "openai/skills",
        "anthropics/skills")

    // --- Internal state ---
    private var _rootDir: File? = null
    private val _treeCache = ConcurrentHashMap<String, Pair<String, List<JSONObject>>>()
    private var _rateLimited = false

    // --- GitHub auth state ---
    private var _cachedToken: String? = null
    private var _cachedMethod: String? = null
    private var _appTokenExpiry: Long = 0

    // --- Cache helpers state ---
    private val _memoryCache = ConcurrentHashMap<String, Pair<Long, Any>>()

    /**
     * Initialize with a root directory (typically context.filesDir on Android).
     */
    fun init(rootDir: File) {
        _rootDir = rootDir
    }

    private fun _hermesHome(): File {
        return _rootDir ?: File(System.getProperty("user.home"), ".hermes")
    }

    // === Normalize bundle path ===
    private fun normalizeBundlePath(pathValue: String, fieldName: String = "path", allowNested: Boolean = false): String {
        if (pathValue.isBlank()) throw IllegalArgumentException("Unsafe $fieldName: empty path")
        val normalized = pathValue.replace("\\", "/")
        if (normalized.startsWith("/")) throw IllegalArgumentException("Unsafe $fieldName: $pathValue")
        val parts = normalized.split("/").filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) throw IllegalArgumentException("Unsafe $fieldName: $pathValue")
        if (parts[0].matches(Regex("[A-Za-z]:"))) throw IllegalArgumentException("Unsafe $fieldName: $pathValue")
        if (!allowNested && parts.size != 1) throw IllegalArgumentException("Unsafe $fieldName: $pathValue")
        return parts.joinToString("/")
    }

    private fun validateSkillName(name: String): String = normalizeBundlePath(name, "skill name", false)
    private fun validateCategoryName(category: String): String = normalizeBundlePath(category, "category", false)
    private fun validateBundleRelPath(relPath: String): String = normalizeBundlePath(relPath, "bundle file path", true)

    // =========================================================================
    // GitHub Authentication
    // =========================================================================

    /** Return authorization headers for GitHub API requests. */
    fun getHeaders(): Map<String, String> {
        val token = _resolveToken()
        val headers = mutableMapOf("Accept" to "application/vnd.github.v3+json")
        if (token != null) {
            headers["Authorization"] = "token $token"
        }
        return headers
    }

    fun isAuthenticated(): Boolean = _resolveToken() != null

    /** Return which auth method is active: 'pat', 'gh-cli', 'github-app', or 'anonymous'. */
    fun authMethod(): String {
        _resolveToken()
        return _cachedMethod ?: "anonymous"
    }

    fun _resolveToken(): String? {
        // Return cached token if still valid
        if (_cachedToken != null) {
            if (_cachedMethod != "github-app" || System.currentTimeMillis() / 1000 < _appTokenExpiry) {
                return _cachedToken
            }
        }

        // 1. Environment variable
        val envToken = System.getenv("GITHUB_TOKEN") ?: System.getenv("GH_TOKEN")
        if (!envToken.isNullOrBlank()) {
            _cachedToken = envToken
            _cachedMethod = "pat"
            return envToken
        }

        // 2. gh CLI
        val ghToken = _tryGhCli()
        if (ghToken != null) {
            _cachedToken = ghToken
            _cachedMethod = "gh-cli"
            return ghToken
        }

        // 3. GitHub App
        val appToken = _tryGithubApp()
        if (appToken != null) {
            _cachedToken = appToken
            _cachedMethod = "github-app"
            _appTokenExpiry = System.currentTimeMillis() / 1000 + 3500
            return appToken
        }

        _cachedMethod = "anonymous"
        return null
    }

    /** Try to get a token from the gh CLI. */
    fun _tryGhCli(): String? {
        return try {
            val proc = ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            val exited = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (exited && proc.exitValue() == 0 && output.isNotEmpty()) output else null
        } catch (_: Exception) {
            null
        }
    }

    /** Try GitHub App JWT authentication if credentials are configured. */
    fun _tryGithubApp(): String? {
        val appId = System.getenv("GITHUB_APP_ID") ?: return null
        val keyPath = System.getenv("GITHUB_APP_PRIVATE_KEY_PATH") ?: return null
        val installationId = System.getenv("GITHUB_APP_INSTALLATION_ID") ?: return null

        val keyFile = File(keyPath)
        if (!keyFile.exists()) return null

        // JWT generation requires external library; skip on Android if unavailable
        return try {
            // Minimal JWT creation using available crypto
            val privateKey = keyFile.readText()
            val now = System.currentTimeMillis() / 1000
            val payload = JSONObject()
                .put("iat", now - 60)
                .put("exp", now + 600)
                .put("iss", appId)

            // If java-jwt or similar is on classpath, generate JWT
            // For now, return null as PyJWT equivalent isn't available
            null
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // HTTP helpers
    // =========================================================================

    private fun _httpGet(url: String, headers: Map<String, String> = getHeaders(), timeout: Int = 15000): Pair<Int, String>? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()
            Pair(code, body)
        } catch (_: Exception) {
            null
        }
    }

    private fun _httpPost(url: String, headers: Map<String, String>, body: String, timeout: Int = 10000): Pair<Int, String>? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeout
                readTimeout = timeout
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()
            Pair(code, resp)
        } catch (_: Exception) {
            null
        }
    }

    private fun _checkRateLimitResponse(code: Int, headers: Map<String, List<String>>) {
        if (code == 403) {
            val remaining = headers["X-RateLimit-Remaining"]?.firstOrNull() ?: ""
            if (remaining == "0") {
                _rateLimited = true
                Log.w(TAG,
                    "GitHub API rate limit exhausted (unauthenticated: 60 req/hr). " +
                    "Set GITHUB_TOKEN or install the gh CLI to raise the limit to 5,000/hr."
                )
            }
        }
    }
}
