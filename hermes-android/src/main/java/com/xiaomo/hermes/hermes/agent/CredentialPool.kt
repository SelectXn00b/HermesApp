package com.xiaomo.hermes.hermes.agent

import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * Credential Pool - 多 key 轮转 + 自动 failover
 * 1:1 对齐 hermes/agent/credential_pool.py
 *
 * 功能：
 * - 每个 provider 维护多个 API key
 * - 支持 weight 加权轮转
 * - exhausted TTL 自动恢复
 * - 失败时自动切换下一个 key
 * - OAuth token refresh
 * - Lease-based concurrency control
 */
class CredentialPool {

    companion object {
        private const val _TAG = "CredentialPool"

        // --- Status and type constants ---
        const val STATUS_OK = "ok"
        const val STATUS_EXHAUSTED = "exhausted"

        const val AUTH_TYPE_OAUTH = "oauth"
        const val AUTH_TYPE_API_KEY = "api_key"

        const val SOURCE_MANUAL = "manual"

        const val STRATEGY_FILL_FIRST = "fill_first"
        const val STRATEGY_ROUND_ROBIN = "round_robin"
        const val STRATEGY_RANDOM = "random"
        const val STRATEGY_LEAST_USED = "least_used"

        val SUPPORTED_POOL_STRATEGIES: Set<String> = setOf(
            STRATEGY_FILL_FIRST,
            STRATEGY_ROUND_ROBIN,
            STRATEGY_RANDOM,
            STRATEGY_LEAST_USED)

        const val EXHAUSTED_TTL_429_SECONDS = 3600L  // 1 hour
        const val EXHAUSTED_TTL_DEFAULT_SECONDS = 3600L  // 1 hour

        const val CUSTOM_POOL_PREFIX = "custom:"

        val EXTRA_KEYS: Set<String> = setOf(
            "token_type", "scope", "client_id", "portal_base_url", "obtained_at",
            "expires_in", "agent_key_id", "agent_key_expires_in", "agent_key_reused",
            "agent_key_obtained_at", "tls")

        const val DEFAULT_MAX_CONCURRENT_PER_CREDENTIAL = 1

        // In-memory auth store for Android (file-backed)
        private var authStoreFile: File? = null

        fun setAuthStoreFile(file: File) {
            authStoreFile = file
        }

        fun getAuthStoreFile(): File? = authStoreFile
    }

    // === Simple API key model (backward compat) ===

    private val providers: MutableMap<String, MutableList<ApiKey>> = mutableMapOf()

    data class ApiKey(
        val key: String,
        var weight: Int = 1,
        var exhaustedUntil: Long = 0L,
        var lastUsed: Long = 0L,
        var failCount: Int = 0
    )

    // === PooledCredential (full credential entry) ===

    data class PooledCredential(
        val provider: String,
        val id: String = UUID.randomUUID().toString().replace("-", "").take(6),
        val label: String = "",
        val authType: String = AUTH_TYPE_API_KEY,
        val priority: Int = 0,
        val source: String = SOURCE_MANUAL,
        val accessToken: String = "",
        val refreshToken: String? = null,
        val lastStatus: String? = null,
        val lastStatusAt: Double? = null,
        val lastErrorCode: Int? = null,
        val lastErrorReason: String? = null,
        val lastErrorMessage: String? = null,
        val lastErrorResetAt: Double? = null,
        val baseUrl: String? = null,
        val expiresAt: String? = null,
        val expiresAtMs: Long? = null,
        val lastRefresh: String? = null,
        val inferenceBaseUrl: String? = null,
        val agentKey: String? = null,
        val agentKeyExpiresAt: String? = null,
        val requestCount: Int = 0,
        val extra: Map<String, Any?> = emptyMap()
    ) {
        fun getExtra(key: String): Any? = extra[key]

        val runtimeApiKey: String
            get() = if (provider == "nous") (agentKey ?: accessToken) else accessToken

        val runtimeBaseUrl: String?
            get() = if (provider == "nous") (inferenceBaseUrl ?: baseUrl) else baseUrl

        fun toDict(): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            result["id"] = id
            result["label"] = label
            result["auth_type"] = authType
            result["priority"] = priority
            result["source"] = source
            result["access_token"] = accessToken
            if (refreshToken != null) result["refresh_token"] = refreshToken
            if (lastStatus != null) result["last_status"] = lastStatus
            if (lastStatusAt != null) result["last_status_at"] = lastStatusAt
            if (lastErrorCode != null) result["last_error_code"] = lastErrorCode
            if (lastErrorReason != null) result["last_error_reason"] = lastErrorReason
            if (lastErrorMessage != null) result["last_error_message"] = lastErrorMessage
            if (lastErrorResetAt != null) result["last_error_reset_at"] = lastErrorResetAt
            if (baseUrl != null) result["base_url"] = baseUrl
            if (expiresAt != null) result["expires_at"] = expiresAt
            if (expiresAtMs != null) result["expires_at_ms"] = expiresAtMs
            if (lastRefresh != null) result["last_refresh"] = lastRefresh
            if (inferenceBaseUrl != null) result["inference_base_url"] = inferenceBaseUrl
            if (agentKey != null) result["agent_key"] = agentKey
            if (agentKeyExpiresAt != null) result["agent_key_expires_at"] = agentKeyExpiresAt
            result["request_count"] = requestCount
            for ((k, v) in extra) {
                if (v != null) result[k] = v
            }
            return result
        }

        companion object {
            fun fromDict(provider: String, payload: Map<String, Any?>): PooledCredential {
                val extra = mutableMapOf<String, Any?>()
                for (k in EXTRA_KEYS) {
                    val v = payload[k]
                    if (v != null) extra[k] = v
                }
                return PooledCredential(
                    provider = provider,
                    id = (payload["id"] as? String) ?: UUID.randomUUID().toString().replace("-", "").take(6),
                    label = (payload["label"] as? String) ?: (payload["source"] as? String ?: provider),
                    authType = (payload["auth_type"] as? String) ?: AUTH_TYPE_API_KEY,
                    priority = (payload["priority"] as? Number)?.toInt() ?: 0,
                    source = (payload["source"] as? String) ?: SOURCE_MANUAL,
                    accessToken = (payload["access_token"] as? String) ?: "",
                    refreshToken = payload["refresh_token"] as? String,
                    lastStatus = payload["last_status"] as? String,
                    lastStatusAt = (payload["last_status_at"] as? Number)?.toDouble(),
                    lastErrorCode = (payload["last_error_code"] as? Number)?.toInt(),
                    lastErrorReason = payload["last_error_reason"] as? String,
                    lastErrorMessage = payload["last_error_message"] as? String,
                    lastErrorResetAt = (payload["last_error_reset_at"] as? Number)?.toDouble(),
                    baseUrl = payload["base_url"] as? String,
                    expiresAt = payload["expires_at"] as? String,
                    expiresAtMs = (payload["expires_at_ms"] as? Number)?.toLong(),
                    lastRefresh = payload["last_refresh"] as? String,
                    inferenceBaseUrl = payload["inference_base_url"] as? String,
                    agentKey = payload["agent_key"] as? String,
                    agentKeyExpiresAt = payload["agent_key_expires_at"] as? String,
                    requestCount = (payload["request_count"] as? Number)?.toInt() ?: 0,
                    extra = extra
                )
            }
        }
    }

    // === Pool-level fields ===

    private var poolProvider: String = ""
    private val _entries: MutableList<PooledCredential> = mutableListOf()
    private var _currentId: String? = null
    private var _strategy: String = STRATEGY_FILL_FIRST
    private val _lock = ReentrantLock()
    private val _activeLeases: MutableMap<String, Int> = mutableMapOf()
    private var _maxConcurrent: Int = DEFAULT_MAX_CONCURRENT_PER_CREDENTIAL

    // === Simple API (backward compat) ===

    /**
     * 添加 API key
     */
    fun addKey(provider: String, key: String, weight: Int = 1) {
        val list = providers.getOrPut(provider) { mutableListOf() }
        list.add(ApiKey(key = key, weight = weight))
    }

    /**
     * 获取可用的 API key
     */
    fun getKey(provider: String): String? {
        val now = System.currentTimeMillis()
        val keys = providers[provider] ?: return null
        val available = keys.filter { it.exhaustedUntil < now }
        if (available.isEmpty()) return null
        return available.maxByOrNull { it.weight }?.also {
            it.lastUsed = now
        }?.key
    }

    /**
     * 标记 key 为 exhausted
     */
    fun markExhausted(provider: String, key: String, ttlSeconds: Int) {
        val keys = providers[provider] ?: return
        keys.find { it.key == key }?.let {
            it.exhaustedUntil = System.currentTimeMillis() + (ttlSeconds * 1000L)
            it.failCount++
        }
    }

    /**
     * 轮转到下一个 key
     */
    fun rotate(provider: String): String? {
        val keys = providers[provider] ?: return null
        if (keys.isEmpty()) return null
        val first = keys.removeAt(0)
        keys.add(first)
        return getKey(provider)
    }

    fun keyCount(provider: String): Int {
        return providers[provider]?.size ?: 0
    }

    fun providers(): List<String> {
        return providers.keys.toList()
    }

    fun resetKey(provider: String, key: String) {
        providers[provider]?.find { it.key == key }?.let {
            it.exhaustedUntil = 0L
            it.failCount = 0
        }
    }

    // === Full credential pool API ===

    constructor()

    constructor(provider: String, entries: List<PooledCredential>) {
        this.poolProvider = provider
        this._entries.addAll(entries.sortedBy { it.priority })
        this._strategy = getPoolStrategy(provider)
    }

    // --- Config loading ---

    private fun loadConfigSafe(): Map<String, Any?>? {
        return try {
            // On Android, try loading from files dir
            val configFile = authStoreFile?.parentFile?.resolve("config.json")
            if (configFile != null && configFile.exists()) {
                val json = JSONObject(configFile.readText())
                jsonToMap(json)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // --- Credential pool methods ---

    fun fromDict(provider: String, payload: Map<String, Any?>): PooledCredential {
        return PooledCredential.fromDict(provider, payload)
    }

    fun toDict(): Map<String, Any?> {
        return mapOf(
            "provider" to poolProvider,
            "entries" to _entries.map { it.toDict() }
        )
    }

    fun runtimeApiKey(): String {
        val entry = current() ?: entries().firstOrNull() ?: return ""
        return entry.runtimeApiKey
    }

    fun runtimeBaseUrl(): String? {
        val entry = current() ?: entries().firstOrNull() ?: return null
        return entry.runtimeBaseUrl
    }

    fun hasCredentials(): Boolean {
        return _entries.isNotEmpty()
    }

    fun hasAvailable(): Boolean {
        return availableEntries().isNotEmpty()
    }

    fun entries(): List<PooledCredential> {
        return _entries.toList()
    }

    fun current(): PooledCredential? {
        val id = _currentId ?: return null
        return _entries.find { it.id == id }
    }

    fun _replaceEntry(old: PooledCredential, new: PooledCredential) {
        for (i in _entries.indices) {
            if (_entries[i].id == old.id) {
                _entries[i] = new
                return
            }
        }
    }

    fun _persist() {
        try {
            val file = authStoreFile ?: return
            val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            val poolArray = JSONArray()
            for (entry in _entries) {
                poolArray.put(JSONObject(entry.toDict()))
            }
            root.put("pool:$poolProvider", poolArray)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to persist credential pool for $poolProvider", e)
        }
    }

    fun _markExhausted(
        entry: PooledCredential,
        statusCode: Int?,
        errorContext: Map<String, Any>? = null
    ): PooledCredential {
        val normalizedError = normalizeErrorContext(errorContext)
        val updated = entry.copy(
            lastStatus = STATUS_EXHAUSTED,
            lastStatusAt = currentTimeSeconds(),
            lastErrorCode = statusCode,
            lastErrorReason = normalizedError["reason"] as? String,
            lastErrorMessage = normalizedError["message"] as? String,
            lastErrorResetAt = normalizedError["reset_at"] as? Double)
        _replaceEntry(entry, updated)
        _persist()
        return updated
    }

    fun _syncAnthropicEntryFromCredentialsFile(entry: PooledCredential): PooledCredential {
        if (poolProvider != "anthropic" || entry.source != "claude_code") return entry
        return try {
            val credFile = File(System.getProperty("user.home", ""), ".claude/.credentials.json")
            if (!credFile.exists()) return entry
            val json = JSONObject(credFile.readText())
            val fileRefresh = json.optString("refreshToken", "")
            val fileAccess = json.optString("accessToken", "")
            val fileExpires = json.optLong("expiresAt", 0)
            if (fileRefresh.isNotEmpty() && fileRefresh != entry.refreshToken) {
                Log.d(_TAG, "Pool entry ${entry.id}: syncing tokens from credentials file")
                val updated = entry.copy(
                    accessToken = fileAccess,
                    refreshToken = fileRefresh,
                    expiresAtMs = fileExpires,
                    lastStatus = null,
                    lastStatusAt = null,
                    lastErrorCode = null)
                _replaceEntry(entry, updated)
                _persist()
                updated
            } else entry
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to sync from credentials file: ${e.message}")
            entry
        }
    }

    fun _syncCodexEntryFromCli(entry: PooledCredential): PooledCredential {
        if (poolProvider != "openai-codex") return entry
        return try {
            val codexFile = File(System.getProperty("user.home", ""), ".codex/auth.json")
            if (!codexFile.exists()) return entry
            val json = JSONObject(codexFile.readText())
            val cliRefresh = json.optString("refresh_token", "")
            val cliAccess = json.optString("access_token", "")
            if (cliRefresh.isNotEmpty() && cliRefresh != entry.refreshToken) {
                Log.d(_TAG, "Pool entry ${entry.id}: syncing tokens from ~/.codex/auth.json")
                val updated = entry.copy(
                    accessToken = cliAccess,
                    refreshToken = cliRefresh,
                    lastStatus = null,
                    lastStatusAt = null,
                    lastErrorCode = null)
                _replaceEntry(entry, updated)
                _persist()
                updated
            } else entry
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to sync from ~/.codex/auth.json: ${e.message}")
            entry
        }
    }

    fun _syncDeviceCodeEntryToAuthStore(entry: PooledCredential) {
        if (entry.source != "device_code") return
        try {
            val file = authStoreFile ?: return
            val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            if (poolProvider == "nous") {
                val stateKey = "provider_state:nous"
                val state = if (root.has(stateKey)) JSONObject(root.getString(stateKey)) else JSONObject()
                state.put("access_token", entry.accessToken)
                entry.refreshToken?.let { state.put("refresh_token", it) }
                entry.expiresAt?.let { state.put("expires_at", it) }
                entry.agentKey?.let { state.put("agent_key", it) }
                entry.agentKeyExpiresAt?.let { state.put("agent_key_expires_at", it) }
                for (k in listOf("obtained_at", "expires_in", "agent_key_id", "agent_key_expires_in", "agent_key_reused", "agent_key_obtained_at")) {
                    entry.getExtra(k)?.let { state.put(k, it) }
                }
                entry.inferenceBaseUrl?.let { state.put("inference_base_url", it) }
                root.put(stateKey, state.toString())
            } else if (poolProvider == "openai-codex") {
                val stateKey = "provider_state:openai-codex"
                val stateStr = root.optString(stateKey, "{}")
                val state = JSONObject(stateStr)
                val tokens = if (state.has("tokens")) JSONObject(state.getString("tokens")) else JSONObject()
                tokens.put("access_token", entry.accessToken)
                entry.refreshToken?.let { tokens.put("refresh_token", it) }
                entry.lastRefresh?.let { state.put("last_refresh", it) }
                state.put("tokens", tokens.toString())
                root.put(stateKey, state.toString())
            } else {
                return
            }
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.d(_TAG, "Failed to sync $poolProvider pool entry back to auth store: ${e.message}")
        }
    }

    fun _refreshEntry(entry: PooledCredential, force: Boolean = false): PooledCredential? {
        if (entry.authType != AUTH_TYPE_OAUTH || entry.refreshToken == null) {
            if (force) _markExhausted(entry, null)
            return null
        }
        // On Android, OAuth token refresh requires network calls to provider APIs.
        // Implement provider-specific refresh logic as needed.
        Log.w(_TAG, "Token refresh not implemented for provider=${poolProvider} on Android. Entry ${entry.id} marked exhausted.")
        _markExhausted(entry, null)
        return null
    }

    fun _entryNeedsRefresh(entry: PooledCredential): Boolean {
        if (entry.authType != AUTH_TYPE_OAUTH) return false
        if (poolProvider == "anthropic") {
            val expiresMs = entry.expiresAtMs ?: return false
            return expiresMs <= System.currentTimeMillis() + 120_000
        }
        if (poolProvider == "openai-codex") {
            // Check if access token JWT is expiring
            val claims = decodeJwtClaims(entry.accessToken) ?: return false
            val exp = (claims["exp"] as? Number)?.toLong() ?: return false
            return exp * 1000 <= System.currentTimeMillis() + 120_000
        }
        if (poolProvider == "nous") return false
        return false
    }

    fun select(): PooledCredential? {
        _lock.withLock { return selectUnlocked() }
    }

    fun availableEntries(
        clearExpired: Boolean = false,
        refresh: Boolean = false
    ): List<PooledCredential> {
        val now = currentTimeSeconds()
        var clearedAny = false
        val available = mutableListOf<PooledCredential>()

        for (entry in _entries) {
            var current = entry

            // Sync anthropic claude_code entries
            if (poolProvider == "anthropic" && entry.source == "claude_code"
                && entry.lastStatus == STATUS_EXHAUSTED) {
                val synced = _syncAnthropicEntryFromCredentialsFile(entry)
                if (synced !== entry) {
                    current = synced
                    clearedAny = true
                }
            }

            // Sync openai-codex entries
            if (poolProvider == "openai-codex"
                && entry.lastStatus == STATUS_EXHAUSTED
                && entry.refreshToken != null) {
                val synced = _syncCodexEntryFromCli(entry)
                if (synced !== entry) {
                    current = synced
                    clearedAny = true
                }
            }

            if (current.lastStatus == STATUS_EXHAUSTED) {
                val exhaustedUntil = exhaustedUntil(current)
                if (exhaustedUntil != null && now < exhaustedUntil) continue
                if (clearExpired) {
                    val cleared = current.copy(
                        lastStatus = STATUS_OK,
                        lastStatusAt = null,
                        lastErrorCode = null,
                        lastErrorReason = null,
                        lastErrorMessage = null,
                        lastErrorResetAt = null)
                    _replaceEntry(current, cleared)
                    current = cleared
                    clearedAny = true
                }
            }

            if (refresh && _entryNeedsRefresh(current)) {
                val refreshed = _refreshEntry(current, force = false)
                if (refreshed == null) continue
                current = refreshed
            }

            available.add(current)
        }

        if (clearedAny) _persist()
        return available
    }

    fun selectUnlocked(): PooledCredential? {
        val available = availableEntries(clearExpired = true, refresh = true)
        if (available.isEmpty()) {
            _currentId = null
            Log.i(_TAG, "credential pool: no available entries (all exhausted or empty)")
            return null
        }

        if (_strategy == STRATEGY_RANDOM) {
            val entry = available.random()
            _currentId = entry.id
            return entry
        }

        if (_strategy == STRATEGY_LEAST_USED && available.size > 1) {
            val entry = available.minByOrNull { it.requestCount }!!
            _currentId = entry.id
            return entry
        }

        if (_strategy == STRATEGY_ROUND_ROBIN && available.size > 1) {
            val entry = available.first()
            val rotated = _entries.filter { it.id != entry.id }.toMutableList()
            rotated.add(entry.copy(priority = _entries.size - 1))
            _entries.clear()
            _entries.addAll(rotated.mapIndexed { idx, e -> e.copy(priority = idx) })
            _persist()
            _currentId = entry.id
            return current() ?: entry
        }

        // fill_first (default)
        val entry = available.first()
        _currentId = entry.id
        return entry
    }

    fun peek(): PooledCredential? {
        val c = current()
        if (c != null) return c
        return availableEntries().firstOrNull()
    }

    fun markExhaustedAndRotate(
        statusCode: Int?,
        errorContext: Map<String, Any>? = null
    ): PooledCredential? {
        _lock.withLock {
            val entry = current() ?: selectUnlocked() ?: return null
            val label = entry.label.ifEmpty { entry.id.take(8) }
            Log.i(_TAG, "credential pool: marking $label exhausted (status=$statusCode), rotating")
            _markExhausted(entry, statusCode, errorContext)
            _currentId = null
            val nextEntry = selectUnlocked()
            if (nextEntry != null) {
                val nextLabel = nextEntry.label.ifEmpty { nextEntry.id.take(8) }
                Log.i(_TAG, "credential pool: rotated to $nextLabel")
            }
            return nextEntry
        }
    }

    fun acquireLease(credentialId: String? = null): String? {
        _lock.withLock {
            if (credentialId != null) {
                _activeLeases[credentialId] = (_activeLeases[credentialId] ?: 0) + 1
                _currentId = credentialId
                return credentialId
            }

            val available = availableEntries(clearExpired = true, refresh = true)
            if (available.isEmpty()) return null

            val belowCap = available.filter { (_activeLeases[it.id] ?: 0) < _maxConcurrent }
            val candidates = if (belowCap.isNotEmpty()) belowCap else available
            val chosen = candidates.minByOrNull { entry ->
                entry.priority
            } ?: return null

            _activeLeases[chosen.id] = (_activeLeases[chosen.id] ?: 0) + 1
            _currentId = chosen.id
            return chosen.id
        }
    }

    fun releaseLease(credentialId: String) {
        _lock.withLock {
            val count = _activeLeases[credentialId] ?: 0
            if (count <= 1) _activeLeases.remove(credentialId)
            else _activeLeases[credentialId] = count - 1
        }
    }

    fun tryRefreshCurrent(): PooledCredential? {
        _lock.withLock { return tryRefreshCurrentUnlocked() }
    }

    fun tryRefreshCurrentUnlocked(): PooledCredential? {
        val entry = current() ?: return null
        val refreshed = _refreshEntry(entry, force = true)
        if (refreshed != null) _currentId = refreshed.id
        return refreshed
    }

    fun resetStatuses(): Int {
        var count = 0
        val newEntries = _entries.map { entry ->
            if (entry.lastStatus != null || entry.lastStatusAt != null || entry.lastErrorCode != null) {
                count++
                entry.copy(
                    lastStatus = null,
                    lastStatusAt = null,
                    lastErrorCode = null,
                    lastErrorReason = null,
                    lastErrorMessage = null,
                    lastErrorResetAt = null)
            } else entry
        }
        if (count > 0) {
            _entries.clear()
            _entries.addAll(newEntries)
            _persist()
        }
        return count
    }

    fun removeIndex(index: Int): PooledCredential? {
        if (index < 1 || index > _entries.size) return null
        val removed = _entries.removeAt(index - 1)
        val reindexed = _entries.mapIndexed { idx, entry -> entry.copy(priority = idx) }
        _entries.clear()
        _entries.addAll(reindexed)
        _persist()
        if (_currentId == removed.id) _currentId = null
        return removed
    }

    fun resolveTarget(target: Any): Triple<Int?, PooledCredential?, String?> {
        val raw = target.toString().trim()
        if (raw.isEmpty()) return Triple(null, null, "No credential target provided.")

        // Match by ID
        for ((idx, entry) in _entries.withIndex()) {
            if (entry.id == raw) return Triple(idx + 1, entry, null)
        }

        // Match by label
        val labelMatches = _entries.mapIndexedNotNull { idx, entry ->
            if (entry.label.trim().lowercase() == raw.lowercase()) Pair(idx + 1, entry) else null
        }
        if (labelMatches.size == 1) return Triple(labelMatches[0].first, labelMatches[0].second, null)
        if (labelMatches.size > 1) return Triple(null, null, "Ambiguous credential label \"$raw\". Use the numeric index or entry id instead.")

        // Match by index number
        val indexNum = raw.toIntOrNull()
        if (indexNum != null) {
            if (indexNum in 1.._entries.size) return Triple(indexNum, _entries[indexNum - 1], null)
            return Triple(null, null, "No credential #$indexNum.")
        }

        return Triple(null, null, "No credential matching \"$raw\".")
    }

    fun addEntry(entry: PooledCredential): PooledCredential {
        val newPriority = (_entries.maxOfOrNull { it.priority } ?: -1) + 1
        val newEntry = entry.copy(priority = newPriority)
        _entries.add(newEntry)
        _persist()
        return newEntry
    }

    // --- Static helper functions ---

    fun currentTimeSeconds(): Double = System.currentTimeMillis() / 1000.0

    fun parseAbsoluteTimestamp(value: Any?): Double? {
        if (value == null || value == "") return null
        if (value is Number) {
            val numeric = value.toDouble()
            if (numeric <= 0) return null
            return if (numeric > 1_000_000_000_000) numeric / 1000.0 else numeric
        }
        if (value is String) {
            val raw = value.trim()
            if (raw.isEmpty()) return null
            val numeric = raw.toDoubleOrNull()
            if (numeric != null) return if (numeric > 1_000_000_000_000) numeric / 1000.0 else numeric
            // Try ISO-8601 parse
            return try {
                val instant = java.time.Instant.parse(raw.replace("Z", "+00:00").replace("+00:00", "Z"))
                instant.epochSecond + instant.nano / 1_000_000_000.0
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun extractRetryDelaySeconds(message: String?): Double? {
        if (message.isNullOrEmpty()) return null
        val delayMatch = Regex("""quotaResetDelay[:\s"]+(\d+(?:\.\d+)?)(ms|s)""", RegexOption.IGNORE_CASE).find(message)
        if (delayMatch != null) {
            val value = delayMatch.groupValues[1].toDouble()
            return if (delayMatch.groupValues[2].lowercase() == "ms") value / 1000.0 else value
        }
        val secMatch = Regex("""retry\s+(?:after\s+)?(\d+(?:\.\d+)?)\s*(?:sec|secs|seconds|s\b)""", RegexOption.IGNORE_CASE).find(message)
        if (secMatch != null) return secMatch.groupValues[1].toDouble()
        return null
    }

    fun normalizeErrorContext(errorContext: Map<String, Any>?): Map<String, Any?> {
        if (errorContext == null) return emptyMap()
        val normalized = mutableMapOf<String, Any?>()
        val reason = errorContext["reason"] as? String
        if (!reason.isNullOrBlank()) normalized["reason"] = reason.trim()
        val message = errorContext["message"] as? String
        if (!message.isNullOrBlank()) normalized["message"] = message.trim()
        var resetAt = parseAbsoluteTimestamp(
            errorContext["reset_at"] ?: errorContext["resets_at"] ?: errorContext["retry_until"]
        )
        if (resetAt == null && !message.isNullOrEmpty()) {
            val retryDelay = extractRetryDelaySeconds(message)
            if (retryDelay != null) resetAt = currentTimeSeconds() + retryDelay
        }
        if (resetAt != null) normalized["reset_at"] = resetAt
        return normalized
    }

    fun exhaustedUntil(entry: PooledCredential): Double? {
        if (entry.lastStatus != STATUS_EXHAUSTED) return null
        val resetAt = parseAbsoluteTimestamp(entry.lastErrorResetAt)
        if (resetAt != null) return resetAt
        if (entry.lastStatusAt != null) {
            val ttl = if (entry.lastErrorCode == 429) EXHAUSTED_TTL_429_SECONDS else EXHAUSTED_TTL_DEFAULT_SECONDS
            return entry.lastStatusAt + ttl
        }
        return null
    }

    fun exhaustedTtl(errorCode: Int?): Long {
        return if (errorCode == 429) EXHAUSTED_TTL_429_SECONDS else EXHAUSTED_TTL_DEFAULT_SECONDS
    }

    fun decodeJwtClaims(token: String): Map<String, Any?>? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = parts[1]
            // Add padding
            val padded = when (payload.length % 4) {
                2 -> payload + "=="
                3 -> payload + "="
                else -> payload
            }
            val decoded = String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            val json = JSONObject(decoded)
            jsonToMap(json)
        } catch (_: Exception) {
            null
        }
    }

    fun getPoolStrategy(provider: String): String {
        val config = loadConfigSafe() ?: return STRATEGY_FILL_FIRST
        val strategies = config["credential_pool_strategies"] as? Map<*, *> ?: return STRATEGY_FILL_FIRST
        val strategy = (strategies[provider] as? String)?.trim()?.lowercase() ?: return STRATEGY_FILL_FIRST
        return if (strategy in SUPPORTED_POOL_STRATEGIES) strategy else STRATEGY_FILL_FIRST
    }

    fun loadPool(provider: String): CredentialPool {
        val normalizedProvider = provider.trim().lowercase()
        val rawEntries = readCredentialPool(normalizedProvider)
        val entries = rawEntries.map { PooledCredential.fromDict(normalizedProvider, it) }

        val pool = CredentialPool(normalizedProvider, entries)
        return pool
    }

    // --- Credential pool persistence helpers ---

    private fun readCredentialPool(provider: String): List<Map<String, Any?>> {
        val file = authStoreFile ?: return emptyList()
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val key = "pool:$provider"
            if (!root.has(key)) return emptyList()
            val arr = when (val v = root.get(key)) {
                is JSONArray -> v
                is String -> JSONArray(v)
                else -> return emptyList()
            }
            (0 until arr.length()).map { i ->
                jsonToMap(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to read credential pool for $provider", e)
            emptyList()
        }
    }

    fun writeCredentialPool(provider: String, entries: List<Map<String, Any?>>) {
        val file = authStoreFile ?: return
        try {
            val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            val arr = JSONArray()
            for (entry in entries) arr.put(JSONObject(entry))
            root.put("pool:$provider", arr)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(_TAG, "Failed to write credential pool for $provider", e)
        }
    }

    // --- Utility ---

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in json.keys()) {
            val value = json.get(key)
            map[key] = when (value) {
                JSONObject.NULL -> null
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        return (0 until arr.length()).map { i ->
            val value = arr.get(i)
            when (value) {
                JSONObject.NULL -> null
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
    }
}

// ── Module-level symbols (1:1 with agent/credential_pool.py) ───────────

/** Extra metadata keys that should be carried on a PooledCredential. */
val _EXTRA_KEYS: Set<String> = setOf(
    "oauth_token",
    "oauth_refresh_token",
    "oauth_expires_at",
    "oauth_scope",
    "oauth_token_type",
    "oauth_account_id",
    "oauth_account_email",
    "profile_dir",
    "session_id",
    "priority",
    "weight",
    "label",
    "notes",
    "expires_at",
    "last_used_at",
)

/** Human-readable label derived from an API key / token string. */
fun labelFromToken(token: String?, fallback: String = ""): String {
    if (token.isNullOrBlank()) return fallback
    val t = token.trim()
    if (t.length <= 8) return fallback.ifEmpty { t }
    return t.take(4) + "…" + t.takeLast(4)
}

/** Return the next priority slot (maxPriority + 1), or 0 for empty list. */
fun _nextPriority(entries: List<Any?>?): Int {
    if (entries.isNullOrEmpty()) return 0
    var maxP = -1
    for (e in entries) {
        val p = (e as? Map<*, *>)?.get("priority") as? Number
        if (p != null && p.toInt() > maxP) maxP = p.toInt()
    }
    return maxP + 1
}

/** True when a credential source label denotes a manually-added entry. */
fun _isManualSource(source: String?): Boolean {
    if (source.isNullOrBlank()) return false
    val s = source.lowercase()
    return s.startsWith("manual") || s.startsWith("user") || s == "cli"
}

/** Normalize a custom pool name into a stable lowercase slug. */
fun _normalizeCustomPoolName(name: String?): String {
    if (name.isNullOrBlank()) return ""
    return name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

/** Iterate over configured custom providers (empty on Android). */
fun _iterCustomProviders(config: Map<String, Any?>? = null): List<Map<String, Any?>> = emptyList()

/** Resolve the pool key for a given custom provider base URL, or null. */
fun getCustomProviderPoolKey(baseUrl: String?): String? {
    if (baseUrl.isNullOrBlank()) return null
    val normalized = baseUrl.trim().trimEnd('/')
    return _normalizeCustomPoolName(normalized).ifEmpty { null }
}

/** Enumerate configured custom pool provider keys. */
fun listCustomPoolProviders(): List<String> = emptyList()

/** Fetch the raw config map for a custom provider pool, or null. */
fun _getCustomProviderConfig(poolKey: String?): Map<String, Any?>? = null

/** Insert / update a pooled-credential entry. Returns true if mutated. */
fun _upsertEntry(
    entries: MutableList<Any?>?,
    provider: String,
    source: String,
    payload: Map<String, Any?>?
): Boolean {
    if (entries == null || payload == null) return false
    val existing = entries.firstOrNull {
        val m = it as? Map<*, *> ?: return@firstOrNull false
        m["source"] == source
    }
    if (existing != null) {
        // Existing entry — caller applies payload merge upstream; report no-op.
        return false
    }
    entries.add(payload + mapOf("provider" to provider, "source" to source))
    return true
}

/** Normalize priorities so they are contiguous starting from 0. */
fun _normalizePoolPriorities(provider: String, entries: MutableList<Any?>?): Boolean {
    if (entries.isNullOrEmpty()) return false
    val sorted = entries.sortedBy { e -> ((e as? Map<*, *>)?.get("priority") as? Number)?.toInt() ?: Int.MAX_VALUE }
    var changed = false
    for ((i, e) in sorted.withIndex()) {
        val m = e as? Map<*, *> ?: continue
        val cur = (m["priority"] as? Number)?.toInt()
        if (cur != i) changed = true
    }
    return changed
}

/** Seed pool from standalone env/config singleton credentials. */
fun _seedFromSingletons(
    provider: String,
    entries: MutableList<Any?>?
): Pair<Boolean, Set<String>> = false to emptySet()

/** Seed pool from environment variables (e.g. ANTHROPIC_API_KEY_2). */
fun _seedFromEnv(
    provider: String,
    entries: MutableList<Any?>?
): Pair<Boolean, Set<String>> = false to emptySet()

/** Remove seeded-but-no-longer-active entries. Returns true if pruned. */
fun _pruneStaleSeededEntries(
    entries: MutableList<Any?>?,
    activeSources: Set<String>?
): Boolean = false

/** Seed a custom provider pool. Android: no-op returning empty set. */
fun _seedCustomPool(
    poolKey: String,
    entries: MutableList<Any?>?
): Pair<Boolean, Set<String>> = false to emptySet()
