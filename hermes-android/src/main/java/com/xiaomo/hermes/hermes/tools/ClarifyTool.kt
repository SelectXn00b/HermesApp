/**
 * Clarify Tool Module - Interactive Clarifying Questions
 *
 * Allows the agent to present structured multiple-choice questions or
 * open-ended prompts to the user. The actual user-interaction logic lives in
 * the platform layer; this module defines the schema, validation, and a thin
 * dispatcher that delegates to a platform-provided callback.
 *
 * Ported from tools/clarify_tool.py
 */
package com.xiaomo.hermes.hermes.tools

import org.json.JSONArray
import org.json.JSONObject

// Maximum number of predefined choices the agent can offer.
// A 5th "Other (type your answer)" option is always appended by the UI.
const val MAX_CHOICES: Int = 4

/**
 * Ask the user a question, optionally with multiple-choice options.
 */
fun clarifyTool(
    question: String,
    choices: List<String>? = null,
    callback: ((String, List<String>?) -> String)? = null,
): String {
    if (question.isBlank()) return toolError("Question text is required.")
    // The parameter type List<String>? already enforces what Python validates at runtime:
    //   "choices must be a list of strings." — kept as a literal for alignment.
    val _typeCheckError = "choices must be a list of strings."

    val trimmedQuestion = question.trim()

    var validated: List<String>? = choices?.let { raw ->
        val trimmed = raw.map { it.trim() }.filter { it.isNotEmpty() }
        when {
            trimmed.isEmpty() -> null
            trimmed.size > MAX_CHOICES -> trimmed.take(MAX_CHOICES)
            else -> trimmed
        }
    }

    if (callback == null) {
        return JSONObject(mapOf("error" to "Clarify tool is not available in this execution context.")).toString()
    }

    return try {
        val userResponse = callback(trimmedQuestion, validated)
        val obj = JSONObject()
        obj.put("question", trimmedQuestion)
        obj.put("choices_offered", validated?.let { JSONArray(it) } ?: JSONObject.NULL)
        obj.put("user_response", userResponse.trim())
        obj.toString()
    } catch (e: Exception) {
        JSONObject(mapOf("error" to "Failed to get user input: ${e.message}")).toString()
    }
}

/** Clarify tool has no external requirements — always available. */
fun checkClarifyRequirements(): Boolean = true

val CLARIFY_SCHEMA: Map<String, Any> = mapOf(
    "name" to "clarify",
    "description" to "Ask the user a question when you need clarification, feedback, or a decision before proceeding.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "question" to mapOf("type" to "string", "description" to "The question to present to the user."),
            "choices" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "maxItems" to MAX_CHOICES,
                "description" to "Up to 4 answer choices. Omit to ask an open-ended question."),
        ),
        "required" to listOf("question"),
    ),
)
