package com.xiaomo.androidforclaw.routing

/**
 * OpenClaw module: routing
 * Source: OpenClaw/src/routing/account-id.ts
 *
 * Account ID normalization with LRU caching and prototype-pollution protection.
 */

const val DEFAULT_ACCOUNT_ID = "default"

private val VALID_ID_RE = Regex("""^[a-z0-9][a-z0-9_-]{0,63}$""", RegexOption.IGNORE_CASE)
private val INVALID_CHARS_RE = Regex("""[^a-z0-9_-]+""")
private val LEADING_DASH_RE = Regex("""^-+""")
private val TRAILING_DASH_RE = Regex("""-+$""")
private const val ACCOUNT_ID_CACHE_MAX = 512

/**
 * Blocked object keys — aligned with OpenClaw/src/infra/prototype-keys.ts.
 * Guards against prototype-pollution attacks when account IDs are used as
 * object/map keys.
 */
private val BLOCKED_OBJECT_KEYS = setOf(
    "__proto__",
    "constructor",
    "prototype",
    "hasOwnProperty",
    "toString",
    "valueOf",
    "isPrototypeOf",
    "propertyIsEnumerable",
    "__defineGetter__",
    "__defineSetter__",
    "__lookupGetter__",
    "__lookupSetter__"
)

private fun isBlockedObjectKey(key: String): Boolean =
    key in BLOCKED_OBJECT_KEYS

// ---------------------------------------------------------------------------
// LRU caches (evict oldest key when capacity exceeded — matches TS Map order)
// ---------------------------------------------------------------------------

private val normalizeAccountIdCache = LinkedHashMap<String, String>(64, 0.75f, false)
private val normalizeOptionalAccountIdCache = LinkedHashMap<String, String?>(64, 0.75f, false)

private fun <T> setNormalizeCache(cache: LinkedHashMap<String, T>, key: String, value: T) {
    cache[key] = value
    if (cache.size <= ACCOUNT_ID_CACHE_MAX) return
    // Evict the oldest entry (first key inserted)
    val oldest = cache.keys.iterator()
    if (oldest.hasNext()) {
        oldest.next()
        oldest.remove()
    }
}

// ---------------------------------------------------------------------------
// Canonicalization
// ---------------------------------------------------------------------------

/**
 * Best-effort canonicalization: lowercase, strip invalid chars, clamp to 64.
 */
fun canonicalizeAccountId(value: String): String {
    if (VALID_ID_RE.matches(value)) {
        return value.lowercase()
    }
    return value
        .lowercase()
        .replace(INVALID_CHARS_RE, "-")
        .replace(LEADING_DASH_RE, "")
        .replace(TRAILING_DASH_RE, "")
        .take(64)
}

/**
 * Canonicalize then reject blocked keys. Returns null when the ID is empty
 * or a blocked object key.
 */
fun normalizeCanonicalAccountId(value: String): String? {
    val canonical = canonicalizeAccountId(value)
    if (canonical.isEmpty() || isBlockedObjectKey(canonical)) {
        return null
    }
    return canonical
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Normalize an account ID to a safe canonical form.
 * Returns [DEFAULT_ACCOUNT_ID] when the input is null, blank, or blocked.
 */
fun normalizeAccountId(value: String?): String {
    val trimmed = (value ?: "").trim()
    if (trimmed.isEmpty()) return DEFAULT_ACCOUNT_ID

    val cached = normalizeAccountIdCache[trimmed]
    if (cached != null) return cached

    val normalized = normalizeCanonicalAccountId(trimmed) ?: DEFAULT_ACCOUNT_ID
    setNormalizeCache(normalizeAccountIdCache, trimmed, normalized)
    return normalized
}

/**
 * Same as [normalizeAccountId] but returns null instead of the default when
 * the input is empty or blocked.
 */
fun normalizeOptionalAccountId(value: String?): String? {
    val trimmed = (value ?: "").trim()
    if (trimmed.isEmpty()) return null

    if (normalizeOptionalAccountIdCache.containsKey(trimmed)) {
        return normalizeOptionalAccountIdCache[trimmed]
    }

    val normalized = normalizeCanonicalAccountId(trimmed)
    setNormalizeCache(normalizeOptionalAccountIdCache, trimmed, normalized)
    return normalized
}
