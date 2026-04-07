package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-tools.ts
 *
 * AndroidForClaw adaptation: unified function-call dispatcher across universal tools and Android tools.
 */

import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.secrets.ResolveSecretRefOptions
import com.xiaomo.androidforclaw.secrets.SecretRef
import com.xiaomo.androidforclaw.secrets.SecretRefSource
import com.xiaomo.androidforclaw.secrets.DEFAULT_SECRET_PROVIDER_ALIAS
import com.xiaomo.androidforclaw.secrets.resolveSecretRefValues

/**
 * Unified tool/function dispatcher.
 *
 * Goal:
 * - Keep function-call scheduling closer to OpenClaw's single dispatch entry
 * - Avoid duplicating tool routing logic inside AgentLoop
 * - Let non-Android backends such as Termux live in the universal tool layer
 */
class ToolCallDispatcher(
    private val toolRegistry: ToolRegistry,
    private val androidToolRegistry: AndroidToolRegistry,
    private val extraTools: Map<String, Tool> = emptyMap()
) {
    companion object {
        private const val TAG = "ToolCallDispatcher"
    }

    fun resolve(name: String): DispatchTarget? {
        return when {
            extraTools.containsKey(name) -> DispatchTarget.Extra(name)
            toolRegistry.contains(name) -> DispatchTarget.Universal(name)
            androidToolRegistry.contains(name) -> DispatchTarget.Android(name)
            else -> null
        }
    }

    suspend fun execute(name: String, args: Map<String, Any?>): SkillResult {
        // Pre-execution: resolve any secret refs embedded in tool args
        val resolvedArgs = resolveSecretsForTool(name, args)

        return when (val target = resolve(name)) {
            is DispatchTarget.Extra -> {
                Log.d(TAG, "Dispatch → extra tool: ${target.name}")
                extraTools[target.name]!!.execute(resolvedArgs)
            }
            is DispatchTarget.Universal -> {
                Log.d(TAG, "Dispatch → universal tool: ${target.name}")
                toolRegistry.execute(target.name, resolvedArgs)
            }
            is DispatchTarget.Android -> {
                Log.d(TAG, "Dispatch → android tool: ${target.name}")
                androidToolRegistry.execute(target.name, resolvedArgs)
            }
            null -> {
                Log.e(TAG, "Unknown function: $name")
                SkillResult.error("Unknown function: $name")
            }
        }
    }

    /**
     * Resolve secret references in tool arguments.
     * Looks for string values matching the pattern "secret:env:<provider>:<id>"
     * and replaces them with the resolved secret value.
     */
    private suspend fun resolveSecretsForTool(toolName: String, args: Map<String, Any?>): Map<String, Any?> {
        val secretRefs = mutableMapOf<String, SecretRef>()
        for ((key, value) in args) {
            if (value is String && value.startsWith("secret:")) {
                val parts = value.removePrefix("secret:").split(":", limit = 3)
                if (parts.size == 3) {
                    val source = when (parts[0]) {
                        "env" -> SecretRefSource.ENV
                        "file" -> SecretRefSource.FILE
                        else -> null
                    }
                    if (source != null) {
                        secretRefs[key] = SecretRef(source = source, provider = parts[1], id = parts[2])
                    }
                }
            }
        }
        if (secretRefs.isEmpty()) return args

        return try {
            val resolved = resolveSecretRefValues(
                refs = secretRefs.values.toList(),
                options = ResolveSecretRefOptions()
            )
            val mutableArgs = args.toMutableMap()
            for ((argKey, ref) in secretRefs) {
                val refKey = "${ref.source.value}:${ref.provider}:${ref.id}"
                resolved[refKey]?.let { mutableArgs[argKey] = it }
            }
            Log.d(TAG, "Resolved ${secretRefs.size} secret(s) for tool: $toolName")
            mutableArgs
        } catch (e: Exception) {
            Log.w(TAG, "Secret resolution failed for tool $toolName: ${e.message}")
            args // Fall back to original args
        }
    }

    sealed class DispatchTarget(open val name: String) {
        data class Universal(override val name: String) : DispatchTarget(name)
        data class Android(override val name: String) : DispatchTarget(name)
        data class Extra(override val name: String) : DispatchTarget(name)
    }
}
