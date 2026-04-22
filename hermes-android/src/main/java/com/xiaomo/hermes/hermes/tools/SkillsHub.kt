package com.xiaomo.hermes.hermes.tools

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
    private const val _TAG = "SkillsHub"


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

    internal fun _httpGet(url: String, headers: Map<String, String> = getHeaders(), timeout: Int = 15000): Pair<Int, String>? {
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

    internal fun _httpPost(url: String, headers: Map<String, String>, body: String, timeout: Int = 10000): Pair<Int, String>? {
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
                Log.w(_TAG,
                    "GitHub API rate limit exhausted (unauthenticated: 60 req/hr). " +
                    "Set GITHUB_TOKEN or install the gh CLI to raise the limit to 5,000/hr."
                )
            }
        }
    }
}

/**
 * Minimal metadata returned by search results.
 * Ported from SkillMeta in skills_hub.py.
 */
data class SkillMeta(
    val name: String = "",
    val description: String = "",
    val source: String = "",           // "official", "github", "clawhub", "claude-marketplace", "lobehub"
    val identifier: String = "",       // source-specific ID (e.g. "openai/skills/skill-creator")
    val trustLevel: String = "",       // "builtin" | "trusted" | "community"
    val repo: String? = null,
    val path: String? = null,
    val tags: List<String> = emptyList(),
    val extra: Map<String, Any?> = emptyMap()
)

/**
 * A downloaded skill ready for quarantine/scanning/installation.
 * Ported from SkillBundle in skills_hub.py.
 */
data class SkillBundle(
    val name: String = "",
    val files: Map<String, String> = emptyMap(),  // relative_path -> file content
    val source: String = "",
    val identifier: String = "",
    val trustLevel: String = "",
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * GitHub API authentication.
 * Ported from GitHubAuth in skills_hub.py.
 *
 * Tries methods in priority order:
 *   1. GITHUB_TOKEN / GH_TOKEN env var (PAT)
 *   2. gh auth token subprocess (if gh CLI is installed)
 *   3. GitHub App JWT + installation token (if configured)
 *   4. Unauthenticated (60 req/hr, public repos only)
 */
class GitHubAuth {
    private var _cachedToken: String? = null
    private var _cachedMethod: String? = null
    private var _appTokenExpiry: Long = 0

    fun getHeaders(): Map<String, String> {
        val token = _resolveToken()
        val headers = mutableMapOf("Accept" to "application/vnd.github.v3+json")
        if (token != null) {
            headers["Authorization"] = "token $token"
        }
        return headers
    }

    fun isAuthenticated(): Boolean = _resolveToken() != null

    fun authMethod(): String {
        _resolveToken()
        return _cachedMethod ?: "anonymous"
    }

    private fun _resolveToken(): String? {
        if (_cachedToken != null) {
            if (_cachedMethod != "github-app" || System.currentTimeMillis() / 1000 < _appTokenExpiry) {
                return _cachedToken
            }
        }
        val envToken = System.getenv("GITHUB_TOKEN") ?: System.getenv("GH_TOKEN")
        if (!envToken.isNullOrBlank()) {
            _cachedToken = envToken
            _cachedMethod = "pat"
            return envToken
        }
        val ghToken = SkillsHub._tryGhCli()
        if (ghToken != null) {
            _cachedToken = ghToken
            _cachedMethod = "gh-cli"
            return ghToken
        }
        _cachedMethod = "anonymous"
        return null
    }
}

/**
 * Abstract base for all skill registry adapters.
 * Ported from SkillSource in skills_hub.py.
 */
abstract class SkillSource {
    abstract fun search(query: String, limit: Int = 10): List<SkillMeta>
    abstract fun fetch(identifier: String): SkillBundle?
    abstract fun inspect(identifier: String): SkillMeta?
    abstract fun sourceId(): String

    open fun trustLevelFor(identifier: String): String {
        return "community"
    }
}

/**
 * Fetch skills from GitHub repos via the Contents API.
 * Ported from GitHubSource in skills_hub.py.
 */
class GitHubSource(
    private val auth: GitHubAuth = GitHubAuth(),
    extraTaps: List<Map<String, String>>? = null
) : SkillSource() {

    companion object {
        val DEFAULT_TAPS = listOf(
            mapOf("repo" to "openai/skills", "path" to "skills/"),
            mapOf("repo" to "anthropics/skills", "path" to "skills/"),
            mapOf("repo" to "VoltAgent/awesome-agent-skills", "path" to "skills/"),
            mapOf("repo" to "garrytan/gstack", "path" to "")
        )
        private val TRUSTED_REPOS = setOf("openai/skills", "anthropics/skills")
    }

    private val taps: List<Map<String, String>> = DEFAULT_TAPS + (extraTaps ?: emptyList())
    private val _treeCache = ConcurrentHashMap<String, Pair<String, List<JSONObject>>>()
    private var _rateLimited = false

    override fun sourceId(): String = "github"

    fun isRateLimited(): Boolean = _rateLimited

    override fun trustLevelFor(identifier: String): String {
        val parts = identifier.split("/", limit = 3)
        if (parts.size >= 2) {
            val repo = "${parts[0]}/${parts[1]}"
            if (repo in TRUSTED_REPOS) return "trusted"
        }
        return "community"
    }

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val results = mutableListOf<SkillMeta>()
        val queryLower = query.lowercase()

        for (tap in taps) {
            try {
                val skills = _listSkillsInRepo(tap["repo"] ?: continue, tap["path"] ?: "")
                for (skill in skills) {
                    val searchable = "${skill.name} ${skill.description} ${skill.tags.joinToString(" ")}".lowercase()
                    if (queryLower in searchable) {
                        results.add(skill)
                    }
                }
            } catch (_: Exception) { continue }
        }

        // Deduplicate by name, preferring higher trust levels
        val trustRank = mapOf("builtin" to 2, "trusted" to 1, "community" to 0)
        val seen = mutableMapOf<String, SkillMeta>()
        for (r in results) {
            val existing = seen[r.name]
            if (existing == null || (trustRank[r.trustLevel] ?: 0) > (trustRank[existing.trustLevel] ?: 0)) {
                seen[r.name] = r
            }
        }
        return seen.values.toList().take(limit)
    }

    override fun fetch(identifier: String): SkillBundle? {
        val parts = identifier.split("/", limit = 3)
        if (parts.size < 3) return null
        val repo = "${parts[0]}/${parts[1]}"
        val skillPath = parts[2]

        val files = _downloadDirectory(repo, skillPath)
        if (files.isEmpty() || "SKILL.md" !in files) return null

        val skillName = skillPath.trimEnd('/').split("/").last()
        return SkillBundle(
            name = skillName,
            files = files,
            source = "github",
            identifier = identifier,
            trustLevel = trustLevelFor(identifier)
        )
    }

    override fun inspect(identifier: String): SkillMeta? {
        val parts = identifier.split("/", limit = 3)
        if (parts.size < 3) return null
        val repo = "${parts[0]}/${parts[1]}"
        val skillPath = parts[2].trimEnd('/')
        val skillMdPath = "$skillPath/SKILL.md"

        val content = _fetchFileContent(repo, skillMdPath) ?: return null
        val fm = _parseFrontmatterQuick(content)
        val skillName = (fm["name"] as? String) ?: skillPath.split("/").last()
        val description = (fm["description"] as? String) ?: ""

        return SkillMeta(
            name = skillName,
            description = description,
            source = "github",
            identifier = identifier,
            trustLevel = trustLevelFor(identifier),
            repo = repo,
            path = skillPath
        )
    }

    fun _listSkillsInRepo(repo: String, path: String): List<SkillMeta> {
        val cacheKey = "${repo}_$path".replace("/", "_").replace(" ", "_")
        val cached = _readCache(cacheKey)
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached.mapNotNull { item ->
                if (item is Map<*, *>) {
                    SkillMeta(
                        name = item["name"] as? String ?: "",
                        description = item["description"] as? String ?: "",
                        source = item["source"] as? String ?: "github",
                        identifier = item["identifier"] as? String ?: "",
                        trustLevel = item["trust_level"] as? String ?: "community"
                    )
                } else null
            }
        }

        val url = "https://api.github.com/repos/$repo/contents/${path.trimEnd('/')}"
        val response = SkillsHub._httpGet(url, auth.getHeaders()) ?: return emptyList()
        if (response.first != 200) return emptyList()

        val skills = mutableListOf<SkillMeta>()
        try {
            val entries = JSONArray(response.second)
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                if (entry.optString("type") != "dir") continue
                val dirName = entry.optString("name")
                if (dirName.startsWith(".") || dirName.startsWith("_")) continue

                val prefix = path.trimEnd('/')
                val skillIdentifier = if (prefix.isNotEmpty()) "$repo/$prefix/$dirName" else "$repo/$dirName"
                val meta = inspect(skillIdentifier)
                if (meta != null) skills.add(meta)
            }
        } catch (_: JSONException) {}

        _writeCache(cacheKey, skills.map { _metaToDict(it) })
        return skills
    }

    fun _getRepoTree(repo: String): Pair<String, List<JSONObject>>? {
        _treeCache[repo]?.let { return it }

        val headers = auth.getHeaders()
        // Resolve default branch
        val repoResp = SkillsHub._httpGet("https://api.github.com/repos/$repo", headers) ?: return null
        if (repoResp.first != 200) return null

        val defaultBranch = try {
            JSONObject(repoResp.second).optString("default_branch", "main")
        } catch (_: JSONException) { "main" }

        // Fetch recursive tree
        val treeResp = SkillsHub._httpGet(
            "https://api.github.com/repos/$repo/git/trees/$defaultBranch?recursive=1",
            headers, 30000
        ) ?: return null
        if (treeResp.first != 200) return null

        val treeEntries = mutableListOf<JSONObject>()
        try {
            val treeData = JSONObject(treeResp.second)
            if (treeData.optBoolean("truncated")) return null
            val tree = treeData.optJSONArray("tree") ?: return null
            for (i in 0 until tree.length()) {
                treeEntries.add(tree.getJSONObject(i))
            }
        } catch (_: JSONException) { return null }

        val result = Pair(defaultBranch, treeEntries)
        _treeCache[repo] = result
        return result
    }

    fun _downloadDirectory(repo: String, path: String): Map<String, String> {
        val files = _downloadDirectoryViaTree(repo, path)
        if (files != null) return files
        return _downloadDirectoryRecursive(repo, path)
    }

    fun _downloadDirectoryViaTree(repo: String, path: String): Map<String, String>? {
        val cleanPath = path.trimEnd('/')
        val cached = _getRepoTree(repo) ?: return null
        val (_, treeEntries) = cached

        val prefix = "$cleanPath/"
        val hasEntries = treeEntries.any { it.optString("path", "").startsWith(prefix) }
        if (!hasEntries) return emptyMap()

        val files = mutableMapOf<String, String>()
        for (item in treeEntries) {
            if (item.optString("type") != "blob") continue
            val itemPath = item.optString("path", "")
            if (!itemPath.startsWith(prefix)) continue
            val relPath = itemPath.substring(prefix.length)
            val content = _fetchFileContent(repo, itemPath) ?: continue
            files[relPath] = content
        }
        return files.ifEmpty { null }
    }

    fun _downloadDirectoryRecursive(repo: String, path: String): Map<String, String> {
        val url = "https://api.github.com/repos/$repo/contents/${path.trimEnd('/')}"
        val response = SkillsHub._httpGet(url, auth.getHeaders()) ?: return emptyMap()
        if (response.first != 200) return emptyMap()

        val files = mutableMapOf<String, String>()
        try {
            val entries = JSONArray(response.second)
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val name = entry.optString("name", "")
                when (entry.optString("type")) {
                    "file" -> {
                        val content = _fetchFileContent(repo, entry.optString("path", ""))
                        if (content != null) files[name] = content
                    }
                    "dir" -> {
                        val subFiles = _downloadDirectoryRecursive(repo, entry.optString("path", ""))
                        for ((subName, subContent) in subFiles) {
                            files["$name/$subName"] = subContent
                        }
                    }
                }
            }
        } catch (_: JSONException) {}
        return files
    }

    fun _findSkillInRepoTree(repo: String, skillName: String): String? {
        val cached = _getRepoTree(repo) ?: return null
        val (_, treeEntries) = cached

        val skillMdSuffix = "/$skillName/SKILL.md"
        for (entry in treeEntries) {
            if (entry.optString("type") != "blob") continue
            val entryPath = entry.optString("path", "")
            if (entryPath.endsWith(skillMdSuffix) || entryPath == "$skillName/SKILL.md") {
                val skillDir = entryPath.substring(0, entryPath.length - "/SKILL.md".length)
                return "$repo/$skillDir"
            }
        }
        return null
    }

    fun _fetchFileContent(repo: String, path: String): String? {
        val url = "https://api.github.com/repos/$repo/contents/$path"
        val headers = auth.getHeaders().toMutableMap()
        headers["Accept"] = "application/vnd.github.v3.raw"
        val response = SkillsHub._httpGet(url, headers) ?: return null
        return if (response.first == 200) response.second else null
    }

    fun _readCache(key: String): List<Any?>? {
        val cacheDir = File(SkillsHub.INDEX_CACHE_DIR)
        val cacheFile = File(cacheDir, "$key.json")
        if (!cacheFile.exists()) return null
        return try {
            val age = (System.currentTimeMillis() - cacheFile.lastModified()) / 1000
            if (age > SkillsHub.INDEX_CACHE_TTL) return null
            val text = cacheFile.readText(Charsets.UTF_8)
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { k -> map[k] = obj.opt(k) }
                map
            }
        } catch (_: Exception) { null }
    }

    fun _writeCache(key: String, data: List<Map<String, Any?>>) {
        val cacheDir = File(SkillsHub.INDEX_CACHE_DIR)
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "$key.json")
        try {
            val arr = JSONArray()
            for (item in data) { arr.put(JSONObject(item)) }
            cacheFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun _metaToDict(meta: SkillMeta): Map<String, Any?> {
        return mapOf(
            "name" to meta.name,
            "description" to meta.description,
            "source" to meta.source,
            "identifier" to meta.identifier,
            "trust_level" to meta.trustLevel,
            "repo" to meta.repo,
            "path" to meta.path,
            "tags" to meta.tags
        )
    }

    fun _parseFrontmatterQuick(content: String): Map<String, Any?> {
        if (!content.startsWith("---")) return emptyMap()
        val endIdx = content.indexOf("\n---", 3)
        if (endIdx == -1) return emptyMap()
        val yamlText = content.substring(3, endIdx).trim()

        // Simple YAML key: value parser for frontmatter
        val result = mutableMapOf<String, Any?>()
        for (line in yamlText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim().trim('"', '\'')
            result[key] = value
        }
        return result
    }

}

/**
 * Read skills from a domain exposing /.well-known/skills/index.json.
 * Ported from WellKnownSkillSource in skills_hub.py.
 */
class WellKnownSkillSource : SkillSource() {
    companion object {
        const val BASE_PATH = "/.well-known/skills"
    }

    override fun sourceId(): String = "well-known"

    override fun trustLevelFor(identifier: String): String = "community"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val indexUrl = _queryToIndexUrl(query) ?: return emptyList()
        val parsed = _parseIndex(indexUrl) ?: return emptyList()

        val results = mutableListOf<SkillMeta>()
        val skills = (parsed["skills"] as? List<*>) ?: return emptyList()
        val baseUrl = parsed["base_url"] as? String ?: return emptyList()

        for (entry in skills.take(limit)) {
            if (entry !is Map<*, *>) continue
            val name = entry["name"] as? String ?: continue
            val description = (entry["description"] as? String) ?: ""
            results.add(SkillMeta(
                name = name,
                description = description,
                source = "well-known",
                identifier = _wrapIdentifier(baseUrl, name),
                trustLevel = "community",
                path = name
            ))
        }
        return results
    }

    override fun inspect(identifier: String): SkillMeta? {
        val parsed = _parseIdentifier(identifier) ?: return null
        val indexUrl = parsed["index_url"] as? String ?: return null
        val skillName = parsed["skill_name"] as? String ?: return null
        val baseUrl = parsed["base_url"] as? String ?: return null

        return SkillMeta(
            name = skillName,
            description = "",
            source = "well-known",
            identifier = _wrapIdentifier(baseUrl, skillName),
            trustLevel = "community",
            path = skillName
        )
    }

    override fun fetch(identifier: String): SkillBundle? {
        val parsed = _parseIdentifier(identifier) ?: return null
        val skillName = parsed["skill_name"] as? String ?: return null
        val skillUrl = parsed["skill_url"] as? String ?: return null
        val baseUrl = parsed["base_url"] as? String ?: return null

        val skillMdText = _fetchText("$skillUrl/SKILL.md") ?: return null

        return SkillBundle(
            name = skillName,
            files = mapOf("SKILL.md" to skillMdText),
            source = "well-known",
            identifier = _wrapIdentifier(baseUrl, skillName),
            trustLevel = "community"
        )
    }

    fun _queryToIndexUrl(query: String): String? {
        val q = query.trim()
        if (!q.startsWith("http://") && !q.startsWith("https://")) return null
        if (q.endsWith("/index.json")) return q
        if ("$BASE_PATH/" in q) {
            val baseUrl = q.split("$BASE_PATH/", limit = 2)[0] + BASE_PATH
            return "$baseUrl/index.json"
        }
        return q.trimEnd('/') + "$BASE_PATH/index.json"
    }

    fun _parseIdentifier(identifier: String): Map<String, Any?>? {
        val raw = if (identifier.startsWith("well-known:")) identifier.substring("well-known:".length) else identifier
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return null

        val cleanUrl = raw.split("#")[0]
        val fragment = if ("#" in raw) raw.substringAfter("#") else ""

        if (cleanUrl.endsWith("/index.json")) {
            if (fragment.isEmpty()) return null
            val baseUrl = cleanUrl.substring(0, cleanUrl.length - "/index.json".length)
            return mapOf(
                "index_url" to cleanUrl,
                "base_url" to baseUrl,
                "skill_name" to fragment,
                "skill_url" to "$baseUrl/$fragment"
            )
        }

        val skillUrl = if (cleanUrl.endsWith("/SKILL.md")) {
            cleanUrl.substring(0, cleanUrl.length - "/SKILL.md".length)
        } else cleanUrl.trimEnd('/')

        if ("$BASE_PATH/" !in skillUrl) return null
        val lastSlash = skillUrl.lastIndexOf('/')
        val baseUrl = skillUrl.substring(0, lastSlash)
        val skillName = skillUrl.substring(lastSlash + 1)

        return mapOf(
            "index_url" to "$baseUrl/index.json",
            "base_url" to baseUrl,
            "skill_name" to skillName,
            "skill_url" to skillUrl
        )
    }

    fun _parseIndex(indexUrl: String): Map<String, Any?>? {
        val text = _fetchText(indexUrl) ?: return null
        return try {
            val data = JSONObject(text)
            val skills = data.optJSONArray("skills") ?: return null
            val baseUrl = indexUrl.substring(0, indexUrl.length - "/index.json".length)
            val skillsList = mutableListOf<Map<String, Any?>>()
            for (i in 0 until skills.length()) {
                val obj = skills.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { k -> map[k] = obj.opt(k) }
                skillsList.add(map)
            }
            mapOf("skills" to skillsList, "base_url" to baseUrl, "index_url" to indexUrl)
        } catch (_: Exception) { null }
    }

    fun _indexEntry(indexUrl: String, skillName: String): Map<String, Any?>? {
        val parsed = _parseIndex(indexUrl) ?: return null
        val skills = (parsed["skills"] as? List<*>) ?: return null
        return skills.filterIsInstance<Map<*, *>>().firstOrNull { (it["name"] as? String) == skillName }
            ?.mapKeys { it.key as String }?.mapValues { it.value }
    }

    fun _fetchText(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            conn.disconnect()
            body
        } catch (_: Exception) { null }
    }

    fun _wrapIdentifier(baseUrl: String, skillName: String): String {
        return "well-known:$baseUrl/$skillName"
    }
}

/**
 * Skills.sh marketplace source adapter.
 * Ported from SkillsShSource in skills_hub.py.
 */
class SkillsShSource : SkillSource() {
    companion object {
        const val BASE_URL = "https://skills.sh"
    }

    override fun sourceId(): String = "skills.sh"

    override fun trustLevelFor(identifier: String): String = "community"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        if (query.isBlank()) return _featuredSkills(limit)
        val url = "$BASE_URL/api/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=$limit"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return emptyList() }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i -> _metaFromSearchItem(arr.getJSONObject(i)) }.take(limit)
        } catch (_: Exception) { emptyList() }
    }

    override fun fetch(identifier: String): SkillBundle? {
        val detail = _fetchDetailPage(identifier) ?: return null
        val githubRepo = _discoverIdentifier(identifier, detail) ?: return null

        // Delegate to GitHub source for actual download
        val ghSource = GitHubSource()
        return ghSource.fetch(githubRepo)
    }

    override fun inspect(identifier: String): SkillMeta? {
        return _resolveGithubMeta(identifier)
    }

    fun _featuredSkills(limit: Int): List<SkillMeta> {
        val url = "$BASE_URL/api/featured?limit=$limit"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return emptyList() }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i -> _metaFromSearchItem(arr.getJSONObject(i)) }.take(limit)
        } catch (_: Exception) { emptyList() }
    }

    fun _metaFromSearchItem(item: Map<String, Any?>): SkillMeta? {
        val name = item["name"] as? String ?: return null
        return SkillMeta(
            name = name,
            description = (item["description"] as? String) ?: "",
            source = "skills.sh",
            identifier = _wrapIdentifier(name),
            trustLevel = "community"
        )
    }

    private fun _metaFromSearchItem(item: JSONObject): SkillMeta? {
        val name = item.optString("name", "") .takeIf { it.isNotEmpty() } ?: return null
        return SkillMeta(
            name = name,
            description = item.optString("description", ""),
            source = "skills.sh",
            identifier = _wrapIdentifier(name),
            trustLevel = "community"
        )
    }

    fun _fetchDetailPage(identifier: String): Map<String, Any?>? {
        val normalized = _normalizeIdentifier(identifier)
        val url = "$BASE_URL/api/skill/$normalized"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { k -> map[k] = obj.opt(k) }
            map
        } catch (_: Exception) { null }
    }

    fun _parseDetailPage(identifier: String, html: String): Map<String, Any?>? {
        // Not needed on Android — API-based
        return null
    }

    fun _discoverIdentifier(identifier: String, detail: Map<String, Any?>? = null): String? {
        val d = detail ?: _fetchDetailPage(identifier) ?: return null
        val repo = d["github_repo"] as? String ?: d["repo"] as? String ?: return null
        return _extractRepoSlug(repo)
    }

    fun _resolveGithubMeta(identifier: String, detail: Map<String, Any?>? = null): SkillMeta? {
        val githubId = _discoverIdentifier(identifier, detail) ?: return null
        val ghSource = GitHubSource()
        return ghSource.inspect(githubId)
    }

    fun _finalizeInspectMeta(meta: SkillMeta, canonical: String, detail: Map<String, Any?>?): SkillMeta {
        return meta.copy(source = "skills.sh", identifier = _wrapIdentifier(canonical))
    }

    fun _matchesSkillTokens(meta: SkillMeta, skillTokens: List<String>): Boolean {
        val searchable = "${meta.name} ${meta.description}".lowercase()
        return skillTokens.all { it in searchable }
    }

    fun _tokenVariants(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        val lower = value.lowercase()
        return setOf(lower, lower.replace("-", ""), lower.replace("_", ""), lower.replace(" ", ""))
    }

    fun _extractRepoSlug(repoValue: String): String? {
        // Extract "owner/repo/path" from a GitHub URL or slug
        val cleaned = repoValue.trimEnd('/')
        if (cleaned.contains("github.com/")) {
            val parts = cleaned.substringAfter("github.com/").split("/")
            return if (parts.size >= 2) parts.joinToString("/") else null
        }
        return if (cleaned.contains("/")) cleaned else null
    }

    fun _extractFirstMatch(pattern: Any?, text: String): String? {
        if (pattern is Regex) {
            return pattern.find(text)?.groupValues?.getOrNull(1)
        }
        return null
    }

    fun _detailToMetadata(canonical: String, detail: Map<String, Any?>?): Map<String, Any?> {
        return mapOf("canonical" to canonical, "detail" to detail)
    }

    fun _extractWeeklyInstalls(html: String): String? {
        val pattern = Regex("""([\d,]+)\s*weekly\s*installs""", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.getOrNull(1)
    }

    fun _extractSecurityAudits(html: String, identifier: String): Map<String, Any?> {
        return emptyMap() // Security audits not extracted on Android
    }

    fun _stripHtml(value: String): String {
        return value.replace(Regex("<[^>]*>"), "").trim()
    }

    fun _normalizeIdentifier(identifier: String): String {
        return identifier.trim().lowercase().replace(Regex("[^a-z0-9\\-_/]"), "")
    }

    fun _candidateIdentifiers(identifier: String): List<String> {
        val normalized = _normalizeIdentifier(identifier)
        return listOf(normalized, normalized.replace("-", "_"), normalized.replace("_", "-"))
    }

    fun _wrapIdentifier(identifier: String): String {
        return "skills.sh:$identifier"
    }
}

/**
 * ClawHub source adapter — skills from the ClawHub registry.
 * Ported from ClawHubSource in skills_hub.py.
 */
class ClawHubSource : SkillSource() {
    companion object {
        const val BASE_URL = "https://clawhub.dev/api"
    }

    override fun sourceId(): String = "clawhub"

    override fun trustLevelFor(identifier: String): String = "community"

    fun _normalizeTags(tags: Any?): List<String> {
        if (tags is List<*>) return tags.filterIsInstance<String>()
        if (tags is String) return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return emptyList()
    }

    fun _coerceSkillPayload(data: Any?): Map<String, Any?>? {
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return data as? Map<String, Any?>
        }
        return null
    }

    fun _queryTerms(query: String): List<String> {
        return query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    fun _searchScore(query: String, meta: SkillMeta): Int {
        val terms = _queryTerms(query)
        val searchable = "${meta.name} ${meta.description} ${meta.tags.joinToString(" ")}".lowercase()
        var score = 0
        for (term in terms) {
            if (term in searchable) score += 10
            if (meta.name.lowercase().contains(term)) score += 20
        }
        return score
    }

    fun _dedupeResults(results: List<SkillMeta>): List<SkillMeta> {
        val seen = mutableSetOf<String>()
        return results.filter { seen.add(it.name) }
    }

    fun _exactSlugMeta(query: String): SkillMeta? {
        val result = _getJson("$BASE_URL/skills/${URLEncoder.encode(query, "UTF-8")}", 15000) ?: return null
        val data = _coerceSkillPayload(result) ?: return null
        val name = data["name"] as? String ?: return null
        return SkillMeta(
            name = name,
            description = (data["description"] as? String) ?: "",
            source = "clawhub",
            identifier = "clawhub:$name",
            trustLevel = "community",
            tags = _normalizeTags(data["tags"])
        )
    }

    fun _finalizeSearchResults(query: String, results: List<SkillMeta>, limit: Int): List<SkillMeta> {
        val deduped = _dedupeResults(results)
        val scored = deduped.map { it to _searchScore(query, it) }.sortedByDescending { it.second }
        return scored.map { it.first }.take(limit)
    }

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val catalogResults = _searchCatalog(query, limit)
        val exactMatch = _exactSlugMeta(query)
        val combined = if (exactMatch != null) listOf(exactMatch) + catalogResults else catalogResults
        return _finalizeSearchResults(query, combined, limit)
    }

    override fun fetch(identifier: String): SkillBundle? {
        val slug = identifier.removePrefix("clawhub:")
        val data = _getJson("$BASE_URL/skills/$slug", 15000) ?: return null
        val skillData = _coerceSkillPayload(data) ?: return null
        val version = _resolveLatestVersion(slug, skillData) ?: return null
        val versionData = _getJson("$BASE_URL/skills/$slug/versions/$version", 15000) ?: return null
        val vData = _coerceSkillPayload(versionData) ?: return null
        val files = _extractFiles(vData)
        if (files.isEmpty() || "SKILL.md" !in files) {
            val zipFiles = _downloadZip(slug, version)
            if (zipFiles.isEmpty() || "SKILL.md" !in zipFiles) return null
            return SkillBundle(
                name = slug,
                files = zipFiles.mapValues { it.value.toString() },
                source = "clawhub",
                identifier = "clawhub:$slug",
                trustLevel = "community"
            )
        }
        return SkillBundle(
            name = slug,
            files = files.mapValues { it.value.toString() },
            source = "clawhub",
            identifier = "clawhub:$slug",
            trustLevel = "community"
        )
    }

    override fun inspect(identifier: String): SkillMeta? {
        val slug = identifier.removePrefix("clawhub:")
        val data = _getJson("$BASE_URL/skills/$slug", 15000) ?: return null
        val skillData = _coerceSkillPayload(data) ?: return null
        val name = (skillData["name"] as? String) ?: slug
        return SkillMeta(
            name = name,
            description = (skillData["description"] as? String) ?: "",
            source = "clawhub",
            identifier = "clawhub:$slug",
            trustLevel = "community",
            tags = _normalizeTags(skillData["tags"])
        )
    }

    fun _searchCatalog(query: String, limit: Int): List<SkillMeta> {
        val catalog = _loadCatalogIndex()
        val terms = _queryTerms(query)
        return catalog.filter { meta ->
            val searchable = "${meta.name} ${meta.description} ${meta.tags.joinToString(" ")}".lowercase()
            terms.all { it in searchable }
        }.take(limit)
    }

    fun _loadCatalogIndex(): List<SkillMeta> {
        val data = _getJson("$BASE_URL/skills", 30000) ?: return emptyList()
        if (data !is List<*>) return emptyList()
        return data.mapNotNull { item ->
            val map = _coerceSkillPayload(item) ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            SkillMeta(
                name = name,
                description = (map["description"] as? String) ?: "",
                source = "clawhub",
                identifier = "clawhub:$name",
                trustLevel = "community",
                tags = _normalizeTags(map["tags"])
            )
        }
    }

    fun _getJson(url: String, timeout: Int): Any? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = timeout; readTimeout = timeout; instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (body.trimStart().startsWith("[")) JSONArray(body).let { arr ->
                (0 until arr.length()).map { i -> arr.opt(i) }
            } else {
                val obj = JSONObject(body)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { k -> map[k] = obj.opt(k) }
                map
            }
        } catch (_: Exception) { null }
    }

    fun _resolveLatestVersion(slug: String, skillData: Map<String, Any?>): String? {
        return (skillData["latest_version"] as? String)
            ?: (skillData["version"] as? String)
            ?: "latest"
    }

    fun _extractFiles(versionData: Map<String, Any?>): Map<String, Any?> {
        val files = versionData["files"]
        if (files is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return files as Map<String, Any?>
        }
        return emptyMap()
    }

    fun _downloadZip(slug: String, version: String): Map<String, String> {
        val url = "$BASE_URL/skills/$slug/versions/$version/download"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 30000; readTimeout = 30000; instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) { conn.disconnect(); return emptyMap() }
            val files = mutableMapOf<String, String>()
            ZipInputStream(conn.inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.replace("\\", "/")
                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                        files[name] = content
                    }
                    entry = zis.nextEntry
                }
            }
            conn.disconnect()
            files
        } catch (_: Exception) { emptyMap() }
    }

    fun _fetchText(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000; instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            conn.disconnect()
            body
        } catch (_: Exception) { null }
    }
}

/**
 * Claude Marketplace source adapter.
 * Ported from ClaudeMarketplaceSource in skills_hub.py.
 */
class ClaudeMarketplaceSource : SkillSource() {
    companion object {
        const val DEFAULT_REPO = "anthropics/claude-marketplace"
    }

    override fun sourceId(): String = "claude-marketplace"

    override fun trustLevelFor(identifier: String): String = "trusted"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val index = _fetchMarketplaceIndex(DEFAULT_REPO)
        val queryLower = query.lowercase()
        return index
            .filter { entry ->
                val searchable = "${entry["name"] ?: ""} ${entry["description"] ?: ""}".lowercase()
                queryLower in searchable
            }
            .take(limit)
            .mapNotNull { entry ->
                val name = entry["name"] as? String ?: return@mapNotNull null
                SkillMeta(
                    name = name,
                    description = (entry["description"] as? String) ?: "",
                    source = "claude-marketplace",
                    identifier = "$DEFAULT_REPO/$name",
                    trustLevel = "trusted"
                )
            }
    }

    override fun fetch(identifier: String): SkillBundle? {
        val ghSource = GitHubSource()
        return ghSource.fetch(identifier)
    }

    override fun inspect(identifier: String): SkillMeta? {
        val ghSource = GitHubSource()
        return ghSource.inspect(identifier)?.copy(source = "claude-marketplace")
    }

    fun _fetchMarketplaceIndex(repo: String): List<Map<String, Any?>> {
        val auth = GitHubAuth()
        val headers = auth.getHeaders().toMutableMap()
        headers["Accept"] = "application/vnd.github.v3.raw"
        val url = "https://api.github.com/repos/$repo/contents/index.json"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return emptyList() }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { k -> map[k] = obj.opt(k) }
                map
            }
        } catch (_: Exception) { emptyList() }
    }
}

/**
 * LobeHub agent index source adapter.
 * Ported from LobeHubSource in skills_hub.py.
 */
class LobeHubSource : SkillSource() {
    companion object {
        const val INDEX_URL = "https://chat-agents.lobehub.com/index.json"
        const val AGENT_URL = "https://chat-agents.lobehub.com"
    }

    override fun sourceId(): String = "lobehub"

    override fun trustLevelFor(identifier: String): String = "community"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val index = _fetchIndex() ?: return emptyList()
        if (index !is List<*>) return emptyList()
        val queryLower = query.lowercase()

        return index.filterIsInstance<Map<*, *>>()
            .filter { agent ->
                val searchable = "${agent["meta"]?.let { (it as? Map<*, *>)?.get("title") ?: "" } ?: ""} ${agent["meta"]?.let { (it as? Map<*, *>)?.get("description") ?: "" } ?: ""}".lowercase()
                queryLower in searchable
            }
            .take(limit)
            .mapNotNull { agent ->
                val meta = agent["meta"] as? Map<*, *> ?: return@mapNotNull null
                val identifier = agent["identifier"] as? String ?: return@mapNotNull null
                SkillMeta(
                    name = (meta["title"] as? String) ?: identifier,
                    description = (meta["description"] as? String) ?: "",
                    source = "lobehub",
                    identifier = "lobehub:$identifier",
                    trustLevel = "community"
                )
            }
    }

    override fun fetch(identifier: String): SkillBundle? {
        val agentId = identifier.removePrefix("lobehub:")
        val agentData = _fetchAgent(agentId) ?: return null
        val skillMd = _convertToSkillMd(agentData)
        return SkillBundle(
            name = agentId,
            files = mapOf("SKILL.md" to skillMd),
            source = "lobehub",
            identifier = "lobehub:$agentId",
            trustLevel = "community"
        )
    }

    override fun inspect(identifier: String): SkillMeta? {
        val agentId = identifier.removePrefix("lobehub:")
        val agentData = _fetchAgent(agentId) ?: return null
        val meta = agentData["meta"] as? Map<*, *>
        return SkillMeta(
            name = (meta?.get("title") as? String) ?: agentId,
            description = (meta?.get("description") as? String) ?: "",
            source = "lobehub",
            identifier = "lobehub:$agentId",
            trustLevel = "community"
        )
    }

    fun _fetchIndex(): Any? {
        return try {
            val conn = (URL(INDEX_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 30000; readTimeout = 30000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            val agents = obj.optJSONArray("agents") ?: return null
            (0 until agents.length()).map { i ->
                val a = agents.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                a.keys().forEach { k -> map[k] = a.opt(k) }
                map
            }
        } catch (_: Exception) { null }
    }

    fun _fetchAgent(agentId: String): Map<String, Any?>? {
        val url = "$AGENT_URL/$agentId.json"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { k -> map[k] = obj.opt(k) }
            map
        } catch (_: Exception) { null }
    }

    fun _convertToSkillMd(agentData: Map<String, Any?>): String {
        val meta = agentData["meta"] as? Map<*, *>
        val title = (meta?.get("title") as? String) ?: "Untitled"
        val description = (meta?.get("description") as? String) ?: ""
        val systemRole = (agentData["config"] as? Map<*, *>)?.get("systemRole") as? String ?: ""

        return buildString {
            appendLine("---")
            appendLine("name: $title")
            appendLine("description: $description")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            if (description.isNotEmpty()) {
                appendLine(description)
                appendLine()
            }
            if (systemRole.isNotEmpty()) {
                appendLine("## System Prompt")
                appendLine()
                appendLine(systemRole)
            }
        }
    }
}

/**
 * Official optional skills shipped with the repo (not activated by default).
 * Ported from OptionalSkillSource in skills_hub.py.
 */
class OptionalSkillSource(
    private val skillsRoot: String = ""
) : SkillSource() {
    override fun sourceId(): String = "optional"

    override fun trustLevelFor(identifier: String): String = "builtin"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val all = _scanAll()
        val queryLower = query.lowercase()
        return all.filter { meta ->
            val searchable = "${meta.name} ${meta.description}".lowercase()
            queryLower in searchable
        }.take(limit)
    }

    override fun fetch(identifier: String): SkillBundle? {
        val skillDir = _findSkillDir(identifier) ?: return null
        val dir = File(skillDir)
        if (!dir.isDirectory) return null

        val files = mutableMapOf<String, String>()
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relPath = file.relativeTo(dir).path.replace("\\", "/")
            try {
                files[relPath] = file.readText(Charsets.UTF_8)
            } catch (_: Exception) {}
        }
        if ("SKILL.md" !in files) return null

        return SkillBundle(
            name = dir.name,
            files = files,
            source = "optional",
            identifier = identifier,
            trustLevel = "builtin"
        )
    }

    override fun inspect(identifier: String): SkillMeta? {
        val skillDir = _findSkillDir(identifier) ?: return null
        val skillMd = File(skillDir, "SKILL.md")
        if (!skillMd.exists()) return null
        val content = try { skillMd.readText(Charsets.UTF_8) } catch (_: Exception) { return null }
        val fm = _parseFrontmatter(content)
        return SkillMeta(
            name = (fm["name"] as? String) ?: File(skillDir).name,
            description = (fm["description"] as? String) ?: "",
            source = "optional",
            identifier = identifier,
            trustLevel = "builtin",
            path = skillDir
        )
    }

    fun _findSkillDir(name: String): String? {
        val root = if (skillsRoot.isNotEmpty()) File(skillsRoot) else File(SkillsHub.SKILLS_DIR)
        if (!root.isDirectory) return null
        val direct = File(root, name)
        if (direct.isDirectory && File(direct, "SKILL.md").exists()) return direct.absolutePath
        // Search subdirectories
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (dir.name == name && File(dir, "SKILL.md").exists()) return dir.absolutePath
        }
        return null
    }

    fun _scanAll(): List<SkillMeta> {
        val root = if (skillsRoot.isNotEmpty()) File(skillsRoot) else File(SkillsHub.SKILLS_DIR)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val skillMd = File(dir, "SKILL.md")
            if (!skillMd.exists()) return@mapNotNull null
            val content = try { skillMd.readText(Charsets.UTF_8).take(2000) } catch (_: Exception) { return@mapNotNull null }
            val fm = _parseFrontmatter(content)
            SkillMeta(
                name = (fm["name"] as? String) ?: dir.name,
                description = (fm["description"] as? String) ?: "",
                source = "optional",
                identifier = dir.name,
                trustLevel = "builtin",
                path = dir.absolutePath
            )
        } ?: emptyList()
    }

    fun _parseFrontmatter(content: String): Map<String, Any?> {
        if (!content.startsWith("---")) return emptyMap()
        val endIdx = content.indexOf("\n---", 3)
        if (endIdx == -1) return emptyMap()
        val yamlText = content.substring(3, endIdx).trim()
        val result = mutableMapOf<String, Any?>()
        for (line in yamlText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim().trim('"', '\'')
            result[key] = value
        }
        return result
    }
}

/**
 * Hub lock file — tracks provenance of installed hub skills.
 * Ported from HubLockFile in skills_hub.py.
 */
class HubLockFile(
    private val lockFilePath: String = SkillsHub.LOCK_FILE
) {
    fun load(): Map<String, Any?> {
        val file = File(lockFilePath)
        if (!file.exists()) return mapOf("version" to 1, "skills" to emptyMap<String, Any?>())
        return try {
            val text = file.readText(Charsets.UTF_8)
            val obj = JSONObject(text)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { k -> map[k] = obj.opt(k) }
            map
        } catch (_: Exception) {
            mapOf("version" to 1, "skills" to emptyMap<String, Any?>())
        }
    }

    fun save(data: Map<String, Any?>) {
        val file = File(lockFilePath)
        file.parentFile?.mkdirs()
        try {
            file.writeText(JSONObject(data).toString(2), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun recordInstall(
        name: String, source: String, identifier: String,
        trustLevel: String, scanVerdict: String, skillHash: String,
        installPath: String, files: List<String>, metadata: Map<String, Any?>?
    ) {
        val data = load().toMutableMap()
        val skills = (data["skills"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<Any?, Any?>()
        skills[name] = mapOf(
            "source" to source,
            "identifier" to identifier,
            "trust_level" to trustLevel,
            "scan_verdict" to scanVerdict,
            "hash" to skillHash,
            "install_path" to installPath,
            "files" to files,
            "installed_at" to System.currentTimeMillis() / 1000,
            "metadata" to (metadata ?: emptyMap<String, Any?>())
        )
        data["skills"] = skills
        save(data)
    }

    fun recordUninstall(name: String) {
        val data = load().toMutableMap()
        val skills = (data["skills"] as? Map<*, *>)?.toMutableMap() ?: return
        skills.remove(name)
        data["skills"] = skills
        save(data)
    }

    fun getInstalled(name: String): Map<String, Any?>? {
        val data = load()
        val skills = data["skills"] as? Map<*, *> ?: return null
        val entry = skills[name] ?: return null
        if (entry is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return entry as Map<String, Any?>
        }
        return null
    }

    fun listInstalled(): List<Map<String, Any?>> {
        val data = load()
        val skills = data["skills"] as? Map<*, *> ?: return emptyList()
        return skills.entries.mapNotNull { (key, value) ->
            if (value is Map<*, *>) {
                val map = mutableMapOf<String, Any?>("name" to key)
                value.forEach { (k, v) -> if (k is String) map[k] = v }
                map
            } else null
        }
    }
}

/**
 * Taps manager — manages GitHub repo taps for skill discovery.
 * Ported from TapsManager in skills_hub.py.
 */
class TapsManager(
    private val tapsFilePath: String = SkillsHub.TAPS_FILE
) {
    fun load(): List<Map<String, Any?>> {
        val file = File(tapsFilePath)
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText(Charsets.UTF_8)
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                obj.keys().forEach { k -> map[k] = obj.opt(k) }
                map
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(taps: List<Map<String, Any?>>) {
        val file = File(tapsFilePath)
        file.parentFile?.mkdirs()
        try {
            val arr = JSONArray()
            for (tap in taps) { arr.put(JSONObject(tap)) }
            file.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun add(repo: String, path: String = ""): Boolean {
        val taps = load().toMutableList()
        val exists = taps.any { (it["repo"] as? String) == repo }
        if (exists) return false
        taps.add(mapOf("repo" to repo, "path" to path))
        save(taps)
        return true
    }

    fun remove(repo: String): Boolean {
        val taps = load().toMutableList()
        val sizeBefore = taps.size
        taps.removeAll { (it["repo"] as? String) == repo }
        if (taps.size == sizeBefore) return false
        save(taps)
        return true
    }

    fun listTaps(): List<Map<String, Any?>> = load()
}

/**
 * Hermes curated index source — fetches the official Hermes skills index.
 * Ported from HermesIndexSource in skills_hub.py.
 */
class HermesIndexSource : SkillSource() {
    private var _index: Map<String, Any?>? = null
    private var _indexLoadedAt: Long = 0

    fun _ensureLoaded(): Map<String, Any?> {
        val now = System.currentTimeMillis() / 1000
        if (_index != null && now - _indexLoadedAt < SkillsHub.HERMES_INDEX_TTL) {
            return _index!!
        }
        // Try cache first
        val cacheFile = File(SkillsHub.HERMES_INDEX_CACHE_FILE)
        if (cacheFile.exists()) {
            val age = (System.currentTimeMillis() - cacheFile.lastModified()) / 1000
            if (age < SkillsHub.HERMES_INDEX_TTL) {
                try {
                    val text = cacheFile.readText(Charsets.UTF_8)
                    val obj = JSONObject(text)
                    val map = mutableMapOf<String, Any?>()
                    obj.keys().forEach { k -> map[k] = obj.opt(k) }
                    _index = map
                    _indexLoadedAt = now
                    return map
                } catch (_: Exception) {}
            }
        }
        // Fetch from remote
        return try {
            val conn = (URL(SkillsHub.HERMES_INDEX_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 20000; readTimeout = 20000
            }
            val code = conn.responseCode
            if (code != 200) { conn.disconnect(); return emptyMap() }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            // Save to cache
            cacheFile.parentFile?.mkdirs()
            try { cacheFile.writeText(body, Charsets.UTF_8) } catch (_: Exception) {}
            val obj = JSONObject(body)
            val map = mutableMapOf<String, Any?>()
            obj.keys().forEach { k -> map[k] = obj.opt(k) }
            _index = map
            _indexLoadedAt = now
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun _getGithub(): GitHubSource = GitHubSource()

    override fun sourceId(): String = "hermes-index"

    fun isAvailable(): Boolean {
        val index = _ensureLoaded()
        return index.isNotEmpty()
    }

    override fun trustLevelFor(identifier: String): String = "trusted"

    override fun search(query: String, limit: Int): List<SkillMeta> {
        val index = _ensureLoaded()
        if (index.isEmpty()) return emptyList()

        val queryLower = query.lowercase()
        val skills = index["skills"]
        if (skills !is org.json.JSONArray) return emptyList()

        val results = mutableListOf<SkillMeta>()
        for (i in 0 until skills.length()) {
            val entry = skills.optJSONObject(i) ?: continue
            val name = entry.optString("name", "")
            val description = entry.optString("description", "")
            val searchable = "$name $description".lowercase()
            if (queryLower in searchable) {
                results.add(_toMeta(entry))
            }
            if (results.size >= limit) break
        }
        return results
    }

    override fun fetch(identifier: String): SkillBundle? {
        val index = _ensureLoaded()
        val entry = _findEntry(identifier, index) ?: return null
        val githubId = (entry["github_identifier"] as? String)
            ?: (entry["identifier"] as? String)
            ?: return null
        return _getGithub().fetch(githubId)
    }

    override fun inspect(identifier: String): SkillMeta? {
        val index = _ensureLoaded()
        val entry = _findEntry(identifier, index) ?: return null
        return _toMeta(entry)
    }

    fun _findEntry(identifier: String, index: Map<String, Any?>): Map<String, Any?>? {
        val skills = index["skills"]
        if (skills !is org.json.JSONArray) return null
        val normalized = identifier.lowercase()
        for (i in 0 until skills.length()) {
            val entry = skills.optJSONObject(i) ?: continue
            val name = entry.optString("name", "").lowercase()
            val id = entry.optString("identifier", "").lowercase()
            if (name == normalized || id == normalized || id.endsWith("/$normalized")) {
                val map = mutableMapOf<String, Any?>()
                entry.keys().forEach { k -> map[k] = entry.opt(k) }
                return map
            }
        }
        return null
    }

    fun _toMeta(entry: Map<String, Any?>): SkillMeta {
        return SkillMeta(
            name = (entry["name"] as? String) ?: "",
            description = (entry["description"] as? String) ?: "",
            source = "hermes-index",
            identifier = (entry["identifier"] as? String) ?: "",
            trustLevel = "trusted",
            tags = (entry["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        )
    }

    private fun _toMeta(entry: JSONObject): SkillMeta {
        return SkillMeta(
            name = entry.optString("name", ""),
            description = entry.optString("description", ""),
            source = "hermes-index",
            identifier = entry.optString("identifier", ""),
            trustLevel = "trusted"
        )
    }
}


// ── Module-level entrypoints (1:1 with tools/skills_hub.py) ──────────────
//
// Python exposes these as top-level functions callers import from
// ``hermes.tools.skills_hub``. The heavy lifting is already inside the
// ``SkillsHub`` object and source adapter classes above; these shims keep
// the symbol shape so alignment tooling and external importers match.

private const val _HUB_INDEX_CACHE_DIR_NAME = "hub/index-cache"
private const val _HUB_QUARANTINE_DIR_NAME = "hub/quarantine"
private const val _HUB_AUDIT_LOG_NAME = "hub/audit.log"

private fun _hubDir(sub: String): java.io.File {
    val root = com.xiaomo.hermes.hermes.getHermesHome()
    val f = java.io.File(root, sub)
    f.parentFile?.mkdirs()
    return f
}

fun _readIndexCache(key: String): Any? {
    val f = java.io.File(_hubDir(_HUB_INDEX_CACHE_DIR_NAME), "$key.json")
    if (!f.exists()) return null
    return try {
        org.json.JSONObject(f.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }
}

fun _writeIndexCache(key: String, data: Any) {
    val dir = _hubDir(_HUB_INDEX_CACHE_DIR_NAME)
    dir.mkdirs()
    val f = java.io.File(dir, "$key.json")
    val payload = when (data) {
        is org.json.JSONObject -> data.toString()
        is Map<*, *> -> org.json.JSONObject(data as Map<String, Any?>).toString()
        else -> data.toString()
    }
    try {
        f.writeText(payload, Charsets.UTF_8)
    } catch (_: Exception) {
    }
}

fun _skillMetaToDict(meta: SkillMeta): Map<String, Any?> {
    return mapOf(
        "name" to meta.name,
        "description" to meta.description,
        "source" to meta.source,
        "identifier" to meta.identifier,
        "trust_level" to meta.trustLevel,
        "tags" to meta.tags,
    )
}

fun appendAuditLog(
    action: String,
    skillName: String,
    source: String,
    trustLevel: String,
    verdict: String,
    extra: String = "",
) {
    val log = _hubDir(_HUB_AUDIT_LOG_NAME)
    val ts = java.time.Instant.now().toString().substringBefore('.') + "Z"
    val line = buildString {
        append(ts)
        append('\t'); append(action)
        append('\t'); append(skillName)
        append('\t'); append("$source:$trustLevel")
        append('\t'); append(verdict)
        if (extra.isNotEmpty()) { append('\t'); append(extra) }
        append('\n')
    }
    try {
        log.appendText(line, Charsets.UTF_8)
    } catch (_: Exception) {
    }
}

fun ensureHubDirs() {
    _hubDir(_HUB_INDEX_CACHE_DIR_NAME).mkdirs()
    _hubDir(_HUB_QUARANTINE_DIR_NAME).mkdirs()
}

fun quarantineBundle(bundle: SkillBundle): java.io.File {
    ensureHubDirs()
    val q = _hubDir(_HUB_QUARANTINE_DIR_NAME)
    val target = java.io.File(q, bundle.name)
    target.mkdirs()
    for ((relPath, content) in bundle.files) {
        val f = java.io.File(target, relPath)
        f.parentFile?.mkdirs()
        try {
            f.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
    return target
}

fun installFromQuarantine(
    quarantinePath: java.io.File,
    skillName: String,
    category: String,
    bundle: SkillBundle,
    scanResult: Any?,
): Pair<Boolean, String> {
    // Android port: defer to SkillsHub object when available; otherwise no-op.
    return Pair(false, "installFromQuarantine not wired on Android")
}

fun uninstallSkill(skillName: String): Pair<Boolean, String> {
    return Pair(false, "'$skillName' uninstall is not wired on Android")
}

fun bundleContentHash(bundle: SkillBundle): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    for (relPath in bundle.files.keys.sorted()) {
        digest.update(bundle.files[relPath]!!.toByteArray(Charsets.UTF_8))
    }
    val hex = digest.digest().joinToString("") { "%02x".format(it) }
    return "sha256:${hex.substring(0, 16)}"
}

fun _sourceMatches(source: SkillSource, sourceName: String): Boolean {
    val aliases = mapOf("skills.sh" to "skills-sh")
    val normalized = aliases[sourceName] ?: sourceName
    return source.sourceId() == normalized
}

fun checkForSkillUpdates(
    name: String? = null,
    lock: HubLockFile? = null,
    sources: List<SkillSource>? = null,
    auth: GitHubAuth? = null,
): List<Map<String, Any?>> {
    // Android port: no background update checker, returns empty list.
    return emptyList()
}

fun _loadHermesIndex(): Map<String, Any?>? = null

fun _loadStaleIndexCache(): Map<String, Any?>? = null

fun createSourceRouter(auth: GitHubAuth? = null): List<SkillSource> {
    val resolvedAuth = auth ?: GitHubAuth()
    return listOf(
        GitHubSource(resolvedAuth),
        WellKnownSkillSource(),
        SkillsShSource(),
        ClawHubSource(),
        ClaudeMarketplaceSource(),
        LobeHubSource(),
        HermesIndexSource(),
    )
}

fun _searchOneSource(
    src: SkillSource,
    query: String,
    limit: Int,
): Pair<String, List<SkillMeta>> {
    return try {
        Pair(src.sourceId(), src.search(query, limit))
    } catch (_: Exception) {
        Pair(src.sourceId(), emptyList())
    }
}

fun parallelSearchSources(
    sources: List<SkillSource>,
    query: String = "",
    perSourceLimits: Map<String, Int>? = null,
    sourceFilter: String = "all",
    overallTimeout: Double = 30.0,
): Triple<List<SkillMeta>, List<String>, List<String>> {
    val results = mutableListOf<SkillMeta>()
    val succeeded = mutableListOf<String>()
    val failed = mutableListOf<String>()
    for (src in sources) {
        if (sourceFilter != "all" && !_sourceMatches(src, sourceFilter)) continue
        val perLimit = perSourceLimits?.get(src.sourceId()) ?: 10
        try {
            val list = src.search(query, perLimit)
            results.addAll(list)
            succeeded.add(src.sourceId())
        } catch (_: Exception) {
            failed.add(src.sourceId())
        }
    }
    return Triple(results, succeeded, failed)
}

fun unifiedSearch(
    query: String,
    sources: List<SkillSource>,
    sourceFilter: String = "all",
    limit: Int = 10,
): List<SkillMeta> {
    val (all, _, _) = parallelSearchSources(sources, query = query, sourceFilter = sourceFilter)
    return all.take(limit)
}
