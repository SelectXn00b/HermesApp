package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/frontmatter.ts
 *
 * Parse HOOK.md frontmatter and resolve hook metadata / invocation policies.
 */

/**
 * Parse frontmatter from HOOK.md content.
 * Aligned with OpenClaw parseFrontmatter.
 *
 * Expects YAML-like frontmatter delimited by --- lines:
 * ```
 * ---
 * name: my-hook
 * description: Does things
 * ---
 * ```
 */
fun parseFrontmatter(content: String): ParsedHookFrontmatter {
    val lines = content.lines()
    val result = mutableMapOf<String, String>()

    // Find opening ---
    var startIdx = -1
    for (i in lines.indices) {
        if (lines[i].trim() == "---") {
            startIdx = i
            break
        }
    }
    if (startIdx < 0) return result

    // Find closing ---
    var endIdx = -1
    for (i in (startIdx + 1) until lines.size) {
        if (lines[i].trim() == "---") {
            endIdx = i
            break
        }
    }
    if (endIdx < 0) return result

    // Parse key: value pairs
    for (i in (startIdx + 1) until endIdx) {
        val line = lines[i]
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) continue
        val key = line.substring(0, colonIdx).trim()
        val value = line.substring(colonIdx + 1).trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        if (key.isNotEmpty()) {
            result[key] = value
        }
    }

    return result
}

/**
 * Resolve OpenClaw hook metadata from frontmatter.
 * Aligned with OpenClaw resolveOpenClawMetadata.
 *
 * On Android, we parse a simplified subset of the metadata block
 * since the full YAML-in-YAML structure is less common.
 */
fun resolveOpenClawMetadata(frontmatter: ParsedHookFrontmatter): OpenClawHookMetadata? {
    // Check if any openclaw-specific fields exist
    val hasMetadata = frontmatter.keys.any { key ->
        key in setOf("always", "emoji", "homepage", "hookKey", "export", "os", "events")
    }
    if (!hasMetadata) return null

    val events = frontmatter["events"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: emptyList()

    return OpenClawHookMetadata(
        always = frontmatter["always"]?.toBooleanStrictOrNull(),
        emoji = frontmatter["emoji"],
        homepage = frontmatter["homepage"],
        hookKey = frontmatter["hookKey"],
        exportName = frontmatter["export"],
        os = frontmatter["os"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        events = events
    )
}

/**
 * Resolve hook invocation policy from frontmatter.
 * Aligned with OpenClaw resolveHookInvocationPolicy.
 */
fun resolveHookInvocationPolicy(frontmatter: ParsedHookFrontmatter): HookInvocationPolicy {
    val enabled = frontmatter["enabled"]?.let { parseFrontmatterBool(it, true) } ?: true
    return HookInvocationPolicy(enabled = enabled)
}

/**
 * Resolve hook key from hook name and entry metadata.
 * Aligned with OpenClaw resolveHookKey.
 */
fun resolveHookKey(hookName: String, entry: HookEntry? = null): String {
    return entry?.metadata?.hookKey ?: hookName
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Parse a boolean from a frontmatter string value.
 * Supports "true"/"false"/"yes"/"no"/"1"/"0".
 */
internal fun parseFrontmatterBool(value: String?, default: Boolean): Boolean {
    if (value == null) return default
    return when (value.lowercase().trim()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> default
    }
}

/**
 * Get a string value from frontmatter by key.
 */
internal fun getFrontmatterString(frontmatter: ParsedHookFrontmatter, key: String): String? {
    return frontmatter[key]?.ifBlank { null }
}
