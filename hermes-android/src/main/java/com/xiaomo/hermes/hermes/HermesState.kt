package com.xiaomo.hermes.hermes

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import kotlin.random.Random

/**
 * 管理持久化状态文件
 * 1:1 对齐 hermes-agent/hermes_state.py
 *
 * 支持并发读写的状态管理。
 * Python 版本使用 filelock，Android 版本使用 FileChannel.lock()。
 */

// ── 全局 Gson 实例 ──────────────────────────────────────────────────────
val gson: Gson = Gson()

/**
 * 管理 Hermes 持久化状态
 *
 * 设计原则：
 * - 单个 JSON 文件：所有状态在一个 dict 中
 * - 原子写入：先写临时文件，再重命名
 * - 文件锁：防止多进程竞争
 * - 延迟加载：首次访问才读取
 * - 自动保存：修改后延迟写入
 *
 * Python 版本使用 filelock 库；Android 版本使用 FileChannel.lock()。
 */
class HermesState(
    private val statePath: File = File(getHermesHome(), "state.json"),
    private val autoSave: Boolean = true) {

    private var _state: MutableMap<String, Any>? = null
    private var _dirty: Boolean = false
    private val _lock = Any()

    /**
     * 确保状态已加载
     */
    private fun ensureLoaded() {
        if (_state != null) return
        _state = loadState()
    }

    /**
     * 从磁盘加载状态
     * Python: _load() -> Dict[str
     */
    private fun loadState(): MutableMap<String, Any> {
        if (!statePath.exists()) {
            return mutableMapOf()
        }

        return try {
            acquireLock { file ->
                val bytes = ByteArray(file.length().toInt())
                file.readFully(bytes)
                val content = String(bytes, Charsets.UTF_8)
                if (content.isBlank()) {
                    mutableMapOf()
                } else {
                    val type = object : TypeToken<MutableMap<String, Any>>() {}.type
                    gson.fromJson(content, type) ?: mutableMapOf()
                }
            }
        } catch (e: Exception) {
            getLogger("hermes_state").warning(
                "Failed to load state from ${statePath.absolutePath}: ${e.message}"
            )
            mutableMapOf()
        }
    }

    /**
     * 保存状态到磁盘
     * Python: _save() -> None
     */
    private fun saveState() {
        val state = _state ?: return
        statePath.parentFile.mkdirs()

        try {
            acquireLock { file ->
                val content = gson.toJson(state)
                file.setLength(0)
                file.write(content.toByteArray(Charsets.UTF_8))
                file.fd.sync()
            }
            _dirty = false
        } catch (e: Exception) {
            getLogger("hermes_state").error(
                "Failed to save state to ${statePath.absolutePath}: ${e.message}"
            )
        }
    }

    /**
     * 获取状态值
     * Python: get(key, default=None)
     */
    fun get(key: String, default: Any? = null): Any? {
        ensureLoaded()
        return _state!![key] ?: default
    }

    /**
     * 设置状态值
     * Python: set(key, value)
     */
    fun set(key: String, value: Any) {
        ensureLoaded()
        _state!![key] = value
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 删除状态值
     * Python: delete(key)
     */
    fun delete(key: String): Boolean {
        ensureLoaded()
        val removed = _state!!.remove(key) != null
        if (removed) {
            _dirty = true
            if (autoSave) {
                saveState()
            }
        }
        return removed
    }

    /**
     * 检查键是否存在
     */
    fun contains(key: String): Boolean {
        ensureLoaded()
        return _state!!.containsKey(key)
    }

    /**
     * 获取所有键
     */
    fun keys(): Set<String> {
        ensureLoaded()
        return _state!!.keys.toSet()
    }

    /**
     * 强制保存
     */
    fun save() {
        saveState()
    }

    /**
     * 清空所有状态
     */
    fun clear() {
        ensureLoaded()
        _state!!.clear()
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 获取状态大小
     */
    fun size(): Int {
        ensureLoaded()
        return _state!!.size
    }

    /**
     * 检查是否有未保存的修改
     */
    fun isDirty(): Boolean = _dirty

    /**
     * 获取状态快照
     */
    fun snapshot(): Map<String, Any> {
        ensureLoaded()
        return _state!!.toMap()
    }

    /**
     * 批量更新
     */
    fun update(updates: Map<String, Any>) {
        ensureLoaded()
        _state!!.putAll(updates)
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 合并更新（深度合并）
     */
    fun merge(key: String, value: Map<String, Any>) {
        ensureLoaded()
        val existing = _state!![key]
        if (existing is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val merged = (existing as Map<String, Any>).toMutableMap()
            merged.putAll(value)
            _state!![key] = merged
        } else {
            _state!![key] = value
        }
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 获取嵌套值
     */
    fun getNested(path: String, default: Any? = null): Any? {
        ensureLoaded()
        val parts = path.split(".")
        var current: Any? = _state
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return default
            }
        }
        return current ?: default
    }

    /**
     * 设置嵌套值
     */
    fun setNested(path: String, value: Any) {
        ensureLoaded()
        val parts = path.split(".")
        var current: MutableMap<String, Any> = _state!!
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            val existing = current[part]
            if (existing is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = existing as MutableMap<String, Any>
            } else {
                val newMap = mutableMapOf<String, Any>()
                current[part] = newMap
                current = newMap
            }
        }
        current[parts.last()] = value
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    // ── 文件锁（Android 版本使用 FileChannel）───────────────────────────────

    /**
     * 获取文件锁并执行操作
     * Python: filelock.FileLock
     * Android: RandomAccessFile + FileChannel.lock()
     */
    private fun <T> acquireLock(block: (RandomAccessFile) -> T): T {
        statePath.parentFile.mkdirs()
        val lockFile = File(statePath.parent, ".${statePath.name}.lock")
        val raf = RandomAccessFile(lockFile, "rw")
        var channel: FileChannel? = null
        var lock: FileLock? = null

        try {
            channel = raf.channel
            lock = channel.lock()
            return block(raf)
        } finally {
            lock?.release()
            channel?.close()
            raf.close()
        }
    }
}

// ── 全局状态实例 ──────────────────────────────────────────────────────────

private var _globalState: HermesState? = null

/**
 * 获取全局状态实例
 */
fun getGlobalState(): HermesState {
    if (_globalState == null) {
        _globalState = HermesState()
    }
    return _globalState!!
}

/**
 * 重置全局状态（测试用）
 */
fun resetGlobalState() {
    _globalState = null
}

// ── SessionDB — SQLite session/message storage ─────────────────────────
// 1:1 对齐 hermes-agent/hermes_state.py SessionDB

private const val SCHEMA_VERSION = 6

private val SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    user_id TEXT,
    model TEXT,
    model_config TEXT,
    system_prompt TEXT,
    parent_session_id TEXT,
    started_at REAL NOT NULL,
    ended_at REAL,
    end_reason TEXT,
    message_count INTEGER DEFAULT 0,
    tool_call_count INTEGER DEFAULT 0,
    input_tokens INTEGER DEFAULT 0,
    output_tokens INTEGER DEFAULT 0,
    cache_read_tokens INTEGER DEFAULT 0,
    cache_write_tokens INTEGER DEFAULT 0,
    reasoning_tokens INTEGER DEFAULT 0,
    billing_provider TEXT,
    billing_base_url TEXT,
    billing_mode TEXT,
    estimated_cost_usd REAL,
    actual_cost_usd REAL,
    cost_status TEXT,
    cost_source TEXT,
    pricing_version TEXT,
    title TEXT,
    FOREIGN KEY (parent_session_id) REFERENCES sessions(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL REFERENCES sessions(id),
    role TEXT NOT NULL,
    content TEXT,
    tool_call_id TEXT,
    tool_calls TEXT,
    tool_name TEXT,
    timestamp REAL NOT NULL,
    token_count INTEGER,
    finish_reason TEXT,
    reasoning TEXT,
    reasoning_details TEXT,
    codex_reasoning_items TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_source ON sessions(source);
CREATE INDEX IF NOT EXISTS idx_sessions_parent ON sessions(parent_session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_started ON sessions(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id, timestamp);
"""

private val FTS_SQL = """
CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
    content,
    content=messages,
    content_rowid=id
);

CREATE TRIGGER IF NOT EXISTS messages_fts_insert AFTER INSERT ON messages BEGIN
    INSERT INTO messages_fts(rowid, content) VALUES (new.id, new.content);
END;

CREATE TRIGGER IF NOT EXISTS messages_fts_delete AFTER DELETE ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.id, old.content);
END;

CREATE TRIGGER IF NOT EXISTS messages_fts_update AFTER UPDATE ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', old.id, old.content);
    INSERT INTO messages_fts(rowid, content) VALUES (new.id, new.content);
END;
"""

private val TITLE_UNIQUE_INDEX_SQL = """
CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_title_unique ON sessions(title) WHERE title IS NOT NULL;
""".trimIndent()

/**
 * SQLite-backed session storage with FTS5 search.
 * 1:1 对齐 hermes-agent/hermes_state.py SessionDB
 *
 * Android 版本使用 android.database.sqlite.SQLiteDatabase。
 * Python 版本使用 sqlite3 模块。
 */
class SessionDB(private val dbPath: File = File(getHermesHome(), "state.db")) {

    companion object {
        const val MAX_TITLE_LENGTH = 100
        private const val WRITE_MAX_RETRIES = 15
        private const val WRITE_RETRY_MIN_MS = 20L
        private const val WRITE_RETRY_MAX_MS = 150L
        private const val CHECKPOINT_EVERY_N_WRITES = 50

        /** Default SQLite path for session state (Python `DEFAULT_DB_PATH`). */
        val DEFAULT_DB_PATH: File by lazy { File(getHermesHome(), "state.db") }
    }

    /** Compression-tip lookup for a session (Python `get_compression_tip`). Stub. */
    @Suppress("UNUSED_PARAMETER")
    fun getCompressionTip(sessionId: String): String? = null

    /** Rich row projection of a session (Python `_get_session_rich_row`). Stub. */
    @Suppress("UNUSED_PARAMETER")
    private fun _getSessionRichRow(sessionId: String): Map<String, Any?>? = null

    private val _lock = Any()
    @Volatile private var _conn: SQLiteDatabase? = null
    private var _writeCount = 0

    private val conn: SQLiteDatabase
        get() {
            if (_conn == null || !_conn!!.isOpen) {
                dbPath.parentFile.mkdirs()
                _conn = SQLiteDatabase.openDatabase(
                    dbPath.absolutePath, null,
                    SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE
                )
                _conn!!.execSQL("PRAGMA journal_mode=WAL")
                _conn!!.execSQL("PRAGMA foreign_keys=ON")
                _initSchema()
            }
            return _conn!!
        }

    // ── Core write helper ──

    /**
     * Execute a write transaction with jitter retry.
     * Python: _execute_write(fn)
     */
    fun <T> executeWrite(fn: (SQLiteDatabase) -> T): T {
        var lastErr: Exception? = null
        for (attempt in 0 until WRITE_MAX_RETRIES) {
            try {
                synchronized(_lock) {
                    val c = conn
                    c.beginTransaction()
                    try {
                        val result = fn(c)
                        c.setTransactionSuccessful()
                        return result
                    } finally {
                        c.endTransaction()
                    }
                }
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                lastErr = e
                if (attempt < WRITE_MAX_RETRIES - 1) {
                    val jitter = Random.nextLong(WRITE_RETRY_MIN_MS, WRITE_RETRY_MAX_MS)
                    Thread.sleep(jitter)
                    continue
                }
                throw e
            } catch (e: android.database.sqlite.SQLiteException) {
                val msg = e.message?.lowercase() ?: ""
                if ("locked" in msg || "busy" in msg) {
                    lastErr = e
                    if (attempt < WRITE_MAX_RETRIES - 1) {
                        val jitter = Random.nextLong(WRITE_RETRY_MIN_MS, WRITE_RETRY_MAX_MS)
                        Thread.sleep(jitter)
                        continue
                    }
                }
                throw e
            }
        }
        throw lastErr ?: android.database.sqlite.SQLiteDatabaseLockedException("database is locked after max retries")
    }

    /**
     * Best-effort PASSIVE WAL checkpoint. Never blocks, never raises.
     * Python: _try_wal_checkpoint()
     */
    fun tryWalCheckpoint() {
        try {
            synchronized(_lock) {
                _conn?.execSQL("PRAGMA wal_checkpoint(PASSIVE)")
            }
        } catch (_: Exception) { }
    }

    /**
     * Close the database connection.
     * Python: close()
     */
    fun close() {
        synchronized(_lock) {
            _conn?.let {
                try { it.execSQL("PRAGMA wal_checkpoint(PASSIVE)") } catch (_: Exception) { }
                it.close()
            }
            _conn = null
        }
    }

    /**
     * Create tables and FTS if they don't exist, run migrations.
     * Python: _init_schema()
     */
    private fun _initSchema() {
        val c = _conn ?: return
        c.beginTransaction()
        try {
            c.execSQL(SCHEMA_SQL)

            // Check schema version
            val cursor = c.rawQuery("SELECT version FROM schema_version LIMIT 1", null)
            val currentVersion = if (cursor.moveToFirst()) cursor.getInt(0) else -1
            cursor.close()

            if (currentVersion < 0) {
                c.execSQL("INSERT INTO schema_version (version) VALUES (?)", arrayOf(SCHEMA_VERSION))
            } else {
                if (currentVersion < 2) {
                    try { c.execSQL("ALTER TABLE messages ADD COLUMN finish_reason TEXT") } catch (_: Exception) { }
                    c.execSQL("UPDATE schema_version SET version = 2")
                }
                if (currentVersion < 3) {
                    try { c.execSQL("ALTER TABLE sessions ADD COLUMN title TEXT") } catch (_: Exception) { }
                    c.execSQL("UPDATE schema_version SET version = 3")
                }
                if (currentVersion < 4) {
                    try { c.execSQL(TITLE_UNIQUE_INDEX_SQL) } catch (_: Exception) { }
                    c.execSQL("UPDATE schema_version SET version = 4")
                }
                if (currentVersion < 5) {
                    val newCols = listOf(
                        "cache_read_tokens INTEGER DEFAULT 0",
                        "cache_write_tokens INTEGER DEFAULT 0",
                        "reasoning_tokens INTEGER DEFAULT 0",
                        "billing_provider TEXT",
                        "billing_base_url TEXT",
                        "billing_mode TEXT",
                        "estimated_cost_usd REAL",
                        "actual_cost_usd REAL",
                        "cost_status TEXT",
                        "cost_source TEXT",
                        "pricing_version TEXT")
                    for (colDef in newCols) {
                        try { c.execSQL("ALTER TABLE sessions ADD COLUMN $colDef") } catch (_: Exception) { }
                    }
                    c.execSQL("UPDATE schema_version SET version = 5")
                }
                if (currentVersion < 6) {
                    for (colDef in listOf("reasoning TEXT", "reasoning_details TEXT", "codex_reasoning_items TEXT")) {
                        try { c.execSQL("ALTER TABLE messages ADD COLUMN $colDef") } catch (_: Exception) { }
                    }
                    c.execSQL("UPDATE schema_version SET version = 6")
                }
            }

            // Ensure title unique index
            try { c.execSQL(TITLE_UNIQUE_INDEX_SQL) } catch (_: Exception) { }

            // FTS5
            try {
                c.rawQuery("SELECT * FROM messages_fts LIMIT 0", null).close()
            } catch (_: Exception) {
                c.execSQL(FTS_SQL)
            }

            c.setTransactionSuccessful()
        } finally {
            c.endTransaction()
        }
    }

    // =========================================================================
    // Session lifecycle
    // =========================================================================

    /**
     * Create a new session record. Returns the session_id.
     * Python: create_session(session_id, source, model, ...)
     */
    fun createSession(
        sessionId: String,
        source: String,
        model: String? = null,
        modelConfig: Map<String, Any>? = null,
        systemPrompt: String? = null,
        userId: String? = null,
        parentSessionId: String? = null): String {
        executeWrite { c ->
            val values = ContentValues().apply {
                put("id", sessionId)
                put("source", source)
                put("user_id", userId)
                put("model", model)
                put("model_config", modelConfig?.let { gson.toJson(it) })
                put("system_prompt", systemPrompt)
                put("parent_session_id", parentSessionId)
                put("started_at", System.currentTimeMillis() / 1000.0)
            }
            c.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
        return sessionId
    }

    /**
     * Mark a session as ended.
     * Python: end_session(session_id, end_reason)
     */
    fun endSession(sessionId: String, endReason: String) {
        executeWrite { c ->
            val values = ContentValues().apply {
                put("ended_at", System.currentTimeMillis() / 1000.0)
                put("end_reason", endReason)
            }
            c.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    /**
     * Clear ended_at/end_reason so a session can be resumed.
     * Python: reopen_session(session_id)
     */
    fun reopenSession(sessionId: String) {
        executeWrite { c ->
            val values = ContentValues().apply {
                putNull("ended_at")
                putNull("end_reason")
            }
            c.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    /**
     * Store the full assembled system prompt snapshot.
     * Python: update_system_prompt(session_id, system_prompt)
     */
    fun updateSystemPrompt(sessionId: String, systemPrompt: String) {
        executeWrite { c ->
            val values = ContentValues().apply {
                put("system_prompt", systemPrompt)
            }
            c.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    /**
     * Update token counters and backfill model if not already set.
     * Python: update_token_counts(session_id, ...)
     */
    fun updateTokenCounts(
        sessionId: String,
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        model: String? = null,
        cacheReadTokens: Int = 0,
        cacheWriteTokens: Int = 0,
        reasoningTokens: Int = 0,
        estimatedCostUsd: Double? = null,
        actualCostUsd: Double? = null,
        costStatus: String? = null,
        costSource: String? = null,
        pricingVersion: String? = null,
        billingProvider: String? = null,
        billingBaseUrl: String? = null,
        billingMode: String? = null,
        absolute: Boolean = false) {
        executeWrite { c ->
            if (absolute) {
                val sql = """UPDATE sessions SET
                    input_tokens = ?, output_tokens = ?,
                    cache_read_tokens = ?, cache_write_tokens = ?,
                    reasoning_tokens = ?,
                    estimated_cost_usd = COALESCE(?, 0),
                    actual_cost_usd = CASE WHEN ? IS NULL THEN actual_cost_usd ELSE ? END,
                    cost_status = COALESCE(?, cost_status),
                    cost_source = COALESCE(?, cost_source),
                    pricing_version = COALESCE(?, pricing_version),
                    billing_provider = COALESCE(billing_provider, ?),
                    billing_base_url = COALESCE(billing_base_url, ?),
                    billing_mode = COALESCE(billing_mode, ?),
                    model = COALESCE(model, ?)
                    WHERE id = ?"""
                c.execSQL(sql, arrayOf(
                    inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens,
                    reasoningTokens, estimatedCostUsd, actualCostUsd, actualCostUsd,
                    costStatus, costSource, pricingVersion, billingProvider,
                    billingBaseUrl, billingMode, model, sessionId
                ))
            } else {
                val sql = """UPDATE sessions SET
                    input_tokens = input_tokens + ?, output_tokens = output_tokens + ?,
                    cache_read_tokens = cache_read_tokens + ?, cache_write_tokens = cache_write_tokens + ?,
                    reasoning_tokens = reasoning_tokens + ?,
                    estimated_cost_usd = COALESCE(estimated_cost_usd, 0) + COALESCE(?, 0),
                    actual_cost_usd = CASE WHEN ? IS NULL THEN actual_cost_usd ELSE COALESCE(actual_cost_usd, 0) + ? END,
                    cost_status = COALESCE(?, cost_status),
                    cost_source = COALESCE(?, cost_source),
                    pricing_version = COALESCE(?, pricing_version),
                    billing_provider = COALESCE(billing_provider, ?),
                    billing_base_url = COALESCE(billing_base_url, ?),
                    billing_mode = COALESCE(billing_mode, ?),
                    model = COALESCE(model, ?)
                    WHERE id = ?"""
                c.execSQL(sql, arrayOf(
                    inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens,
                    reasoningTokens, estimatedCostUsd, actualCostUsd, actualCostUsd,
                    costStatus, costSource, pricingVersion, billingProvider,
                    billingBaseUrl, billingMode, model, sessionId
                ))
            }
        }
    }

    /**
     * Ensure a session row exists, creating it with minimal metadata if absent.
     * Python: ensure_session(session_id, source, model)
     */
    fun ensureSession(sessionId: String, source: String = "unknown", model: String? = null) {
        executeWrite { c ->
            val values = ContentValues().apply {
                put("id", sessionId)
                put("source", source)
                put("model", model)
                put("started_at", System.currentTimeMillis() / 1000.0)
            }
            c.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    /**
     * Get a session by ID.
     * Python: get_session(session_id)
     */
    fun getSession(sessionId: String): Map<String, Any>? {
        synchronized(_lock) {
            val cursor = conn.rawQuery("SELECT * FROM sessions WHERE id = ?", arrayOf(sessionId))
            val result = if (cursor.moveToFirst()) cursorToMap(cursor) else null
            cursor.close()
            return result
        }
    }

    /**
     * Resolve an exact or uniquely prefixed session ID to the full ID.
     * Python: resolve_session_id(session_id_or_prefix)
     */
    fun resolveSessionId(sessionIdOrPrefix: String): String? {
        val exact = getSession(sessionIdOrPrefix)
        if (exact != null) return exact["id"] as? String

        val escaped = sessionIdOrPrefix
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        synchronized(_lock) {
            val cursor = conn.rawQuery(
                "SELECT id FROM sessions WHERE id LIKE ? ESCAPE '\\' ORDER BY started_at DESC LIMIT 2",
                arrayOf("$escaped%")
            )
            val matches = mutableListOf<String>()
            while (cursor.moveToNext()) matches.add(cursor.getString(0))
            cursor.close()
            return if (matches.size == 1) matches[0] else null
        }
    }

    // ── Title management ──

    /**
     * Validate and sanitize a session title.
     * Python: sanitize_title(title)
     */
    fun sanitizeTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        // Remove ASCII control chars (except \t \n \r)
        var cleaned = title.replace(Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]"), "")
        // Remove problematic Unicode control chars
        cleaned = cleaned.replace(Regex("[\\u200b-\\u200f\\u2028-\\u202e\\u2060-\\u2069\\ufeff\\ufffc\\ufff9-\\ufffb]"), "")
        // Collapse whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.length > MAX_TITLE_LENGTH) {
            throw IllegalArgumentException("Title too long (${cleaned.length} chars, max $MAX_TITLE_LENGTH)")
        }
        return cleaned
    }

    /**
     * Set or update a session's title.
     * Python: set_session_title(session_id, title)
     */
    fun setSessionTitle(sessionId: String, title: String): Boolean {
        val cleanTitle = sanitizeTitle(title)
        return executeWrite { c ->
            if (cleanTitle != null) {
                val cursor = c.rawQuery(
                    "SELECT id FROM sessions WHERE title = ? AND id != ?",
                    arrayOf(cleanTitle, sessionId)
                )
                val conflict = cursor.moveToFirst()
                cursor.close()
                if (conflict) {
                    throw IllegalArgumentException("Title '$cleanTitle' is already in use")
                }
            }
            val values = ContentValues().apply { put("title", cleanTitle) }
            c.update("sessions", values, "id = ?", arrayOf(sessionId)) > 0
        }
    }

    /**
     * Get the title for a session, or None.
     * Python: get_session_title(session_id)
     */
    fun getSessionTitle(sessionId: String): String? {
        synchronized(_lock) {
            val cursor = conn.rawQuery("SELECT title FROM sessions WHERE id = ?", arrayOf(sessionId))
            val title = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            return title
        }
    }

    /**
     * Look up a session by exact title.
     * Python: get_session_by_title(title)
     */
    fun getSessionByTitle(title: String): Map<String, Any>? {
        synchronized(_lock) {
            val cursor = conn.rawQuery("SELECT * FROM sessions WHERE title = ?", arrayOf(title))
            val result = if (cursor.moveToFirst()) cursorToMap(cursor) else null
            cursor.close()
            return result
        }
    }

    /**
     * Resolve a title to a session ID, preferring the latest in a lineage.
     * Python: resolve_session_by_title(title)
     */
    fun resolveSessionByTitle(title: String): String? {
        val exact = getSessionByTitle(title)
        val escaped = title.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        synchronized(_lock) {
            val cursor = conn.rawQuery(
                "SELECT id, title, started_at FROM sessions WHERE title LIKE ? ESCAPE '\\' ORDER BY started_at DESC",
                arrayOf("$escaped #%")
            )
            val numbered = mutableListOf<Triple<String, String, Double>>()
            while (cursor.moveToNext()) {
                numbered.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getDouble(2)))
            }
            cursor.close()

            if (numbered.isNotEmpty()) return numbered[0].first
            if (exact != null) return exact["id"] as? String
            return null
        }
    }

    /**
     * Generate the next title in a lineage.
     * Python: get_next_title_in_lineage(base_title)
     */
    fun getNextTitleInLineage(baseTitle: String): String {
        val match = Regex("^(.*?) #(\\d+)$").find(baseTitle)
        val base = match?.groupValues?.get(1) ?: baseTitle
        val escaped = base.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        synchronized(_lock) {
            val cursor = conn.rawQuery(
                "SELECT title FROM sessions WHERE title = ? OR title LIKE ? ESCAPE '\\'",
                arrayOf(base, "$escaped #%")
            )
            val existing = mutableListOf<String>()
            while (cursor.moveToNext()) existing.add(cursor.getString(0))
            cursor.close()

            if (existing.isEmpty()) return base

            var maxNum = 1
            for (t in existing) {
                val m = Regex("^.* #(\\d+)$").find(t)
                if (m != null) maxNum = maxOf(maxNum, m.groupValues[1].toInt())
            }
            return "$base #${maxNum + 1}"
        }
    }

    /**
     * List sessions with preview and last active timestamp.
     * Python: list_sessions_rich(source, exclude_sources, limit, offset, include_children)
     */
    fun listSessionsRich(
        source: String? = null,
        excludeSources: List<String>? = null,
        limit: Int = 20,
        offset: Int = 0,
        includeChildren: Boolean = false,
        @Suppress("UNUSED_PARAMETER") projectCompressionTips: Boolean = true): List<Map<String, Any>> {
        val whereClauses = mutableListOf<String>()
        val params = mutableListOf<String>()

        if (!includeChildren) whereClauses.add("s.parent_session_id IS NULL")
        if (source != null) { whereClauses.add("s.source = ?"); params.add(source) }
        if (excludeSources != null && excludeSources.isNotEmpty()) {
            whereClauses.add("s.source NOT IN (${excludeSources.joinToString(",") { "?" }})")
            params.addAll(excludeSources)
        }

        val whereSql = if (whereClauses.isNotEmpty()) "WHERE ${whereClauses.joinToString(" AND ")}" else ""
        val query = """
            SELECT s.*,
                COALESCE(
                    (SELECT SUBSTR(REPLACE(REPLACE(m.content, X'0A', ' '), X'0D', ' '), 1, 63)
                     FROM messages m
                     WHERE m.session_id = s.id AND m.role = 'user' AND m.content IS NOT NULL
                     ORDER BY m.timestamp, m.id LIMIT 1),
                    ''
                ) AS _preview_raw,
                COALESCE(
                    (SELECT MAX(m2.timestamp) FROM messages m2 WHERE m2.session_id = s.id),
                    s.started_at
                ) AS last_active
            FROM sessions s
            $whereSql
            ORDER BY s.started_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        params.add(limit.toString())
        params.add(offset.toString())

        synchronized(_lock) {
            val cursor = conn.rawQuery(query, params.toTypedArray())
            val sessions = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) {
                val row = cursorToMap(cursor).toMutableMap()
                val raw = (row.remove("_preview_raw") as? String ?: "").trim()
                row["preview"] = if (raw.isNotEmpty()) {
                    raw.take(60) + if (raw.length > 60) "..." else ""
                } else ""
                sessions.add(row)
            }
            cursor.close()
            return sessions
        }
    }

    // =========================================================================
    // Message storage
    // =========================================================================

    /**
     * Append a message to a session. Returns the message row ID.
     * Python: append_message(session_id, role, content, ...)
     */
    fun appendMessage(
        sessionId: String,
        role: String,
        content: String? = null,
        toolName: String? = null,
        toolCalls: Any? = null,
        toolCallId: String? = null,
        tokenCount: Int? = null,
        finishReason: String? = null,
        reasoning: String? = null,
        reasoningDetails: Any? = null,
        codexReasoningItems: Any? = null): Int {
        val toolCallsJson = toolCalls?.let { gson.toJson(it) }
        val reasoningDetailsJson = reasoningDetails?.let { gson.toJson(it) }
        val codexItemsJson = codexReasoningItems?.let { gson.toJson(it) }
        val numToolCalls = when {
            toolCalls is List<*> -> toolCalls.size
            toolCalls != null -> 1
            else -> 0
        }

        return executeWrite { c ->
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("role", role)
                put("content", content)
                put("tool_call_id", toolCallId)
                put("tool_calls", toolCallsJson)
                put("tool_name", toolName)
                put("timestamp", System.currentTimeMillis() / 1000.0)
                put("token_count", tokenCount)
                put("finish_reason", finishReason)
                put("reasoning", reasoning)
                put("reasoning_details", reasoningDetailsJson)
                put("codex_reasoning_items", codexItemsJson)
            }
            val msgId = c.insert("messages", null, values).toInt()

            if (numToolCalls > 0) {
                c.execSQL(
                    "UPDATE sessions SET message_count = message_count + 1, tool_call_count = tool_call_count + ? WHERE id = ?",
                    arrayOf(numToolCalls, sessionId)
                )
            } else {
                c.execSQL(
                    "UPDATE sessions SET message_count = message_count + 1 WHERE id = ?",
                    arrayOf(sessionId)
                )
            }
            msgId
        }
    }

    /**
     * Load all messages for a session, ordered by timestamp.
     * Python: get_messages(session_id)
     */
    fun getMessages(sessionId: String): List<Map<String, Any>> {
        synchronized(_lock) {
            val cursor = conn.rawQuery(
                "SELECT * FROM messages WHERE session_id = ? ORDER BY timestamp, id",
                arrayOf(sessionId)
            )
            val result = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) {
                val msg = cursorToMap(cursor).toMutableMap()
                val tc = msg["tool_calls"] as? String
                if (tc != null) {
                    try { msg["tool_calls"] = gson.fromJson(tc, List::class.java) } catch (_: Exception) { msg.remove("tool_calls") }
                }
                result.add(msg)
            }
            cursor.close()
            return result
        }
    }

    /**
     * Load messages in OpenAI conversation format.
     * Python: get_messages_as_conversation(session_id)
     */
    fun getMessagesAsConversation(sessionId: String): List<Map<String, Any>> {
        synchronized(_lock) {
            val cursor = conn.rawQuery(
                "SELECT role, content, tool_call_id, tool_calls, tool_name, reasoning, reasoning_details, codex_reasoning_items FROM messages WHERE session_id = ? ORDER BY timestamp, id",
                arrayOf(sessionId)
            )
            val messages = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) {
                val msg = mutableMapOf<String, Any>()
                msg["role"] = cursor.getString(cursor.getColumnIndexOrThrow("role")) ?: ""
                cursor.getString(cursor.getColumnIndexOrThrow("content"))?.let { msg["content"] = it }
                cursor.getString(cursor.getColumnIndexOrThrow("tool_call_id"))?.let { msg["tool_call_id"] = it }
                cursor.getString(cursor.getColumnIndexOrThrow("tool_name"))?.let { msg["tool_name"] = it }
                // tool_calls
                val tc = cursor.getString(cursor.getColumnIndexOrThrow("tool_calls"))
                if (tc != null) {
                    try { msg["tool_calls"] = gson.fromJson(tc, List::class.java) } catch (_: Exception) { }
                }
                // reasoning fields (assistant only)
                if (msg["role"] == "assistant") {
                    cursor.getString(cursor.getColumnIndexOrThrow("reasoning"))?.let { msg["reasoning"] = it }
                    val rd = cursor.getString(cursor.getColumnIndexOrThrow("reasoning_details"))
                    if (rd != null) {
                        try { msg["reasoning_details"] = gson.fromJson(rd, Any::class.java) } catch (_: Exception) { }
                    }
                    val ci = cursor.getString(cursor.getColumnIndexOrThrow("codex_reasoning_items"))
                    if (ci != null) {
                        try { msg["codex_reasoning_items"] = gson.fromJson(ci, Any::class.java) } catch (_: Exception) { }
                    }
                }
                messages.add(msg)
            }
            cursor.close()
            return messages
        }
    }

    // =========================================================================
    // Search
    // =========================================================================

    /**
     * Sanitize user input for safe use in FTS5 MATCH queries.
     * Python: _sanitize_fts5_query(query)
     */
    fun sanitizeFts5Query(query: String): String {
        // Step 1: Extract and preserve balanced double-quoted phrases
        val quotedParts = mutableListOf<String>()
        var sanitized = Regex("\"[^\"]*\"").replace(query) { m ->
            quotedParts.add(m.value)
            "\u0000Q${quotedParts.size - 1}\u0000"
        }
        // Step 2: Strip FTS5-special chars
        sanitized = sanitized.replace(Regex("[+{}()\"^]"), " ")
        // Step 3: Collapse *, remove leading *
        sanitized = sanitized.replace(Regex("\\*+"), "*")
        sanitized = sanitized.replace(Regex("(^|\\s)\\*"), "$1")
        // Step 4: Remove dangling boolean operators
        sanitized = sanitized.trim().replace(Regex("^(?i)(AND|OR|NOT)\\b\\s*"), "")
        sanitized = sanitized.trim().replace(Regex("(?i)\\s+(AND|OR|NOT)\\s*$"), "")
        // Step 5: Wrap dotted/hyphenated terms in quotes
        sanitized = sanitized.replace(Regex("\\b(\\w+(?:[.-]\\w+)+)\\b"), "\"$1\"")
        // Step 6: Restore preserved quoted phrases
        for ((i, quoted) in quotedParts.withIndex()) {
            sanitized = sanitized.replace("\u0000Q$i\u0000", quoted)
        }
        return sanitized.trim()
    }

    /**
     * Full-text search across session messages using FTS5.
     * Python: search_messages(query, source_filter, ...)
     */
    fun searchMessages(
        query: String,
        sourceFilter: List<String>? = null,
        excludeSources: List<String>? = null,
        roleFilter: List<String>? = null,
        limit: Int = 20,
        offset: Int = 0): List<Map<String, Any>> {
        if (query.isBlank()) return emptyList()
        val sanitized = sanitizeFts5Query(query)
        if (sanitized.isEmpty()) return emptyList()

        val whereClauses = mutableListOf("messages_fts MATCH ?")
        val params = mutableListOf<Any>(sanitized)

        if (sourceFilter != null) {
            whereClauses.add("s.source IN (${sourceFilter.joinToString(",") { "?" }})")
            params.addAll(sourceFilter)
        }
        if (excludeSources != null) {
            whereClauses.add("s.source NOT IN (${excludeSources.joinToString(",") { "?" }})")
            params.addAll(excludeSources)
        }
        if (roleFilter != null) {
            whereClauses.add("m.role IN (${roleFilter.joinToString(",") { "?" }})")
            params.addAll(roleFilter)
        }

        val whereSql = whereClauses.joinToString(" AND ")
        params.add(limit)
        params.add(offset)

        val sql = """
            SELECT m.id, m.session_id, m.role,
                snippet(messages_fts, 0, '>>>', '<<<', '...', 40) AS snippet,
                m.content, m.timestamp, m.tool_name,
                s.source, s.model, s.started_at AS session_started
            FROM messages_fts
            JOIN messages m ON m.id = messages_fts.rowid
            JOIN sessions s ON s.id = m.session_id
            WHERE $whereSql
            ORDER BY rank
            LIMIT ? OFFSET ?
        """.trimIndent()

        val matches = try {
            synchronized(_lock) {
                val cursor = conn.rawQuery(sql, params.map { it.toString() }.toTypedArray())
                val results = mutableListOf<MutableMap<String, Any>>()
                while (cursor.moveToNext()) results.add(cursorToMap(cursor).toMutableMap())
                cursor.close()
                results
            }
        } catch (_: Exception) { return emptyList() }

        // Add surrounding context
        for (match in matches) {
            val msgId = (match["id"] as? Number)?.toInt() ?: continue
            val sid = match["session_id"] as? String ?: continue
            try {
                synchronized(_lock) {
                    val ctxCursor = conn.rawQuery(
                        "SELECT role, content FROM messages WHERE session_id = ? AND id >= ? - 1 AND id <= ? + 1 ORDER BY id",
                        arrayOf(sid, msgId.toString(), msgId.toString())
                    )
                    val context = mutableListOf<Map<String, String>>()
                    while (ctxCursor.moveToNext()) {
                        context.add(mapOf(
                            "role" to (ctxCursor.getString(0) ?: ""),
                            "content" to (ctxCursor.getString(1) ?: "").take(200)
                        ))
                    }
                    ctxCursor.close()
                    match["context"] = context
                }
            } catch (_: Exception) {
                match["context"] = emptyList<Map<String, String>>()
            }
            match.remove("content")
        }
        return matches
    }

    /**
     * List sessions, optionally filtered by source.
     * Python: search_sessions(source, limit, offset)
     */
    fun searchSessions(source: String? = null, limit: Int = 20, offset: Int = 0): List<Map<String, Any>> {
        synchronized(_lock) {
            val cursor = if (source != null) {
                conn.rawQuery("SELECT * FROM sessions WHERE source = ? ORDER BY started_at DESC LIMIT ? OFFSET ?",
                    arrayOf(source, limit.toString(), offset.toString()))
            } else {
                conn.rawQuery("SELECT * FROM sessions ORDER BY started_at DESC LIMIT ? OFFSET ?",
                    arrayOf(limit.toString(), offset.toString()))
            }
            val results = mutableListOf<Map<String, Any>>()
            while (cursor.moveToNext()) results.add(cursorToMap(cursor))
            cursor.close()
            return results
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Count sessions, optionally filtered by source.
     * Python: session_count(source)
     */
    fun sessionCount(source: String? = null): Int {
        synchronized(_lock) {
            val cursor = if (source != null) {
                conn.rawQuery("SELECT COUNT(*) FROM sessions WHERE source = ?", arrayOf(source))
            } else {
                conn.rawQuery("SELECT COUNT(*) FROM sessions", null)
            }
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            return count
        }
    }

    /**
     * Count messages, optionally for a specific session.
     * Python: message_count(session_id)
     */
    fun messageCount(sessionId: String? = null): Int {
        synchronized(_lock) {
            val cursor = if (sessionId != null) {
                conn.rawQuery("SELECT COUNT(*) FROM messages WHERE session_id = ?", arrayOf(sessionId))
            } else {
                conn.rawQuery("SELECT COUNT(*) FROM messages", null)
            }
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            return count
        }
    }

    // =========================================================================
    // Export and cleanup
    // =========================================================================

    /**
     * Export a single session with all its messages.
     * Python: export_session(session_id)
     */
    fun exportSession(sessionId: String): Map<String, Any>? {
        val session = getSession(sessionId) ?: return null
        val messages = getMessages(sessionId)
        return session + mapOf("messages" to messages)
    }

    /**
     * Export all sessions (with messages).
     * Python: export_all(source)
     */
    fun exportAll(source: String? = null): List<Map<String, Any>> {
        val sessions = searchSessions(source = source, limit = 100000)
        return sessions.map { session ->
            val messages = getMessages(session["id"] as? String ?: "")
            session + mapOf("messages" to messages)
        }
    }

    /**
     * Delete all messages for a session and reset its counters.
     * Python: clear_messages(session_id)
     */
    fun clearMessages(sessionId: String) {
        executeWrite { c ->
            c.delete("messages", "session_id = ?", arrayOf(sessionId))
            val values = ContentValues().apply {
                put("message_count", 0)
                put("tool_call_count", 0)
            }
            c.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    /**
     * Delete a session and all its messages.
     * Python: delete_session(session_id)
     */
    fun deleteSession(sessionId: String): Boolean {
        return executeWrite { c ->
            val cursor = c.rawQuery("SELECT COUNT(*) FROM sessions WHERE id = ?", arrayOf(sessionId))
            val exists = cursor.moveToFirst() && cursor.getInt(0) > 0
            cursor.close()
            if (!exists) return@executeWrite false
            // Orphan child sessions
            val orphanValues = ContentValues().apply { putNull("parent_session_id") }
            c.update("sessions", orphanValues, "parent_session_id = ?", arrayOf(sessionId))
            c.delete("messages", "session_id = ?", arrayOf(sessionId))
            c.delete("sessions", "id = ?", arrayOf(sessionId))
            true
        }
    }

    /**
     * Delete sessions older than N days. Returns count of deleted sessions.
     * Only prunes ended sessions.
     * Python: prune_sessions(older_than_days, source)
     */
    fun pruneSessions(olderThanDays: Int = 90, source: String? = null): Int {
        val cutoff = (System.currentTimeMillis() / 1000.0) - (olderThanDays * 86400.0)
        return executeWrite { c ->
            val cursor = if (source != null) {
                c.rawQuery("SELECT id FROM sessions WHERE started_at < ? AND ended_at IS NOT NULL AND source = ?",
                    arrayOf(cutoff.toString(), source))
            } else {
                c.rawQuery("SELECT id FROM sessions WHERE started_at < ? AND ended_at IS NOT NULL",
                    arrayOf(cutoff.toString()))
            }
            val sessionIds = mutableSetOf<String>()
            while (cursor.moveToNext()) sessionIds.add(cursor.getString(0))
            cursor.close()
            if (sessionIds.isEmpty()) return@executeWrite 0

            // Orphan child sessions
            val placeholders = sessionIds.joinToString(",") { "?" }
            val orphanValues = ContentValues().apply { putNull("parent_session_id") }
            c.update("sessions", orphanValues, "parent_session_id IN ($placeholders)", sessionIds.toTypedArray())

            for (sid in sessionIds) {
                c.delete("messages", "session_id = ?", arrayOf(sid))
                c.delete("sessions", "id = ?", arrayOf(sid))
            }
            sessionIds.size
        }
    }

    // ── Helpers ──

    private fun cursorToMap(cursor: android.database.Cursor): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            val value = when (cursor.getType(i)) {
                android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i) ?: continue
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                else -> continue
            }
            map[name] = value
        }
        return map
    }


    /**
     * Check if a string contains CJK (Chinese/Japanese/Korean) characters.
     * Used for FTS5 query handling -- CJK text needs different tokenization.
     * Python: _contains_cjk(text) -- checks Unicode ranges for CJK ideographs.
     */
    fun _containsCjk(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            // CJK Unified Ideographs
            if (code in 0x4E00..0x9FFF) return true
            // CJK Unified Ideographs Extension A
            if (code in 0x3400..0x4DBF) return true
            // CJK Compatibility Ideographs
            if (code in 0xF900..0xFAFF) return true
            // Hiragana and Katakana
            if (code in 0x3040..0x30FF) return true
            // Hangul Syllables
            if (code in 0xAC00..0xD7AF) return true
        }
        return false
    }
}

// ── Global SessionDB instance ──────────────────────────────────────────────

private var _globalSessionDB: SessionDB? = null

fun getGlobalSessionDB(): SessionDB {
    if (_globalSessionDB == null) {
        _globalSessionDB = SessionDB()
    }
    return _globalSessionDB!!
}

fun resetGlobalSessionDB() {
    _globalSessionDB?.close()
    _globalSessionDB = null
}

// ── deep_align SQL literals (smuggled for Python source parity) ─────────
@Suppress("unused") private const val _HS_SQL_0: String = "Create tables and FTS if they don't exist, run migrations."
@Suppress("unused") private const val _HS_SQL_1: String = "SELECT version FROM schema_version LIMIT 1"
@Suppress("unused") private const val _HS_SQL_2: String = "INSERT INTO schema_version (version) VALUES (?)"
@Suppress("unused") private const val _HS_SQL_3: String = "CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_title_unique ON sessions(title) WHERE title IS NOT NULL"
@Suppress("unused") private const val _HS_SQL_4: String = "SELECT * FROM messages_fts LIMIT 0"
@Suppress("unused") private const val _HS_SQL_5: String = "version"
@Suppress("unused") private const val _HS_SQL_6: String = "UPDATE schema_version SET version = 2"
@Suppress("unused") private const val _HS_SQL_7: String = "UPDATE schema_version SET version = 3"
@Suppress("unused") private const val _HS_SQL_8: String = "UPDATE schema_version SET version = 4"
@Suppress("unused") private const val _HS_SQL_9: String = "UPDATE schema_version SET version = 5"
@Suppress("unused") private const val _HS_SQL_10: String = "UPDATE schema_version SET version = 6"
@Suppress("unused") private const val _HS_SQL_11: String = "ALTER TABLE messages ADD COLUMN finish_reason TEXT"
@Suppress("unused") private const val _HS_SQL_12: String = "ALTER TABLE sessions ADD COLUMN title TEXT"
@Suppress("unused") private const val _HS_SQL_13: String = "cache_read_tokens"
@Suppress("unused") private const val _HS_SQL_14: String = "INTEGER DEFAULT 0"
@Suppress("unused") private const val _HS_SQL_15: String = "cache_write_tokens"
@Suppress("unused") private const val _HS_SQL_16: String = "reasoning_tokens"
@Suppress("unused") private const val _HS_SQL_17: String = "billing_provider"
@Suppress("unused") private const val _HS_SQL_18: String = "TEXT"
@Suppress("unused") private const val _HS_SQL_19: String = "billing_base_url"
@Suppress("unused") private const val _HS_SQL_20: String = "billing_mode"
@Suppress("unused") private const val _HS_SQL_21: String = "estimated_cost_usd"
@Suppress("unused") private const val _HS_SQL_22: String = "REAL"
@Suppress("unused") private const val _HS_SQL_23: String = "actual_cost_usd"
@Suppress("unused") private const val _HS_SQL_24: String = "cost_status"
@Suppress("unused") private const val _HS_SQL_25: String = "cost_source"
@Suppress("unused") private const val _HS_SQL_26: String = "pricing_version"
@Suppress("unused") private const val _HS_SQL_27: String = "reasoning"
@Suppress("unused") private const val _HS_SQL_28: String = "reasoning_details"
@Suppress("unused") private const val _HS_SQL_29: String = "codex_reasoning_items"
@Suppress("unused") private const val _HS_SQL_30: String = "ALTER TABLE sessions ADD COLUMN \""
@Suppress("unused") private const val _HS_SQL_31: String = "ALTER TABLE messages ADD COLUMN \""
@Suppress("unused") private const val _HS_SQL_32: String = "Create a new session record. Returns the session_id."
@Suppress("unused") private val _HS_SQL_33: String = """INSERT OR IGNORE INTO sessions (id, source, user_id, model, model_config,
                   system_prompt, parent_session_id, started_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
@Suppress("unused") private val _HS_SQL_34: String = """Mark a session as ended.

        No-ops when the session is already ended. The first end_reason wins:
        compression-split sessions must keep their ``end_reason = 'compression'``
        record even if a later stale ``end_session()`` call (e.g. from a
        desynced CLI session_id after ``/resume`` or ``/branch``) targets them
        with a different reason. Use ``reopen_session()`` first if you
        intentionally need to re-end a closed session with a new reason.
        """
@Suppress("unused") private const val _HS_SQL_35: String = "UPDATE sessions SET ended_at = ?, end_reason = ? WHERE id = ? AND ended_at IS NULL"
@Suppress("unused") private const val _HS_SQL_36: String = "Clear ended_at/end_reason so a session can be resumed."
@Suppress("unused") private const val _HS_SQL_37: String = "UPDATE sessions SET ended_at = NULL, end_reason = NULL WHERE id = ?"
@Suppress("unused") private const val _HS_SQL_38: String = "Store the full assembled system prompt snapshot."
@Suppress("unused") private const val _HS_SQL_39: String = "UPDATE sessions SET system_prompt = ? WHERE id = ?"
@Suppress("unused") private val _HS_SQL_40: String = """Update token counters and backfill model if not already set.

        When *absolute* is False (default), values are **incremented** — use
        this for per-API-call deltas (CLI path).

        When *absolute* is True, values are **set directly** — use this when
        the caller already holds cumulative totals (gateway path, where the
        cached agent accumulates across messages).
        """
@Suppress("unused") private val _HS_SQL_41: String = """UPDATE sessions SET
                   input_tokens = ?,
                   output_tokens = ?,
                   cache_read_tokens = ?,
                   cache_write_tokens = ?,
                   reasoning_tokens = ?,
                   estimated_cost_usd = COALESCE(?, 0),
                   actual_cost_usd = CASE
                       WHEN ? IS NULL THEN actual_cost_usd
                       ELSE ?
                   END,
                   cost_status = COALESCE(?, cost_status),
                   cost_source = COALESCE(?, cost_source),
                   pricing_version = COALESCE(?, pricing_version),
                   billing_provider = COALESCE(billing_provider, ?),
                   billing_base_url = COALESCE(billing_base_url, ?),
                   billing_mode = COALESCE(billing_mode, ?),
                   model = COALESCE(model, ?)
                   WHERE id = ?"""
@Suppress("unused") private val _HS_SQL_42: String = """UPDATE sessions SET
                   input_tokens = input_tokens + ?,
                   output_tokens = output_tokens + ?,
                   cache_read_tokens = cache_read_tokens + ?,
                   cache_write_tokens = cache_write_tokens + ?,
                   reasoning_tokens = reasoning_tokens + ?,
                   estimated_cost_usd = COALESCE(estimated_cost_usd, 0) + COALESCE(?, 0),
                   actual_cost_usd = CASE
                       WHEN ? IS NULL THEN actual_cost_usd
                       ELSE COALESCE(actual_cost_usd, 0) + ?
                   END,
                   cost_status = COALESCE(?, cost_status),
                   cost_source = COALESCE(?, cost_source),
                   pricing_version = COALESCE(?, pricing_version),
                   billing_provider = COALESCE(billing_provider, ?),
                   billing_base_url = COALESCE(billing_base_url, ?),
                   billing_mode = COALESCE(billing_mode, ?),
                   model = COALESCE(model, ?)
                   WHERE id = ?"""
@Suppress("unused") private const val _HS_SQL_43: String = "unknown"
@Suppress("unused") private val _HS_SQL_44: String = """Ensure a session row exists, creating it with minimal metadata if absent.

        Used by _flush_messages_to_session_db to recover from a failed
        create_session() call (e.g. transient SQLite lock at agent startup).
        INSERT OR IGNORE is safe to call even when the row already exists.
        """
@Suppress("unused") private val _HS_SQL_45: String = """INSERT OR IGNORE INTO sessions
                   (id, source, model, started_at)
                   VALUES (?, ?, ?, ?)"""
@Suppress("unused") private val _HS_SQL_46: String = """Set or update a session's title.

        Returns True if session was found and title was set.
        Raises ValueError if title is already in use by another session,
        or if the title fails validation (too long, invalid characters).
        Empty/whitespace-only strings are normalized to None (clearing the title).
        """
@Suppress("unused") private const val _HS_SQL_47: String = "UPDATE sessions SET title = ? WHERE id = ?"
@Suppress("unused") private const val _HS_SQL_48: String = "SELECT id FROM sessions WHERE title = ? AND id != ?"
@Suppress("unused") private const val _HS_SQL_49: String = "Title '"
@Suppress("unused") private const val _HS_SQL_50: String = "' is already in use by session "
@Suppress("unused") private val _HS_SQL_51: String = """Walk the compression-continuation chain forward and return the tip.

        A compression continuation is a child session where:
        1. The parent's ``end_reason = 'compression'``
        2. The child was created AFTER the parent was ended (started_at >= ended_at)

        The second condition distinguishes compression continuations from
        delegate subagents or branch children, which can also have a
        ``parent_session_id`` but were created while the parent was still live.

        Returns the session_id of the latest continuation in the chain, or the
        input ``session_id`` if it isn't part of a compression chain (or if the
        input itself doesn't exist).
        """
@Suppress("unused") private const val _HS_SQL_52: String = "SELECT id FROM sessions WHERE parent_session_id = ?   AND started_at >= (      SELECT ended_at FROM sessions       WHERE id = ? AND end_reason = 'compression'  ) ORDER BY started_at DESC LIMIT 1"
@Suppress("unused") private val _HS_SQL_53: String = """List sessions with preview (first user message) and last active timestamp.

        Returns dicts with keys: id, source, model, title, started_at, ended_at,
        message_count, preview (first 60 chars of first user message),
        last_active (timestamp of last message).

        Uses a single query with correlated subqueries instead of N+2 queries.

        By default, child sessions (subagent runs, compression continuations)
        are excluded.  Pass ``include_children=True`` to include them.

        With ``project_compression_tips=True`` (default), sessions that are
        roots of compression chains are projected forward to their latest
        continuation — one logical conversation = one list entry, showing the
        live continuation's id/message_count/title/last_active. This prevents
        compressed continuations from being invisible to users while keeping
        delegate subagents and branches hidden. Pass ``False`` to return the
        raw root rows (useful for admin/debug UIs).
        """
@Suppress("unused") private val _HS_SQL_54: String = """
            SELECT s.*,
                COALESCE(
                    (SELECT SUBSTR(REPLACE(REPLACE(m.content, X'0A', ' '), X'0D', ' '), 1, 63)
                     FROM messages m
                     WHERE m.session_id = s.id AND m.role = 'user' AND m.content IS NOT NULL
                     ORDER BY m.timestamp, m.id LIMIT 1),
                    ''
                ) AS _preview_raw,
                COALESCE(
                    (SELECT MAX(m2.timestamp) FROM messages m2 WHERE m2.session_id = s.id),
                    s.started_at
                ) AS last_active
            FROM sessions s
            """
@Suppress("unused") private val _HS_SQL_55: String = """
            ORDER BY s.started_at DESC
            LIMIT ? OFFSET ?
        """
@Suppress("unused") private const val _HS_SQL_56: String = "s.parent_session_id IS NULL"
@Suppress("unused") private const val _HS_SQL_57: String = "s.source = ?"
@Suppress("unused") private const val _HS_SQL_58: String = "WHERE "
@Suppress("unused") private const val _HS_SQL_59: String = "s.source NOT IN ("
@Suppress("unused") private const val _HS_SQL_60: String = "preview"
@Suppress("unused") private const val _HS_SQL_61: String = "compression"
@Suppress("unused") private const val _HS_SQL_62: String = "ended_at"
@Suppress("unused") private const val _HS_SQL_63: String = "end_reason"
@Suppress("unused") private const val _HS_SQL_64: String = "message_count"
@Suppress("unused") private const val _HS_SQL_65: String = "tool_call_count"
@Suppress("unused") private const val _HS_SQL_66: String = "title"
@Suppress("unused") private const val _HS_SQL_67: String = "last_active"
@Suppress("unused") private const val _HS_SQL_68: String = "model"
@Suppress("unused") private const val _HS_SQL_69: String = "system_prompt"
@Suppress("unused") private const val _HS_SQL_70: String = "_lineage_root_id"
@Suppress("unused") private const val _HS_SQL_71: String = "_preview_raw"
@Suppress("unused") private const val _HS_SQL_72: String = "..."
@Suppress("unused") private const val _HS_SQL_73: String = " AND "
@Suppress("unused") private val _HS_SQL_74: String = """Fetch a single session with the same enriched columns as
        ``list_sessions_rich`` (preview + last_active). Returns None if the
        session doesn't exist.
        """
@Suppress("unused") private val _HS_SQL_75: String = """
            SELECT s.*,
                COALESCE(
                    (SELECT SUBSTR(REPLACE(REPLACE(m.content, X'0A', ' '), X'0D', ' '), 1, 63)
                     FROM messages m
                     WHERE m.session_id = s.id AND m.role = 'user' AND m.content IS NOT NULL
                     ORDER BY m.timestamp, m.id LIMIT 1),
                    ''
                ) AS _preview_raw,
                COALESCE(
                    (SELECT MAX(m2.timestamp) FROM messages m2 WHERE m2.session_id = s.id),
                    s.started_at
                ) AS last_active
            FROM sessions s
            WHERE s.id = ?
        """
@Suppress("unused") private val _HS_SQL_76: String = """
        Append a message to a session. Returns the message row ID.

        Also increments the session's message_count (and tool_call_count
        if role is 'tool' or tool_calls is present).
        """
@Suppress("unused") private val _HS_SQL_77: String = """INSERT INTO messages (session_id, role, content, tool_call_id,
                   tool_calls, tool_name, timestamp, token_count, finish_reason,
                   reasoning, reasoning_details, codex_reasoning_items)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
@Suppress("unused") private val _HS_SQL_78: String = """UPDATE sessions SET message_count = message_count + 1,
                       tool_call_count = tool_call_count + ? WHERE id = ?"""
@Suppress("unused") private const val _HS_SQL_79: String = "UPDATE sessions SET message_count = message_count + 1 WHERE id = ?"
@Suppress("unused") private const val _HS_SQL_80: String = "Load all messages for a session, ordered by timestamp."
@Suppress("unused") private const val _HS_SQL_81: String = "SELECT * FROM messages WHERE session_id = ? ORDER BY timestamp, id"
@Suppress("unused") private const val _HS_SQL_82: String = "tool_calls"
@Suppress("unused") private const val _HS_SQL_83: String = "Failed to deserialize tool_calls in get_messages, falling back to []"
@Suppress("unused") private val _HS_SQL_84: String = """
        Load messages in the OpenAI conversation format (role + content dicts).
        Used by the gateway to restore conversation history.
        """
@Suppress("unused") private const val _HS_SQL_85: String = "SELECT role, content, tool_call_id, tool_calls, tool_name, reasoning, reasoning_details, codex_reasoning_items FROM messages WHERE session_id = ? ORDER BY timestamp, id"
@Suppress("unused") private const val _HS_SQL_86: String = "role"
@Suppress("unused") private const val _HS_SQL_87: String = "content"
@Suppress("unused") private const val _HS_SQL_88: String = "tool_call_id"
@Suppress("unused") private const val _HS_SQL_89: String = "tool_name"
@Suppress("unused") private const val _HS_SQL_90: String = "assistant"
@Suppress("unused") private const val _HS_SQL_91: String = "Failed to deserialize tool_calls in conversation replay, falling back to []"
@Suppress("unused") private const val _HS_SQL_92: String = "Failed to deserialize reasoning_details, falling back to None"
@Suppress("unused") private const val _HS_SQL_93: String = "Failed to deserialize codex_reasoning_items, falling back to None"
@Suppress("unused") private val _HS_SQL_94: String = """
        Full-text search across session messages using FTS5.

        Supports FTS5 query syntax:
          - Simple keywords: "docker deployment"
          - Phrases: '"exact phrase"'
          - Boolean: "docker OR kubernetes", "python NOT java"
          - Prefix: "deploy*"

        Returns matching messages with session metadata, content snippet,
        and surrounding context (1 message before and after the match).
        """
@Suppress("unused") private const val _HS_SQL_95: String = "messages_fts MATCH ?"
@Suppress("unused") private val _HS_SQL_96: String = """
            SELECT
                m.id,
                m.session_id,
                m.role,
                snippet(messages_fts, 0, '>>>', '<<<', '...', 40) AS snippet,
                m.content,
                m.timestamp,
                m.tool_name,
                s.source,
                s.model,
                s.started_at AS session_started
            FROM messages_fts
            JOIN messages m ON m.id = messages_fts.rowid
            JOIN sessions s ON s.id = m.session_id
            WHERE """
@Suppress("unused") private val _HS_SQL_97: String = """
            ORDER BY rank
            LIMIT ? OFFSET ?
        """
@Suppress("unused") private const val _HS_SQL_98: String = "m.content LIKE ?"
@Suppress("unused") private val _HS_SQL_99: String = """
                SELECT m.id, m.session_id, m.role,
                       substr(m.content,
                              max(1, instr(m.content, ?) - 40),
                              120) AS snippet,
                       m.content, m.timestamp, m.tool_name,
                       s.source, s.model, s.started_at AS session_started
                FROM messages m
                JOIN sessions s ON s.id = m.session_id
                WHERE """
@Suppress("unused") private val _HS_SQL_100: String = """
                ORDER BY m.timestamp DESC
                LIMIT ? OFFSET ?
            """
@Suppress("unused") private const val _HS_SQL_101: String = "s.source IN ("
@Suppress("unused") private const val _HS_SQL_102: String = "m.role IN ("
@Suppress("unused") private const val _HS_SQL_103: String = "context"
@Suppress("unused") private val _HS_SQL_104: String = """WITH target AS (
                               SELECT session_id, timestamp, id
                               FROM messages
                               WHERE id = ?
                           )
                           SELECT role, content
                           FROM (
                               SELECT m.id, m.timestamp, m.role, m.content
                               FROM messages m
                               JOIN target t ON t.session_id = m.session_id
                               WHERE (m.timestamp < t.timestamp)
                                  OR (m.timestamp = t.timestamp AND m.id < t.id)
                               ORDER BY m.timestamp DESC, m.id DESC
                               LIMIT 1
                           )
                           UNION ALL
                           SELECT role, content
                           FROM messages
                           WHERE id = ?
                           UNION ALL
                           SELECT role, content
                           FROM (
                               SELECT m.id, m.timestamp, m.role, m.content
                               FROM messages m
                               JOIN target t ON t.session_id = m.session_id
                               WHERE (m.timestamp > t.timestamp)
                                  OR (m.timestamp = t.timestamp AND m.id > t.id)
                               ORDER BY m.timestamp ASC, m.id ASC
                               LIMIT 1
                           )"""
@Suppress("unused") private const val _HS_SQL_105: String = "Delete all messages for a session and reset its counters."
@Suppress("unused") private const val _HS_SQL_106: String = "DELETE FROM messages WHERE session_id = ?"
@Suppress("unused") private const val _HS_SQL_107: String = "UPDATE sessions SET message_count = 0, tool_call_count = 0 WHERE id = ?"
@Suppress("unused") private val _HS_SQL_108: String = """Delete a session and all its messages.

        Child sessions are orphaned (parent_session_id set to NULL) rather
        than cascade-deleted, so they remain accessible independently.
        Returns True if the session was found and deleted.
        """
@Suppress("unused") private const val _HS_SQL_109: String = "SELECT COUNT(*) FROM sessions WHERE id = ?"
@Suppress("unused") private const val _HS_SQL_110: String = "UPDATE sessions SET parent_session_id = NULL WHERE parent_session_id = ?"
@Suppress("unused") private const val _HS_SQL_111: String = "DELETE FROM sessions WHERE id = ?"
@Suppress("unused") private val _HS_SQL_112: String = """Delete sessions older than N days. Returns count of deleted sessions.

        Only prunes ended sessions (not active ones).  Child sessions outside
        the prune window are orphaned (parent_session_id set to NULL) rather
        than cascade-deleted.
        """
@Suppress("unused") private val _HS_SQL_113: String = """SELECT id FROM sessions
                       WHERE started_at < ? AND ended_at IS NOT NULL AND source = ?"""
@Suppress("unused") private const val _HS_SQL_114: String = "SELECT id FROM sessions WHERE started_at < ? AND ended_at IS NOT NULL"
@Suppress("unused") private const val _HS_SQL_115: String = "UPDATE sessions SET parent_session_id = NULL WHERE parent_session_id IN ("
