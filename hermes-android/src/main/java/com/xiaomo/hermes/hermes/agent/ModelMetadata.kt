/**
 * Model metadata, context lengths, and token estimation utilities.
 *
 * 1:1 对齐 hermes/agent/model_metadata.py
 *
 * Pure utility functions with no AIAgent dependency. Used by ContextCompressor
 * and run_agent.py for pre-flight context checks.
 */
package com.xiaomo.hermes.hermes.agent

import com.xiaomo.hermes.hermes.baseUrlHostMatches
import com.xiaomo.hermes.hermes.baseUrlHostname
import com.xiaomo.hermes.hermes.getHermesHome
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

// Provider names that can appear as a "provider:" prefix before a model ID.
// Only these are stripped — Ollama-style "model:tag" colons (e.g. "qwen3.5:27b")
// are preserved so the full model name reaches cache lookups and server queries.
val _PROVIDER_PREFIXES: Set<String> = setOf(
    "openrouter", "nous", "openai-codex", "copilot", "copilot-acp",
    "gemini", "ollama-cloud", "zai", "kimi-coding", "kimi-coding-cn",
    "minimax", "minimax-cn", "anthropic", "deepseek",
    "opencode-zen", "opencode-go", "ai-gateway", "kilocode", "alibaba",
    "qwen-oauth",
    "xiaomi",
    "arcee",
    "custom", "local",
    "google", "google-gemini", "google-ai-studio",
    "glm", "z-ai", "z.ai", "zhipu", "github", "github-copilot",
    "github-models", "kimi", "moonshot", "kimi-cn", "moonshot-cn", "claude", "deep-seek",
    "ollama",
    "opencode", "zen", "go", "vercel", "kilo", "dashscope", "aliyun", "qwen",
    "mimo", "xiaomi-mimo",
    "arcee-ai", "arceeai",
    "xai", "x-ai", "x.ai", "grok",
    "nvidia", "nim", "nvidia-nim", "nemotron",
    "qwen-portal",
)


val _OLLAMA_TAG_PATTERN: Regex = Regex(
    "^(\\d+\\.?\\d*b|latest|stable|q\\d|fp?\\d|instruct|chat|coder|vision|text)",
    RegexOption.IGNORE_CASE,
)


fun _stripProviderPrefix(model: String): String {
    if (":" !in model || model.startsWith("http")) return model
    val idx = model.indexOf(':')
    val prefix = model.substring(0, idx)
    val suffix = model.substring(idx + 1)
    val prefixLower = prefix.trim().lowercase()
    if (prefixLower in _PROVIDER_PREFIXES) {
        if (_OLLAMA_TAG_PATTERN.containsMatchIn(suffix.trim())) return model
        return suffix
    }
    return model
}

private val _modelMetadataCache: MutableMap<String, Map<String, Any?>> = mutableMapOf()
private var _modelMetadataCacheTime: Long = 0
const val _MODEL_CACHE_TTL: Long = 3600L
private val _endpointModelMetadataCache: MutableMap<String, Map<String, Map<String, Any?>>> = mutableMapOf()
private val _endpointModelMetadataCacheTime: MutableMap<String, Long> = mutableMapOf()
const val _ENDPOINT_MODEL_CACHE_TTL: Long = 300L

// Descending tiers for context length probing when the model is unknown.
val CONTEXT_PROBE_TIERS: List<Int> = listOf(
    128_000,
    64_000,
    32_000,
    16_000,
    8_000,
)

// Default context length when no detection method succeeds.
val DEFAULT_FALLBACK_CONTEXT: Int = CONTEXT_PROBE_TIERS[0]

// Minimum context length required to run Hermes Agent.
val MINIMUM_CONTEXT_LENGTH: Int = 64_000

// Thin fallback defaults — only broad model family patterns.
val DEFAULT_CONTEXT_LENGTHS: Map<String, Int> = mapOf(
    "claude-opus-4-7" to 1000000,
    "claude-opus-4.7" to 1000000,
    "claude-opus-4-6" to 1000000,
    "claude-sonnet-4-6" to 1000000,
    "claude-opus-4.6" to 1000000,
    "claude-sonnet-4.6" to 1000000,
    "claude" to 200000,
    "gpt-5.4-nano" to 400000,
    "gpt-5.4-mini" to 400000,
    "gpt-5.4" to 1050000,
    "gpt-5.1-chat" to 128000,
    "gpt-5" to 400000,
    "gpt-4.1" to 1047576,
    "gpt-4" to 128000,
    "gemini" to 1048576,
    "gemma-4-31b" to 256000,
    "gemma-3" to 131072,
    "gemma" to 8192,
    "deepseek" to 128000,
    "llama" to 131072,
    "qwen3-coder-plus" to 1000000,
    "qwen3-coder" to 262144,
    "qwen" to 131072,
    "minimax" to 204800,
    "glm" to 202752,
    "grok-code-fast" to 256000,
    "grok-4-1-fast" to 2000000,
    "grok-2-vision" to 8192,
    "grok-4-fast" to 2000000,
    "grok-4.20" to 2000000,
    "grok-4" to 256000,
    "grok-3" to 131072,
    "grok-2" to 131072,
    "grok" to 131072,
    "kimi" to 262144,
    "nemotron" to 131072,
    "trinity" to 262144,
    "elephant" to 262144,
    "Qwen/Qwen3.5-397B-A17B" to 131072,
    "Qwen/Qwen3.5-35B-A3B" to 131072,
    "deepseek-ai/DeepSeek-V3.2" to 65536,
    "moonshotai/Kimi-K2.5" to 262144,
    "moonshotai/Kimi-K2.6" to 262144,
    "moonshotai/Kimi-K2-Thinking" to 262144,
    "MiniMaxAI/MiniMax-M2.5" to 204800,
    "XiaomiMiMo/MiMo-V2-Flash" to 256000,
    "mimo-v2-pro" to 1000000,
    "mimo-v2-omni" to 256000,
    "mimo-v2-flash" to 256000,
    "zai-org/GLM-5" to 202752,
)

val _CONTEXT_LENGTH_KEYS: List<String> = listOf(
    "context_length",
    "context_window",
    "max_context_length",
    "max_position_embeddings",
    "max_model_len",
    "max_input_tokens",
    "max_sequence_length",
    "max_seq_len",
    "n_ctx_train",
    "n_ctx",
)

val _MAX_COMPLETION_KEYS: List<String> = listOf(
    "max_completion_tokens",
    "max_output_tokens",
    "max_tokens",
)

val _LOCAL_HOSTS: List<String> = listOf("localhost", "127.0.0.1", "::1", "0.0.0.0")

val _CONTAINER_LOCAL_SUFFIXES: List<String> = listOf(
    ".docker.internal",
    ".containers.internal",
    ".lima.internal",
)


fun _normalizeBaseUrl(baseUrl: String?): String {
    return (baseUrl ?: "").trim().trimEnd('/')
}


fun _authHeaders(apiKey: String = ""): Map<String, String> {
    val token = apiKey.trim()
    if (token.isEmpty()) return emptyMap()
    return mapOf("Authorization" to "Bearer $token")
}


fun _isOpenrouterBaseUrl(baseUrl: String): Boolean {
    return baseUrlHostMatches(baseUrl, "openrouter.ai")
}


fun _isCustomEndpoint(baseUrl: String): Boolean {
    val normalized = _normalizeBaseUrl(baseUrl)
    return normalized.isNotEmpty() && !_isOpenrouterBaseUrl(normalized)
}


val _URL_TO_PROVIDER: Map<String, String> = linkedMapOf(
    "api.openai.com" to "openai",
    "chatgpt.com" to "openai",
    "api.anthropic.com" to "anthropic",
    "api.z.ai" to "zai",
    "api.moonshot.ai" to "kimi-coding",
    "api.moonshot.cn" to "kimi-coding-cn",
    "api.kimi.com" to "kimi-coding",
    "api.arcee.ai" to "arcee",
    "api.minimax" to "minimax",
    "dashscope.aliyuncs.com" to "alibaba",
    "dashscope-intl.aliyuncs.com" to "alibaba",
    "portal.qwen.ai" to "qwen-oauth",
    "openrouter.ai" to "openrouter",
    "generativelanguage.googleapis.com" to "gemini",
    "inference-api.nousresearch.com" to "nous",
    "api.deepseek.com" to "deepseek",
    "api.githubcopilot.com" to "copilot",
    "models.github.ai" to "copilot",
    "api.fireworks.ai" to "fireworks",
    "opencode.ai" to "opencode-go",
    "api.x.ai" to "xai",
    "integrate.api.nvidia.com" to "nvidia",
    "api.xiaomimimo.com" to "xiaomi",
    "xiaomimimo.com" to "xiaomi",
    "ollama.com" to "ollama-cloud",
)


fun _inferProviderFromUrl(baseUrl: String): String? {
    val normalized = _normalizeBaseUrl(baseUrl)
    if (normalized.isEmpty()) return null
    val withScheme = if ("://" in normalized) normalized else "https://$normalized"
    val host = try {
        val parsed = URI(withScheme)
        (parsed.host ?: parsed.path ?: "").lowercase()
    } catch (_: Exception) {
        normalized.lowercase()
    }
    for ((urlPart, provider) in _URL_TO_PROVIDER) {
        if (urlPart in host) return provider
    }
    return null
}


fun _isKnownProviderBaseUrl(baseUrl: String): Boolean {
    return _inferProviderFromUrl(baseUrl) != null
}


fun isLocalEndpoint(baseUrl: String): Boolean {
    val normalized = _normalizeBaseUrl(baseUrl)
    if (normalized.isEmpty()) return false
    val url = if ("://" in normalized) normalized else "http://$normalized"
    val host = try {
        URI(url).host ?: ""
    } catch (_: Exception) {
        return false
    }
    if (host in _LOCAL_HOSTS) return true
    if (_CONTAINER_LOCAL_SUFFIXES.any { host.endsWith(it) }) return true
    // RFC-1918 / link-local / loopback checks
    try {
        val addr = java.net.InetAddress.getByName(host)
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
            return true
        }
    } catch (_: Exception) {
        // fall through
    }
    // Bare IP in private ranges
    val parts = host.split(".")
    if (parts.size == 4) {
        try {
            val first = parts[0].toInt()
            val second = parts[1].toInt()
            if (first == 10) return true
            if (first == 172 && second in 16..31) return true
            if (first == 192 && second == 168) return true
        } catch (_: NumberFormatException) {
            // not a bare IPv4
        }
    }
    return false
}


fun detectLocalServerType(baseUrl: String, apiKey: String = ""): String? {
    val normalized = _normalizeBaseUrl(baseUrl)
    var serverUrl = normalized
    if (serverUrl.endsWith("/v1")) serverUrl = serverUrl.dropLast(3)
    val headers = _authHeaders(apiKey)

    // LM Studio exposes /api/v1/models — check first (most specific)
    _httpGet("$serverUrl/api/v1/models", headers, 2_000)?.let {
        if (it.status == 200) return "lm-studio"
    }
    // Ollama exposes /api/tags and responds with {"models": [...]}
    _httpGet("$serverUrl/api/tags", headers, 2_000)?.let {
        if (it.status == 200) {
            try {
                val data = JSONObject(it.body)
                if (data.has("models")) return "ollama"
            } catch (_: Exception) {
                // fall through
            }
        }
    }
    // llama.cpp exposes /v1/props (older builds used /props)
    _httpGet("$serverUrl/v1/props", headers, 2_000)?.let { r ->
        val body = if (r.status == 200) r.body else _httpGet("$serverUrl/props", headers, 2_000)?.body ?: ""
        if (body.contains("default_generation_settings")) return "llamacpp"
    }
    // vLLM: /version
    _httpGet("$serverUrl/version", headers, 2_000)?.let {
        if (it.status == 200) {
            try {
                val data = JSONObject(it.body)
                if (data.has("version")) return "vllm"
            } catch (_: Exception) {
                // fall through
            }
        }
    }
    return null
}


fun _iterNestedDicts(value: Any?): Sequence<Map<String, Any?>> = sequence {
    when (value) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val asMap = value as Map<String, Any?>
            yield(asMap)
            for (nested in asMap.values) yieldAll(_iterNestedDicts(nested))
        }
        is JSONObject -> {
            val m = mutableMapOf<String, Any?>()
            for (k in value.keys()) m[k] = value.opt(k)
            yield(m)
            for (nested in m.values) yieldAll(_iterNestedDicts(nested))
        }
        is List<*> -> {
            for (item in value) yieldAll(_iterNestedDicts(item))
        }
        is JSONArray -> {
            for (i in 0 until value.length()) yieldAll(_iterNestedDicts(value.opt(i)))
        }
    }
}


fun _coerceReasonableInt(value: Any?, minimum: Int = 1024, maximum: Int = 10_000_000): Int? {
    val result: Int = try {
        when (value) {
            is Boolean -> return null
            is Number -> value.toInt()
            is String -> value.trim().replace(",", "").toInt()
            else -> return null
        }
    } catch (_: Exception) {
        return null
    }
    return if (result in minimum..maximum) result else null
}


fun _extractFirstInt(payload: Map<String, Any?>, keys: List<String>): Int? {
    val keyset = keys.map { it.lowercase() }.toSet()
    for (mapping in _iterNestedDicts(payload)) {
        for ((key, value) in mapping) {
            if (key.toString().lowercase() !in keyset) continue
            val coerced = _coerceReasonableInt(value)
            if (coerced != null) return coerced
        }
    }
    return null
}


fun _extractContextLength(payload: Map<String, Any?>): Int? {
    return _extractFirstInt(payload, _CONTEXT_LENGTH_KEYS)
}


fun _extractMaxCompletionTokens(payload: Map<String, Any?>): Int? {
    return _extractFirstInt(payload, _MAX_COMPLETION_KEYS)
}


fun _extractPricing(payload: Map<String, Any?>): Map<String, Any?> {
    val aliasMap: Map<String, List<String>> = mapOf(
        "prompt" to listOf("prompt", "input", "input_cost_per_token", "prompt_token_cost"),
        "completion" to listOf("completion", "output", "output_cost_per_token", "completion_token_cost"),
        "request" to listOf("request", "request_cost"),
        "cache_read" to listOf("cache_read", "cached_prompt", "input_cache_read", "cache_read_cost_per_token"),
        "cache_write" to listOf("cache_write", "cache_creation", "input_cache_write", "cache_write_cost_per_token"),
    )
    for (mapping in _iterNestedDicts(payload)) {
        val normalized: Map<String, Any?> = mapping.entries.associate { (k, v) -> k.toString().lowercase() to v }
        if (aliasMap.values.none { aliases -> aliases.any { it in normalized } }) continue
        val pricing = mutableMapOf<String, Any?>()
        for ((target, aliases) in aliasMap) {
            for (alias in aliases) {
                if (alias in normalized && normalized[alias] != null && normalized[alias] != "") {
                    pricing[target] = normalized[alias]
                    break
                }
            }
        }
        if (pricing.isNotEmpty()) return pricing
    }
    return emptyMap()
}


fun _addModelAliases(cache: MutableMap<String, Map<String, Any?>>, modelId: String, entry: Map<String, Any?>) {
    cache[modelId] = entry
    if ("/" in modelId) {
        val bare = modelId.substringAfter("/")
        cache.putIfAbsent(bare, entry)
    }
}


fun fetchModelMetadata(forceRefresh: Boolean = false): Map<String, Map<String, Any?>> {
    val now = System.currentTimeMillis() / 1000L
    if (!forceRefresh && _modelMetadataCache.isNotEmpty() && (now - _modelMetadataCacheTime) < _MODEL_CACHE_TTL) {
        return _modelMetadataCache
    }
    val openrouterUrl = "https://openrouter.ai/api/v1/models"
    val resp = _httpGet(openrouterUrl, emptyMap(), 10_000) ?: return _modelMetadataCache
    if (resp.status !in 200..299) return _modelMetadataCache
    try {
        val data = JSONObject(resp.body)
        val list = data.optJSONArray("data") ?: return _modelMetadataCache
        val cache = mutableMapOf<String, Map<String, Any?>>()
        for (i in 0 until list.length()) {
            val model = list.optJSONObject(i) ?: continue
            val modelId = model.optString("id", "")
            if (modelId.isEmpty()) continue
            val topProvider = model.optJSONObject("top_provider")
            val entry = mapOf(
                "context_length" to model.optInt("context_length", 128_000),
                "max_completion_tokens" to (topProvider?.optInt("max_completion_tokens", 4096) ?: 4096),
                "name" to model.optString("name", modelId),
                "pricing" to _jsonToMap(model.optJSONObject("pricing")),
            )
            _addModelAliases(cache, modelId, entry)
            val canonical = model.optString("canonical_slug", "")
            if (canonical.isNotEmpty() && canonical != modelId) {
                _addModelAliases(cache, canonical, entry)
            }
        }
        _modelMetadataCache.clear()
        _modelMetadataCache.putAll(cache)
        _modelMetadataCacheTime = now
        return _modelMetadataCache
    } catch (_: Exception) {
        return _modelMetadataCache
    }
}


fun fetchEndpointModelMetadata(
    baseUrl: String,
    apiKey: String = "",
    forceRefresh: Boolean = false,
): Map<String, Map<String, Any?>> {
    val normalized = _normalizeBaseUrl(baseUrl)
    if (normalized.isEmpty() || _isOpenrouterBaseUrl(normalized)) return emptyMap()

    val now = System.currentTimeMillis() / 1000L
    if (!forceRefresh) {
        val cached = _endpointModelMetadataCache[normalized]
        val cachedAt = _endpointModelMetadataCacheTime[normalized] ?: 0L
        if (cached != null && (now - cachedAt) < _ENDPOINT_MODEL_CACHE_TTL) return cached
    }

    val candidates = mutableListOf(normalized)
    val alternate = if (normalized.endsWith("/v1")) normalized.dropLast(3).trimEnd('/')
    else "$normalized/v1"
    if (alternate.isNotEmpty() && alternate !in candidates) candidates.add(alternate)

    val headers = _authHeaders(apiKey)

    if (isLocalEndpoint(normalized)) {
        try {
            if (detectLocalServerType(normalized, apiKey) == "lm-studio") {
                val serverUrl = if (normalized.endsWith("/v1")) normalized.dropLast(3).trimEnd('/') else normalized
                val resp = _httpGet(serverUrl.trimEnd('/') + "/api/v1/models", headers, 10_000)
                if (resp != null && resp.status in 200..299) {
                    val payload = JSONObject(resp.body)
                    val cache = mutableMapOf<String, Map<String, Any?>>()
                    val models = payload.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until models.length()) {
                        val m = models.optJSONObject(i) ?: continue
                        val modelId = m.optString("key", "").ifEmpty { m.optString("id", "") }
                        if (modelId.isEmpty()) continue
                        val entry = mutableMapOf<String, Any?>(
                            "name" to m.optString("name", modelId),
                        )
                        var contextLength: Int? = null
                        val insts = m.optJSONArray("loaded_instances") ?: JSONArray()
                        for (j in 0 until insts.length()) {
                            val inst = insts.optJSONObject(j) ?: continue
                            val cfg = inst.optJSONObject("config")
                            val ctx = cfg?.optInt("context_length", -1) ?: -1
                            if (ctx > 0) { contextLength = ctx; break }
                        }
                        if (contextLength == null) contextLength = _extractContextLength(_jsonToMap(m))
                        if (contextLength != null) entry["context_length"] = contextLength
                        val maxCompletion = _extractMaxCompletionTokens(_jsonToMap(m))
                        if (maxCompletion != null) entry["max_completion_tokens"] = maxCompletion
                        val pricing = _extractPricing(_jsonToMap(m))
                        if (pricing.isNotEmpty()) entry["pricing"] = pricing
                        _addModelAliases(cache, modelId, entry)
                        val altId = m.optString("id", "")
                        if (altId.isNotEmpty() && altId != modelId) _addModelAliases(cache, altId, entry)
                    }
                    _endpointModelMetadataCache[normalized] = cache
                    _endpointModelMetadataCacheTime[normalized] = now
                    return cache
                }
            }
        } catch (_: Exception) {
            // fall through to candidate loop
        }
    }

    for (candidate in candidates) {
        val url = candidate.trimEnd('/') + "/models"
        val resp = _httpGet(url, headers, 10_000) ?: continue
        if (resp.status !in 200..299) continue
        try {
            val payload = JSONObject(resp.body)
            val cache = mutableMapOf<String, Map<String, Any?>>()
            val list = payload.optJSONArray("data") ?: JSONArray()
            var isLlamacpp = false
            for (i in 0 until list.length()) {
                val m = list.optJSONObject(i) ?: continue
                if (m.optString("owned_by") == "llamacpp") isLlamacpp = true
                val modelId = m.optString("id", "")
                if (modelId.isEmpty()) continue
                val entry = mutableMapOf<String, Any?>(
                    "name" to m.optString("name", modelId),
                )
                val contextLength = _extractContextLength(_jsonToMap(m))
                if (contextLength != null) entry["context_length"] = contextLength
                val maxCompletion = _extractMaxCompletionTokens(_jsonToMap(m))
                if (maxCompletion != null) entry["max_completion_tokens"] = maxCompletion
                val pricing = _extractPricing(_jsonToMap(m))
                if (pricing.isNotEmpty()) entry["pricing"] = pricing
                _addModelAliases(cache, modelId, entry)
            }
            if (isLlamacpp) {
                try {
                    val base = candidate.trimEnd('/').replace("/v1", "")
                    var propsResp = _httpGet("$base/v1/props", headers, 5_000)
                    if (propsResp == null || propsResp.status !in 200..299) {
                        propsResp = _httpGet("$base/props", headers, 5_000)
                    }
                    if (propsResp != null && propsResp.status in 200..299) {
                        val props = JSONObject(propsResp.body)
                        val genSettings = props.optJSONObject("default_generation_settings")
                        val nCtx = genSettings?.optInt("n_ctx", -1) ?: -1
                        val modelAlias = props.optString("model_alias", "")
                        if (nCtx > 0 && modelAlias.isNotEmpty() && modelAlias in cache) {
                            val updated = cache[modelAlias]!!.toMutableMap()
                            updated["context_length"] = nCtx
                            cache[modelAlias] = updated
                        }
                    }
                } catch (_: Exception) {
                    // fall through
                }
            }
            _endpointModelMetadataCache[normalized] = cache
            _endpointModelMetadataCacheTime[normalized] = now
            return cache
        } catch (_: Exception) {
            continue
        }
    }

    _endpointModelMetadataCache[normalized] = emptyMap()
    _endpointModelMetadataCacheTime[normalized] = now
    return emptyMap()
}


fun _getContextCachePath(): File {
    return File(getHermesHome(), "context_length_cache.yaml")
}


fun _loadContextCache(): MutableMap<String, Int> {
    val path = _getContextCachePath()
    if (!path.exists()) return mutableMapOf()
    return try {
        val text = path.readText(Charsets.UTF_8).trim()
        if (text.isEmpty()) return mutableMapOf()
        val parsed = _parseSimpleYamlMap(text)
        val ctxLens = parsed["context_lengths"]
        if (ctxLens is Map<*, *>) {
            val out = mutableMapOf<String, Int>()
            for ((k, v) in ctxLens) {
                val key = k?.toString() ?: continue
                val value = (v as? Number)?.toInt() ?: v?.toString()?.toIntOrNull() ?: continue
                out[key] = value
            }
            out
        } else mutableMapOf()
    } catch (_: Exception) {
        mutableMapOf()
    }
}


fun saveContextLength(model: String, baseUrl: String, length: Int) {
    val key = "$model@$baseUrl"
    val cache = _loadContextCache()
    if (cache[key] == length) return
    cache[key] = length
    val path = _getContextCachePath()
    try {
        path.parentFile?.mkdirs()
        val body = StringBuilder("context_lengths:\n")
        for ((k, v) in cache) body.append("  \"$k\": $v\n")
        path.writeText(body.toString(), Charsets.UTF_8)
    } catch (_: Exception) {
        // best-effort
    }
}


fun getCachedContextLength(model: String, baseUrl: String): Int? {
    val key = "$model@$baseUrl"
    return _loadContextCache()[key]
}


fun getNextProbeTier(currentLength: Int): Int? {
    for (tier in CONTEXT_PROBE_TIERS) {
        if (tier < currentLength) return tier
    }
    return null
}


fun parseContextLimitFromError(errorMsg: String): Int? {
    val errorLower = errorMsg.lowercase()
    val patterns = listOf(
        Regex("(?:max(?:imum)?|limit)\\s*(?:context\\s*)?(?:length|size|window)?\\s*(?:is|of|:)?\\s*(\\d{4,})"),
        Regex("context\\s*(?:length|size|window)\\s*(?:is|of|:)?\\s*(\\d{4,})"),
        Regex("(\\d{4,})\\s*(?:token)?\\s*(?:context|limit)"),
        Regex(">\\s*(\\d{4,})\\s*(?:max|limit|token)"),
        Regex("(\\d{4,})\\s*(?:max(?:imum)?)\\b"),
    )
    for (pattern in patterns) {
        val match = pattern.find(errorLower) ?: continue
        val limit = match.groupValues[1].toIntOrNull() ?: continue
        if (limit in 1024..10_000_000) return limit
    }
    return null
}


fun parseAvailableOutputTokensFromError(errorMsg: String): Int? {
    val errorLower = errorMsg.lowercase()
    val isOutputCapError = "max_tokens" in errorLower &&
        ("available_tokens" in errorLower || "available tokens" in errorLower)
    if (!isOutputCapError) return null
    val patterns = listOf(
        Regex("available_tokens[:\\s]+(\\d+)"),
        Regex("available\\s+tokens[:\\s]+(\\d+)"),
        Regex("=\\s*(\\d+)\\s*$"),
    )
    for (pattern in patterns) {
        val match = pattern.find(errorLower) ?: continue
        val tokens = match.groupValues[1].toIntOrNull() ?: continue
        if (tokens >= 1) return tokens
    }
    return null
}


fun _modelIdMatches(candidateId: String, lookupModel: String): Boolean {
    if (candidateId == lookupModel) return true
    if ("/" in candidateId && candidateId.substringAfterLast("/") == lookupModel) return true
    return false
}


fun queryOllamaNumCtx(model: String, baseUrl: String, apiKey: String = ""): Int? {
    val bareModel = _stripProviderPrefix(model)
    var serverUrl = baseUrl.trimEnd('/')
    if (serverUrl.endsWith("/v1")) serverUrl = serverUrl.dropLast(3)
    val serverType = try { detectLocalServerType(baseUrl, apiKey) } catch (_: Exception) { return null }
    if (serverType != "ollama") return null
    val headers = _authHeaders(apiKey)
    val resp = _httpPost("$serverUrl/api/show", headers, """{"name":"$bareModel"}""", 3_000)
        ?: return null
    if (resp.status !in 200..299) return null
    return try {
        val data = JSONObject(resp.body)
        val params = data.optString("parameters", "")
        if ("num_ctx" in params) {
            for (line in params.split("\n")) {
                if ("num_ctx" in line) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) parts.last().toIntOrNull()?.let { return it }
                }
            }
        }
        val modelInfo = data.optJSONObject("model_info") ?: return null
        for (key in modelInfo.keys()) {
            if ("context_length" in key) {
                val value = modelInfo.opt(key)
                if (value is Number) return value.toInt()
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}


fun _queryLocalContextLength(model: String, baseUrl: String, apiKey: String = ""): Int? {
    val bareModel = _stripProviderPrefix(model)
    var serverUrl = baseUrl.trimEnd('/')
    if (serverUrl.endsWith("/v1")) serverUrl = serverUrl.dropLast(3)
    val headers = _authHeaders(apiKey)
    val serverType = try { detectLocalServerType(baseUrl, apiKey) } catch (_: Exception) { null }

    if (serverType == "ollama") {
        val resp = _httpPost("$serverUrl/api/show", headers, """{"name":"$bareModel"}""", 3_000)
        if (resp != null && resp.status in 200..299) {
            try {
                val data = JSONObject(resp.body)
                val params = data.optString("parameters", "")
                if ("num_ctx" in params) {
                    for (line in params.split("\n")) {
                        if ("num_ctx" in line) {
                            val parts = line.trim().split(Regex("\\s+"))
                            if (parts.size >= 2) parts.last().toIntOrNull()?.let { return it }
                        }
                    }
                }
                val modelInfo = data.optJSONObject("model_info")
                if (modelInfo != null) {
                    for (key in modelInfo.keys()) {
                        if ("context_length" in key) {
                            val value = modelInfo.opt(key)
                            if (value is Number) return value.toInt()
                        }
                    }
                }
            } catch (_: Exception) {
                // fall through
            }
        }
    }

    if (serverType == "lm-studio") {
        val resp = _httpGet("$serverUrl/api/v1/models", headers, 3_000)
        if (resp != null && resp.status in 200..299) {
            try {
                val data = JSONObject(resp.body)
                val models = data.optJSONArray("models") ?: JSONArray()
                for (i in 0 until models.length()) {
                    val m = models.optJSONObject(i) ?: continue
                    if (_modelIdMatches(m.optString("key", ""), bareModel) ||
                        _modelIdMatches(m.optString("id", ""), bareModel)
                    ) {
                        val insts = m.optJSONArray("loaded_instances") ?: JSONArray()
                        for (j in 0 until insts.length()) {
                            val inst = insts.optJSONObject(j) ?: continue
                            val cfg = inst.optJSONObject("config") ?: continue
                            val ctx = cfg.optInt("context_length", -1)
                            if (ctx > 0) return ctx
                        }
                        val ctx = m.optInt("max_context_length", -1).takeIf { it > 0 }
                            ?: m.optInt("context_length", -1).takeIf { it > 0 }
                        if (ctx != null) return ctx
                    }
                }
            } catch (_: Exception) {
                // fall through
            }
        }
    }

    val resp1 = _httpGet("$serverUrl/v1/models/$bareModel", headers, 3_000)
    if (resp1 != null && resp1.status in 200..299) {
        try {
            val data = JSONObject(resp1.body)
            val ctx = data.optInt("max_model_len", -1).takeIf { it > 0 }
                ?: data.optInt("context_length", -1).takeIf { it > 0 }
                ?: data.optInt("max_tokens", -1).takeIf { it > 0 }
            if (ctx != null) return ctx
        } catch (_: Exception) {
            // fall through
        }
    }

    val resp2 = _httpGet("$serverUrl/v1/models", headers, 3_000)
    if (resp2 != null && resp2.status in 200..299) {
        try {
            val data = JSONObject(resp2.body)
            val modelsList = data.optJSONArray("data") ?: JSONArray()
            for (i in 0 until modelsList.length()) {
                val m = modelsList.optJSONObject(i) ?: continue
                if (_modelIdMatches(m.optString("id", ""), bareModel)) {
                    val ctx = m.optInt("max_model_len", -1).takeIf { it > 0 }
                        ?: m.optInt("context_length", -1).takeIf { it > 0 }
                        ?: m.optInt("max_tokens", -1).takeIf { it > 0 }
                    if (ctx != null) return ctx
                }
            }
        } catch (_: Exception) {
            // fall through
        }
    }
    return null
}


fun _normalizeModelVersion(model: String): String {
    return model.replace(".", "-")
}


fun _queryAnthropicContextLength(model: String, baseUrl: String, apiKey: String): Int? {
    if (apiKey.isEmpty() || apiKey.startsWith("sk-ant-oat")) return null
    return try {
        var base = baseUrl.trimEnd('/')
        if (base.endsWith("/v1")) base = base.dropLast(3)
        val url = "$base/v1/models?limit=1000"
        val headers = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
        )
        val resp = _httpGet(url, headers, 10_000) ?: return null
        if (resp.status !in 200..299) return null
        val data = JSONObject(resp.body)
        val list = data.optJSONArray("data") ?: return null
        for (i in 0 until list.length()) {
            val m = list.optJSONObject(i) ?: continue
            if (m.optString("id") == model) {
                val ctx = m.optInt("max_input_tokens", -1)
                if (ctx > 0) return ctx
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}


fun _resolveNousContextLength(model: String): Int? {
    val metadata = fetchModelMetadata()
    metadata[model]?.get("context_length")?.let { v ->
        (v as? Number)?.toInt()?.let { return it }
    }
    val normalized = _normalizeModelVersion(model).lowercase()
    for ((orId, entry) in metadata) {
        val bare = if ("/" in orId) orId.substringAfter("/") else orId
        if (bare.lowercase() == model.lowercase() || _normalizeModelVersion(bare).lowercase() == normalized) {
            (entry["context_length"] as? Number)?.toInt()?.let { return it }
        }
    }
    val modelLower = model.lowercase()
    for ((orId, entry) in metadata) {
        val bare = if ("/" in orId) orId.substringAfter("/") else orId
        for ((candidate, query) in listOf(
            bare.lowercase() to modelLower,
            _normalizeModelVersion(bare).lowercase() to normalized,
        )) {
            if (candidate.startsWith(query) &&
                (candidate.length == query.length || candidate[query.length] in "-:.")
            ) {
                (entry["context_length"] as? Number)?.toInt()?.let { return it }
            }
        }
    }
    return null
}


fun getModelContextLength(
    model: String,
    baseUrl: String = "",
    apiKey: String = "",
    configContextLength: Int? = null,
    provider: String = "",
): Int {
    if (configContextLength != null && configContextLength > 0) return configContextLength

    val strippedModel = _stripProviderPrefix(model)

    if (baseUrl.isNotEmpty()) {
        getCachedContextLength(strippedModel, baseUrl)?.let { return it }
    }

    if (_isCustomEndpoint(baseUrl) && !_isKnownProviderBaseUrl(baseUrl)) {
        val endpointMetadata = fetchEndpointModelMetadata(baseUrl, apiKey)
        var matched: Map<String, Any?>? = endpointMetadata[strippedModel]
        if (matched == null) {
            if (endpointMetadata.size == 1) {
                matched = endpointMetadata.values.first()
            } else {
                for ((key, entry) in endpointMetadata) {
                    if (strippedModel in key || key in strippedModel) {
                        matched = entry
                        break
                    }
                }
            }
        }
        if (matched != null) {
            (matched["context_length"] as? Number)?.toInt()?.let { return it }
        }
        if (!_isKnownProviderBaseUrl(baseUrl)) {
            if (isLocalEndpoint(baseUrl)) {
                val localCtx = _queryLocalContextLength(strippedModel, baseUrl, apiKey)
                if (localCtx != null && localCtx > 0) {
                    saveContextLength(strippedModel, baseUrl, localCtx)
                    return localCtx
                }
            }
            return DEFAULT_FALLBACK_CONTEXT
        }
    }

    if (provider == "anthropic" || (baseUrl.isNotEmpty() && baseUrlHostname(baseUrl) == "api.anthropic.com")) {
        val ctx = _queryAnthropicContextLength(
            strippedModel,
            baseUrl.ifEmpty { "https://api.anthropic.com" },
            apiKey,
        )
        if (ctx != null) return ctx
    }

    // Bedrock lookup is skipped — adapter lives in BedrockAdapter.kt.

    var effectiveProvider = provider
    if (effectiveProvider.isEmpty() || effectiveProvider in setOf("openrouter", "custom")) {
        if (baseUrl.isNotEmpty()) _inferProviderFromUrl(baseUrl)?.let { effectiveProvider = it }
    }

    if (effectiveProvider == "nous") {
        _resolveNousContextLength(strippedModel)?.let { return it }
    }
    // models.dev provider-aware lookup is intentionally omitted — ModelsDev.kt
    // exposes the registry via a class API rather than a module function.

    val metadata = fetchModelMetadata()
    if (strippedModel in metadata) {
        (metadata[strippedModel]?.get("context_length") as? Number)?.toInt()?.let { return it }
    }

    val modelLower = strippedModel.lowercase()
    val sorted = DEFAULT_CONTEXT_LENGTHS.entries.sortedByDescending { it.key.length }
    for ((defaultModel, length) in sorted) {
        if (defaultModel in modelLower) return length
    }

    if (baseUrl.isNotEmpty() && isLocalEndpoint(baseUrl)) {
        val localCtx = _queryLocalContextLength(strippedModel, baseUrl, apiKey)
        if (localCtx != null && localCtx > 0) {
            saveContextLength(strippedModel, baseUrl, localCtx)
            return localCtx
        }
    }

    return DEFAULT_FALLBACK_CONTEXT
}


fun estimateTokensRough(text: String): Int {
    if (text.isEmpty()) return 0
    return (text.length + 3) / 4
}


fun estimateMessagesTokensRough(messages: List<Map<String, Any?>>): Int {
    val totalChars = messages.sumOf { it.toString().length }
    return (totalChars + 3) / 4
}


fun estimateRequestTokensRough(
    messages: List<Map<String, Any?>>,
    systemPrompt: String = "",
    tools: List<Map<String, Any?>>? = null,
): Int {
    var totalChars = 0
    if (systemPrompt.isNotEmpty()) totalChars += systemPrompt.length
    if (messages.isNotEmpty()) totalChars += messages.sumOf { it.toString().length }
    if (tools != null) totalChars += tools.toString().length
    return (totalChars + 3) / 4
}


// ── Private helpers (not counted as Python module functions) ─────────────

private data class _HttpResponse(val status: Int, val body: String)

private fun _httpGet(url: String, headers: Map<String, String>, timeoutMs: Int): _HttpResponse? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
            ?: ""
        conn.disconnect()
        _HttpResponse(status, body)
    } catch (_: Exception) {
        null
    }
}

private fun _httpPost(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): _HttpResponse? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val resp = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
            ?: ""
        conn.disconnect()
        _HttpResponse(status, resp)
    } catch (_: Exception) {
        null
    }
}

private fun _jsonToMap(obj: JSONObject?): Map<String, Any?> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, Any?>()
    for (k in obj.keys()) {
        val v = obj.opt(k)
        out[k] = when (v) {
            is JSONObject -> _jsonToMap(v)
            is JSONArray -> _jsonToList(v)
            JSONObject.NULL -> null
            else -> v
        }
    }
    return out
}

private fun _jsonToList(arr: JSONArray): List<Any?> {
    val out = mutableListOf<Any?>()
    for (i in 0 until arr.length()) {
        val v = arr.opt(i)
        out.add(
            when (v) {
                is JSONObject -> _jsonToMap(v)
                is JSONArray -> _jsonToList(v)
                JSONObject.NULL -> null
                else -> v
            }
        )
    }
    return out
}

/** Minimal YAML reader for the flat `context_lengths` shape we persist. */
private fun _parseSimpleYamlMap(text: String): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val nested = mutableMapOf<String, Int>()
    val stack = mutableListOf<Pair<Int, MutableMap<String, Any?>>>(0 to result)
    for (raw in text.split("\n")) {
        if (raw.isBlank() || raw.trim().startsWith("#")) continue
        val indent = raw.takeWhile { it == ' ' }.length
        val line = raw.trim()
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val key = line.substring(0, colon).trim().trim('"', '\'')
        val value = line.substring(colon + 1).trim()
        while (stack.size > 1 && stack.last().first >= indent) stack.removeAt(stack.size - 1)
        val container = stack.last().second
        if (value.isEmpty()) {
            val child = mutableMapOf<String, Any?>()
            container[key] = child
            stack.add(indent + 2 to child)
        } else {
            val coerced: Any? = value.toIntOrNull() ?: value.toDoubleOrNull() ?: value.trim('"', '\'')
            container[key] = coerced
        }
        nested[key] = indent
    }
    return result
}
