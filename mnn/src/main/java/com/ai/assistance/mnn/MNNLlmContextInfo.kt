package com.ai.assistance.mnn

import org.json.JSONObject

/**
 * 最近一次推理上下文的快照。
 */
data class MNNLlmContextInfo(
    val promptLen: Int,
    val generatedSeqLen: Int,
    val totalSeqLen: Int,
    val loadUs: Long,
    val visionUs: Long,
    val audioUs: Long,
    val prefillUs: Long,
    val decodeUs: Long,
    val sampleUs: Long,
    val pixelsMp: Double,
    val audioInputSeconds: Double,
    val currentToken: Int,
    val statusCode: Int,
    val status: String,
    val generatedText: String,
    val historyTokens: IntArray,
    val outputTokens: IntArray
) {
    companion object {
        internal fun fromJson(json: String): MNNLlmContextInfo {
            val obj = JSONObject(json)
            return MNNLlmContextInfo(
                promptLen = obj.optInt("prompt_len"),
                generatedSeqLen = obj.optInt("gen_seq_len"),
                totalSeqLen = obj.optInt("all_seq_len"),
                loadUs = obj.optLong("load_us"),
                visionUs = obj.optLong("vision_us"),
                audioUs = obj.optLong("audio_us"),
                prefillUs = obj.optLong("prefill_us"),
                decodeUs = obj.optLong("decode_us"),
                sampleUs = obj.optLong("sample_us"),
                pixelsMp = obj.optDouble("pixels_mp"),
                audioInputSeconds = obj.optDouble("audio_input_s"),
                currentToken = obj.optInt("current_token"),
                statusCode = obj.optInt("status_code"),
                status = obj.optString("status"),
                generatedText = obj.optString("generate_str"),
                historyTokens = obj.optIntArray("history_tokens"),
                outputTokens = obj.optIntArray("output_tokens")
            )
        }

        private fun JSONObject.optIntArray(name: String): IntArray {
            val array = optJSONArray(name) ?: return IntArray(0)
            return IntArray(array.length()) { index -> array.optInt(index) }
        }
    }
}
