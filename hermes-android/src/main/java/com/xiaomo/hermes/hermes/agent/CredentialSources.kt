package com.xiaomo.hermes.hermes.agent

/**
 * Unified removal contract for every credential source Hermes reads from.
 *
 * Hermes seeds its credential pool from many places:
 *
 *     env:<VAR>     — os.environ / ~/.hermes/.env
 *     claude_code   — ~/.claude/.credentials.json
 *     hermes_pkce   — ~/.hermes/.anthropic_oauth.json
 *     device_code   — auth.json providers.<provider> (nous, openai-codex, ...)
 *     qwen-cli      — ~/.qwen/oauth_creds.json
 *     gh_cli        — gh auth token
 *     config:<name> — custom_providers config entry
 *     model_config  — model.api_key when model.provider == "custom"
 *     manual        — user ran `hermes auth add`
 *
 * Each source registers a [RemovalStep] that cleans external state,
 * suppresses the (provider, source_id) in auth.json, and returns a
 * [RemovalResult] describing what happened.
 *
 * Ported from agent/credential_sources.py
 */

import java.io.File

/**
 * Outcome of removing a credential source.
 *
 * @property cleaned Short strings describing external state that was actually mutated.
 * @property hints Diagnostic lines ABOUT state the user may need to clean up themselves.
 * @property suppress Whether to call suppressCredentialSource after cleanup.
 */
data class RemovalResult(
    val cleaned: MutableList<String> = mutableListOf(),
    val hints: MutableList<String> = mutableListOf(),
    val suppress: Boolean = true)

/**
 * How to remove one specific credential source cleanly.
 *
 * @property provider Provider pool key. Special value "*" means "matches any provider".
 * @property sourceId Source identifier as it appears in PooledCredential.source.
 * @property matchFn Optional predicate overriding literal sourceId matching.
 * @property removeFn (provider, removedEntry) -> RemovalResult. Does the cleanup.
 * @property description One-line human-readable description for docs / tests.
 */
data class RemovalStep(
    val provider: String,
    val sourceId: String,
    val removeFn: (String, Any?) -> RemovalResult,
    val matchFn: ((String) -> Boolean)? = null,
    val description: String = "") {

    fun matches(provider: String, source: String): Boolean {
        if (this.provider != "*" && this.provider != provider) return false
        matchFn?.let { return it(source) }
        return source == sourceId
    }
}

private val _REGISTRY: MutableList<RemovalStep> = mutableListOf()

fun register(step: RemovalStep): RemovalStep {
    _REGISTRY.add(step)
    return step
}

/**
 * Return the first matching RemovalStep, or null if unregistered.
 *
 * Unregistered sources fall through to the default remove path.
 */
fun findRemovalStep(provider: String, source: String): RemovalStep? {
    for (step in _REGISTRY) {
        if (step.matches(provider, source)) return step
    }
    return null
}

// ---------------------------------------------------------------------------
// Individual RemovalStep implementations — one per source.
// ---------------------------------------------------------------------------

/** env:<VAR> — the most common case. */
private fun _removeEnvSource(provider: String, removed: Any?): RemovalResult {
    // TODO: port hermes_cli.config get_env_path / remove_env_value
    val result = RemovalResult()
    val envVar = _getSource(removed).removePrefix("env:")
    if (envVar.isEmpty()) return result

    val envInProcess = !System.getenv(envVar).isNullOrEmpty()
    val envInDotenv = false  // TODO: check ~/.hermes/.env
    val shellExported = envInProcess && !envInDotenv

    if (shellExported) {
        result.hints.add("Note: $envVar is still set in your shell environment (not in ~/.hermes/.env).")
        result.hints.add("  Unset it there (shell profile, systemd EnvironmentFile, launchd plist, etc.) or it will keep being visible to Hermes.")
        result.hints.add("  The pool entry is now suppressed — Hermes will ignore $envVar until you run `hermes auth add $provider`.")
    } else {
        result.hints.add("Suppressed env:$envVar — it will not be re-seeded even if the variable is re-exported later.")
    }
    return result
}

/** ~/.claude/.credentials.json is owned by Claude Code itself. */
private fun _removeClaudeCode(provider: String, removed: Any?): RemovalResult {
    val result = RemovalResult()
    result.hints.add("Suppressed claude_code credential — it will not be re-seeded.")
    result.hints.add("Note: Claude Code credentials still live in ~/.claude/.credentials.json")
    result.hints.add("Run `hermes auth add anthropic` to re-enable if needed.")
    return result
}

/** ~/.hermes/.anthropic_oauth.json is ours — delete it outright. */
private fun _removeHermesPkce(provider: String, removed: Any?): RemovalResult {
    val result = RemovalResult()
    val hermesHome = File(System.getProperty("user.home") ?: "/", ".hermes")
    val oauthFile = File(hermesHome, ".anthropic_oauth.json")
    if (oauthFile.exists()) {
        try {
            if (oauthFile.delete()) {
                result.cleaned.add("Cleared Hermes Anthropic OAuth credentials")
            }
        } catch (e: SecurityException) {
            result.hints.add("Could not delete ${oauthFile.absolutePath}: ${e.message}")
        }
    }
    return result
}

/** Delete auth_store.providers[provider]. Returns True if deleted. */
private fun _clearAuthStoreProvider(provider: String): Boolean {
    // TODO: port hermes_cli.auth _load_auth_store / _save_auth_store
    return false
}

/** Nous OAuth lives in auth.json providers.nous — clear it and suppress. */
private fun _removeNousDeviceCode(provider: String, removed: Any?): RemovalResult {
    val result = RemovalResult()
    if (_clearAuthStoreProvider(provider)) {
        result.cleaned.add("Cleared $provider OAuth tokens from auth store")
    }
    return result
}

/** Codex tokens live in auth store AND ~/.codex/auth.json. */
private fun _removeCodexDeviceCode(provider: String, removed: Any?): RemovalResult {
    val result = RemovalResult()
    if (_clearAuthStoreProvider(provider)) {
        result.cleaned.add("Cleared $provider OAuth tokens from auth store")
    }
    // TODO: port suppress_credential_source(provider, "device_code")
    result.hints.add("Suppressed openai-codex device_code source — it will not be re-seeded.")
    result.hints.add("Note: Codex CLI credentials still live in ~/.codex/auth.json")
    result.hints.add("Run `hermes auth add openai-codex` to re-enable if needed.")
    return result
}

/** ~/.qwen/oauth_creds.json is owned by the Qwen CLI. */
private fun _removeQwenCli(provider: String, removed: Any?): RemovalResult {
    val result = RemovalResult()
    result.hints.add("Suppressed qwen-cli credential — it will not be re-seeded.")
    result.hints.add("Note: Qwen CLI credentials still live in ~/.qwen/oauth_creds.json")
    result.hints.add("Run `hermes auth add qwen-oauth` to re-enable if needed.")
    return result
}

/** Copilot token comes from `gh auth token` or COPILOT_GITHUB_TOKEN / GH_TOKEN / GITHUB_TOKEN. */
private fun _removeCopilotGh(provider: String, removed: Any?): RemovalResult {
    // TODO: port suppress_credential_source for gh_cli + env vars
    return RemovalResult(
        hints = mutableListOf(
            "Suppressed all copilot token sources (gh_cli + env vars) — they will not be re-seeded.",
            "Note: Your gh CLI / shell environment is unchanged.",
            "Run `hermes auth add copilot` to re-enable if needed."))
}

/** Custom provider pools are seeded from custom_providers config or model.api_key. */
private fun _removeCustomConfig(provider: String, removed: Any?): RemovalResult {
    val sourceLabel = _getSource(removed)
    return RemovalResult(
        hints = mutableListOf(
            "Suppressed $sourceLabel — it will not be re-seeded.",
            "Note: The underlying value in config.yaml is unchanged.  Edit it directly if you want to remove the credential from disk."))
}

private fun _getSource(removed: Any?): String {
    if (removed == null) return ""
    if (removed is Map<*, *>) return (removed["source"] as? String) ?: ""
    return try {
        val field = removed.javaClass.getDeclaredField("source")
        field.isAccessible = true
        (field.get(removed) as? String) ?: ""
    } catch (_: Exception) {
        ""
    }
}

/**
 * Called once on module import.
 *
 * ORDER MATTERS — findRemovalStep returns the first match.
 */
private fun _registerAllSources() {
    register(RemovalStep(
        provider = "copilot", sourceId = "gh_cli",
        matchFn = { src -> src == "gh_cli" || src.startsWith("env:") },
        removeFn = ::_removeCopilotGh,
        description = "gh auth token / COPILOT_GITHUB_TOKEN / GH_TOKEN"))
    register(RemovalStep(
        provider = "*", sourceId = "env:",
        matchFn = { src -> src.startsWith("env:") },
        removeFn = ::_removeEnvSource,
        description = "Any env-seeded credential (XAI_API_KEY, DEEPSEEK_API_KEY, etc.)"))
    register(RemovalStep(
        provider = "anthropic", sourceId = "claude_code",
        removeFn = ::_removeClaudeCode,
        description = "~/.claude/.credentials.json"))
    register(RemovalStep(
        provider = "anthropic", sourceId = "hermes_pkce",
        removeFn = ::_removeHermesPkce,
        description = "~/.hermes/.anthropic_oauth.json"))
    register(RemovalStep(
        provider = "nous", sourceId = "device_code",
        removeFn = ::_removeNousDeviceCode,
        description = "auth.json providers.nous"))
    register(RemovalStep(
        provider = "openai-codex", sourceId = "device_code",
        matchFn = { src -> src == "device_code" || src.endsWith(":device_code") },
        removeFn = ::_removeCodexDeviceCode,
        description = "auth.json providers.openai-codex + ~/.codex/auth.json"))
    register(RemovalStep(
        provider = "qwen-oauth", sourceId = "qwen-cli",
        removeFn = ::_removeQwenCli,
        description = "~/.qwen/oauth_creds.json"))
    register(RemovalStep(
        provider = "*", sourceId = "config:",
        matchFn = { src -> src.startsWith("config:") || src == "model_config" },
        removeFn = ::_removeCustomConfig,
        description = "Custom provider config.yaml api_key field"))
}

private val _initRegistry: Unit = _registerAllSources()
