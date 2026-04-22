package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all hermes-agent tools.
 *
 * Ported from tools/registry.py.
 *
 * Each tool file calls ``registry.register()`` at module level to declare its
 * schema, handler, toolset membership, and availability check.
 */

/**
 * Metadata for a single registered tool.
 * Ported from ToolEntry in registry.py.
 */
data class ToolEntry(
    val name: String = "",
    val toolset: String = "",
    val schema: Map<String, Any?> = emptyMap(),
    val handler: ((Map<String, Any>) -> String)? = null,
    val checkFn: (() -> Boolean)? = null,
    val requiresEnv: List<String> = emptyList(),
    val isAsync: Boolean = false,
    val description: String = "",
    val emoji: String = "",
    val maxResultSizeChars: Int? = null,
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

    fun _snapshotState(): Pair<List<ToolEntry>, Map<String, () -> Boolean>> {
        synchronized(_lock) {
            return Pair(_tools.values.toList(), _toolsetChecks.toMap())
        }
    }

    fun _snapshotEntries(): List<ToolEntry> = _snapshotState().first

    fun _snapshotToolsetChecks(): Map<String, () -> Boolean> = _snapshotState().second

    fun _evaluateToolsetCheck(toolset: String, check: (() -> Boolean)?): Boolean {
        if (check == null) return true
        return try {
            check()
        } catch (_: Exception) {
            Log.d("ToolRegistry", "Toolset $toolset check raised; marking unavailable")
            false
        }
    }

    fun getEntry(name: String): ToolEntry? {
        synchronized(_lock) {
            return _tools[name]
        }
    }

    fun getRegisteredToolsetNames(): List<String> {
        return _snapshotEntries().map { it.toolset }.distinct().sorted()
    }

    fun getToolNamesForToolset(toolset: String): List<String> {
        return _snapshotEntries().filter { it.toolset == toolset }.map { it.name }.sorted()
    }

    fun registerToolsetAlias(alias: String, toolset: String) {
        synchronized(_lock) {
            val existing = _toolsetAliases[alias]
            if (existing != null && existing != toolset) {
                Log.w("ToolRegistry", "Toolset alias collision: '$alias' ($existing) overwritten by $toolset")
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

    fun register(
        name: String,
        toolset: String,
        schema: Map<String, Any?>,
        handler: (Map<String, Any>) -> String,
        checkFn: (() -> Boolean)? = null,
        requiresEnv: List<String>? = null,
        isAsync: Boolean = false,
        description: String = "",
        emoji: String = "",
        maxResultSizeChars: Int? = null,
    ) {
        register(ToolEntry(
            name = name,
            toolset = toolset,
            schema = schema,
            handler = handler,
            checkFn = checkFn,
            requiresEnv = requiresEnv ?: emptyList(),
            isAsync = isAsync,
            description = description.ifEmpty { schema["description"] as? String ?: "" },
            emoji = emoji,
            maxResultSizeChars = maxResultSizeChars,
        ))
    }

    /** Kotlin convenience overload — build a ToolEntry in place and register it. */
    fun register(entry: ToolEntry) {
        synchronized(_lock) {
            val existing = _tools[entry.name]
            if (existing != null && existing.toolset != entry.toolset) {
                val bothMcp = existing.toolset.startsWith("mcp-") && entry.toolset.startsWith("mcp-")
                if (!bothMcp) {
                    Log.e("ToolRegistry",
                        "Tool registration REJECTED: '${entry.name}' (toolset '${entry.toolset}') would " +
                        "shadow existing tool from toolset '${existing.toolset}'.")
                    return
                }
            }
            _tools[entry.name] = entry
            val check = entry.checkFn
            if (check != null && !_toolsetChecks.containsKey(entry.toolset)) {
                _toolsetChecks[entry.toolset] = check
            }
        }
    }

    fun deregister(name: String) {
        synchronized(_lock) {
            val entry = _tools.remove(name) ?: return
            val toolsetStillExists = _tools.values.any { it.toolset == entry.toolset }
            if (!toolsetStillExists) {
                _toolsetChecks.remove(entry.toolset)
                val filtered = _toolsetAliases.filterValues { it != entry.toolset }
                _toolsetAliases.clear()
                _toolsetAliases.putAll(filtered)
            }
        }
        Log.d("ToolRegistry", "Deregistered tool: $name")
    }

    fun getDefinitions(toolNames: Set<String>, quiet: Boolean = false): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        val checkResults = mutableMapOf<() -> Boolean, Boolean>()
        val entriesByName = _snapshotEntries().associateBy { it.name }
        for (name in toolNames.sorted()) {
            val entry = entriesByName[name] ?: continue
            val check = entry.checkFn
            if (check != null) {
                val cached = checkResults.getOrPut(check) {
                    try { check() } catch (_: Exception) { false }
                }
                if (!cached) continue
            }
            val schemaWithName = entry.schema.toMutableMap().apply { put("name", entry.name) }
            result.add(mapOf("type" to "function", "function" to schemaWithName))
        }
        return result
    }

    fun dispatch(name: String, args: Map<String, Any>): String {
        val entry = getEntry(name)
            ?: return Gson().toJson(mapOf("error" to "Unknown tool: $name"))
        return try {
            entry.handler?.invoke(args) ?: Gson().toJson(mapOf("error" to "Tool '$name' has no handler"))
        } catch (e: Exception) {
            Log.e("ToolRegistry", "Tool $name dispatch error: ${e.message}", e)
            Gson().toJson(mapOf("error" to "Tool execution failed: ${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    fun getMaxResultSize(name: String, default: Int? = null): Int {
        val entry = getEntry(name)
        if (entry?.maxResultSizeChars != null) return entry.maxResultSizeChars
        if (default != null) return default
        return 50000
    }

    fun getAllToolNames(): List<String> = _snapshotEntries().map { it.name }.sorted()

    fun getSchema(name: String): Map<String, Any?>? = getEntry(name)?.schema

    fun getToolsetForTool(name: String): String? = getEntry(name)?.toolset

    fun getEmoji(name: String, default: String = "⚡"): String {
        val entry = getEntry(name)
        return if (entry != null && entry.emoji.isNotEmpty()) entry.emoji else default
    }

    fun getToolToToolsetMap(): Map<String, String> =
        _snapshotEntries().associate { it.name to it.toolset }

    fun isToolsetAvailable(toolset: String): Boolean {
        val check = synchronized(_lock) { _toolsetChecks[toolset] }
        return _evaluateToolsetCheck(toolset, check)
    }

    fun checkToolsetRequirements(): Map<String, Boolean> {
        val (entries, checks) = _snapshotState()
        val toolsets = entries.map { it.toolset }.distinct().sorted()
        return toolsets.associateWith { _evaluateToolsetCheck(it, checks[it]) }
    }

    fun getAvailableToolsets(): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, MutableMap<String, Any?>>()
        val (entries, checks) = _snapshotState()
        for (entry in entries) {
            val ts = entry.toolset
            val meta = result.getOrPut(ts) {
                mutableMapOf(
                    "available" to _evaluateToolsetCheck(ts, checks[ts]),
                    "tools" to mutableListOf<String>(),
                    "description" to "",
                    "requirements" to mutableListOf<String>(),
                )
            }
            @Suppress("UNCHECKED_CAST")
            (meta["tools"] as MutableList<String>).add(entry.name)
            if (entry.requiresEnv.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val reqs = meta["requirements"] as MutableList<String>
                for (env in entry.requiresEnv) if (env !in reqs) reqs.add(env)
            }
        }
        return result
    }

    fun getToolsetRequirements(): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, MutableMap<String, Any?>>()
        val (entries, checks) = _snapshotState()
        for (entry in entries) {
            val ts = entry.toolset
            val meta = result.getOrPut(ts) {
                mutableMapOf(
                    "name" to ts,
                    "env_vars" to mutableListOf<String>(),
                    "check_fn" to checks[ts],
                    "setup_url" to null,
                    "tools" to mutableListOf<String>(),
                )
            }
            @Suppress("UNCHECKED_CAST")
            val tools = meta["tools"] as MutableList<String>
            if (entry.name !in tools) tools.add(entry.name)
            @Suppress("UNCHECKED_CAST")
            val envs = meta["env_vars"] as MutableList<String>
            for (env in entry.requiresEnv) if (env !in envs) envs.add(env)
        }
        return result
    }

    fun checkToolAvailability(quiet: Boolean = false): Pair<List<String>, List<Map<String, Any?>>> {
        val available = mutableListOf<String>()
        val unavailable = mutableListOf<Map<String, Any?>>()
        val seen = mutableSetOf<String>()
        val (entries, checks) = _snapshotState()
        for (entry in entries) {
            val ts = entry.toolset
            if (ts in seen) continue
            seen.add(ts)
            if (_evaluateToolsetCheck(ts, checks[ts])) {
                available.add(ts)
            } else {
                unavailable.add(mapOf(
                    "name" to ts,
                    "env_vars" to entry.requiresEnv,
                    "tools" to entries.filter { it.toolset == ts }.map { it.name },
                ))
            }
        }
        return Pair(available, unavailable)
    }
}

/** Module-level singleton — matches Python `registry = ToolRegistry()`. */
val registry: ToolRegistry = ToolRegistry()

/**
 * Return a JSON error string for tool handlers.
 * Ported from tool_error in registry.py.
 */
fun toolError(message: String, extra: Map<String, Any?> = emptyMap()): String {
    val result = mutableMapOf<String, Any?>("error" to message)
    result.putAll(extra)
    return Gson().toJson(result)
}

/**
 * Return a JSON result string for tool handlers.
 * Ported from tool_result in registry.py.
 */
fun toolResult(data: Any? = null, kwargs: Map<String, Any?> = emptyMap()): String {
    if (data != null) return Gson().toJson(data)
    return Gson().toJson(kwargs)
}
