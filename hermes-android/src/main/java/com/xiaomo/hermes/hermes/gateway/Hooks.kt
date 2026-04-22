package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway hooks — lightweight plugin system for intercepting messages at
 * various points in the gateway lifecycle.
 *
 * Ported from gateway/hooks.py
 */

import android.util.Log
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Enumeration of hook event types.
 *
 * The hook pipeline runs in the order listed here:
 * pre_validate → post_validate → pre_agent → post_agent → pre_send → post_send
 */
enum class HookEvent {
    /** Before the message is validated (allowlist, rate-limit, etc.). */
    PRE_VALIDATE,
    /** After validation succeeds but before the agent is invoked. */
    POST_VALIDATE,
    /** Immediately before the agent loop starts. */
    PRE_AGENT,
    /** After the agent loop finishes (before the response is sent). */
    POST_AGENT,
    /** Before the response is delivered to the platform adapter. */
    PRE_SEND,
    /** After the response is successfully sent. */
    POST_SEND,
    /** On gateway startup. */
    ON_START,
    /** On gateway shutdown. */
    ON_STOP,
    /** On platform connect. */
    ON_PLATFORM_CONNECT,
    /** On platform disconnect. */
    ON_PLATFORM_DISCONNECT,
    /** On session create. */
    ON_SESSION_CREATE,
    /** On session destroy. */
    ON_SESSION_DESTROY,
}

/**
 * Result of a hook invocation.
 *
 * Hooks can either pass (continue to the next hook) or short-circuit
 * the entire pipeline by returning a [HookResult.Halt].
 */
sealed class HookResult {
    /** Continue to the next hook. */
    object Continue : HookResult()

    /** Short-circuit the pipeline — no further hooks run. */
    data class Halt(val reason: String = "") : HookResult()

    /** Replace the message text (only valid for text-mutating hooks). */
    data class Replace(val newText: String) : HookResult()
}

/**
 * A single hook entry.
 */
data class HookEntry(
    /** Unique name for this hook (used for logging and removal). */
    val name: String,
    /** The event this hook listens to. */
    val event: HookEvent,
    /** Priority — lower runs first.  Default 100. */
    val priority: Int = 100,
    /** The hook function. */
    val handler: suspend (HookContext) -> HookResult)

/**
 * Context passed to every hook invocation.
 */
data class HookContext(
    /** The event type that triggered this hook. */
    val event: HookEvent,
    /** The session key (if applicable). */
    val sessionKey: String = "",
    /** The message text (mutable for text-mutating hooks). */
    var text: String = "",
    /** The platform name. */
    val platform: String = "",
    /** The chat id. */
    val chatId: String = "",
    /** The user id. */
    val userId: String = "",
    /** Arbitrary metadata bag. */
    val metadata: JSONObject = JSONObject())

/**
 * Gateway hook pipeline.
 *
 * Thread-safe.  Hooks are registered once at startup and invoked in
 * priority order at each lifecycle event.
 */
class HookPipeline {
    /** All registered hooks, sorted by priority. */
    private val _hooks: CopyOnWriteArrayList<HookEntry> = CopyOnWriteArrayList()

    /** Register a hook. */
    fun register(entry: HookEntry) {
        _hooks.add(entry)
        _hooks.sortBy { it.priority }
    }

    /** Remove a hook by name. */
    fun remove(name: String) {
        _hooks.removeAll { it.name == name }
    }

    /** Remove all hooks for a given event. */
    fun removeForEvent(event: HookEvent) {
        _hooks.removeAll { it.event == event }
    }

    /** True when no hooks are registered. */
    val isEmpty: Boolean get() = _hooks.isEmpty()

    /** Number of registered hooks. */
    val size: Int get() = _hooks.size

    /**
     * Run all hooks for [event] in priority order.
     *
     * Returns [HookResult.Continue] if all hooks pass, or the first
     * [HookResult.Halt] encountered.
     */
    suspend fun run(event: HookEvent, context: HookContext): HookResult {
        var ctx = context.copy(event = event)
        for (hook in _hooks) {
            if (hook.event != event) continue
            val result = hook.handler(ctx)
            when (result) {
                is HookResult.Halt -> return result
                is HookResult.Replace -> ctx = ctx.copy(text = result.newText)
                is HookResult.Continue -> { /* continue */ }
            }
        }
        return HookResult.Continue
    }

    /**
     * Run all hooks for [event] with the given parameters.
     *
     * Convenience overload that builds the [HookContext] from primitive values.
     */
    suspend fun run(
        event: HookEvent,
        sessionKey: String = "",
        text: String = "",
        platform: String = "",
        chatId: String = "",
        userId: String = ""): HookResult = run(event, HookContext(
        event = event,
        sessionKey = sessionKey,
        text = text,
        platform = platform,
        chatId = chatId,
        userId = userId))
}

/** Well-known built-in hook names. */
object BuiltinHookNames {
    const val BOOT_MD = "boot_md"
    const val RATE_LIMIT = "rate_limit"
    const val ALLOWLIST = "allowlist"
    const val PROFANITY_FILTER = "profanity_filter"
    const val COMMAND_INTERCEPT = "command_intercept"
}

/**
 * Hook registry — discovers, loads, and fires event hooks.
 * Ported from HookRegistry in gateway/hooks.py
 */
class HookRegistry {
    companion object {
        private const val _TAG = "HookRegistry"
    }

    /** event_type → [handler_fn, ...] */
    private val _handlers: java.util.concurrent.ConcurrentHashMap<String, MutableList<suspend (String, Map<String, Any?>) -> Unit>> = java.util.concurrent.ConcurrentHashMap()
    /** metadata for listing */
    private val _loadedHooks: MutableList<Map<String, Any?>> = java.util.concurrent.CopyOnWriteArrayList()

    /** Return metadata about all loaded hooks. */
    fun loadedHooks(): List<Map<String, Any?>> = _loadedHooks.toList()

    /** Register built-in hooks that are always active. */
    private fun registerBuiltinHooks() {
        // boot-md hook would be registered here on desktop; Android skips
    }

    /** Discover and load hooks from the hooks directory. */
    fun discoverAndLoad(hooksDir: java.io.File? = null) {
        registerBuiltinHooks()
        val dir = hooksDir ?: return
        if (!dir.exists() || !dir.isDirectory) return
        for (hookDir in dir.listFiles()?.sorted() ?: emptyList()) {
            if (!hookDir.isDirectory) continue
            val manifestPath = java.io.File(hookDir, "HOOK.yaml")
            val handlerPath = java.io.File(hookDir, "handler.py")
            if (!manifestPath.exists() || !handlerPath.exists()) continue
            try {
                // Parse YAML metadata (simplified)
                val text = manifestPath.readText()
                val name = Regex("""name:\s*(.+)""").find(text)?.groupValues?.get(1)?.trim() ?: hookDir.name
                val events = Regex("""events:\s*\[(.+)]""").find(text)?.groupValues?.get(1)
                    ?.split(",")?.map { it.trim().trim('"') } ?: continue
                _loadedHooks.add(mapOf("name" to name, "events" to events, "path" to hookDir.absolutePath))
            } catch (_unused: Exception) {}
        }
    }

    /** Fire all handlers registered for an event. */
    suspend fun emit(eventType: String, context: Map<String, Any?> = emptyMap()) {
        val handlers = mutableListOf<suspend (String, Map<String, Any?>) -> Unit>()
        handlers.addAll(_handlers[eventType] ?: emptyList())
        // Check for wildcard patterns (e.g., "command:*" matches "command:reset")
        if (":" in eventType) {
            val base = eventType.substringBefore(":")
            handlers.addAll(_handlers["$base:*"] ?: emptyList())
        }
        for (fn in handlers) {
            try { fn(eventType, context) } catch (_unused: Exception) {}
        }
    }

    /** Register a handler for an event. */
    fun handle(event: String, handler: suspend (String, Map<String, Any?>) -> Unit) {
        _handlers.getOrPut(event) { mutableListOf() }.add(handler)
    }

    /** Register built-in hooks that are always active. */
    fun _registerBuiltinHooks() {
        // Pre-agent: sanitize input, check rate limits
        handle("pre_agent") { sessionKey, data ->
            val text = data["text"] as? String ?: ""
            if (text.isBlank()) {
                Log.w(_TAG, "Empty message in pre-agent hook")
            }
        }
        // Post-agent: format output, log usage
        handle("post_agent") { sessionKey, data ->
            val response = data["text"] as? String ?: ""
            Log.d(_TAG, "Post-agent response length: ${response.length}")
        }
    }

}
