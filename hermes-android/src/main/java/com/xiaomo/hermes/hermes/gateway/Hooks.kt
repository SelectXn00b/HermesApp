package com.xiaomo.hermes.hermes.gateway

/**
 * Gateway hooks — lightweight plugin system for intercepting messages at
 * various points in the gateway lifecycle.
 *
 * Ported from gateway/hooks.py
 */

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

