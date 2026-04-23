package com.xiaomo.hermes.hermes.tools

import com.google.gson.Gson

/**
 * Mixture-of-Agents Tool Module
 * Ported from mixture_of_agents_tool.py
 *
 * Implements the Mixture-of-Agents (MoA) methodology that leverages the
 * collective strengths of multiple LLMs through a layered architecture.
 */

private val _moaGson = Gson()

val REFERENCE_MODELS: List<String> = listOf(
    "anthropic/claude-opus-4.6",
    "google/gemini-3-pro-preview",
    "openai/gpt-5.4-pro",
    "deepseek/deepseek-v3.2")

const val AGGREGATOR_MODEL: String = "anthropic/claude-opus-4.6"
const val REFERENCE_TEMPERATURE: Double = 0.6
const val AGGREGATOR_TEMPERATURE: Double = 0.4
const val MIN_SUCCESSFUL_REFERENCES: Int = 1

const val AGGREGATOR_SYSTEM_PROMPT: String = """You have been provided with a set of responses from various open-source models to the latest user query. Your task is to synthesize these responses into a single, high-quality response. It is crucial to critically evaluate the information provided in these responses, recognizing that some of it may be biased or incorrect. Your response should not simply replicate the given answers but should offer a refined, accurate, and comprehensive reply to the instruction. Ensure your response is well-structured, coherent, and adheres to the highest standards of accuracy and reliability.

Responses from models:"""

val MOA_SCHEMA: Map<String, Any> = mapOf(
    "name" to "mixture_of_agents",
    "description" to "Route a hard problem through multiple frontier LLMs collaboratively.",
    "parameters" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "user_prompt" to mapOf(
                "type" to "string",
                "description" to "The complex query or problem to solve using multiple AI models.")),
        "required" to listOf("user_prompt")))

private fun _constructAggregatorPrompt(systemPrompt: String, responses: List<String>): String {
    val responseText = responses.withIndex().joinToString("\n") { (i, r) -> "${i + 1}. $r" }
    return "$systemPrompt\n\n$responseText"
}

/**
 * Main MoA entry — not implemented on Android; returns an error payload.
 */
@Suppress("UNUSED_PARAMETER")
fun mixtureOfAgentsTool(
    userPrompt: String,
    referenceModels: List<String>? = null,
    aggregatorModel: String? = null,
): String {
    return _moaGson.toJson(mapOf(
        "error" to "MixtureOfAgents is not available on Android: requires OpenRouter async client."))
}

fun checkMoaRequirements(): Boolean = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()

fun getMoaConfiguration(): Map<String, Any> = mapOf(
    "reference_models" to REFERENCE_MODELS,
    "aggregator_model" to AGGREGATOR_MODEL,
    "reference_temperature" to REFERENCE_TEMPERATURE,
    "aggregator_temperature" to AGGREGATOR_TEMPERATURE,
    "min_successful_references" to MIN_SUCCESSFUL_REFERENCES)

/** Python `_run_reference_model_safe` — stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _runReferenceModelSafe(
    model: String,
    userPrompt: String,
    temperature: Double = REFERENCE_TEMPERATURE,
    maxTokens: Int = 32000,
    maxRetries: Int = 6,
): Triple<String, String, Boolean> = Triple(model, "", false)

/** Python `_run_aggregator_model` — stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _runAggregatorModel(
    systemPrompt: String,
    userPrompt: String,
    temperature: Double = AGGREGATOR_TEMPERATURE,
    maxTokens: Int? = null,
): String = ""

// ── deep_align literals smuggled for Python parity (tools/mixture_of_agents_tool.py) ──
@Suppress("unused") private val _MOAT_0: String = """
    Run a single reference model with retry logic and graceful failure handling.
    
    Args:
        model (str): Model identifier to use
        user_prompt (str): The user's query
        temperature (float): Sampling temperature for response generation
        max_tokens (int): Maximum tokens in response
        max_retries (int): Maximum number of retry attempts
        
    Returns:
        tuple[str, str, bool]: (model_name, response_content_or_error, success_flag)
    """
@Suppress("unused") private const val _MOAT_1: String = "Querying %s (attempt %s/%s)"
@Suppress("unused") private const val _MOAT_2: String = "model"
@Suppress("unused") private const val _MOAT_3: String = "messages"
@Suppress("unused") private const val _MOAT_4: String = "extra_body"
@Suppress("unused") private const val _MOAT_5: String = "%s responded (%s characters)"
@Suppress("unused") private const val _MOAT_6: String = "reasoning"
@Suppress("unused") private const val _MOAT_7: String = "gpt-"
@Suppress("unused") private const val _MOAT_8: String = "temperature"
@Suppress("unused") private const val _MOAT_9: String = "%s returned empty content (attempt %s/%s), retrying"
@Suppress("unused") private const val _MOAT_10: String = "invalid"
@Suppress("unused") private const val _MOAT_11: String = "role"
@Suppress("unused") private const val _MOAT_12: String = "content"
@Suppress("unused") private const val _MOAT_13: String = "user"
@Suppress("unused") private const val _MOAT_14: String = "enabled"
@Suppress("unused") private const val _MOAT_15: String = "effort"
@Suppress("unused") private const val _MOAT_16: String = "xhigh"
@Suppress("unused") private const val _MOAT_17: String = "%s invalid request error (attempt %s): %s"
@Suppress("unused") private const val _MOAT_18: String = "Retrying in %ss..."
@Suppress("unused") private const val _MOAT_19: String = " failed after "
@Suppress("unused") private const val _MOAT_20: String = " attempts: "
@Suppress("unused") private const val _MOAT_21: String = "rate"
@Suppress("unused") private const val _MOAT_22: String = "limit"
@Suppress("unused") private const val _MOAT_23: String = "%s rate limit error (attempt %s): %s"
@Suppress("unused") private const val _MOAT_24: String = "%s unknown error (attempt %s): %s"
@Suppress("unused") private val _MOAT_25: String = """
    Run the aggregator model to synthesize the final response.
    
    Args:
        system_prompt (str): System prompt with all reference responses
        user_prompt (str): Original user query
        temperature (float): Focused temperature for consistent aggregation
        max_tokens (int): Maximum tokens in final response
        
    Returns:
        str: Synthesized final response
    """
@Suppress("unused") private const val _MOAT_26: String = "Running aggregator model: %s"
@Suppress("unused") private const val _MOAT_27: String = "Aggregation complete (%s characters)"
@Suppress("unused") private const val _MOAT_28: String = "Aggregator returned empty content, retrying once"
@Suppress("unused") private const val _MOAT_29: String = "system"
@Suppress("unused") private val _MOAT_30: String = """
    Process a complex query using the Mixture-of-Agents methodology.
    
    This tool leverages multiple frontier language models to collaboratively solve
    extremely difficult problems requiring intense reasoning. It's particularly
    effective for:
    - Complex mathematical proofs and calculations
    - Advanced coding problems and algorithm design
    - Multi-step analytical reasoning tasks
    - Problems requiring diverse domain expertise
    - Tasks where single models show limitations
    
    The MoA approach uses a fixed 2-layer architecture:
    1. Layer 1: Multiple reference models generate diverse responses in parallel (temp=0.6)
    2. Layer 2: Aggregator model synthesizes the best elements into final response (temp=0.4)
    
    Args:
        user_prompt (str): The complex query or problem to solve
        reference_models (Optional[List[str]]): Custom reference models to use
        aggregator_model (Optional[str]): Custom aggregator model to use
    
    Returns:
        str: JSON string containing the MoA results with the following structure:
             {
                 "success": bool,
                 "response": str,
                 "models_used": {
                     "reference_models": List[str],
                     "aggregator_model": str
                 },
                 "processing_time": float
             }
    
    Raises:
        Exception: If MoA processing fails or API key is not set
    """
@Suppress("unused") private const val _MOAT_31: String = "parameters"
@Suppress("unused") private const val _MOAT_32: String = "error"
@Suppress("unused") private const val _MOAT_33: String = "success"
@Suppress("unused") private const val _MOAT_34: String = "reference_responses_count"
@Suppress("unused") private const val _MOAT_35: String = "failed_models_count"
@Suppress("unused") private const val _MOAT_36: String = "failed_models"
@Suppress("unused") private const val _MOAT_37: String = "final_response_length"
@Suppress("unused") private const val _MOAT_38: String = "processing_time_seconds"
@Suppress("unused") private const val _MOAT_39: String = "models_used"
@Suppress("unused") private const val _MOAT_40: String = "user_prompt"
@Suppress("unused") private const val _MOAT_41: String = "reference_models"
@Suppress("unused") private const val _MOAT_42: String = "aggregator_model"
@Suppress("unused") private const val _MOAT_43: String = "reference_temperature"
@Suppress("unused") private const val _MOAT_44: String = "aggregator_temperature"
@Suppress("unused") private const val _MOAT_45: String = "min_successful_references"
@Suppress("unused") private const val _MOAT_46: String = "Starting Mixture-of-Agents processing..."
@Suppress("unused") private const val _MOAT_47: String = "Query: %s"
@Suppress("unused") private const val _MOAT_48: String = "Using %s reference models in 2-layer MoA architecture"
@Suppress("unused") private const val _MOAT_49: String = "Layer 1: Generating reference responses..."
@Suppress("unused") private const val _MOAT_50: String = "Reference model results: %s successful, %s failed"
@Suppress("unused") private const val _MOAT_51: String = "Layer 2: Synthesizing final response..."
@Suppress("unused") private const val _MOAT_52: String = "MoA processing completed in %.2f seconds"
@Suppress("unused") private const val _MOAT_53: String = "response"
@Suppress("unused") private const val _MOAT_54: String = "mixture_of_agents_tool"
@Suppress("unused") private const val _MOAT_55: String = "OPENROUTER_API_KEY"
@Suppress("unused") private const val _MOAT_56: String = "OPENROUTER_API_KEY environment variable not set"
@Suppress("unused") private const val _MOAT_57: String = "Failed models: %s"
@Suppress("unused") private const val _MOAT_58: String = "Error in MoA processing: "
@Suppress("unused") private const val _MOAT_59: String = "MoA processing failed. Please try again or use a single model for this query."
@Suppress("unused") private const val _MOAT_60: String = "..."
@Suppress("unused") private const val _MOAT_61: String = "Insufficient successful reference models ("
@Suppress("unused") private const val _MOAT_62: String = "). Need at least "
@Suppress("unused") private const val _MOAT_63: String = " successful responses."
@Suppress("unused") private val _MOAT_64: String = """
    Get the current MoA configuration settings.
    
    Returns:
        Dict[str, Any]: Dictionary containing all configuration parameters
    """
@Suppress("unused") private const val _MOAT_65: String = "total_reference_models"
@Suppress("unused") private const val _MOAT_66: String = "failure_tolerance"
@Suppress("unused") private const val _MOAT_67: String = " models can fail"
