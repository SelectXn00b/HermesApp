package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * Mixture of Agents Tool — combine responses from multiple models.
 * Ported from mixture_of_agents_tool.py
 */
object MixtureOfAgentsTool {

    private val gson = Gson()

    data class MoaResult(
        val response: String = "",
        val sources: List<String> = emptyList(),
        val error: String? = null)

    /**
     * Callback interface for calling individual models.
     */
    fun interface ModelCaller {
        fun call(model: String, prompt: String): String
    }

    /**
     * Run mixture of agents: call multiple models and aggregate.
     */
    fun run(
        prompt: String,
        models: List<String> = emptyList(),
        caller: ModelCaller? = null,
        aggregationPrompt: String? = null): String {
        if (prompt.isBlank()) return gson.toJson(mapOf("error" to "Prompt is required"))
        if (models.isEmpty()) return gson.toJson(mapOf("error" to "No models specified"))
        if (caller == null) return gson.toJson(mapOf("error" to "No model caller configured"))

        val responses = mutableListOf<Pair<String, String>>()
        for (model in models) {
            try {
                val response = caller.call(model, prompt)
                responses.add(model to response)
            } catch (e: Exception) {
                responses.add(model to "Error: ${e.message}")
            }
        }

        // Simple aggregation: return all responses
        val aggregated = responses.joinToString("\n\n---\n\n") { (model, resp) ->
            "[$model]:\n$resp"
        }

        return gson.toJson(MoaResult(
            response = aggregated,
            sources = responses.map { it.first }))
    }


}
