package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/allowlist-config-edit.ts
 *
 * Allowlist config editing helpers for channel plugins.
 * Provides read/write operations for DM and group allowlists in config.
 * Android adaptation: uses Map<String, Any?> for config traversal.
 */

// ---------- Types ----------

/**
 * Paths for reading and writing allowlist config.
 * Aligned with TS AllowlistConfigPaths.
 */
data class AllowlistConfigPaths(
    val readPaths: List<List<String>>,
    val writePath: List<String>,
    val cleanupPaths: List<List<String>>? = null,
)

/**
 * Allowlist group override.
 * Aligned with TS AllowlistGroupOverride.
 */
data class AllowlistGroupOverride(
    val label: String,
    val entries: List<String>,
)

/**
 * Allowlist name resolution entry.
 * Aligned with TS AllowlistNameResolution element.
 */
data class AllowlistNameResolutionEntry(
    val input: String,
    val resolved: Boolean,
    val name: String? = null,
)

/**
 * Result of applying an allowlist config edit.
 * Aligned with TS ChannelAllowlistAdapter applyConfigEdit return type.
 */
sealed class AllowlistConfigEditResult {
    data class Ok(
        val changed: Boolean,
        val pathLabel: String,
        val writeTarget: ConfigWriteTarget,
    ) : AllowlistConfigEditResult()

    data object InvalidEntry : AllowlistConfigEditResult()
}

// ---------- Default Paths ----------

val DM_ALLOWLIST_CONFIG_PATHS = AllowlistConfigPaths(
    readPaths = listOf(listOf("allowFrom")),
    writePath = listOf("allowFrom"),
)

val GROUP_ALLOWLIST_CONFIG_PATHS = AllowlistConfigPaths(
    readPaths = listOf(listOf("groupAllowFrom")),
    writePath = listOf("groupAllowFrom"),
)

val LEGACY_DM_ALLOWLIST_CONFIG_PATHS = AllowlistConfigPaths(
    readPaths = listOf(listOf("allowFrom"), listOf("dm", "allowFrom")),
    writePath = listOf("allowFrom"),
    cleanupPaths = listOf(listOf("dm", "allowFrom")),
)

/**
 * Resolve DM/group allowlist config paths.
 * Aligned with TS resolveDmGroupAllowlistConfigPaths.
 */
fun resolveDmGroupAllowlistConfigPaths(scope: String): AllowlistConfigPaths? = when (scope) {
    "dm" -> DM_ALLOWLIST_CONFIG_PATHS
    "group" -> GROUP_ALLOWLIST_CONFIG_PATHS
    else -> null
}

/**
 * Resolve legacy DM allowlist config paths.
 * Aligned with TS resolveLegacyDmAllowlistConfigPaths.
 */
fun resolveLegacyDmAllowlistConfigPaths(scope: String): AllowlistConfigPaths? = when (scope) {
    "dm" -> LEGACY_DM_ALLOWLIST_CONFIG_PATHS
    else -> null
}

// ---------- Read Helpers ----------

/**
 * Coerce stored allowlist entries into presentable non-empty strings.
 * Aligned with TS readConfiguredAllowlistEntries.
 */
fun readConfiguredAllowlistEntries(
    entries: List<Any?>?,
): List<String> = (entries ?: emptyList()).map { it.toString() }.filter { it.isNotEmpty() }

/**
 * Collect labeled allowlist overrides from a flat keyed record.
 * Aligned with TS collectAllowlistOverridesFromRecord.
 */
fun <T> collectAllowlistOverridesFromRecord(
    record: Map<String, T?>?,
    label: (key: String, value: T) -> String,
    resolveEntries: (value: T) -> List<Any?>?,
): List<AllowlistGroupOverride> {
    val overrides = mutableListOf<AllowlistGroupOverride>()
    for ((key, value) in record ?: emptyMap()) {
        if (value == null) continue
        val entries = readConfiguredAllowlistEntries(resolveEntries(value))
        if (entries.isEmpty()) continue
        overrides.add(AllowlistGroupOverride(label = label(key, value), entries = entries))
    }
    return overrides
}

// ---------- Nested Traversal ----------

@Suppress("UNCHECKED_CAST")
private fun getNestedValue(root: Map<String, Any?>, path: List<String>): Any? {
    var current: Any? = root
    for (key in path) {
        if (current == null || current !is Map<*, *>) return null
        current = (current as Map<String, Any?>)[key]
    }
    return current
}

@Suppress("UNCHECKED_CAST")
private fun ensureNestedObject(
    root: MutableMap<String, Any?>,
    path: List<String>,
): MutableMap<String, Any?> {
    var current = root
    for (key in path) {
        val existing = current[key]
        if (existing == null || existing !is MutableMap<*, *>) {
            val newMap = mutableMapOf<String, Any?>()
            current[key] = newMap
            current = newMap
        } else {
            current = existing as MutableMap<String, Any?>
        }
    }
    return current
}

@Suppress("UNCHECKED_CAST")
private fun setNestedValue(root: MutableMap<String, Any?>, path: List<String>, value: Any?) {
    if (path.isEmpty()) return
    if (path.size == 1) {
        root[path[0]] = value
        return
    }
    val parent = ensureNestedObject(root, path.dropLast(1))
    parent[path.last()] = value
}

@Suppress("UNCHECKED_CAST")
private fun deleteNestedValue(root: MutableMap<String, Any?>, path: List<String>) {
    if (path.isEmpty()) return
    if (path.size == 1) {
        root.remove(path[0])
        return
    }
    val parent = getNestedValue(root, path.dropLast(1))
    if (parent == null || parent !is MutableMap<*, *>) return
    (parent as MutableMap<String, Any?>).remove(path.last())
}

// ---------- Account-Scoped Edit ----------

/**
 * Apply an account-scoped allowlist config edit.
 * Aligned with TS applyAccountScopedAllowlistConfigEdit.
 */
@Suppress("UNCHECKED_CAST")
fun applyAccountScopedAllowlistConfigEdit(
    parsedConfig: MutableMap<String, Any?>,
    channelId: String,
    accountId: String?,
    action: String, // "add" | "remove"
    entry: String,
    normalize: (List<Any>) -> List<String>,
    paths: AllowlistConfigPaths,
): AllowlistConfigEditResult {
    // Resolve write target
    val channels = (parsedConfig.getOrPut("channels") { mutableMapOf<String, Any?>() }) as MutableMap<String, Any?>
    val channel = (channels.getOrPut(channelId) { mutableMapOf<String, Any?>() }) as MutableMap<String, Any?>
    val normalizedAccountId = normalizeAccountId(accountId)

    val target: MutableMap<String, Any?>
    val pathPrefix: String
    val writeTarget: ConfigWriteTarget

    val accounts = channel["accounts"] as? Map<String, Any?>
    val useAccount = normalizedAccountId != DEFAULT_ACCOUNT_ID ||
        (accounts != null && accounts.containsKey(normalizedAccountId))

    if (useAccount) {
        val accountsMap = (channel.getOrPut("accounts") { mutableMapOf<String, Any?>() }) as MutableMap<String, Any?>
        if (!accountsMap.containsKey(normalizedAccountId)) {
            accountsMap[normalizedAccountId] = mutableMapOf<String, Any?>()
        }
        target = accountsMap[normalizedAccountId] as MutableMap<String, Any?>
        pathPrefix = "channels.$channelId.accounts.$normalizedAccountId"
        writeTarget = ConfigWriteTarget.Account(
            ConfigWriteScope(channelId = channelId, accountId = normalizedAccountId)
        )
    } else {
        target = channel
        pathPrefix = "channels.$channelId"
        writeTarget = ConfigWriteTarget.Channel(ConfigWriteScope(channelId = channelId))
    }

    // Read existing entries from all read paths
    val existing = mutableListOf<String>()
    for (path in paths.readPaths) {
        val existingRaw = getNestedValue(target, path) as? List<*> ?: continue
        for (e in existingRaw) {
            val value = e.toString().trim()
            if (value.isNotEmpty() && value !in existing) {
                existing.add(value)
            }
        }
    }

    val normalizedEntry = normalize(listOf(entry))
    if (normalizedEntry.isEmpty()) return AllowlistConfigEditResult.InvalidEntry

    val existingNormalized = normalize(existing)
    val shouldMatch: (String) -> Boolean = { value -> normalizedEntry.contains(value) }

    var changed = false
    var next = existing.toList()

    val configHasEntry = existingNormalized.any(shouldMatch)
    if (action == "add") {
        if (!configHasEntry) {
            next = existing + entry.trim()
            changed = true
        }
    } else {
        val keep = mutableListOf<String>()
        for (e in existing) {
            val normalized = normalize(listOf(e))
            if (normalized.any(shouldMatch)) {
                changed = true
                continue
            }
            keep.add(e)
        }
        next = keep
    }

    if (changed) {
        if (next.isEmpty()) {
            deleteNestedValue(target, paths.writePath)
        } else {
            setNestedValue(target, paths.writePath, next)
        }
        for (path in paths.cleanupPaths ?: emptyList()) {
            deleteNestedValue(target, path)
        }
    }

    return AllowlistConfigEditResult.Ok(
        changed = changed,
        pathLabel = "$pathPrefix.${paths.writePath.joinToString(".")}",
        writeTarget = writeTarget,
    )
}
