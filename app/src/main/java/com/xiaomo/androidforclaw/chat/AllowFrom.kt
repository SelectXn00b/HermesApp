package com.xiaomo.androidforclaw.chat

/**
 * OpenClaw module: channels
 * Source: OpenClaw/src/channels/allow-from.ts
 *
 * DM/group allow-from list helpers: merges config and stored allow lists,
 * resolves group-specific lists, and checks sender ID allowance.
 */

// ---------------------------------------------------------------------------
// DM allow-from  (aligned with TS mergeDmAllowFromSources)
// ---------------------------------------------------------------------------

fun mergeDmAllowFromSources(
    allowFrom: List<Any>? = null,
    storeAllowFrom: List<Any>? = null,
    dmPolicy: String? = null,
): List<String> {
    val storeEntries = if (dmPolicy == "allowlist") emptyList() else storeAllowFrom.orEmpty()
    return (allowFrom.orEmpty() + storeEntries)
        .map { it.toString().trim() }
        .filter { it.isNotEmpty() }
}

// ---------------------------------------------------------------------------
// Group allow-from  (aligned with TS resolveGroupAllowFromSources)
// ---------------------------------------------------------------------------

fun resolveGroupAllowFromSources(
    allowFrom: List<Any>? = null,
    groupAllowFrom: List<Any>? = null,
    fallbackToAllowFrom: Boolean = true,
): List<String> {
    val explicitGroupAllowFrom =
        if (!groupAllowFrom.isNullOrEmpty()) groupAllowFrom else null
    val scoped = explicitGroupAllowFrom
        ?: if (!fallbackToAllowFrom) emptyList() else allowFrom.orEmpty()
    return scoped.map { it.toString().trim() }.filter { it.isNotEmpty() }
}

// ---------------------------------------------------------------------------
// firstDefined  (aligned with TS firstDefined)
// ---------------------------------------------------------------------------

fun <T> firstDefined(vararg values: T?): T? {
    for (value in values) {
        if (value != null) return value
    }
    return null
}

// ---------------------------------------------------------------------------
// Sender ID allowance check  (aligned with TS isSenderIdAllowed)
// ---------------------------------------------------------------------------

data class AllowList(
    val entries: List<String>,
    val hasWildcard: Boolean,
    val hasEntries: Boolean,
)

fun isSenderIdAllowed(
    allow: AllowList,
    senderId: String?,
    allowWhenEmpty: Boolean,
): Boolean {
    if (!allow.hasEntries) return allowWhenEmpty
    if (allow.hasWildcard) return true
    if (senderId == null) return false
    return senderId in allow.entries
}
