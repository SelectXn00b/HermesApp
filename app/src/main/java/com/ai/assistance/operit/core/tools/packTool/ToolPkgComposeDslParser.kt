package com.ai.assistance.operit.core.tools.packTool

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ToolPkgComposeDslNode(
    val type: String,
    val props: Map<String, Any?>,
    val children: List<ToolPkgComposeDslNode>
)

data class ToolPkgComposeDslRenderResult(
    val tree: ToolPkgComposeDslNode,
    val state: Map<String, Any?>,
    val memo: Map<String, Any?>
)

object ToolPkgComposeDslParser {
    fun parseRenderResult(rawResult: Any?): ToolPkgComposeDslRenderResult? {
        val root = parseRootObject(rawResult) ?: return null
        val treeNode = parseNode(root.opt("tree")) ?: return null
        return ToolPkgComposeDslRenderResult(
            tree = treeNode,
            state = asMap(root.opt("state")),
            memo = asMap(root.opt("memo"))
        )
    }

    fun extractActionId(value: Any?): String? {
        return when (value) {
            is JSONObject -> value.optString("__actionId").trim().ifBlank { null }
            is Map<*, *> -> value["__actionId"]?.toString()?.trim()?.ifBlank { null }
            is String -> {
                val normalized = value.trim()
                when {
                    normalized.startsWith("__action:") -> normalized.removePrefix("__action:").trim().ifBlank { null }
                    normalized.isNotBlank() -> normalized
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun parseRootObject(rawResult: Any?): JSONObject? {
        return when (rawResult) {
            is JSONObject -> rawResult
            is String -> parseRootObjectFromString(rawResult)
            else -> parseRootObjectFromString(rawResult?.toString().orEmpty())
        }
    }

    private fun parseRootObjectFromString(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) {
            return null
        }

        val first = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
        if (first is JSONObject) {
            return first
        }

        if (first is String) {
            val nested = first.trim()
            if (nested.startsWith("{") && nested.endsWith("}")) {
                return runCatching { JSONObject(nested) }.getOrNull()
            }
        }

        return null
    }

    private fun parseNode(value: Any?): ToolPkgComposeDslNode? {
        val nodeObj =
            when (value) {
                is JSONObject -> value
                is Map<*, *> -> JSONObject(value)
                else -> return null
            }

        val type = nodeObj.optString("type").trim()
        if (type.isBlank()) {
            return null
        }

        val props = asMap(nodeObj.opt("props"))
        val children = mutableListOf<ToolPkgComposeDslNode>()

        val rawChildren = nodeObj.opt("children")
        when (rawChildren) {
            is JSONArray -> {
                for (index in 0 until rawChildren.length()) {
                    parseNode(rawChildren.opt(index))?.let { children.add(it) }
                }
            }
            is List<*> -> {
                rawChildren.forEach { child ->
                    parseNode(child)?.let { children.add(it) }
                }
            }
            is JSONObject -> {
                parseNode(rawChildren)?.let { children.add(it) }
            }
        }

        return ToolPkgComposeDslNode(type = type, props = props, children = children)
    }

    private fun asMap(value: Any?): Map<String, Any?> {
        return when (value) {
            is JSONObject -> {
                val map = linkedMapOf<String, Any?>()
                value.keys().forEach { key ->
                    map[key] = normalize(value.opt(key))
                }
                map
            }
            is Map<*, *> -> {
                value.entries.associate { entry ->
                    entry.key.toString() to normalize(entry.value)
                }
            }
            else -> emptyMap()
        }
    }

    private fun asList(value: Any?): List<Any?> {
        return when (value) {
            is JSONArray -> {
                buildList {
                    for (index in 0 until value.length()) {
                        add(normalize(value.opt(index)))
                    }
                }
            }
            is List<*> -> value.map { normalize(it) }
            else -> emptyList()
        }
    }

    private fun normalize(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> asMap(value)
            is JSONArray -> asList(value)
            else -> value
        }
    }
}

