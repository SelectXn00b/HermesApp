/** 1:1 对齐 hermes/plugins/memory/holographic/store.py */
package com.xiaomo.hermes.hermes.plugins.memory.holographic

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.xiaomo.hermes.hermes.getHermesHome
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLite-backed fact store with entity resolution and trust scoring.
 * Single-user Hermes memory store plugin.
 */

private val _SCHEMA = """
CREATE TABLE IF NOT EXISTS facts (
    fact_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    content         TEXT NOT NULL UNIQUE,
    category        TEXT DEFAULT 'general',
    tags            TEXT DEFAULT '',
    trust_score     REAL DEFAULT 0.5,
    retrieval_count INTEGER DEFAULT 0,
    helpful_count   INTEGER DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hrr_vector      BLOB
);

CREATE TABLE IF NOT EXISTS entities (
    entity_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    entity_type TEXT DEFAULT 'unknown',
    aliases     TEXT DEFAULT '',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fact_entities (
    fact_id   INTEGER REFERENCES facts(fact_id),
    entity_id INTEGER REFERENCES entities(entity_id),
    PRIMARY KEY (fact_id, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_facts_trust    ON facts(trust_score DESC);
CREATE INDEX IF NOT EXISTS idx_facts_category ON facts(category);
CREATE INDEX IF NOT EXISTS idx_entities_name  ON entities(name);

CREATE VIRTUAL TABLE IF NOT EXISTS facts_fts
    USING fts5(content, tags, content=facts, content_rowid=fact_id);

CREATE TRIGGER IF NOT EXISTS facts_ai AFTER INSERT ON facts BEGIN
    INSERT INTO facts_fts(rowid, content, tags)
        VALUES (new.fact_id, new.content, new.tags);
END;

CREATE TRIGGER IF NOT EXISTS facts_ad AFTER DELETE ON facts BEGIN
    INSERT INTO facts_fts(facts_fts, rowid, content, tags)
        VALUES ('delete', old.fact_id, old.content, old.tags);
END;

CREATE TRIGGER IF NOT EXISTS facts_au AFTER UPDATE ON facts BEGIN
    INSERT INTO facts_fts(facts_fts, rowid, content, tags)
        VALUES ('delete', old.fact_id, old.content, old.tags);
    INSERT INTO facts_fts(rowid, content, tags)
        VALUES (new.fact_id, new.content, new.tags);
END;

CREATE TABLE IF NOT EXISTS memory_banks (
    bank_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_name  TEXT NOT NULL UNIQUE,
    vector     BLOB NOT NULL,
    dim        INTEGER NOT NULL,
    fact_count INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
""".trimIndent()

// Trust adjustment constants
private const val _HELPFUL_DELTA = 0.05f
private const val _UNHELPFUL_DELTA = -0.10f
private const val _TRUST_MIN = 0.0f
private const val _TRUST_MAX = 1.0f

// Entity extraction patterns
private val _RE_CAPITALIZED = Regex("""\b([A-Z][a-z]+(?:\s+[A-Z][a-z]+)+)\b""")
private val _RE_DOUBLE_QUOTE = Regex(""""([^"]+)"""")
private val _RE_SINGLE_QUOTE = Regex("""'([^']+)'""")
private val _RE_AKA = Regex(
    """(\w+(?:\s+\w+)*)\s+(?:aka|also known as)\s+(\w+(?:\s+\w+)*)""",
    RegexOption.IGNORE_CASE
)

private fun _clampTrust(value: Float): Float {
    return value.coerceIn(_TRUST_MIN, _TRUST_MAX)
}

class MemoryStore(
    dbPath: String? = null,
    val defaultTrust: Float = 0.5f,
    var hrrDim: Int = 1024
) : Closeable {
    val dbPath: File
    val _conn: SQLiteDatabase
    private val _lock = ReentrantLock()
    private val _hrrAvailable: Boolean = Holographic.hasNumpy

    init {
        val path = if (dbPath != null) {
            File(dbPath)
        } else {
            File(getHermesHome(), "memory_store.db")
        }
        this.dbPath = path
        path.parentFile?.mkdirs()
        _conn = SQLiteDatabase.openOrCreateDatabase(path, null)
        _initDb()
    }

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    private fun _initDb() {
        _conn.execSQL("PRAGMA journal_mode=WAL")
        _SCHEMA.split(";").filter { it.isNotBlank() }.forEach { stmt ->
            try {
                _conn.execSQL(stmt.trim() + ";")
            } catch (e: Exception) {
                // Ignore errors for IF NOT EXISTS statements
            }
        }
        // Migrate: add hrr_vector column if missing
        val cursor = _conn.rawQuery("PRAGMA table_info(facts)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        if ("hrr_vector" !in columns) {
            _conn.execSQL("ALTER TABLE facts ADD COLUMN hrr_vector BLOB")
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun addFact(
        content: String,
        category: String = "general",
        tags: String = ""
    ): Int {
        _lock.withLock {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("content must not be empty")

            return try {
                _conn.execSQL(
                    "INSERT INTO facts (content, category, tags, trust_score) VALUES (?, ?, ?, ?)",
                    arrayOf(trimmed, category, tags, _clampTrust(defaultTrust))
                )
                val cursor = _conn.rawQuery("SELECT last_insert_rowid()", null)
                cursor.moveToFirst()
                val factId = cursor.getInt(0)
                cursor.close()

                // Entity extraction and linking
                for (name in _extractEntities(trimmed)) {
                    val entityId = _resolveEntity(name)
                    _linkFactEntity(factId, entityId)
                }

                // Compute HRR vector after entity linking
                _computeHrrVector(factId, trimmed)
                _rebuildBank(category)

                factId
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Duplicate content — return existing id
                val cursor = _conn.rawQuery(
                    "SELECT fact_id FROM facts WHERE content = ?", arrayOf(trimmed)
                )
                cursor.moveToFirst()
                val factId = cursor.getInt(0)
                cursor.close()
                factId
            }
        }
    }

    fun searchFacts(
        query: String,
        category: String? = null,
        minTrust: Float = 0.3f,
        limit: Int = 10
    ): List<Map<String, Any?>> {
        _lock.withLock {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return emptyList()

            val params = mutableListOf<String>(trimmed, minTrust.toString())
            var categoryClause = ""
            if (category != null) {
                categoryClause = "AND f.category = ?"
                params.add(category)
            }
            params.add(limit.toString())

            val sql = """
                SELECT f.fact_id, f.content, f.category, f.tags,
                       f.trust_score, f.retrieval_count, f.helpful_count,
                       f.created_at, f.updated_at
                FROM facts f
                JOIN facts_fts fts ON fts.rowid = f.fact_id
                WHERE facts_fts MATCH ?
                  AND f.trust_score >= ?
                  $categoryClause
                ORDER BY fts.rank, f.trust_score DESC
                LIMIT ?
            """.trimIndent()

            val cursor = _conn.rawQuery(sql, params.toTypedArray())
            val results = mutableListOf<Map<String, Any?>>()
            while (cursor.moveToNext()) {
                results.add(_cursorToDict(cursor))
            }
            cursor.close()

            if (results.isNotEmpty()) {
                val ids = results.map { it["fact_id"].toString() }
                val placeholders = ids.joinToString(",") { "?" }
                _conn.execSQL(
                    "UPDATE facts SET retrieval_count = retrieval_count + 1 WHERE fact_id IN ($placeholders)",
                    ids.toTypedArray()
                )
            }

            return results
        }
    }

    fun updateFact(
        factId: Int,
        content: String? = null,
        trustDelta: Float? = null,
        tags: String? = null,
        category: String? = null
    ): Boolean {
        _lock.withLock {
            val cursor = _conn.rawQuery(
                "SELECT fact_id, trust_score FROM facts WHERE fact_id = ?",
                arrayOf(factId.toString())
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                return false
            }
            val oldTrust = cursor.getFloat(cursor.getColumnIndexOrThrow("trust_score"))
            cursor.close()

            val assignments = mutableListOf("updated_at = CURRENT_TIMESTAMP")
            val params = mutableListOf<Any>()

            if (content != null) {
                assignments.add("content = ?")
                params.add(content.trim())
            }
            if (tags != null) {
                assignments.add("tags = ?")
                params.add(tags)
            }
            if (category != null) {
                assignments.add("category = ?")
                params.add(category)
            }
            if (trustDelta != null) {
                val newTrust = _clampTrust(oldTrust + trustDelta)
                assignments.add("trust_score = ?")
                params.add(newTrust)
            }

            params.add(factId)
            _conn.execSQL(
                "UPDATE facts SET ${assignments.joinToString(", ")} WHERE fact_id = ?",
                params.map { it.toString() }.toTypedArray()
            )

            // If content changed, re-extract entities
            if (content != null) {
                _conn.execSQL("DELETE FROM fact_entities WHERE fact_id = ?", arrayOf(factId.toString()))
                for (name in _extractEntities(content)) {
                    val entityId = _resolveEntity(name)
                    _linkFactEntity(factId, entityId)
                }
                _computeHrrVector(factId, content)
            }

            // Rebuild bank for relevant category
            val catForBank = category ?: run {
                val c = _conn.rawQuery(
                    "SELECT category FROM facts WHERE fact_id = ?", arrayOf(factId.toString())
                )
                c.moveToFirst()
                val cat = c.getString(0)
                c.close()
                cat
            }
            _rebuildBank(catForBank)

            return true
        }
    }

    fun removeFact(factId: Int): Boolean {
        _lock.withLock {
            val cursor = _conn.rawQuery(
                "SELECT fact_id, category FROM facts WHERE fact_id = ?",
                arrayOf(factId.toString())
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                return false
            }
            val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
            cursor.close()

            _conn.execSQL("DELETE FROM fact_entities WHERE fact_id = ?", arrayOf(factId.toString()))
            _conn.execSQL("DELETE FROM facts WHERE fact_id = ?", arrayOf(factId.toString()))
            _rebuildBank(category)
            return true
        }
    }

    fun listFacts(
        category: String? = null,
        minTrust: Float = 0.0f,
        limit: Int = 50
    ): List<Map<String, Any?>> {
        _lock.withLock {
            val params = mutableListOf(minTrust.toString())
            var categoryClause = ""
            if (category != null) {
                categoryClause = "AND category = ?"
                params.add(category)
            }
            params.add(limit.toString())

            val sql = """
                SELECT fact_id, content, category, tags, trust_score,
                       retrieval_count, helpful_count, created_at, updated_at
                FROM facts
                WHERE trust_score >= ?
                  $categoryClause
                ORDER BY trust_score DESC
                LIMIT ?
            """.trimIndent()

            val cursor = _conn.rawQuery(sql, params.toTypedArray())
            val results = mutableListOf<Map<String, Any?>>()
            while (cursor.moveToNext()) {
                results.add(_cursorToDict(cursor))
            }
            cursor.close()
            return results
        }
    }

    fun recordFeedback(factId: Int, helpful: Boolean): Map<String, Any?> {
        _lock.withLock {
            val cursor = _conn.rawQuery(
                "SELECT fact_id, trust_score, helpful_count FROM facts WHERE fact_id = ?",
                arrayOf(factId.toString())
            )
            if (!cursor.moveToFirst()) {
                cursor.close()
                throw NoSuchElementException("fact_id $factId not found")
            }
            val oldTrust = cursor.getFloat(cursor.getColumnIndexOrThrow("trust_score"))
            val oldHelpful = cursor.getInt(cursor.getColumnIndexOrThrow("helpful_count"))
            cursor.close()

            val delta = if (helpful) _HELPFUL_DELTA else _UNHELPFUL_DELTA
            val newTrust = _clampTrust(oldTrust + delta)
            val helpfulIncrement = if (helpful) 1 else 0

            _conn.execSQL(
                """
                UPDATE facts
                SET trust_score = ?,
                    helpful_count = helpful_count + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE fact_id = ?
                """.trimIndent(),
                arrayOf(newTrust.toString(), helpfulIncrement.toString(), factId.toString())
            )

            return mapOf(
                "fact_id" to factId,
                "old_trust" to oldTrust,
                "new_trust" to newTrust,
                "helpful_count" to oldHelpful + helpfulIncrement
            )
        }
    }

    // ------------------------------------------------------------------
    // Entity helpers
    // ------------------------------------------------------------------

    private fun _extractEntities(text: String): List<String> {
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<String>()

        fun add(name: String) {
            val stripped = name.trim()
            if (stripped.isNotEmpty() && stripped.lowercase() !in seen) {
                seen.add(stripped.lowercase())
                candidates.add(stripped)
            }
        }

        _RE_CAPITALIZED.findAll(text).forEach { add(it.groupValues[1]) }
        _RE_DOUBLE_QUOTE.findAll(text).forEach { add(it.groupValues[1]) }
        _RE_SINGLE_QUOTE.findAll(text).forEach { add(it.groupValues[1]) }
        _RE_AKA.findAll(text).forEach {
            add(it.groupValues[1])
            add(it.groupValues[2])
        }

        return candidates
    }

    private fun _resolveEntity(name: String): Int {
        // Exact name match
        val cursor = _conn.rawQuery(
            "SELECT entity_id FROM entities WHERE name LIKE ?", arrayOf(name)
        )
        if (cursor.moveToFirst()) {
            val id = cursor.getInt(0)
            cursor.close()
            return id
        }
        cursor.close()

        // Search aliases
        val aliasCursor = _conn.rawQuery(
            "SELECT entity_id FROM entities WHERE ',' || aliases || ',' LIKE '%,' || ? || ',%'",
            arrayOf(name)
        )
        if (aliasCursor.moveToFirst()) {
            val id = aliasCursor.getInt(0)
            aliasCursor.close()
            return id
        }
        aliasCursor.close()

        // Create new entity
        _conn.execSQL("INSERT INTO entities (name) VALUES (?)", arrayOf(name))
        val idCursor = _conn.rawQuery("SELECT last_insert_rowid()", null)
        idCursor.moveToFirst()
        val id = idCursor.getInt(0)
        idCursor.close()
        return id
    }

    private fun _linkFactEntity(factId: Int, entityId: Int) {
        try {
            _conn.execSQL(
                "INSERT OR IGNORE INTO fact_entities (fact_id, entity_id) VALUES (?, ?)",
                arrayOf(factId.toString(), entityId.toString())
            )
        } catch (_: Exception) {
            // Ignore duplicates
        }
    }

    private fun _computeHrrVector(factId: Int, content: String) {
        _lock.withLock {
            if (!_hrrAvailable) return

            val cursor = _conn.rawQuery(
                """
                SELECT e.name FROM entities e
                JOIN fact_entities fe ON fe.entity_id = e.entity_id
                WHERE fe.fact_id = ?
                """.trimIndent(),
                arrayOf(factId.toString())
            )
            val entities = mutableListOf<String>()
            while (cursor.moveToNext()) {
                entities.add(cursor.getString(0))
            }
            cursor.close()

            val vector = Holographic.encodeFact(content, entities, hrrDim)
            _conn.execSQL(
                "UPDATE facts SET hrr_vector = ? WHERE fact_id = ?",
                arrayOf(Holographic.phasesToBytes(vector), factId)
            )
        }
    }

    private fun _rebuildBank(category: String) {
        _lock.withLock {
            if (!_hrrAvailable) return

            val bankName = "cat:$category"
            val cursor = _conn.rawQuery(
                "SELECT hrr_vector FROM facts WHERE category = ? AND hrr_vector IS NOT NULL",
                arrayOf(category)
            )
            val vectors = mutableListOf<FloatArray>()
            while (cursor.moveToNext()) {
                val blob = cursor.getBlob(0)
                vectors.add(Holographic.bytesToPhases(blob))
            }
            cursor.close()

            if (vectors.isEmpty()) {
                _conn.execSQL("DELETE FROM memory_banks WHERE bank_name = ?", arrayOf(bankName))
                return
            }

            val bankVector = Holographic.bundle(*vectors.toTypedArray())
            val factCount = vectors.size

            Holographic.snrEstimate(hrrDim, factCount)

            _conn.execSQL(
                """
                INSERT INTO memory_banks (bank_name, vector, dim, fact_count, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(bank_name) DO UPDATE SET
                    vector = excluded.vector,
                    dim = excluded.dim,
                    fact_count = excluded.fact_count,
                    updated_at = excluded.updated_at
                """.trimIndent(),
                arrayOf(bankName, Holographic.phasesToBytes(bankVector), hrrDim, factCount)
            )
        }
    }

    fun rebuildAllVectors(dim: Int? = null): Int {
        _lock.withLock {
            if (!_hrrAvailable) return 0
            if (dim != null) hrrDim = dim

            val cursor = _conn.rawQuery("SELECT fact_id, content, category FROM facts", null)
            val categories = mutableSetOf<String>()
            val count = cursor.count

            while (cursor.moveToNext()) {
                val factId = cursor.getInt(cursor.getColumnIndexOrThrow("fact_id"))
                val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
                val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                _computeHrrVector(factId, content)
                categories.add(category)
            }
            cursor.close()

            for (category in categories) {
                _rebuildBank(category)
            }

            return count
        }
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    private fun _cursorToDict(cursor: android.database.Cursor): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            map[name] = when (cursor.getType(i)) {
                android.database.Cursor.FIELD_TYPE_NULL -> null
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                else -> cursor.getString(i)
            }
        }
        return map
    }

    override fun close() {
        _conn.close()
    }


    fun _rowToDict(row: Any?): Map<String, Any?> {
        if (row == null) return emptyMap()
        // On Android, rows come as Cursor-based maps already converted via _cursorToDict.
        // This is a compatibility shim for any raw row object passed in.
        return when (row) {
            is Map<*, *> -> row.entries.associate { (k, v) -> k.toString() to v }
            else -> emptyMap()
        }
    }
}

@Suppress("unused")
private val _SQL_LINK_FACT_ENTITY: String = """
            INSERT OR IGNORE INTO fact_entities (fact_id, entity_id)
            VALUES (?, ?)
            """

@Suppress("unused")
private val _SQL_INSERT_FACT: String = """
                    INSERT INTO facts (content, category, tags, trust_score)
                    VALUES (?, ?, ?, ?)
                    """

@Suppress("unused")
private val _SQL_UPDATE_FEEDBACK: String = """
                UPDATE facts
                SET trust_score    = ?,
                    helpful_count  = helpful_count + ?,
                    updated_at     = CURRENT_TIMESTAMP
                WHERE fact_id = ?
                """
