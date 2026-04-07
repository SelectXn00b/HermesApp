package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/runtime.ts
 *   (prepareSecretsRuntimeSnapshot, activateSecretsRuntimeSnapshot,
 *    getActiveSecretsRuntimeSnapshot, clearSecretsRuntimeSnapshot)
 *
 * AndroidForClaw adaptation: runtime secrets management.
 * Manages secrets lifecycle (prepare -> activate -> query -> clear).
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log

/**
 * Auth store snapshot for a single agent directory.
 * Aligned with TS PreparedSecretsRuntimeSnapshot.authStores.
 */
data class AuthStoreSnapshot(
    val agentDir: String,
    val store: Map<String, Any?> = emptyMap()
)

/**
 * Runtime secrets snapshot.
 * Aligned with TS PreparedSecretsRuntimeSnapshot.
 */
data class SecretsRuntimeSnapshot(
    val sourceConfig: OpenClawConfig,
    val config: OpenClawConfig,
    val authStores: List<AuthStoreSnapshot> = emptyList(),
    val webTools: RuntimeWebToolsMetadata = RuntimeWebToolsMetadata(),
    val warnings: List<SecretResolverWarning> = emptyList(),
    val preparedAt: Long = System.currentTimeMillis()
)

/**
 * Secret target - a config path that expects a secret value.
 * Aligned with TS target-registry.ts.
 */
data class SecretTarget(
    val configPath: String,
    val description: String,
    val required: Boolean = false
)

/**
 * SecretsRuntime - Runtime secrets management.
 * Aligned with TS secrets/runtime.ts.
 */
object SecretsRuntime {

    private const val TAG = "SecretsRuntime"

    @Volatile
    private var activeSnapshot: SecretsRuntimeSnapshot? = null

    /** Well-known secret targets for validation. */
    val SECRET_TARGETS = listOf(
        SecretTarget("channels.feishu.appSecret", "Feishu App Secret", true),
        SecretTarget("channels.feishu.encryptKey", "Feishu Encrypt Key"),
        SecretTarget("channels.discord.token", "Discord Bot Token", true),
        SecretTarget("channels.telegram.botToken", "Telegram Bot Token", true),
        SecretTarget("channels.slack.botToken", "Slack Bot Token", true),
        SecretTarget("channels.slack.appToken", "Slack App Token"),
        SecretTarget("channels.whatsapp.phoneNumber", "WhatsApp Phone Number"),
        SecretTarget("gateway.authToken", "Gateway Auth Token", true),
        SecretTarget("models.providers.*.apiKey", "Provider API Key", true)
    )

    /**
     * Prepare a secrets runtime snapshot.
     * Aligned with TS prepareSecretsRuntimeSnapshot.
     *
     * Collects secret assignments from the config, resolves refs via env/file providers,
     * applies resolved values, and returns a snapshot with warnings.
     */
    suspend fun prepare(
        config: OpenClawConfig,
        authStores: List<AuthStoreSnapshot> = emptyList(),
        envOverrides: Map<String, String> = emptyMap(),
        secretProviders: Map<String, SecretProviderConfig> = emptyMap()
    ): SecretsRuntimeSnapshot {
        val context = ResolverContext(
            envOverrides = envOverrides,
            cache = SecretRefResolveCache()
        )

        // Collect secret input assignments from config
        collectConfigAssignments(config, context)

        // Resolve all collected assignments
        if (context.assignments.isNotEmpty()) {
            val refs = context.assignments.map { it.ref }
            try {
                val resolved = resolveSecretRefValues(
                    refs = refs,
                    options = ResolveSecretRefOptions(
                        secretProviders = secretProviders,
                        envOverrides = envOverrides,
                        cache = context.cache
                    )
                )
                applyResolvedAssignments(
                    assignments = context.assignments,
                    resolved = resolved
                )
            } catch (e: Exception) {
                Log.w(TAG, "Secret resolution failed: ${e.message}")
                context.pushWarning(SecretResolverWarning(
                    code = SecretResolverWarningCode.SECRETS_REF_OVERRIDES_PLAINTEXT,
                    path = "secrets",
                    message = "Secret resolution error: ${e.message}"
                ))
            }
        }

        // Validate required targets
        validateSecretTargets(config, context)

        // Resolve web tools metadata
        val webTools = resolveRuntimeWebTools(config, context)

        return SecretsRuntimeSnapshot(
            sourceConfig = config,
            config = config,
            authStores = authStores,
            webTools = webTools,
            warnings = context.warnings.toList()
        )
    }

    /**
     * Activate a prepared snapshot as the current runtime state.
     * Aligned with TS activateSecretsRuntimeSnapshot.
     */
    fun activate(snapshot: SecretsRuntimeSnapshot) {
        activeSnapshot = snapshot
        RuntimeWebToolsState.set(snapshot.webTools)
        Log.i(TAG, "Secrets runtime snapshot activated (${snapshot.warnings.size} warnings)")
    }

    /**
     * Get the currently active snapshot.
     * Aligned with TS getActiveSecretsRuntimeSnapshot.
     */
    fun getActiveSnapshot(): SecretsRuntimeSnapshot? = activeSnapshot

    /**
     * Clear the active snapshot.
     * Aligned with TS clearSecretsRuntimeSnapshot.
     */
    fun clear() {
        activeSnapshot = null
        RuntimeWebToolsState.clear()
        Log.d(TAG, "Secrets runtime snapshot cleared")
    }

    /**
     * Get active web tools metadata.
     * Aligned with TS getActiveRuntimeWebToolsMetadata.
     */
    fun getActiveWebToolsMetadata(): RuntimeWebToolsMetadata? =
        RuntimeWebToolsState.get()

    /**
     * Resolve a secret reference to its value from the active snapshot or env.
     * Android adaptation: supports env and file sources. Exec is not supported.
     */
    suspend fun resolveSecretRef(
        ref: SecretRef,
        options: ResolveSecretRefOptions = ResolveSecretRefOptions()
    ): Any? = resolveSecretRefValue(ref, options)

    /**
     * Resolve a simple secret ref by source/key to a string.
     * Convenience wrapper for backward compatibility.
     */
    fun resolveSimpleSecretRef(source: String, key: String, fallback: String? = null): String? {
        return when (source) {
            "env" -> System.getenv(key) ?: fallback
            "config" -> {
                val config = activeSnapshot?.config
                resolveConfigPath(config, key) ?: fallback
            }
            "file" -> {
                try {
                    val content = java.io.File(key).readText().trim()
                    content.ifEmpty { fallback }
                } catch (_: Exception) {
                    fallback
                }
            }
            "exec" -> {
                Log.w(TAG, "Secret source 'exec' not supported on Android: $key")
                fallback
            }
            else -> fallback
        }
    }

    // ---------- Private helpers ----------

    /**
     * Collect secret input assignments from config paths.
     * Simplified version of TS collectConfigAssignments.
     */
    private fun collectConfigAssignments(config: OpenClawConfig, context: ResolverContext) {
        // Provider API keys
        config.resolveProviders().forEach { (providerId, providerConfig) ->
            val apiKey = providerConfig.apiKey
            if (apiKey != null) {
                val ref = coerceSecretRef(apiKey as? Map<*, *>, null)
                if (ref != null) {
                    collectSecretInputAssignment(
                        value = apiKey,
                        path = "models.providers.$providerId.apiKey",
                        expected = SecretExpectedResolvedValue.STRING,
                        defaults = null,
                        context = context,
                        apply = { /* On Android config is immutable at this point */ }
                    )
                }
            }
        }
    }

    private fun validateSecretTargets(config: OpenClawConfig, context: ResolverContext) {
        for (target in SECRET_TARGETS) {
            if (!target.required) continue
            val value = resolveConfigPath(config, target.configPath)
            if (value.isNullOrBlank()) {
                context.pushWarning(SecretResolverWarning(
                    code = SecretResolverWarningCode.SECRETS_REF_OVERRIDES_PLAINTEXT,
                    path = target.configPath,
                    message = "${target.description} is not configured"
                ))
            }
        }
    }

    /**
     * Resolve web tools metadata from config.
     * Simplified version of TS resolveRuntimeWebTools.
     */
    private fun resolveRuntimeWebTools(
        config: OpenClawConfig,
        context: ResolverContext
    ): RuntimeWebToolsMetadata {
        return RuntimeWebToolsMetadata(
            search = RuntimeWebSearchMetadata(),
            fetch = RuntimeWebFetchMetadata(),
            diagnostics = emptyList()
        )
    }

    private fun resolveConfigPath(config: OpenClawConfig?, path: String): String? {
        if (config == null) return null
        return when (path) {
            "channels.feishu.appSecret" -> config.channels.feishu.appSecret
            "channels.feishu.encryptKey" -> config.channels.feishu.encryptKey
            "channels.discord.token" -> config.channels.discord?.token
            "channels.telegram.botToken" -> config.channels.telegram?.botToken
            "channels.slack.botToken" -> config.channels.slack?.botToken
            "channels.slack.appToken" -> config.channels.slack?.appToken
            "gateway.authToken" -> config.gateway.auth?.token
            else -> null
        }
    }
}
