package com.ai.assistance.operit.data.model

enum class MemoryScoreMode {
    BALANCED,
    KEYWORD_FIRST,
    SEMANTIC_FIRST
}

data class MemorySearchConfig(
    val scoreMode: MemoryScoreMode = MemoryScoreMode.BALANCED,
    val keywordWeight: Float = 10.0f,
    val tagWeight: Float = 0.0f,
    val vectorWeight: Float = 0.0f,
    val edgeWeight: Float = 0.4f
) {
    fun normalized(): MemorySearchConfig {
        return copy(
            keywordWeight = keywordWeight.coerceAtLeast(0.0f),
            tagWeight = tagWeight.coerceAtLeast(0.0f),
            vectorWeight = vectorWeight.coerceAtLeast(0.0f),
            edgeWeight = edgeWeight.coerceAtLeast(0.0f)
        )
    }
}
