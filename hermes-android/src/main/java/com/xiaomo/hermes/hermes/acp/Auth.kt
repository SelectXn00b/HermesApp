package com.xiaomo.hermes.hermes.acp

import com.xiaomo.hermes.hermes.agent.CredentialPool

/**
 * ACP auth helpers — detect the currently configured Hermes provider.
 *
 * Ported from acp_adapter/auth.py
 */

/** Resolve the active Hermes runtime provider, or null if unavailable. */
fun detectProvider(): String? {
    return try {
        val runtime = _resolveRuntimeProvider() ?: return null
        val apiKey = runtime["api_key"] as? String
        val provider = runtime["provider"] as? String
        if (!apiKey.isNullOrBlank() && !provider.isNullOrBlank()) {
            provider.trim().lowercase()
        } else null
    } catch (_: Exception) {
        null
    }
}

/** Return True if Hermes can resolve any runtime provider credentials. */
fun hasProvider(): Boolean = detectProvider() != null

/**
 * Android 端 runtime provider 解析：遍历常见 provider，找到第一个在
 * CredentialPool 里有可用凭证的就返回。对齐 Python
 * hermes_cli.runtime_provider.resolve_runtime_provider 的语义，但不依赖
 * hermes_cli（Android 不打包 CLI 模块）。
 *
 * 顺序取 HERMES_INFERENCE_PROVIDER env → 常见 provider 列表。
 */
private fun _resolveRuntimeProvider(): Map<String, Any?>? {
    val candidates = LinkedHashSet<String>()
    System.getenv("HERMES_INFERENCE_PROVIDER")?.trim()?.lowercase()?.let {
        if (it.isNotEmpty()) candidates.add(it)
    }
    candidates.addAll(
        listOf("anthropic", "openai", "openrouter", "nous", "gemini", "openai-codex")
    )
    for (provider in candidates) {
        val pool = try {
            CredentialPool().loadPool(provider)
        } catch (_: Exception) {
            continue
        }
        if (!pool.hasCredentials()) continue
        val entry = pool.select() ?: continue
        val apiKey = entry.runtimeApiKey
        if (apiKey.isBlank()) continue
        return mapOf(
            "provider" to provider,
            "api_key" to apiKey,
            "base_url" to entry.runtimeBaseUrl,
            "source" to "pool:$provider",
        )
    }
    return null
}
