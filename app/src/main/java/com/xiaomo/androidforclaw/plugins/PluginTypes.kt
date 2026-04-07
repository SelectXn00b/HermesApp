package com.xiaomo.androidforclaw.plugins

import com.xiaomo.androidforclaw.pluginsdk.PluginManifest

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/types.ts
 *
 * Core types for the plugin system.
 */

// ---------------------------------------------------------------------------
// Plugin kind
// ---------------------------------------------------------------------------
enum class PluginKind(val value: String) {
    MEMORY("memory"),
    CONTEXT_ENGINE("context-engine");

    companion object {
        fun fromString(raw: String): PluginKind? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Plugin origin
// ---------------------------------------------------------------------------
enum class PluginOrigin(val value: String) {
    BUNDLED("bundled"),
    WORKSPACE("workspace"),
    INSTALLED("installed"),
    EXTERNAL("external");

    companion object {
        fun fromString(raw: String): PluginOrigin? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Plugin format
// ---------------------------------------------------------------------------
enum class PluginFormat(val value: String) {
    MODULE("module"),
    BUNDLE("bundle"),
    APK("apk"),
    SKILL("skill");

    companion object {
        fun fromString(raw: String): PluginFormat? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class PluginBundleFormat(val value: String) {
    MCP("mcp"),
    SKILL("skill");

    companion object {
        fun fromString(raw: String): PluginBundleFormat? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Plugin state
// ---------------------------------------------------------------------------
enum class PluginState { REGISTERED, LOADED, ACTIVE, ERROR, DISABLED }

// ---------------------------------------------------------------------------
// Plugin capability
// ---------------------------------------------------------------------------
enum class PluginCapability {
    TOOL, COMMAND, PROVIDER, FLOW, CONTEXT_ENGINE, MEMORY, WEBHOOK
}

// ---------------------------------------------------------------------------
// Plugin registration mode
// ---------------------------------------------------------------------------
enum class PluginRegistrationMode(val value: String) {
    FULL("full"),
    VALIDATE("validate");

    companion object {
        fun fromString(raw: String): PluginRegistrationMode? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------
interface PluginLogger {
    fun debug(message: String) {}
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

// ---------------------------------------------------------------------------
// Config UI hint
// ---------------------------------------------------------------------------
data class PluginConfigUiHint(
    val label: String? = null,
    val help: String? = null,
    val tags: List<String>? = null,
    val advanced: Boolean? = null,
    val sensitive: Boolean? = null,
    val placeholder: String? = null,
)

// ---------------------------------------------------------------------------
// Config validation
// ---------------------------------------------------------------------------
sealed class PluginConfigValidation {
    data class Ok(val value: Any? = null) : PluginConfigValidation()
    data class Failed(val errors: List<String>) : PluginConfigValidation()
}

// ---------------------------------------------------------------------------
// Plugin diagnostic
// ---------------------------------------------------------------------------
data class PluginDiagnostic(
    val pluginId: String? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.WARN,
    val code: String? = null,
    val message: String,
    val source: String? = null,
)

enum class DiagnosticSeverity { INFO, WARN, ERROR }

// ---------------------------------------------------------------------------
// Plugin activation source
// ---------------------------------------------------------------------------
enum class PluginActivationSource(val value: String) {
    DISABLED("disabled"),
    EXPLICIT("explicit"),
    AUTO("auto"),
    DEFAULT("default");

    companion object {
        fun fromString(raw: String): PluginActivationSource? =
            entries.find { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

// ---------------------------------------------------------------------------
// Plugin activation state
// ---------------------------------------------------------------------------
data class PluginActivationState(
    val enabled: Boolean,
    val activated: Boolean,
    val explicitlyEnabled: Boolean,
    val source: PluginActivationSource,
    val reason: String? = null,
)

// ---------------------------------------------------------------------------
// Plugin info (replaces the older version)
// ---------------------------------------------------------------------------
data class PluginInfo(
    val manifest: PluginManifest,
    val state: PluginState = PluginState.REGISTERED,
    val capabilities: Set<PluginCapability> = emptySet(),
    val loadedAt: Long? = null,
    val errorMessage: String? = null,
    val origin: PluginOrigin = PluginOrigin.BUNDLED,
    val format: PluginFormat? = null,
    val bundleFormat: PluginBundleFormat? = null,
    val activationState: PluginActivationState? = null,
    val rootDir: String? = null,
    val source: String? = null,
)

// ---------------------------------------------------------------------------
// Plugin lifecycle
// ---------------------------------------------------------------------------
data class PluginLifecycle(
    val pluginId: String,
    val events: List<PluginLifecycleEvent> = emptyList(),
)

data class PluginLifecycleEvent(
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null,
)

// ---------------------------------------------------------------------------
// Hook names  (aligned with TS PluginHookName)
// ---------------------------------------------------------------------------
enum class PluginHookName(val value: String) {
    BEFORE_AGENT_START("before_agent_start"),
    BEFORE_AGENT_REPLY("before_agent_reply"),
    AGENT_END("agent_end"),
    BEFORE_TOOL_CALL("before_tool_call"),
    AFTER_TOOL_CALL("after_tool_call"),
    TOOL_RESULT_PERSIST("tool_result_persist"),
    BEFORE_MODEL_RESOLVE("before_model_resolve"),
    BEFORE_PROMPT_BUILD("before_prompt_build"),
    BEFORE_COMPACTION("before_compaction"),
    AFTER_COMPACTION("after_compaction"),
    BEFORE_RESET("before_reset"),
    BEFORE_DISPATCH("before_dispatch"),
    REPLY_DISPATCH("reply_dispatch"),
    INBOUND_CLAIM("inbound_claim"),
    LLM_INPUT("llm_input"),
    LLM_OUTPUT("llm_output"),
    MESSAGE_RECEIVED("message_received"),
    MESSAGE_SENDING("message_sending"),
    MESSAGE_SENT("message_sent"),
    BEFORE_MESSAGE_WRITE("before_message_write"),
    SESSION_START("session_start"),
    SESSION_END("session_end"),
    GATEWAY_START("gateway_start"),
    GATEWAY_STOP("gateway_stop"),
    SUBAGENT_SPAWNING("subagent_spawning"),
    SUBAGENT_SPAWNED("subagent_spawned"),
    SUBAGENT_ENDED("subagent_ended"),
    SUBAGENT_DELIVERY_TARGET("subagent_delivery_target"),
    BEFORE_INSTALL("before_install");

    companion object {
        private val byValue = entries.associateBy { it.value }
        fun fromString(raw: String): PluginHookName? = byValue[raw.trim()]
        fun isHookName(raw: String): Boolean = byValue.containsKey(raw.trim())
    }
}

/** Prompt injection hook names that strip prompt mutation fields from results. */
val PROMPT_INJECTION_HOOK_NAMES: Set<PluginHookName> = setOf(
    PluginHookName.BEFORE_AGENT_START,
    PluginHookName.BEFORE_AGENT_REPLY,
    PluginHookName.BEFORE_PROMPT_BUILD,
)

fun isPromptInjectionHookName(hookName: PluginHookName): Boolean =
    hookName in PROMPT_INJECTION_HOOK_NAMES
