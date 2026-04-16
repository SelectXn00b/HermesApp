/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/server-model-catalog.ts
 */
package com.xiaomo.androidforclaw.gateway.methods

// ⚠️ DEPRECATED (2026-04-16): Part of old gateway, replaced by hermes GatewayRunner + AppChatAdapter.

import android.content.Context
import com.xiaomo.androidforclaw.config.ConfigLoader

/**
 * Models RPC methods implementation
 *
 * Provides model listing and management
 */
class ModelsMethods(
    private val context: Context
) {
    private val configLoader = ConfigLoader(context)

    /**
     * models.list() - List all available models
     *
     * Returns all models from all providers in openclaw.json
     */
    fun modelsList(): ModelsListResult {
        val models = try {
            configLoader.listAllModels().map { (provider, modelDef) ->
                ModelInfo(
                    id = modelDef.id,
                    name = modelDef.name ?: modelDef.id,
                    provider = provider,
                    contextWindow = modelDef.contextWindow ?: 200000,
                    maxTokens = modelDef.maxTokens ?: 16384,
                    reasoning = modelDef.reasoning ?: false,
                    input = modelDef.input?.map { it.toString() } ?: listOf("text"),
                    cost = modelDef.cost?.let { c ->
                        ModelCost(
                            input = c.input,
                            output = c.output,
                            cacheWrite = c.cacheWrite,
                            cacheRead = c.cacheRead
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        return ModelsListResult(models = models)
    }
}

/**
 * Models list result
 */
data class ModelsListResult(
    val models: List<ModelInfo>
)

/**
 * Model information
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val contextWindow: Int,
    val maxTokens: Int,
    val reasoning: Boolean,
    val input: List<String>,
    val cost: ModelCost? = null
)

/**
 * Model cost information
 */
data class ModelCost(
    val input: Double,
    val output: Double,
    val cacheWrite: Double? = null,
    val cacheRead: Double? = null
)
