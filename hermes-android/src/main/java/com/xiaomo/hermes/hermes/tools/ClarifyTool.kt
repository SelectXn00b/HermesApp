package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Clarify Tool — interactive clarifying questions.
 * Ported from clarify_tool.py
 */
object ClarifyTool {

    const val MAX_CHOICES = 4

    data class ClarifyResult(
        @SerializedName("question") val question: String,
        @SerializedName("choices_offered") val choicesOffered: List<String>?,
        @SerializedName("user_response") val userResponse: String)

    data class ClarifyError(
        @SerializedName("error") val error: String)

    /**
     * Callback interface for platform-provided UI interaction.
     */
    fun interface ClarifyCallback {
        fun ask(question: String, choices: List<String>?): String
    }

    private val gson = Gson()

    /**
     * Ask the user a question, optionally with multiple-choice options.
     */
    fun clarifyTool(
        question: String,
        choices: List<String>? = null,
        callback: ClarifyCallback? = null): String {
        if (question.isBlank()) {
            return gson.toJson(ClarifyError("Question text is required."))
        }

        val trimmedQuestion = question.trim()

        // Validate and trim choices
        val validatedChoices = choices?.let {
            val trimmed = it.map { c -> c.trim() }.filter { c -> c.isNotEmpty() }
            if (trimmed.isEmpty()) null
            else trimmed.take(MAX_CHOICES)
        }

        if (callback == null) {
            return gson.toJson(ClarifyError("Clarify tool is not available in this execution context."))
        }

        return try {
            val userResponse = callback.ask(trimmedQuestion, validatedChoices)
            gson.toJson(ClarifyResult(
                question = trimmedQuestion,
                choicesOffered = validatedChoices,
                userResponse = userResponse.trim()))
        } catch (e: Exception) {
            gson.toJson(ClarifyError("Failed to get user input: ${e.message}"))
        }
    }
}
