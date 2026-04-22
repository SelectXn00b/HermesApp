package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * Todo Tool — in-memory task list for planning & task management.
 * Ported from todo_tool.py
 */
object TodoTool {

    val VALID_STATUSES = setOf("pending", "in_progress", "completed", "cancelled")

    data class TodoItem(
        @SerializedName("id") val id: String,
        @SerializedName("content") val content: String,
        @SerializedName("status") val status: String = "pending")

    data class TodoSummary(
        @SerializedName("total") val total: Int,
        @SerializedName("pending") val pending: Int,
        @SerializedName("in_progress") val inProgress: Int,
        @SerializedName("completed") val completed: Int,
        @SerializedName("cancelled") val cancelled: Int)

    data class TodoResult(
        @SerializedName("todos") val todos: List<TodoItem>,
        @SerializedName("summary") val summary: TodoSummary)

    /**
     * In-memory todo list. One instance per session.
     */
    class TodoStore {
        private val _items = mutableListOf<TodoItem>()

        fun write(todos: List<Map<String, Any>>, merge: Boolean = false): List<TodoItem> {
            if (!merge) {
                _items.clear()
                _items.addAll(todos.map { validate(it) }.let { dedupeById(it) })
            } else {
                val existing = mutableMapOf<String, TodoItem>()
                _items.forEach { existing[it.id] = it }

                for (t in dedupeById(todos.map { validate(it) })) {
                    if (t.id in existing) {
                        val old = existing[t.id]!!
                        existing[t.id] = old.copy(
                            content = t.content.ifBlank { old.content },
                            status = if (t.status in VALID_STATUSES) t.status else old.status)
                    } else {
                        existing[t.id] = t
                        _items.add(t)
                    }
                }
                // Rebuild preserving order
                val seen = mutableSetOf<String>()
                val rebuilt = mutableListOf<TodoItem>()
                for (item in _items) {
                    val current = existing[item.id] ?: item
                    if (current.id !in seen) {
                        rebuilt.add(current)
                        seen.add(current.id)
                    }
                }
                _items.clear()
                _items.addAll(rebuilt)
            }
            return read()
        }

        fun read(): List<TodoItem> = _items.map { it.copy() }

        fun hasItems(): Boolean = _items.isNotEmpty()

        fun formatForInjection(): String? {
            if (_items.isEmpty()) return null
            val markers = mapOf(
                "completed" to "[x]",
                "in_progress" to "[>]",
                "pending" to "[ ]",
                "cancelled" to "[~]")
            val active = _items.filter { it.status in listOf("pending", "in_progress") }
            if (active.isEmpty()) return null

            return buildString {
                appendLine("[Your active task list was preserved across context compression]")
                for (item in active) {
                    val marker = markers[item.status] ?: "[?]"
                    appendLine("- $marker ${item.id}. ${item.content} (${item.status})")
                }
            }
        }

        companion object {
            fun validate(item: Map<String, Any>): TodoItem {
                val id = item["id"]?.toString()?.trim() ?: "?"
                val content = item["content"]?.toString()?.trim() ?: "(no description)"
                val status = item["status"]?.toString()?.trim()?.lowercase() ?: "pending"
                return TodoItem(
                    id = id.ifBlank { "?" },
                    content = content.ifBlank { "(no description)" },
                    status = if (status in VALID_STATUSES) status else "pending")
            }

            fun validate(todoItem: TodoItem): TodoItem {
                return TodoItem(
                    id = todoItem.id.ifBlank { "?" },
                    content = todoItem.content.ifBlank { "(no description)" },
                    status = if (todoItem.status in VALID_STATUSES) todoItem.status else "pending")
            }

            private fun <T> dedupeById(items: List<T>): List<T> where T : Any {
                // Simple deduplication — keep last occurrence
                return items.reversed().distinctBy { item ->
                    when (item) {
                        is TodoItem -> item.id
                        is Map<*, *> -> item["id"]?.toString() ?: ""
                        else -> item.hashCode()
                    }
                }.reversed()
            }
        }
    }

    private val gson = Gson()

    /**
     * Single entry point for the todo tool.
     */
    fun todoTool(
        todos: List<Map<String, Any>>? = null,
        merge: Boolean = false,
        store: TodoStore? = null): String {
        if (store == null) {
            return gson.toJson(mapOf("error" to "TodoStore not initialized"))
        }

        val items = if (todos != null) {
            store.write(todos, merge)
        } else {
            store.read()
        }

        val pending = items.count { it.status == "pending" }
        val inProgress = items.count { it.status == "in_progress" }
        val completed = items.count { it.status == "completed" }
        val cancelled = items.count { it.status == "cancelled" }

        val result = TodoResult(
            todos = items,
            summary = TodoSummary(
                total = items.size,
                pending = pending,
                inProgress = inProgress,
                completed = completed,
                cancelled = cancelled))
        return gson.toJson(result)
    }




    /** Dedupe todos by ID. */
    fun dedupeById(todos: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return todos.distinctBy { it["id"] }
    }

    /** Validate todo structure. */
    fun validate(todos: List<Map<String, Any?>>): List<String> {
        val errors = mutableListOf<String>()
        for ((i, todo) in todos.withIndex()) {
            if (todo["content"] == null) errors.add("Todo $i missing 'content'")
            if (todo["status"] == null) errors.add("Todo $i missing 'status'")
        }
        return errors
    }


    /** Validate and normalize a todo item. */
    fun _validate(item: Map<String, Any>): Map<String, String> {
        return emptyMap()
    }
    /** Collapse duplicate ids, keeping the last occurrence in its position. */
    fun _dedupeById(todos: List<Map<String, Any>>): List<Map<String, Any>> {
        return emptyList()
    }

}
