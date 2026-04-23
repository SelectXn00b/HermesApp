/** 1:1 对齐 hermes/gateway/platforms/feishu_comment_rules.py */
package com.xiaomo.hermes.hermes.gateway.platforms

import com.xiaomo.hermes.hermes.getHermesHome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.logging.Logger

/**
 * Feishu document comment access-control rules.
 *
 * 3-tier rule resolution: exact doc > wildcard "*" > top-level > code defaults.
 * Each field (enabled/policy/allowFrom) falls back independently.
 * Config: ~/.hermes/feishu_comment_rules.json (mtime-cached, hot-reload).
 * Pairing store: ~/.hermes/feishu_comment_pairing.json.
 */

private val logger = Logger.getLogger("FeishuCommentRules")

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------

val RULES_FILE: File get() = File(getHermesHome(), "feishu_comment_rules.json")
val PAIRING_FILE: File get() = File(getHermesHome(), "feishu_comment_pairing.json")

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

private val _VALID_POLICIES = setOf("allowlist", "pairing")

data class CommentDocumentRule(
    val enabled: Boolean? = null,
    val policy: String? = null,
    val allowFrom: Set<String>? = null
)

data class CommentsConfig(
    val enabled: Boolean = true,
    val policy: String = "pairing",
    val allowFrom: Set<String> = emptySet(),
    val documents: Map<String, CommentDocumentRule> = emptyMap()
)

data class ResolvedCommentRule(
    val enabled: Boolean,
    val policy: String,
    val allowFrom: Set<String>,
    val matchSource: String // e.g. "exact:docx:xxx" | "wildcard" | "top" | "default"
)

// ---------------------------------------------------------------------------
// Mtime-cached file loading
// ---------------------------------------------------------------------------

class MtimeCache(private val path: File) {
    private var _mtime: Long = 0L
    private var _data: Map<String, Any?>? = null

    fun load(): Map<String, Any?> {
        val mtime: Long
        try {
            mtime = path.lastModified()
            if (mtime == 0L && !path.exists()) {
                _mtime = 0L
                _data = emptyMap()
                return emptyMap()
            }
        } catch (e: Exception) {
            _mtime = 0L
            _data = emptyMap()
            return emptyMap()
        }

        if (mtime == _mtime && _data != null) {
            return _data!!
        }

        val data: Map<String, Any?> = try {
            val text = path.readText(Charsets.UTF_8)
            val jsonObj = Json.parseToJsonElement(text).jsonObject
            jsonObj.toMap()
        } catch (e: Exception) {
            logger.warning("[Feishu-Rules] Failed to read $path, using empty config")
            emptyMap()
        }

        _mtime = mtime
        _data = data
        return data
    }

    fun invalidate() {
        _mtime = 0L
        _data = null
    }
}

private val _rulesCache = MtimeCache(RULES_FILE)
private val _pairingCache = MtimeCache(PAIRING_FILE)

// ---------------------------------------------------------------------------
// Helper: convert JsonObject to Map<String, Any?>
// ---------------------------------------------------------------------------

private fun JsonObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for ((key, value) in this) {
        map[key] = value
    }
    return map
}

// ---------------------------------------------------------------------------
// Config parsing
// ---------------------------------------------------------------------------

private fun _parseFrozenSet(raw: Any?): Set<String>? {
    if (raw == null) return null
    if (raw is kotlinx.serialization.json.JsonArray) {
        return raw.mapNotNull {
            it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() }
        }.toSet()
    }
    if (raw is List<*>) {
        return raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.toSet()
    }
    return null
}

private fun _parseDocumentRule(raw: kotlinx.serialization.json.JsonElement): CommentDocumentRule {
    val obj = raw.jsonObject
    var enabled: Boolean? = obj["enabled"]?.jsonPrimitive?.booleanOrNull
    var policy: String? = obj["policy"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
    if (policy != null && policy !in _VALID_POLICIES) {
        policy = null
    }
    val allowFrom = _parseFrozenSet(obj["allow_from"])
    return CommentDocumentRule(enabled = enabled, policy = policy, allowFrom = allowFrom)
}

fun loadConfig(): CommentsConfig {
    val raw = _rulesCache.load()
    if (raw.isEmpty()) return CommentsConfig()

    val documents = mutableMapOf<String, CommentDocumentRule>()
    val rawDocs = raw["documents"]
    if (rawDocs is kotlinx.serialization.json.JsonElement) {
        try {
            val docsObj = rawDocs.jsonObject
            for ((key, ruleRaw) in docsObj) {
                documents[key] = _parseDocumentRule(ruleRaw)
            }
        } catch (_: Exception) {
            // not a valid object
        }
    }

    var policy = "pairing"
    val rawPolicy = raw["policy"]
    if (rawPolicy is kotlinx.serialization.json.JsonElement) {
        val p = rawPolicy.jsonPrimitive.contentOrNull?.trim()?.lowercase()
        if (p != null && p in _VALID_POLICIES) {
            policy = p
        }
    }

    val enabled = (raw["enabled"] as? kotlinx.serialization.json.JsonElement)
        ?.jsonPrimitive?.booleanOrNull ?: true

    val allowFrom = _parseFrozenSet(raw["allow_from"]) ?: emptySet()

    return CommentsConfig(
        enabled = enabled,
        policy = policy,
        allowFrom = allowFrom,
        documents = documents
    )
}

// ---------------------------------------------------------------------------
// Rule resolution (§8.4 field-by-field fallback)
// ---------------------------------------------------------------------------

fun hasWikiKeys(cfg: CommentsConfig): Boolean {
    return cfg.documents.keys.any { it.startsWith("wiki:") }
}

fun resolveRule(
    cfg: CommentsConfig,
    fileType: String,
    fileToken: String,
    wikiToken: String = ""
): ResolvedCommentRule {
    val exactKey = "$fileType:$fileToken"

    var exact = cfg.documents[exactKey]
    var exactSrc = "exact:$exactKey"
    if (exact == null && wikiToken.isNotEmpty()) {
        val wikiKey = "wiki:$wikiToken"
        exact = cfg.documents[wikiKey]
        exactSrc = "exact:$wikiKey"
    }

    val wildcard = cfg.documents["*"]

    data class Layer(val rule: CommentDocumentRule, val source: String)

    val layers = mutableListOf<Layer>()
    if (exact != null) layers.add(Layer(exact, exactSrc))
    if (wildcard != null) layers.add(Layer(wildcard, "wildcard"))

    fun <T> pick(fieldGetter: (CommentDocumentRule) -> T?, topValue: T): Pair<T, String> {
        for (layer in layers) {
            val v = fieldGetter(layer.rule)
            if (v != null) return v to layer.source
        }
        return topValue to "top"
    }

    val (enabled, enSrc) = pick({ it.enabled }, cfg.enabled)
    val (policy, polSrc) = pick({ it.policy }, cfg.policy)
    val (allowFrom, _) = pick({ it.allowFrom }, cfg.allowFrom)

    val priorityOrder = mapOf("exact" to 0, "wildcard" to 1, "top" to 2)
    val bestSrc = listOf(enSrc, polSrc).minByOrNull {
        priorityOrder[it.split(":").first()] ?: 3
    } ?: "top"

    return ResolvedCommentRule(
        enabled = enabled,
        policy = policy,
        allowFrom = allowFrom,
        matchSource = bestSrc
    )
}

// ---------------------------------------------------------------------------
// Pairing store
// ---------------------------------------------------------------------------

private fun _loadPairingApproved(): Set<String> {
    val data = _pairingCache.load()
    val approved = data["approved"]
    if (approved is kotlinx.serialization.json.JsonElement) {
        try {
            val obj = approved.jsonObject
            return obj.keys
        } catch (_: Exception) {}
        try {
            val arr = approved.jsonArray
            return arr.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
        } catch (_: Exception) {}
    }
    return emptySet()
}

private fun _savePairing(data: Map<String, Any?>) {
    PAIRING_FILE.parentFile?.mkdirs()
    val jsonStr = Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            for ((k, v) in data) {
                if (v is kotlinx.serialization.json.JsonElement) {
                    put(k, v)
                }
            }
        })
    val tmp = File(PAIRING_FILE.parentFile, "${PAIRING_FILE.name}.tmp")
    tmp.writeText(jsonStr, Charsets.UTF_8)
    tmp.renameTo(PAIRING_FILE)
    _pairingCache.invalidate()
}

fun pairingAdd(userOpenId: String): Boolean {
    val data = _pairingCache.load().toMutableMap()
    val approved = (data["approved"] as? kotlinx.serialization.json.JsonElement)
        ?.let {
            try { it.jsonObject.toMutableMap() } catch (_: Exception) { mutableMapOf() }
        } ?: mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    if (userOpenId in approved) return false

    approved[userOpenId] = kotlinx.serialization.json.buildJsonObject {
        put("approved_at", kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis() / 1000.0))
    }
    data["approved"] = kotlinx.serialization.json.JsonObject(approved)
    _savePairing(data)
    return true
}

fun pairingRemove(userOpenId: String): Boolean {
    val data = _pairingCache.load().toMutableMap()
    val approved = (data["approved"] as? kotlinx.serialization.json.JsonElement)
        ?.let {
            try { it.jsonObject.toMutableMap() } catch (_: Exception) { return false }
        } ?: return false

    if (userOpenId !in approved) return false

    approved.remove(userOpenId)
    data["approved"] = kotlinx.serialization.json.JsonObject(approved)
    _savePairing(data)
    return true
}

fun pairingList(): Map<String, kotlinx.serialization.json.JsonElement> {
    val data = _pairingCache.load()
    val approved = data["approved"]
    if (approved is kotlinx.serialization.json.JsonElement) {
        try {
            return approved.jsonObject.toMap().mapValues { it.value as kotlinx.serialization.json.JsonElement }
        } catch (_: Exception) {}
    }
    return emptyMap()
}

// ---------------------------------------------------------------------------
// Access check (public API for FeishuComment.kt)
// ---------------------------------------------------------------------------

fun isUserAllowed(rule: ResolvedCommentRule, userOpenId: String): Boolean {
    if (userOpenId in rule.allowFrom) return true
    if (rule.policy == "pairing") {
        return userOpenId in _loadPairingApproved()
    }
    return false
}

/**
 * Legacy alias for MtimeCache (Python: class _MtimeCache).
 * The implementation is provided by MtimeCache above.
 */
typealias _MtimeCache = MtimeCache

// ── Module-level aligned with gateway/platforms/feishu_comment_rules.py ──

/** Parse a comma-separated string into a Set (Python frozenset). */
fun _parseFrozenset(raw: String?): Set<String> =
    (raw ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/** Log a short human-readable status line for a rules check. */
fun _printStatus(): Unit = Unit

/** Run a single rule check (stub). */
@Suppress("UNUSED_PARAMETER")
fun _doCheck(docKey: String, userOpenId: String) = Unit

/** CLI entry-point equivalent (Android stub). */
fun _main(): Unit = Unit
