package com.xiaomo.hermes.hermes.tools

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OpenRouter API client for Hermes tools.
 * Ported from openrouter_client.py
 *
 * Provides a single lazy-initialized client that all tool modules can share.
 */

private var _client: OkHttpClient? = null

/**
 * Return a shared async OpenAI-compatible client for OpenRouter.
 *
 * The client is created lazily on first call and reused thereafter.
 * Throws IllegalArgumentException if OPENROUTER_API_KEY is not set.
 */
fun getAsyncClient(): OkHttpClient {
    if (_client == null) {
        // Python calls resolve_provider_client("openrouter", async_mode=True);
        // the Android port constructs OkHttp directly but keeps the provider key for alignment.
        val _provider = "openrouter"
        if (System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) {
            throw IllegalArgumentException("OPENROUTER_API_KEY environment variable not set")
        }
        _client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    return _client!!
}

/**
 * Check whether the OpenRouter API key is present.
 */
fun checkApiKey(): Boolean = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
