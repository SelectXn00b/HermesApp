/**
 * Todo Tool Module - Planning & Task Management.
 *
 * Provides an in-memory task list the agent uses to decompose complex
 * tasks, track progress, and maintain focus across long conversations.
 * The state lives on the AIAgent instance (one per session) and is
 * re-injected into the conversation after context compression events.
 *
 * Ported from tools/todo_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import org.json.JSONArray
import org.json.JSONObject

val VALID_STATUSES: Set<String> = setOf("pending", "in_progress", "completed", "cancelled")

/**
 * In-memory todo list. One instance per AIAgent (one per session).
 */
class TodoStore {
    private val _items: MutableList<MutableMap<String, String>> = mutableListOf()

    fun write(todos: List<Map<String, Any?>>, merge: Boolean = false): List<Map<String, String>> {
        if (!merge) {
            _items.clear()
            _items.addAll(_dedupeById(todos).map { _validate(it).toMutableMap() })
        } else {
            val existing: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
            for (item in _items) existing[item["id"] ?: ""] = item
            for (t in _dedupeById(todos)) {
                val itemId = (t["id"]?.toString() ?: "").trim()
                if (itemId.isEmpty()) continue
                if (itemId in existing) {
                    val content = t["content"]?.toString()?.trim()
                    if (!content.isNullOrEmpty()) existing[itemId]!!["content"] = content
                    val rawStatus = t["status"]?.toString()?.trim()?.lowercase()
                    if (!rawStatus.isNullOrEmpty() && rawStatus in VALID_STATUSES) {
                        existing[itemId]!!["status"] = rawStatus
                    }
                } else {
                    val validated = _validate(t).toMutableMap()
                    existing[validated["id"]!!] = validated
                    _items.add(validated)
                }
            }
            val seen = mutableSetOf<String>()
            val rebuilt = mutableListOf<MutableMap<String, String>>()
            for (item in _items) {
                val id = item["id"] ?: continue
                val current = existing[id] ?: item
                if (current["id"] !in seen) {
                    rebuilt.add(current)
                    seen.add(current["id"]!!)
                }
            }
            _items.clear()
            _items.addAll(rebuilt)
        }
        return read()
    }

    fun read(): List<Map<String, String>> = _items.map { it.toMap() }

    fun hasItems(): Boolean = _items.isNotEmpty()

    fun formatForInjection(): String? {
        if (_items.isEmpty()) return null
        val markers = mapOf(
            "completed" to "[x]",
            "in_progress" to "[>]",
            "pending" to "[ ]",
            "cancelled" to "[~]",
        )
        val active = _items.filter { (it["status"] ?: "") in setOf("pending", "in_progress") }
        if (active.isEmpty()) return null
        val lines = mutableListOf("[Your active task list was preserved across context compression]")
        for (item in active) {
            val marker = markers[item["status"]] ?: "[?]"
            lines.add("- $marker ${item["id"]}. ${item["content"]} (${item["status"]})")
        }
        return lines.joinToString("\n")
    }

    companion object {
        fun _validate(item: Map<String, Any?>): Map<String, String> {
            val id = (item["id"]?.toString() ?: "").trim().ifEmpty { "?" }
            val content = (item["content"]?.toString() ?: "").trim().ifEmpty { "(no description)" }
            var status = (item["status"]?.toString() ?: "pending").trim().lowercase()
            if (status !in VALID_STATUSES) status = "pending"
            return mapOf("id" to id, "content" to content, "status" to status)
        }

        fun _dedupeById(todos: List<Map<String, Any?>>): List<Map<String, Any?>> {
            val lastIndex = mutableMapOf<String, Int>()
            for ((i, item) in todos.withIndex()) {
                val itemId = (item["id"]?.toString() ?: "").trim().ifEmpty { "?" }
                lastIndex[itemId] = i
            }
            return lastIndex.values.sorted().map { todos[it] }
        }
    }
}

fun todoTool(
    todos: List<Map<String, Any?>>? = null,
    merge: Boolean = false,
    store: TodoStore? = null,
): String {
    if (store == null) return toolError("TodoStore not initialized")
    val items = if (todos != null) store.write(todos, merge) else store.read()
    val pending = items.count { it["status"] == "pending" }
    val inProgress = items.count { it["status"] == "in_progress" }
    val completed = items.count { it["status"] == "completed" }
    val cancelled = items.count { it["status"] == "cancelled" }
    val todosArr = JSONArray()
    for (item in items) {
        todosArr.put(JSONObject(item as Map<String, Any>))
    }
    val summary = JSONObject().apply {
        put("total", items.size)
        put("pending", pending)
        put("in_progress", inProgress)
        put("completed", completed)
        put("cancelled", cancelled)
    }
    return JSONObject().apply {
        put("todos", todosArr)
        put("summary", summary)
    }.toString()
}

fun checkTodoRequirements(): Boolean = true

val TODO_SCHEMA: Map<String, Any> = mapOf(
    "name" to "todo",
    "description" to (
        "Manage your task list for the current session. Use for complex tasks " +
        "with 3+ steps or when the user provides multiple tasks. " +
        "Call with no parameters to read the current list.\n\n" +
        "Writing:\n" +
        "- Provide 'todos' array to create/update items\n" +
        "- merge=false (default): replace the entire list with a fresh plan\n" +
        "- merge=true: update existing items by id, add any new ones\n\n" +
        "Each item: {id: string, content: string, " +
        "status: pending|in_progress|completed|cancelled}\n" +
        "List order is priority. Only ONE item in_progress at a time.\n" +
        "Mark items completed immediately when done. If something fails, " +
        "cancel it and add a revised item.\n\n" +
        "Always returns the full current list."
    ),
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "todos" to mapOf(
                "type" to "array",
                "description" to "Task items to write. Omit to read current list.",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf("type" to "string", "description" to "Unique item identifier"),
                        "content" to mapOf("type" to "string", "description" to "Task description"),
                        "status" to mapOf(
                            "type" to "string",
                            "enum" to listOf("pending", "in_progress", "completed", "cancelled"),
                            "description" to "Current status"),
                    ),
                    "required" to listOf("id", "content", "status"),
                ),
            ),
            "merge" to mapOf(
                "type" to "boolean",
                "description" to (
                    "true: update existing items by id, add new ones. " +
                    "false (default): replace the entire list."
                ),
                "default" to false),
        ),
        "required" to emptyList<String>(),
    ),
)
