package com.ai.assistance.operit.data.model

data class CloudEmbeddingConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = ""
) {
    fun normalized(): CloudEmbeddingConfig {
        return copy(
            endpoint = endpoint.trim(),
            apiKey = apiKey.trim(),
            model = model.trim()
        )
    }

    fun isReady(): Boolean {
        val normalized = normalized()
        return normalized.enabled &&
            normalized.endpoint.isNotBlank() &&
            normalized.apiKey.isNotBlank() &&
            normalized.model.isNotBlank()
    }
}
