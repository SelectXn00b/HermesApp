package com.xiaomo.hermes.hermes.gateway

/**
 * Local file-cache for stickers and GIFs.
 *
 * Sticker sets and individual sticker files are downloaded on first use and
 * served from disk afterwards, avoiding redundant API calls and network
 * round-trips on every message.
 *
 * Ported from gateway/sticker_cache.py
 */

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Two-level cache for sticker images.
 *
 * Level 1 —  cache*: keeps the list of sticker file-ids that belong to
 * each sticker set so we don't have to re-fetch the set metadata every time.
 *
 * Level 2 —  cache*: the actual sticker image bytes stored under
 * ``<cacheRoot>/<set>/<file_id>.webp``.
 *
 * Both levels are best-effort and safe to delete at any time.
 */
class StickerCache(
    private val context: Context,
    private val cacheRoot: File = File(context.cacheDir, "sticker_cache"),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()) {
    companion object {
        private const val TAG = "StickerCache"

        /** Make a filesystem-safe name from an arbitrary sticker file-id. */
        fun _sanitise(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    /** Set-name → ordered list of file-ids. */
    private val _setIndex: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()

    /** File-id → cached local path (absolute). */
    private val _fileCache: ConcurrentHashMap<String, File> = ConcurrentHashMap()

    private val _mutex = Mutex()

    init {
        cacheRoot.mkdirs()
    }

    // ------------------------------------------------------------------
    // Set metadata
    // ------------------------------------------------------------------

    /**
     * Register a sticker set.  * is the ordered list of file-ids
     * that belong to the set.
     */
    fun registerSet(setName: String, members: List<String>) {
        _setIndex[setName] = members.toList()
    }

    /** Return the file-ids that belong to *, or null if unknown. */
    fun getSetMembers(setName: String): List<String>? = _setIndex[setName]

    // ------------------------------------------------------------------
    // File-level cache
    // ------------------------------------------------------------------

    /**
     * Return the local path for * if it's already cached, otherwise
     * download it from * and cache the result.
     *
     * Returns null when the download fails.
     */
    suspend fun getOrDownload(fileId: String, url: String): File? {
        // Fast path — already cached in memory.
        _fileCache[fileId]?.let { if (it.exists()) return it }

        return _mutex.withLock {
            // Double-check after acquiring lock.
            _fileCache[fileId]?.let { if (it.exists()) return@withLock it }

            try {
                val dest = _destPath(fileId)
                downloadToFile(url, dest)
                _fileCache[fileId] = dest
                dest
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download sticker $fileId: ${e.message}")
                null
            }
        }
    }

    /**
     * Return the local path for * if it is already cached, without
     * triggering a download.
     */
    fun getCached(fileId: String): File? {
        val f = _fileCache[fileId]
        if (f != null && f.exists()) return f
        // Probe disk in case the cache was warmed externally.
        val onDisk = _destPath(fileId)
        if (onDisk.exists()) {
            _fileCache[fileId] = onDisk
            return onDisk
        }
        return null
    }

    /** Remove a single sticker from both levels of cache. */
    fun evict(fileId: String) {
        _fileCache.remove(fileId)?.delete()
    }

    /** Wipe the entire cache directory. */
    fun clear() {
        _fileCache.clear()
        _setIndex.clear()
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun _destPath(fileId: String): File {
        val safe = _sanitise(fileId)
        return File(cacheRoot, "$safe.webp")
    }

    private suspend fun downloadToFile(url: String, dest: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code} downloading sticker")
            }
            dest.parentFile?.mkdirs()
            dest.writeBytes(resp.body!!.bytes())
        }
    }
}
