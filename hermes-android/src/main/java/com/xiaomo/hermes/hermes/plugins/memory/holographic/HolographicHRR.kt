/** 1:1 对齐 hermes/plugins/memory/holographic/holographic.py
 *
 * Holographic Reduced Representations (HRR) with phase encoding.
 *
 * HRRs are a vector symbolic architecture for encoding compositional structure
 * into fixed-width distributed representations. This module uses *phase vectors*:
 * each concept is a vector of angles in [0, 2pi). The algebraic operations are:
 *
 *   bind   - circular convolution (phase addition)  - associates two concepts
 *   unbind - circular correlation (phase subtraction) - retrieves a bound value
 *   bundle - superposition (circular mean)           - merges multiple concepts
 *
 * Phase encoding is numerically stable, avoids the magnitude collapse of
 * traditional complex-number HRRs, and maps cleanly to cosine similarity.
 *
 * Atoms are generated deterministically from SHA-256 so representations are
 * identical across processes, machines, and language versions.
 */
package com.xiaomo.hermes.hermes.plugins.memory.holographic

import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.*

private const val TWO_PI = 2.0f * Math.PI.toFloat()

/**
 * Kotlin port of holographic.py — the HRR (Holographic Reduced Representation) engine.
 *
 * In Python, this is imported as `from . import holographic as hrr` and used as `hrr.encode_atom(...)`.
 * In Kotlin, this becomes `Holographic.encodeAtom(...)`.
 */
object Holographic {

    /**
     * On Android we always have math available, so this is always true.
     * In the Python version, this gates on numpy availability.
     */
    val hasNumpy: Boolean = true

    /**
     * Deterministic phase vector via SHA-256 counter blocks.
     *
     * Algorithm:
     * - Generate enough SHA-256 blocks by hashing "$word:$i" for i=0,1,2,...
     * - Concatenate digests, interpret as uint16 values via little-endian unpacking
     * - Scale to [0, 2pi): phases = values * (2pi / 65536)
     * - Truncate to dim elements
     * - Returns FloatArray of shape (dim)
     */
    fun encodeAtom(word: String, dim: Int = 1024): FloatArray {
        val valuesPerBlock = 16 // Each SHA-256 digest is 32 bytes = 16 uint16 values
        val blocksNeeded = ceil(dim.toDouble() / valuesPerBlock).toInt()

        val uint16Values = mutableListOf<Int>()
        for (i in 0 until blocksNeeded) {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest("$word:$i".toByteArray(Charsets.UTF_8))
            val buf = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until 16) {
                uint16Values.add(buf.getShort().toInt() and 0xFFFF)
            }
        }

        val phases = FloatArray(dim)
        for (i in 0 until dim) {
            phases[i] = uint16Values[i].toFloat() * (TWO_PI / 65536.0f)
        }
        return phases
    }

    /**
     * Circular convolution = element-wise phase addition.
     * Binding associates two concepts into a single composite vector.
     */
    fun bind(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(a.size)
        for (i in a.indices) {
            result[i] = (a[i] + b[i]) % TWO_PI
        }
        return result
    }

    /**
     * Circular correlation = element-wise phase subtraction.
     * Unbinding retrieves the value associated with a key from a memory vector.
     * unbind(bind(a, b), a) ~ b
     */
    fun unbind(memory: FloatArray, key: FloatArray): FloatArray {
        val result = FloatArray(memory.size)
        for (i in memory.indices) {
            result[i] = ((memory[i] - key[i]) % TWO_PI + TWO_PI) % TWO_PI
        }
        return result
    }

    /**
     * Superposition via circular mean of complex exponentials.
     * Bundling merges multiple vectors into one that is similar to each input.
     */
    fun bundle(vararg vectors: FloatArray): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val dim = vectors[0].size
        val realSum = FloatArray(dim)
        val imagSum = FloatArray(dim)

        for (vec in vectors) {
            for (i in 0 until dim) {
                realSum[i] += cos(vec[i].toDouble()).toFloat()
                imagSum[i] += sin(vec[i].toDouble()).toFloat()
            }
        }

        val result = FloatArray(dim)
        for (i in 0 until dim) {
            val angle = atan2(imagSum[i].toDouble(), realSum[i].toDouble()).toFloat()
            result[i] = ((angle % TWO_PI) + TWO_PI) % TWO_PI
        }
        return result
    }

    /**
     * Phase cosine similarity. Range [-1, 1].
     * Returns 1.0 for identical vectors, near 0.0 for random (unrelated) vectors.
     */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        for (i in a.indices) {
            sum += cos((a[i] - b[i]).toDouble())
        }
        return (sum / a.size).toFloat()
    }

    /**
     * Bag-of-words: bundle of atom vectors for each token.
     * Tokenizes by lowercasing, splitting on whitespace, and stripping punctuation.
     */
    fun encodeText(text: String, dim: Int = 1024): FloatArray {
        val tokens = text.lowercase().split(Regex("\\s+"))
            .map { it.trim(*".,!?;:\"'()[]{}".toCharArray()) }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return encodeAtom("__hrr_empty__", dim)
        }

        val atomVectors = tokens.map { encodeAtom(it, dim) }
        return bundle(*atomVectors.toTypedArray())
    }

    /**
     * Structured encoding: content bound to ROLE_CONTENT, each entity bound to ROLE_ENTITY, all bundled.
     *
     * Enables algebraic extraction:
     *   unbind(fact, bind(entity, ROLE_ENTITY)) ~ content_vector
     */
    fun encodeFact(content: String, entities: List<String>, dim: Int = 1024): FloatArray {
        val roleContent = encodeAtom("__hrr_role_content__", dim)
        val roleEntity = encodeAtom("__hrr_role_entity__", dim)

        val components = mutableListOf<FloatArray>()
        components.add(bind(encodeText(content, dim), roleContent))

        for (entity in entities) {
            components.add(bind(encodeAtom(entity.lowercase(), dim), roleEntity))
        }

        return bundle(*components.toTypedArray())
    }

    /**
     * Serialize phase vector to bytes. float32 -> 4 bytes per element.
     * At dim=1024 this produces 4 KB.
     */
    fun phasesToBytes(phases: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(phases.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (phase in phases) {
            buffer.putFloat(phase)
        }
        return buffer.array()
    }

    /**
     * Deserialize bytes back to phase vector. Inverse of phasesToBytes.
     */
    fun bytesToPhases(data: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val dim = data.size / 4
        val phases = FloatArray(dim)
        for (i in 0 until dim) {
            phases[i] = buffer.getFloat()
        }
        return phases
    }

    /**
     * Signal-to-noise ratio estimate for holographic storage.
     * SNR = sqrt(dim / n_items) when n_items > 0, else infinity.
     */
    fun snrEstimate(dim: Int, nItems: Int): Float {
        if (nItems <= 0) return Float.MAX_VALUE
        return sqrt(dim.toFloat() / nItems.toFloat())
    }
}

// ---------------------------------------------------------------------------
// SQLiteDatabase extension functions used by Retrieval.kt
// ---------------------------------------------------------------------------

/**
 * Execute a SQL query that returns a single BLOB column from the first row.
 * Returns null if no rows match.
 */
fun SQLiteDatabase.query(sql: String, args: Array<String>): ByteArray? {
    val cursor = rawQuery(sql, args)
    return try {
        if (cursor.moveToFirst() && cursor.columnCount > 0) {
            cursor.getBlob(0)
        } else {
            null
        }
    } finally {
        cursor.close()
    }
}

/**
 * Query entity names associated with a given fact_id.
 * Returns a list of entity name strings.
 */
fun SQLiteDatabase.queryEntitiesForFact(factId: Any?): List<String> {
    if (factId == null) return emptyList()
    val sql = """
        SELECT e.name FROM entities e
        JOIN fact_entities fe ON fe.entity_id = e.entity_id
        WHERE fe.fact_id = ?
    """.trimIndent()
    val cursor = rawQuery(sql, arrayOf(factId.toString()))
    val results = mutableListOf<String>()
    try {
        while (cursor.moveToNext()) {
            cursor.getString(0)?.let { results.add(it) }
        }
    } finally {
        cursor.close()
    }
    return results
}

/**
 * Execute a SQL query with typed parameters, returning a list of row maps.
 * Used by FactRetriever for FTS5 candidate retrieval.
 */
fun SQLiteDatabase.executeQuery(sql: String, params: List<Any>): List<Map<String, Any?>> {
    val args = params.map { it.toString() }.toTypedArray()
    val cursor = rawQuery(sql, args)
    val results = mutableListOf<Map<String, Any?>>()
    try {
        while (cursor.moveToNext()) {
            val row = mutableMapOf<String, Any?>()
            for (i in 0 until cursor.columnCount) {
                val name = cursor.getColumnName(i)
                row[name] = when (cursor.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> null
                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                    else -> cursor.getString(i)
                }
            }
            results.add(row)
        }
    } finally {
        cursor.close()
    }
    return results
}
