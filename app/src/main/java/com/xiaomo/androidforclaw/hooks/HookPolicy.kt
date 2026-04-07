package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/policy.ts
 *
 * Hook source policies: precedence, trust, default enable modes, override rules.
 * Hook resolution: deduplication by name with source-based precedence.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

// ============================================================================
// Enable state
// ============================================================================

enum class HookEnableStateReason(val value: String) {
    DISABLED_IN_CONFIG("disabled in config"),
    WORKSPACE_HOOK_DISABLED_BY_DEFAULT("workspace hook (disabled by default)")
}

data class HookEnableState(
    val enabled: Boolean,
    val reason: HookEnableStateReason? = null
)

// ============================================================================
// Source policy
// ============================================================================

data class HookSourcePolicy(
    val precedence: Int,
    val trustedLocalCode: Boolean,
    val defaultEnableMode: HookDefaultEnableMode,
    val canOverride: Set<HookSource>,
    val canBeOverriddenBy: Set<HookSource>
)

enum class HookDefaultEnableMode {
    DEFAULT_ON,
    EXPLICIT_OPT_IN
}

private val HOOK_SOURCE_POLICIES: Map<HookSource, HookSourcePolicy> = mapOf(
    HookSource.OPENCLAW_BUNDLED to HookSourcePolicy(
        precedence = 10,
        trustedLocalCode = true,
        defaultEnableMode = HookDefaultEnableMode.DEFAULT_ON,
        canOverride = setOf(HookSource.OPENCLAW_BUNDLED),
        canBeOverriddenBy = setOf(HookSource.OPENCLAW_MANAGED, HookSource.OPENCLAW_PLUGIN)
    ),
    HookSource.OPENCLAW_PLUGIN to HookSourcePolicy(
        precedence = 20,
        trustedLocalCode = true,
        defaultEnableMode = HookDefaultEnableMode.DEFAULT_ON,
        canOverride = setOf(HookSource.OPENCLAW_BUNDLED, HookSource.OPENCLAW_PLUGIN),
        canBeOverriddenBy = setOf(HookSource.OPENCLAW_MANAGED)
    ),
    HookSource.OPENCLAW_MANAGED to HookSourcePolicy(
        precedence = 30,
        trustedLocalCode = true,
        defaultEnableMode = HookDefaultEnableMode.DEFAULT_ON,
        canOverride = setOf(HookSource.OPENCLAW_BUNDLED, HookSource.OPENCLAW_MANAGED, HookSource.OPENCLAW_PLUGIN),
        canBeOverriddenBy = setOf(HookSource.OPENCLAW_MANAGED)
    ),
    HookSource.OPENCLAW_WORKSPACE to HookSourcePolicy(
        precedence = 40,
        trustedLocalCode = true,
        defaultEnableMode = HookDefaultEnableMode.EXPLICIT_OPT_IN,
        canOverride = setOf(HookSource.OPENCLAW_WORKSPACE),
        canBeOverriddenBy = setOf(HookSource.OPENCLAW_WORKSPACE)
    )
)

fun getHookSourcePolicy(source: HookSource): HookSourcePolicy =
    HOOK_SOURCE_POLICIES[source] ?: error("Unknown hook source: $source")

// ============================================================================
// Hook config resolution
// ============================================================================

/**
 * Resolve per-hook config from OpenClawConfig.
 * Aligned with OpenClaw resolveHookConfig.
 *
 * Note: On Android, HooksConfig doesn't have internal.entries yet,
 * so this returns null for now (hooks are always enabled by default).
 */
fun resolveHookConfig(
    @Suppress("UNUSED_PARAMETER") config: OpenClawConfig?,
    @Suppress("UNUSED_PARAMETER") hookKey: String
): Map<String, Any?>? {
    // TODO: When OpenClawConfig gains hooks.internal.entries, look it up here.
    return null
}

/**
 * Resolve whether a hook is enabled.
 * Aligned with OpenClaw resolveHookEnableState.
 */
fun resolveHookEnableState(
    entry: HookEntry,
    @Suppress("UNUSED_PARAMETER") config: OpenClawConfig? = null,
    hookConfig: Map<String, Any?>? = null
): HookEnableState {
    // Plugin hooks are always enabled
    if (entry.hook.source == HookSource.OPENCLAW_PLUGIN) {
        return HookEnableState(enabled = true)
    }

    // Explicit disable in config
    if (hookConfig?.get("enabled") == false) {
        return HookEnableState(enabled = false, reason = HookEnableStateReason.DISABLED_IN_CONFIG)
    }

    // Check default enable mode from source policy
    val sourcePolicy = getHookSourcePolicy(entry.hook.source)
    if (sourcePolicy.defaultEnableMode == HookDefaultEnableMode.EXPLICIT_OPT_IN &&
        hookConfig?.get("enabled") != true
    ) {
        return HookEnableState(
            enabled = false,
            reason = HookEnableStateReason.WORKSPACE_HOOK_DISABLED_BY_DEFAULT
        )
    }

    return HookEnableState(enabled = true)
}

// ============================================================================
// Hook resolution collision
// ============================================================================

data class HookResolutionCollision(
    val name: String,
    val kept: HookEntry,
    val ignored: HookEntry
)

private fun canOverrideHook(candidate: HookEntry, existing: HookEntry): Boolean {
    val candidatePolicy = getHookSourcePolicy(candidate.hook.source)
    val existingPolicy = getHookSourcePolicy(existing.hook.source)
    return candidatePolicy.canOverride.contains(existing.hook.source) &&
        existingPolicy.canBeOverriddenBy.contains(candidate.hook.source)
}

/**
 * Resolve hook entries by deduplication with source-based precedence.
 * Aligned with OpenClaw resolveHookEntries.
 */
fun resolveHookEntries(
    entries: List<HookEntry>,
    onCollisionIgnored: ((HookResolutionCollision) -> Unit)? = null
): List<HookEntry> {
    val ordered = entries.withIndex()
        .sortedWith(compareBy(
            { getHookSourcePolicy(it.value.hook.source).precedence },
            { it.index }
        ))

    val merged = linkedMapOf<String, HookEntry>()
    for ((_, entry) in ordered) {
        val existing = merged[entry.hook.name]
        if (existing == null) {
            merged[entry.hook.name] = entry
            continue
        }
        if (canOverrideHook(entry, existing)) {
            merged[entry.hook.name] = entry
            continue
        }
        onCollisionIgnored?.invoke(
            HookResolutionCollision(
                name = entry.hook.name,
                kept = existing,
                ignored = entry
            )
        )
    }

    return merged.values.toList()
}
