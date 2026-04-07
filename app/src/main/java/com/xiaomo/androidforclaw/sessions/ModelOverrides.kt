package com.xiaomo.androidforclaw.sessions

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/sessions/model-overrides.ts
 *
 * AndroidForClaw adaptation: model override logic for session entries.
 *
 * Session entries are represented as `MutableMap<String, Any?>` because the
 * full SessionEntry type may not be fully defined in the Android config yet.
 */

/**
 * Describes a model override selection.
 */
data class ModelOverrideSelection(
    val provider: String,
    val model: String,
    val isDefault: Boolean? = null
)

/**
 * Apply a model override selection to a session entry.
 *
 * Handles all the delete/set logic for:
 *   providerOverride, modelOverride, model, modelProvider, contextTokens,
 *   authProfileOverride, authProfileOverrideSource, authProfileOverrideCompactionCount,
 *   fallbackNotice*, liveModelSwitchPending, updatedAt.
 *
 * @return `true` if the entry was modified.
 */
fun applyModelOverrideToSessionEntry(
    entry: MutableMap<String, Any?>,
    selection: ModelOverrideSelection,
    profileOverride: String? = null,
    profileOverrideSource: String? = "user",
    markLiveSwitchPending: Boolean? = null
): Boolean {
    val effectiveProfileOverrideSource = profileOverrideSource ?: "user"
    var updated = false
    var selectionUpdated = false

    // --- Provider / model override ---
    if (selection.isDefault == true) {
        if (entry.containsKey("providerOverride")) {
            entry.remove("providerOverride")
            updated = true
            selectionUpdated = true
        }
        if (entry.containsKey("modelOverride")) {
            entry.remove("modelOverride")
            updated = true
            selectionUpdated = true
        }
    } else {
        if (entry["providerOverride"] != selection.provider) {
            entry["providerOverride"] = selection.provider
            updated = true
            selectionUpdated = true
        }
        if (entry["modelOverride"] != selection.model) {
            entry["modelOverride"] = selection.model
            updated = true
            selectionUpdated = true
        }
    }

    // --- Runtime model identity ---
    val runtimeModel = (entry["model"] as? String)?.trim().orEmpty()
    val runtimeProvider = (entry["modelProvider"] as? String)?.trim().orEmpty()
    val runtimePresent = runtimeModel.isNotEmpty() || runtimeProvider.isNotEmpty()
    val runtimeAligned =
        runtimeModel == selection.model &&
                (runtimeProvider.isEmpty() || runtimeProvider == selection.provider)

    if (runtimePresent && (selectionUpdated || !runtimeAligned)) {
        if (entry.containsKey("model")) {
            entry.remove("model")
            updated = true
        }
        if (entry.containsKey("modelProvider")) {
            entry.remove("modelProvider")
            updated = true
        }
    }

    // --- Context tokens ---
    if (entry.containsKey("contextTokens") &&
        (selectionUpdated || (runtimePresent && !runtimeAligned))
    ) {
        entry.remove("contextTokens")
        updated = true
    }

    // --- Auth profile override ---
    if (profileOverride != null) {
        if (entry["authProfileOverride"] != profileOverride) {
            entry["authProfileOverride"] = profileOverride
            updated = true
        }
        if (entry["authProfileOverrideSource"] != effectiveProfileOverrideSource) {
            entry["authProfileOverrideSource"] = effectiveProfileOverrideSource
            updated = true
        }
        if (entry.containsKey("authProfileOverrideCompactionCount")) {
            entry.remove("authProfileOverrideCompactionCount")
            updated = true
        }
    } else {
        if (entry.containsKey("authProfileOverride")) {
            entry.remove("authProfileOverride")
            updated = true
        }
        if (entry.containsKey("authProfileOverrideSource")) {
            entry.remove("authProfileOverrideSource")
            updated = true
        }
        if (entry.containsKey("authProfileOverrideCompactionCount")) {
            entry.remove("authProfileOverrideCompactionCount")
            updated = true
        }
    }

    // --- Fallback notice cleanup & live switch ---
    if (updated) {
        if (selectionUpdated && markLiveSwitchPending == true) {
            entry["liveModelSwitchPending"] = true
        }
        entry.remove("fallbackNoticeSelectedModel")
        entry.remove("fallbackNoticeActiveModel")
        entry.remove("fallbackNoticeReason")
        entry["updatedAt"] = System.currentTimeMillis()
    }

    return updated
}
