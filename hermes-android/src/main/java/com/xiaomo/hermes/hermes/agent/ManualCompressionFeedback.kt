package com.xiaomo.hermes.hermes.agent

/**
 * Manual Compression Feedback - 压缩反馈
 * 1:1 对齐 hermes/agent/manual_compression_feedback.py
 *
 * 用户手动触发压缩后的反馈回调。
 */

interface ManualCompressionFeedback {
    /**
     * 压缩完成后的回调
     *
     * @param originalTokens 压缩前 token 数
     * @param compressedTokens 压缩后 token 数
     * @param removedCount 被移除的消息数
     */
    fun onCompressionComplete(
        originalTokens: Int,
        compressedTokens: Int,
        removedCount: Int
    )

    /**
     * 压缩失败时的回调
     *
     * @param error 错误信息
     */
    fun onCompressionFailed(error: String)
}

/**
 * 默认实现：空操作
 */
class NoOpCompressionFeedback : ManualCompressionFeedback {
    override fun onCompressionComplete(originalTokens: Int, compressedTokens: Int, removedCount: Int) {
        // no-op
    }

    override fun onCompressionFailed(error: String) {
        // no-op
    }


}

/** Python `summarize_manual_compression` — user-facing feedback for manual compression. */
fun summarizeManualCompression(
    beforeMessages: List<Map<String, Any?>>,
    afterMessages: List<Map<String, Any?>>,
    beforeTokens: Int,
    afterTokens: Int,
): Map<String, Any?> {
    val beforeCount = beforeMessages.size
    val afterCount = afterMessages.size
    val noop = afterMessages == beforeMessages

    val headline: String
    val tokenLine: String
    if (noop) {
        headline = "No changes from compression: %,d messages".format(beforeCount)
        tokenLine = if (afterTokens == beforeTokens) {
            "Rough transcript estimate: ~%,d tokens (unchanged)".format(beforeTokens)
        } else {
            "Rough transcript estimate: ~%,d → ~%,d tokens".format(beforeTokens, afterTokens)
        }
    } else {
        headline = "Compressed: %,d → %,d messages".format(beforeCount, afterCount)
        tokenLine = "Rough transcript estimate: ~%,d → ~%,d tokens".format(beforeTokens, afterTokens)
    }

    var note: String? = null
    if (!noop && afterCount < beforeCount && afterTokens > beforeTokens) {
        note = "Note: fewer messages can still raise this rough transcript estimate when compression rewrites the transcript into denser summaries."
    }

    return mapOf(
        "noop" to noop,
        "headline" to headline,
        "token_line" to tokenLine,
        "note" to note,
    )
}
