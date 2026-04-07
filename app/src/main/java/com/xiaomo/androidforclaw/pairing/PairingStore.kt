package com.xiaomo.androidforclaw.pairing

import com.xiaomo.androidforclaw.infra.JsonFile
import com.xiaomo.androidforclaw.infra.AsyncLock
import com.xiaomo.androidforclaw.infra.generateSecureInt
import com.xiaomo.androidforclaw.pairing.PairingConstants.DEFAULT_ACCOUNT_ID
import com.xiaomo.androidforclaw.pairing.PairingConstants.PAIRING_CODE_ALPHABET
import com.xiaomo.androidforclaw.pairing.PairingConstants.PAIRING_CODE_LENGTH
import com.xiaomo.androidforclaw.pairing.PairingConstants.PAIRING_PENDING_MAX
import com.xiaomo.androidforclaw.pairing.PairingConstants.PAIRING_PENDING_TTL_MS
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/pairing-store.ts
 *
 * File-backed pairing store with in-memory allowFrom cache.
 * Aligned 1:1 with TS pairing-store.ts logic.
 * Android adaptation: uses File + JSONObject instead of node:fs + readJsonFileWithFallback.
 */
object PairingStore {

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------

    private val storeLock = AsyncLock()

    /** In-memory allowFrom cache: filePath -> AllowFromCacheEntry */
    private val allowFromReadCache = ConcurrentHashMap<String, AllowFromCacheEntry>()

    private data class AllowFromCacheEntry(
        val exists: Boolean,
        val mtimeMs: Long?,
        val size: Long?,
        val entries: List<String>,
    )

    // ---------------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------------

    /**
     * Base directory for pairing data files.
     * On Android, this should be set from outside (e.g., context.filesDir / "pairing").
     */
    @Volatile
    var baseDir: File? = null

    private fun resolveBaseDir(): File {
        return baseDir ?: throw IllegalStateException("PairingStore.baseDir not configured")
    }

    private fun safeChannelKey(channel: PairingChannel): String {
        val raw = channel.trim().lowercase()
        require(raw.isNotEmpty()) { "invalid pairing channel" }
        val safe = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").replace("..", "_")
        require(safe.isNotEmpty() && safe != "_") { "invalid pairing channel" }
        return safe
    }

    private fun safeAccountKey(accountId: String): String {
        val raw = accountId.trim().lowercase()
        require(raw.isNotEmpty()) { "invalid pairing account id" }
        val safe = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").replace("..", "_")
        require(safe.isNotEmpty() && safe != "_") { "invalid pairing account id" }
        return safe
    }

    private fun resolvePairingPath(channel: PairingChannel): File {
        return File(resolveBaseDir(), "${safeChannelKey(channel)}-pairing.json")
    }

    private fun resolveAllowFromPath(channel: PairingChannel, accountId: String? = null): File {
        val base = safeChannelKey(channel)
        val normalizedAccountId = accountId?.trim() ?: ""
        return if (normalizedAccountId.isEmpty()) {
            File(resolveBaseDir(), "$base-allowFrom.json")
        } else {
            File(resolveBaseDir(), "$base-${safeAccountKey(normalizedAccountId)}-allowFrom.json")
        }
    }

    fun resolveChannelAllowFromPath(channel: PairingChannel, accountId: String? = null): File {
        return resolveAllowFromPath(channel, accountId)
    }

    // ---------------------------------------------------------------------------
    // Code generation — aligned with TS randomCode / generateUniqueCode
    // ---------------------------------------------------------------------------

    private fun randomCode(): String {
        val sb = StringBuilder(PAIRING_CODE_LENGTH)
        for (i in 0 until PAIRING_CODE_LENGTH) {
            val idx = generateSecureInt(PAIRING_CODE_ALPHABET.length)
            sb.append(PAIRING_CODE_ALPHABET[idx])
        }
        return sb.toString()
    }

    private fun generateUniqueCode(existing: Set<String>): String {
        for (attempt in 0 until 500) {
            val code = randomCode()
            if (code !in existing) return code
        }
        throw IllegalStateException("failed to generate unique pairing code")
    }

    // ---------------------------------------------------------------------------
    // Timestamp helpers
    // ---------------------------------------------------------------------------

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun nowIso(): String = isoFormat.format(Date())

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            isoFormat.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun isExpired(entry: PairingRequest, nowMs: Long): Boolean {
        val createdAt = parseTimestamp(entry.createdAt) ?: return true
        return nowMs - createdAt > PAIRING_PENDING_TTL_MS
    }

    // ---------------------------------------------------------------------------
    // Normalization helpers — aligned with TS
    // ---------------------------------------------------------------------------

    private fun normalizePairingAccountId(accountId: String?): String {
        return accountId?.trim()?.lowercase() ?: ""
    }

    private fun resolveAllowFromAccountId(accountId: String?): String {
        return normalizePairingAccountId(accountId).ifEmpty { DEFAULT_ACCOUNT_ID }
    }

    private fun resolvePairingRequestAccountId(entry: PairingRequest): String {
        return normalizePairingAccountId(entry.meta?.get("accountId")).ifEmpty { DEFAULT_ACCOUNT_ID }
    }

    private fun requestMatchesAccountId(entry: PairingRequest, normalizedAccountId: String): Boolean {
        if (normalizedAccountId.isEmpty()) return true
        return resolvePairingRequestAccountId(entry) == normalizedAccountId
    }

    private fun normalizeId(value: String): String = value.trim()

    private fun normalizeAllowEntry(entry: String): String {
        val trimmed = entry.trim()
        if (trimmed.isEmpty() || trimmed == "*") return ""
        return trimmed
    }

    private fun dedupePreserveOrder(entries: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return entries.filter { e ->
            val n = e.trim()
            n.isNotEmpty() && seen.add(n)
        }
    }

    // ---------------------------------------------------------------------------
    // JSON file I/O
    // ---------------------------------------------------------------------------

    private fun readPairingRequests(file: File): List<PairingRequest> {
        val json = JsonFile.loadJsonFile(file) ?: return emptyList()
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("requests") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                PairingRequest(
                    id = r.optString("id", ""),
                    code = r.optString("code", ""),
                    createdAt = r.optString("createdAt", ""),
                    lastSeenAt = r.optString("lastSeenAt", ""),
                    meta = r.optJSONObject("meta")?.let { m ->
                        m.keys().asSequence().associate { k -> k to m.optString(k, "") }
                    },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writePairingRequests(file: File, requests: List<PairingRequest>) {
        val arr = JSONArray()
        for (r in requests) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("code", r.code)
            obj.put("createdAt", r.createdAt)
            obj.put("lastSeenAt", r.lastSeenAt)
            r.meta?.let { meta ->
                val metaObj = JSONObject()
                for ((k, v) in meta) metaObj.put(k, v)
                obj.put("meta", metaObj)
            }
            arr.put(obj)
        }
        val store = JSONObject()
        store.put("version", 1)
        store.put("requests", arr)
        JsonFile.saveJsonFile(file, store.toString(2))
    }

    private fun readAllowFromList(file: File): List<String> {
        val json = JsonFile.loadJsonFile(file) ?: return emptyList()
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("allowFrom") ?: return emptyList()
            val entries = (0 until arr.length()).map { arr.optString(it, "") }
            dedupePreserveOrder(entries.map { normalizeAllowEntry(it) }.filter { it.isNotEmpty() })
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeAllowFromList(file: File, allowFrom: List<String>) {
        val store = JSONObject()
        store.put("version", 1)
        store.put("allowFrom", JSONArray(allowFrom))
        JsonFile.saveJsonFile(file, store.toString(2))
    }

    // ---------------------------------------------------------------------------
    // Pruning — aligned with TS pruneExpiredRequests / pruneExcessRequestsByAccount
    // ---------------------------------------------------------------------------

    private fun pruneExpiredRequests(reqs: List<PairingRequest>, nowMs: Long): Pair<List<PairingRequest>, Boolean> {
        val kept = mutableListOf<PairingRequest>()
        var removed = false
        for (req in reqs) {
            if (isExpired(req, nowMs)) {
                removed = true
            } else {
                kept.add(req)
            }
        }
        return kept to removed
    }

    private fun resolveLastSeenAt(entry: PairingRequest): Long {
        return parseTimestamp(entry.lastSeenAt) ?: parseTimestamp(entry.createdAt) ?: 0L
    }

    private fun pruneExcessRequestsByAccount(reqs: List<PairingRequest>, maxPending: Int): Pair<List<PairingRequest>, Boolean> {
        if (maxPending <= 0 || reqs.size <= maxPending) return reqs to false

        val grouped = mutableMapOf<String, MutableList<Int>>()
        for ((index, entry) in reqs.withIndex()) {
            val accountId = resolvePairingRequestAccountId(entry)
            grouped.getOrPut(accountId) { mutableListOf() }.add(index)
        }

        val droppedIndexes = mutableSetOf<Int>()
        for (indexes in grouped.values) {
            if (indexes.size <= maxPending) continue
            val sorted = indexes.sortedBy { resolveLastSeenAt(reqs[it]) }
            for (idx in sorted.take(sorted.size - maxPending)) {
                droppedIndexes.add(idx)
            }
        }
        if (droppedIndexes.isEmpty()) return reqs to false

        return reqs.filterIndexed { index, _ -> index !in droppedIndexes } to true
    }

    // ---------------------------------------------------------------------------
    // Public API: allowFrom
    // ---------------------------------------------------------------------------

    /**
     * Read the channel allowFrom list (with legacy backward compat).
     * Aligned with TS readChannelAllowFromStore.
     */
    fun readChannelAllowFromStore(channel: PairingChannel, accountId: String? = null): List<String> {
        val resolvedAccountId = resolveAllowFromAccountId(accountId)

        if (resolvedAccountId != DEFAULT_ACCOUNT_ID) {
            val scopedPath = resolveAllowFromPath(channel, resolvedAccountId)
            return readAllowFromList(scopedPath)
        }

        val scopedPath = resolveAllowFromPath(channel, resolvedAccountId)
        val scopedEntries = readAllowFromList(scopedPath)
        // Backward compat: legacy channel-level allowFrom store was unscoped
        val legacyPath = resolveAllowFromPath(channel)
        val legacyEntries = readAllowFromList(legacyPath)
        return dedupePreserveOrder(scopedEntries + legacyEntries)
    }

    /**
     * Add an entry to the channel allowFrom store.
     * Aligned with TS addChannelAllowFromStoreEntry.
     */
    suspend fun addChannelAllowFromStoreEntry(
        channel: PairingChannel,
        entry: String,
        accountId: String? = null,
    ): Pair<Boolean, List<String>> {
        return storeLock.withLock {
            val file = resolveAllowFromPath(channel, accountId)
            file.parentFile?.mkdirs()
            val current = readAllowFromList(file)
            val normalized = normalizeAllowEntry(normalizeId(entry))
            if (normalized.isEmpty() || current.contains(normalized)) {
                return@withLock false to current
            }
            val next = current + normalized
            writeAllowFromList(file, next)
            true to next
        }
    }

    /**
     * Remove an entry from the channel allowFrom store.
     * Aligned with TS removeChannelAllowFromStoreEntry.
     */
    suspend fun removeChannelAllowFromStoreEntry(
        channel: PairingChannel,
        entry: String,
        accountId: String? = null,
    ): Pair<Boolean, List<String>> {
        return storeLock.withLock {
            val file = resolveAllowFromPath(channel, accountId)
            val current = readAllowFromList(file)
            val normalized = normalizeAllowEntry(normalizeId(entry))
            if (normalized.isEmpty()) return@withLock false to current
            val next = current.filter { it != normalized }
            if (next.size == current.size) return@withLock false to current
            writeAllowFromList(file, next)
            true to next
        }
    }

    // ---------------------------------------------------------------------------
    // Public API: pairing requests
    // ---------------------------------------------------------------------------

    /**
     * List pairing requests for a channel.
     * Aligned with TS listChannelPairingRequests.
     */
    suspend fun listChannelPairingRequests(
        channel: PairingChannel,
        accountId: String? = null,
    ): List<PairingRequest> {
        return storeLock.withLock {
            val file = resolvePairingPath(channel)
            val reqs = readPairingRequests(file)
            val (prunedExpired, expiredRemoved) = pruneExpiredRequests(reqs, System.currentTimeMillis())
            val (pruned, cappedRemoved) = pruneExcessRequestsByAccount(prunedExpired, PAIRING_PENDING_MAX)

            if (expiredRemoved || cappedRemoved) {
                file.parentFile?.mkdirs()
                writePairingRequests(file, pruned)
            }

            val normalizedAccountId = normalizePairingAccountId(accountId)
            val filtered = if (normalizedAccountId.isNotEmpty()) {
                pruned.filter { requestMatchesAccountId(it, normalizedAccountId) }
            } else {
                pruned
            }

            filtered
                .filter { it.id.isNotEmpty() && it.code.isNotEmpty() && it.createdAt.isNotEmpty() }
                .sortedBy { it.createdAt }
        }
    }

    /**
     * Upsert a pairing request (create or update).
     * Aligned with TS upsertChannelPairingRequest.
     */
    suspend fun upsertChannelPairingRequest(
        channel: PairingChannel,
        id: String,
        accountId: String = DEFAULT_ACCOUNT_ID,
        meta: Map<String, String?>? = null,
    ): UpsertResult {
        return storeLock.withLock {
            val file = resolvePairingPath(channel)
            file.parentFile?.mkdirs()
            val now = nowIso()
            val nowMs = System.currentTimeMillis()
            val normalizedId = normalizeId(id)
            val normalizedAccountId = normalizePairingAccountId(accountId).ifEmpty { DEFAULT_ACCOUNT_ID }

            val baseMeta = meta?.entries
                ?.filter { it.value?.trim()?.isNotEmpty() == true }
                ?.associate { it.key to it.value!!.trim() }
            val effectiveMeta = (baseMeta ?: emptyMap()) + ("accountId" to normalizedAccountId)

            var reqs = readPairingRequests(file)
            val (prunedExpired, expiredRemoved) = pruneExpiredRequests(reqs, nowMs)
            reqs = prunedExpired

            val existingIdx = reqs.indexOfFirst { r ->
                r.id == normalizedId && requestMatchesAccountId(r, normalizedAccountId)
            }
            val existingCodes = reqs.map { it.code.trim().uppercase() }.toSet()

            if (existingIdx >= 0) {
                val existing = reqs[existingIdx]
                val existingCode = existing.code.trim().ifEmpty { null }
                val code = existingCode ?: generateUniqueCode(existingCodes)
                val next = PairingRequest(
                    id = normalizedId,
                    code = code,
                    createdAt = existing.createdAt,
                    lastSeenAt = now,
                    meta = effectiveMeta,
                )
                val mutableReqs = reqs.toMutableList()
                mutableReqs[existingIdx] = next
                val (capped, _) = pruneExcessRequestsByAccount(mutableReqs, PAIRING_PENDING_MAX)
                writePairingRequests(file, capped)
                return@withLock UpsertResult(code = code, created = false)
            }

            val (capped, cappedRemoved) = pruneExcessRequestsByAccount(reqs, PAIRING_PENDING_MAX)
            reqs = capped
            val accountRequestCount = reqs.count { requestMatchesAccountId(it, normalizedAccountId) }
            if (PAIRING_PENDING_MAX > 0 && accountRequestCount >= PAIRING_PENDING_MAX) {
                if (expiredRemoved || cappedRemoved) {
                    writePairingRequests(file, reqs)
                }
                return@withLock UpsertResult(code = "", created = false)
            }

            val code = generateUniqueCode(existingCodes)
            val next = PairingRequest(
                id = normalizedId,
                code = code,
                createdAt = now,
                lastSeenAt = now,
                meta = effectiveMeta,
            )
            writePairingRequests(file, reqs + next)
            UpsertResult(code = code, created = true)
        }
    }

    /**
     * Approve a pairing request by code.
     * Aligned with TS approveChannelPairingCode.
     */
    suspend fun approveChannelPairingCode(
        channel: PairingChannel,
        code: String,
        accountId: String? = null,
    ): ApprovePairingResult? {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.isEmpty()) return null

        return storeLock.withLock {
            val file = resolvePairingPath(channel)
            val (pruned, removed) = pruneExpiredRequests(readPairingRequests(file), System.currentTimeMillis())
            val normalizedAccountId = normalizePairingAccountId(accountId)

            val idx = pruned.indexOfFirst { r ->
                r.code.uppercase() == normalizedCode && requestMatchesAccountId(r, normalizedAccountId)
            }

            if (idx < 0) {
                if (removed) {
                    file.parentFile?.mkdirs()
                    writePairingRequests(file, pruned)
                }
                return@withLock null
            }

            val entry = pruned[idx]
            val mutablePruned = pruned.toMutableList()
            mutablePruned.removeAt(idx)
            file.parentFile?.mkdirs()
            writePairingRequests(file, mutablePruned)

            val entryAccountId = entry.meta?.get("accountId")?.trim()?.ifEmpty { null }
            addChannelAllowFromStoreEntry(
                channel = channel,
                entry = entry.id,
                accountId = accountId?.trim()?.ifEmpty { null } ?: entryAccountId,
            )

            ApprovePairingResult(id = entry.id, entry = entry)
        }
    }

    /**
     * Clear the allowFrom read cache (for testing).
     */
    fun clearPairingAllowFromReadCacheForTest() {
        allowFromReadCache.clear()
    }
}

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

data class ApprovePairingResult(
    val id: String,
    val entry: PairingRequest? = null,
)
