package com.ai.assistance.operit.hermes

import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt

/**
 * Export AIToolHandler-registered [ToolPrompt]s as OpenAI-spec tool schemas.
 *
 * Hermes' agent loop passes these through [com.xiaomo.hermes.hermes.ChatCompletionServer.chatCompletion]
 * as the `tools` parameter, and [EnhancedAIService] derives `validToolNames`
 * from the resulting schemas so there is a single source of truth.
 */
fun toolPromptsToOpenAiSchemas(tools: List<ToolPrompt>): List<Map<String, Any?>> =
    tools.map { it.toOpenAiSchema() }

/** Extract tool names from schemas produced by [toolPromptsToOpenAiSchemas]. */
fun extractToolNames(schemas: List<Map<String, Any?>>): Set<String> =
    schemas.mapNotNullTo(LinkedHashSet()) { schema ->
        (schema["function"] as? Map<*, *>)?.get("name") as? String
    }

private fun ToolPrompt.toOpenAiSchema(): Map<String, Any?> {
    val structured = parametersStructured ?: emptyList()
    val properties = linkedMapOf<String, Any?>()
    val required = mutableListOf<String>()
    for (p in structured) {
        properties[p.name] = p.toPropertySchema()
        if (p.required) required.add(p.name)
    }
    val paramsSchema = linkedMapOf<String, Any?>(
        "type" to "object",
        "properties" to properties
    )
    if (required.isNotEmpty()) paramsSchema["required"] = required

    val fullDescription = buildString {
        append(description)
        if (details.isNotBlank()) {
            append('\n')
            append(details)
        }
        if (notes.isNotBlank()) {
            append('\n')
            append(notes)
        }
    }

    return linkedMapOf(
        "type" to "function",
        "function" to linkedMapOf<String, Any?>(
            "name" to name,
            "description" to fullDescription,
            "parameters" to paramsSchema
        )
    )
}

private fun ToolParameterSchema.toPropertySchema(): Map<String, Any?> {
    val m = linkedMapOf<String, Any?>(
        "type" to type,
        "description" to description
    )
    if (default != null) m["default"] = default
    return m
}
