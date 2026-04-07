package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/webhook-targets.ts
 *
 * Webhook target registration, resolution, and auth helpers.
 * Android adaptation: skip Node.js HTTP server concepts (IncomingMessage/ServerResponse);
 * provide the core target bucket registration and resolution logic.
 */

import java.util.concurrent.ConcurrentHashMap

// ---------- Types ----------

/**
 * Registered webhook target with unregister callback.
 * Aligned with TS RegisteredWebhookTarget.
 */
data class RegisteredWebhookTarget<T>(
    val target: T,
    val unregister: () -> Unit,
)

/**
 * Webhook target match result.
 * Aligned with TS WebhookTargetMatchResult.
 */
sealed class WebhookTargetMatchResult<out T> {
    data object None : WebhookTargetMatchResult<Nothing>()
    data class Single<T>(val target: T) : WebhookTargetMatchResult<T>()
    data object Ambiguous : WebhookTargetMatchResult<Nothing>()
}

// ---------- Target Registration ----------

/**
 * Add a normalized target to a path bucket and clean up route state
 * when the last target leaves.
 * Aligned with TS registerWebhookTarget.
 */
fun <T : Any> registerWebhookTarget(
    targetsByPath: ConcurrentHashMap<String, MutableList<T>>,
    target: T,
    getPath: (T) -> String,
    setPath: (T, String) -> T,
    onFirstPathTarget: ((path: String, target: T) -> (() -> Unit)?)? = null,
    onLastPathTargetRemoved: ((path: String) -> Unit)? = null,
): RegisteredWebhookTarget<T> {
    val key = normalizeWebhookPath(getPath(target))
    val normalizedTarget = setPath(target, key)

    val existing = targetsByPath.getOrPut(key) { mutableListOf() }
    val wasEmpty = existing.isEmpty()

    var teardown: (() -> Unit)? = null
    if (wasEmpty && onFirstPathTarget != null) {
        teardown = onFirstPathTarget(key, normalizedTarget)
    }

    existing.add(normalizedTarget)

    var isActive = true
    val unregister = {
        if (isActive) {
            isActive = false
            val list = targetsByPath[key]
            list?.remove(normalizedTarget)
            if (list != null && list.isEmpty()) {
                targetsByPath.remove(key)
                teardown?.invoke()
                onLastPathTargetRemoved?.invoke(key)
            }
        }
    }

    return RegisteredWebhookTarget(target = normalizedTarget, unregister = unregister)
}

// ---------- Target Resolution ----------

/**
 * Resolve all registered webhook targets for a given path.
 * Aligned with TS resolveWebhookTargets (without HTTP req dependency).
 */
fun <T> resolveWebhookTargetsForPath(
    path: String,
    targetsByPath: Map<String, List<T>>,
): Pair<String, List<T>>? {
    val normalizedPath = normalizeWebhookPath(path)
    val targets = targetsByPath[normalizedPath]
    if (targets.isNullOrEmpty()) return null
    return normalizedPath to targets
}

// ---------- Single Target Matching ----------

/**
 * Match exactly one synchronous target or report whether resolution was empty or ambiguous.
 * Aligned with TS resolveSingleWebhookTarget.
 */
fun <T> resolveSingleWebhookTarget(
    targets: List<T>,
    isMatch: (T) -> Boolean,
): WebhookTargetMatchResult<T> {
    var matched: T? = null
    for (target in targets) {
        if (!isMatch(target)) continue
        if (matched != null) return WebhookTargetMatchResult.Ambiguous
        matched = target
    }
    return if (matched != null) WebhookTargetMatchResult.Single(matched) else WebhookTargetMatchResult.None
}

/**
 * Async variant of single-target resolution for auth checks that need I/O.
 * Aligned with TS resolveSingleWebhookTargetAsync.
 */
suspend fun <T> resolveSingleWebhookTargetAsync(
    targets: List<T>,
    isMatch: suspend (T) -> Boolean,
): WebhookTargetMatchResult<T> {
    var matched: T? = null
    for (target in targets) {
        if (!isMatch(target)) continue
        if (matched != null) return WebhookTargetMatchResult.Ambiguous
        matched = target
    }
    return if (matched != null) WebhookTargetMatchResult.Single(matched) else WebhookTargetMatchResult.None
}
