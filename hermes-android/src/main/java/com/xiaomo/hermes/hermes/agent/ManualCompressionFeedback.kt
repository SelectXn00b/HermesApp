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
