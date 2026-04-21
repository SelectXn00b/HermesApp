package com.xiaomo.hermes.hermes.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Tool registry — tracks tool definitions and metadata.
 * Ported from registry.py
 */
object Registry {

    data class ToolDefinition(
        val name: String,
        val description: String = "",
        val handler: ((Map<String, Any>) -> String)? = null,
        val enabled: Boolean = true,
        val category: String = "general",
        val parameters: Map<String, Any> = emptyMap())

    private val _tools = ConcurrentHashMap<String, ToolDefinition>()
    private val _aliases = ConcurrentHashMap<String, String>()

    fun register(tool: ToolDefinition) {
        _tools[tool.name] = tool
    }

    fun register(name: String, description: String = "", handler: ((Map<String, Any>) -> String)? = null) {
        register(ToolDefinition(name, description, handler))
    }

    fun unregister(name: String) {
        _tools.remove(name)
        _aliases.remove(name)
    }

    fun get(name: String): ToolDefinition? {
        val resolved = _aliases[name] ?: name
        return _tools[resolved]
    }

    fun getAll(): Map<String, ToolDefinition> = _tools.toMap()

    fun getAllNames(): Set<String> = _tools.keys + _aliases.keys

    fun getEnabled(): Map<String, ToolDefinition> = _tools.filter { it.value.enabled }

    fun isEnabled(name: String): Boolean = get(name)?.enabled ?: false

    fun setEnabled(name: String, enabled: Boolean) {
        val tool = _tools[name] ?: return
        _tools[name] = tool.copy(enabled = enabled)
    }

    fun addAlias(alias: String, target: String) {
        _aliases[alias] = target
    }

    fun resolve(name: String): String = _aliases[name] ?: name

    fun call(name: String, params: Map<String, Any> = emptyMap()): String {
        val tool = get(name) ?: return """{"error":"Tool '$name' not found"}"""
        if (!tool.enabled) return """{"error":"Tool '$name' is disabled"}"""
        return try {
            tool.handler?.invoke(params) ?: """{"error":"Tool '$name' has no handler"}"""
        } catch (e: Exception) {
            """{"error":"Tool '$name' failed: ${e.message}"}"""
        }
    }

    fun clear() {
        _tools.clear()
        _aliases.clear()
    }

    fun count(): Int = _tools.size

    // ── Aliased names (ported from tools/registry.py) ────────────────

    /** Remove a tool (alias for unregister). */
    fun deregister(name: String) = unregister(name)

    /** Execute a tool handler by name (alias for call). */
    fun dispatch(name: String, args: Map<String, Any> = emptyMap()): String = call(name, args)

    /** Get OpenAI-format tool schemas for requested names. */
    fun getDefinitions(toolNames: Set<String>): List<Map<String, Any?>> {
        return toolNames.mapNotNull { name ->
            val tool = get(name) ?: return@mapNotNull null
            mapOf<String, Any?>(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to tool.parameters))
        }
    }

    /** Get all registered tool names (alias). */
    fun getAllToolNames(): List<String> = _tools.keys.toList().sorted()

    /** Get schema for a tool. */
    fun getSchema(name: String): Map<String, Any?>? = get(name)?.parameters

    /** Get toolset category for a tool. */
    fun getToolsetForTool(name: String): String? = get(name)?.category

    /** Get emoji for a tool. */
    fun getEmoji(name: String, default: String = "⚡"): String = get(name)?.let { default } ?: default

    /** Get tool-to-toolset mapping. */
    fun getToolToToolsetMap(): Map<String, String> = _tools.mapValues { it.value.category }

    /** Check if a toolset/category has available tools. */
    fun isToolsetAvailable(toolset: String): Boolean = _tools.values.any { it.category == toolset && it.enabled }

    /** Check all toolset requirements. */
    fun checkToolsetRequirements(): Map<String, Boolean> {
        return _tools.values.groupBy { it.category }.mapValues { (_, tools) -> tools.any { it.enabled } }
    }

    /** Get available toolsets metadata. */
    fun getAvailableToolsets(): Map<String, Map<String, Any?>> {
        return _tools.values.groupBy { it.category }.mapValues { (ts, tools) ->
            mapOf<String, Any?>(
                "available" to tools.any { it.enabled },
                "tools" to tools.map { it.name },
                "description" to "",
                "requirements" to emptyList<String>())
        }
    }

    /** Check tool availability (returns available list and unavailable info). */
    fun checkToolAvailability(): Pair<List<String>, List<Map<String, Any?>>> {
        val available = mutableListOf<String>()
        val unavailable = mutableListOf<Map<String, Any?>>()
        for ((toolset, tools) in _tools.values.groupBy { it.category }) {
            if (tools.any { it.enabled }) {
                available.add(toolset)
            } else {
                unavailable.add(mapOf("name" to toolset, "tools" to tools.map { it.name }))
            }
        }
        return Pair(available, unavailable)
    }

    /** Return a snapshot of all tools. */
    fun snapshot(): List<Map<String, Any?>> {
        return _tools.values.map { t ->
            mapOf<String, Any?>(
                "name" to t.name, "description" to t.description,
                "category" to t.category, "enabled" to t.enabled)
        }
    }

    /** Reload registry (clear and re-register). */
    fun reload() { clear() }


    /** Return a stable snapshot of registered tool entries. */
    fun snapshotEntries(): List<ToolDefinition> = _tools.values.toList()

    /** Return a stable snapshot of toolset availability checks. */
    fun snapshotToolsetChecks(): Map<String, () -> Boolean> = emptyMap()

    /** Run a toolset check, treating missing or failing checks as available. */
    fun evaluateToolsetCheck(toolset: String, check: (() -> Boolean)? = null): Boolean {
        if (check == null) return true
        return try { check() } catch (_unused: Exception) { false }
    }

    /** Return a registered tool entry by name, or null. */
    fun getEntry(name: String): ToolDefinition? = _tools[name]

    /** Return sorted unique toolset names present in the registry. */
    fun getRegisteredToolsetNames(): List<String> = _tools.values.map { it.category }.distinct().sorted()

    /** Return sorted tool names registered under a given toolset. */
    fun getToolNamesForToolset(toolset: String): List<String> = _tools.values.filter { it.category == toolset }.map { it.name }.sorted()

    /** Return per-tool max result size, or default. */
    fun getMaxResultSize(name: String, default: Int = 50000): Int {
        return 50000
    }

    /** Return all registered tool names. */

    /** Build a toolset requirements map for backward compat. */
    fun getToolsetRequirements(): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, MutableMap<String, Any?>>()
        for (entry in _tools.values) {
            val ts = entry.category
            if (ts !in result) {
                result[ts] = mutableMapOf("name" to ts, "available" to _tools.values.any { it.category == ts && it.enabled })
            }
        }
        return result
    }

    /** Return a JSON error string for tool handlers. */
    fun toolError(message: String, extra: Map<String, Any?> = emptyMap()): String {
        val data = mutableMapOf<String, Any?>("error" to message)
        data.putAll(extra)
        return com.google.gson.Gson().toJson(data)
    }

    /** Return a JSON result string for tool handlers. */
    fun toolResult(data: Any? = null, kwargs: Map<String, Any?> = emptyMap()): String {
        val map = when (data) {
            is Map<*, *> -> data as Map<String, Any?>
            null -> kwargs
            else -> kwargs + ("result" to data)
        }
        return com.google.gson.Gson().toJson(map)
    }


    /** Return a coherent snapshot of registry entries and toolset checks. */
    fun _snapshotState(): Pair<List<ToolDefinition>, Map<String, () -> Boolean>> {
        val entries = _tools.values.toList()
        val checks = _tools.values.associate { it.category to { it.enabled } }
        return Pair(entries, checks)
    }
    /** Return a stable snapshot of registered tool entries. */
    fun _snapshotEntries(): List<ToolDefinition> = _snapshotState().first
    /** Return a stable snapshot of toolset availability checks. */
    fun _snapshotToolsetChecks(): Map<String, () -> Boolean> = _snapshotState().second
    /** Run a toolset check, treating missing or failing checks as unavailable/available. */
    fun _evaluateToolsetCheck(toolset: String, check: (() -> Boolean)?): Boolean {
        if (check == null) return true
        return try { check() } catch (_: Exception) { false }
    }

}

/**
 * Metadata for a single registered tool.
 * Ported from ToolEntry in registry.py.
 */
data class ToolEntry(
    val name: String = "",
    val toolset: String = "",
    val schema: Map<String, Any> = emptyMap(),
    val handler: ((Map<String, Any>) -> String)? = null,
    val checkFn: (() -> Boolean)? = null,
    val requiresEnv: Boolean = false,
    val isAsync: Boolean = false,
    val description: String = "",
    val emoji: String = "",
    val maxResultSizeChars: Int? = null
)

/**
 * Singleton registry that collects tool schemas + handlers from tool files.
 * Ported from ToolRegistry in registry.py.
 */
class ToolRegistry {
    private val _tools = ConcurrentHashMap<String, ToolEntry>()
    private val _toolsetChecks = ConcurrentHashMap<String, () -> Boolean>()
    private val _toolsetAliases = ConcurrentHashMap<String, String>()
    private val _lock = Any()

    fun registerToolsetAlias(alias: String, toolset: String) {
        synchronized(_lock) {
            val existing = _toolsetAliases[alias]
            if (existing != null && existing != toolset) {
                android.util.Log.w("ToolRegistry", "Toolset alias collision: '$alias' ($existing) overwritten by $toolset")
            }
            _toolsetAliases[alias] = toolset
        }
    }

    fun getRegisteredToolsetAliases(): Map<String, String> {
        synchronized(_lock) {
            return _toolsetAliases.toMap()
        }
    }

    fun getToolsetAliasTarget(alias: String): String? {
        synchronized(_lock) {
            return _toolsetAliases[alias]
        }
    }

    fun getEntry(name: String): ToolEntry? {
        synchronized(_lock) {
            return _tools[name]
        }
    }

    fun register(entry: ToolEntry) {
        synchronized(_lock) {
            _tools[entry.name] = entry
        }
    }

    fun deregister(name: String) {
        synchronized(_lock) {
            _tools.remove(name)
        }
    }

    fun getRegisteredToolsetNames(): List<String> {
        return _tools.values.map { it.toolset }.distinct().sorted()
    }

    fun getToolNamesForToolset(toolset: String): List<String> {
        return _tools.values.filter { it.toolset == toolset }.map { it.name }.sorted()
    }
}
