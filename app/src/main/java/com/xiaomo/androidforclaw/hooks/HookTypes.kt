package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/types.ts
 *
 * Core type definitions for the hook system.
 */

/**
 * Hook install spec — aligned with OpenClaw HookInstallSpec.
 */
data class HookInstallSpec(
    val id: String? = null,
    val kind: HookInstallKind,
    val label: String? = null,
    val packageName: String? = null,
    val repository: String? = null,
    val bins: List<String>? = null
)

enum class HookInstallKind {
    BUNDLED, NPM, GIT;

    companion object {
        fun fromString(value: String?): HookInstallKind? = when (value?.lowercase()) {
            "bundled" -> BUNDLED
            "npm" -> NPM
            "git" -> GIT
            else -> null
        }
    }
}

/**
 * Hook metadata from HOOK.md frontmatter — aligned with OpenClaw OpenClawHookMetadata.
 */
data class OpenClawHookMetadata(
    val always: Boolean? = null,
    val hookKey: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val events: List<String> = emptyList(),
    val exportName: String? = null,
    val os: List<String>? = null,
    val requires: HookRequires? = null,
    val install: List<HookInstallSpec>? = null
)

data class HookRequires(
    val bins: List<String>? = null,
    val anyBins: List<String>? = null,
    val env: List<String>? = null,
    val config: List<String>? = null
)

/**
 * Invocation policy — aligned with OpenClaw HookInvocationPolicy.
 */
data class HookInvocationPolicy(
    val enabled: Boolean
)

/**
 * Parsed hook frontmatter — simple key-value pairs.
 */
typealias ParsedHookFrontmatter = Map<String, String>

/**
 * Hook source — aligned with OpenClaw HookSource.
 */
enum class HookSource(val value: String) {
    OPENCLAW_BUNDLED("openclaw-bundled"),
    OPENCLAW_MANAGED("openclaw-managed"),
    OPENCLAW_WORKSPACE("openclaw-workspace"),
    OPENCLAW_PLUGIN("openclaw-plugin");

    companion object {
        fun fromString(value: String?): HookSource? = entries.find { it.value == value }
    }
}

/**
 * A discovered hook — aligned with OpenClaw Hook.
 */
data class Hook(
    val name: String,
    val description: String,
    val source: HookSource,
    val pluginId: String? = null,
    val filePath: String,
    val baseDir: String,
    val handlerPath: String
)

/**
 * Hook entry: hook + parsed frontmatter + metadata + invocation.
 * Aligned with OpenClaw HookEntry.
 */
data class HookEntry(
    val hook: Hook,
    val frontmatter: ParsedHookFrontmatter,
    val metadata: OpenClawHookMetadata? = null,
    val invocation: HookInvocationPolicy? = null
)

/**
 * Hook eligibility context for runtime filtering.
 * Aligned with OpenClaw HookEligibilityContext.
 */
data class HookEligibilityContext(
    val remote: RemoteEligibility? = null
) {
    data class RemoteEligibility(
        val platforms: List<String>,
        val hasBin: (String) -> Boolean,
        val hasAnyBin: (List<String>) -> Boolean,
        val note: String? = null
    )
}

/**
 * Hook snapshot for agent bootstrap.
 * Aligned with OpenClaw HookSnapshot.
 */
data class HookSnapshot(
    val hooks: List<HookSnapshotEntry>,
    val resolvedHooks: List<Hook>? = null,
    val version: Int? = null
) {
    data class HookSnapshotEntry(
        val name: String,
        val events: List<String>
    )
}
