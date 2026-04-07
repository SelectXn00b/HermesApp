package com.xiaomo.androidforclaw.plugins

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/slots.ts
 *
 * Plugin slot management: maps plugin kinds to exclusive slots
 * (e.g. "memory" kind -> "memory" slot).
 */

enum class PluginSlotKey(val value: String) {
    MEMORY("memory"),
    CONTEXT_ENGINE("contextEngine");

    companion object {
        fun fromString(raw: String): PluginSlotKey? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

private val SLOT_BY_KIND: Map<PluginKind, PluginSlotKey> = mapOf(
    PluginKind.MEMORY to PluginSlotKey.MEMORY,
    PluginKind.CONTEXT_ENGINE to PluginSlotKey.CONTEXT_ENGINE,
)

private val DEFAULT_SLOT_BY_KEY: Map<PluginSlotKey, String> = mapOf(
    PluginSlotKey.MEMORY to "memory-core",
    PluginSlotKey.CONTEXT_ENGINE to "legacy",
)

/** Normalize a kind field to a list for uniform iteration. */
fun normalizeKinds(kind: Any?): List<PluginKind> = when (kind) {
    null -> emptyList()
    is PluginKind -> listOf(kind)
    is List<*> -> kind.filterIsInstance<PluginKind>()
    else -> emptyList()
}

/** Check whether a plugin's kind field includes a specific kind. */
fun hasKind(kind: Any?, target: PluginKind): Boolean = normalizeKinds(kind).contains(target)

/** Returns the slot key for a single-kind plugin. */
fun slotKeyForPluginKind(kind: PluginKind?): PluginSlotKey? =
    kind?.let { SLOT_BY_KIND[it] }

/** Order-insensitive equality check for two kind values. */
fun kindsEqual(a: Any?, b: Any?): Boolean {
    val aN = normalizeKinds(a).sorted()
    val bN = normalizeKinds(b).sorted()
    return aN == bN
}

/** Return all slot keys that a plugin's kind field maps to. */
fun slotKeysForPluginKind(kind: Any?): List<PluginSlotKey> =
    normalizeKinds(kind).mapNotNull { SLOT_BY_KIND[it] }

fun defaultSlotIdForKey(slotKey: PluginSlotKey): String =
    DEFAULT_SLOT_BY_KEY[slotKey] ?: ""

// ---------------------------------------------------------------------------
// Exclusive slot selection (aligned with TS applyExclusiveSlotSelection)
// ---------------------------------------------------------------------------
data class SlotSelectionResult(
    val config: Map<String, Any?>,
    val warnings: List<String>,
    val changed: Boolean,
)

fun applyExclusiveSlotSelection(
    config: Map<String, Any?>,
    selectedId: String,
    selectedKind: Any?,
    registryPlugins: List<PluginRecord>? = null,
): SlotSelectionResult {
    val slotKeys = slotKeysForPluginKind(selectedKind)
    if (slotKeys.isEmpty()) {
        return SlotSelectionResult(config, emptyList(), false)
    }

    val warnings = mutableListOf<String>()
    @Suppress("UNCHECKED_CAST")
    val pluginsConfig = (config["plugins"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    @Suppress("UNCHECKED_CAST")
    val entries = (pluginsConfig["entries"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    @Suppress("UNCHECKED_CAST")
    val slots = (pluginsConfig["slots"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    var anyChanged = false

    for (slotKey in slotKeys) {
        val prevSlot = slots[slotKey.value] as? String
        slots[slotKey.value] = selectedId

        val inferredPrevSlot = prevSlot ?: defaultSlotIdForKey(slotKey)
        if (inferredPrevSlot.isNotEmpty() && inferredPrevSlot != selectedId) {
            warnings.add(
                "Exclusive slot \"${slotKey.value}\" switched from \"$inferredPrevSlot\" to \"$selectedId\"."
            )
        }

        val disabledIds = mutableListOf<String>()
        if (registryPlugins != null) {
            for (plugin in registryPlugins) {
                if (plugin.id == selectedId) continue
                val kindForSlot = SLOT_BY_KIND.entries
                    .find { it.value == slotKey }?.key ?: continue
                if (!hasKind(plugin.kind, kindForSlot)) continue
                // Don't disable a plugin that still owns another slot
                val stillOwnsOtherSlot = SLOT_BY_KIND.entries
                    .filter { it.value != slotKey }
                    .any { (_, sk) ->
                        (slots[sk.value] as? String ?: defaultSlotIdForKey(sk)) == plugin.id
                    }
                if (stillOwnsOtherSlot) continue
                @Suppress("UNCHECKED_CAST")
                val entryMap = entries[plugin.id] as? Map<String, Any?>
                if (entryMap == null || entryMap["enabled"] != false) {
                    entries[plugin.id] = (entryMap?.toMutableMap() ?: mutableMapOf()).apply {
                        put("enabled", false)
                    }
                    disabledIds.add(plugin.id)
                }
            }
        }

        if (disabledIds.isNotEmpty()) {
            warnings.add(
                "Disabled other \"${slotKey.value}\" slot plugins: ${disabledIds.sorted().joinToString(", ")}."
            )
        }

        if (prevSlot != selectedId || disabledIds.isNotEmpty()) {
            anyChanged = true
        }
    }

    if (!anyChanged) {
        return SlotSelectionResult(config, emptyList(), false)
    }

    pluginsConfig["slots"] = slots
    pluginsConfig["entries"] = entries

    return SlotSelectionResult(
        config = config.toMutableMap().apply { put("plugins", pluginsConfig) },
        warnings = warnings,
        changed = true,
    )
}
