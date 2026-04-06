package com.xiaomo.androidforclaw.agent.loop

/**
 * Stop Reason Recovery — 对齐 OpenClaw attempt.stop-reason-recovery.ts
 *
 * 检测 LLM 返回的异常 stop_reason（如 "Unhandled stop reason: xxx"），
 * 转为友好错误信息返回给用户。
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/attempt.stop-reason-recovery.ts
 */

private val UNHANDLED_STOP_REASON_RE = Regex("""^Unhandled stop reason:\s*(.+)$""", RegexOption.IGNORE_CASE)

/**
 * 格式化未处理的 stop reason 为友好错误信息。
 * OpenClaw: formatUnhandledStopReasonErrorMessage
 */
fun formatUnhandledStopReasonErrorMessage(stopReason: String): String {
    return "The model stopped because the provider returned an unhandled stop reason: $stopReason. Please rephrase and try again."
}

/**
 * 检测并规范化 "Unhandled stop reason: xxx" 格式的消息。
 * OpenClaw: normalizeUnhandledStopReasonMessage
 *
 * @return 友好错误信息，如果不是未处理的 stop reason 则返回 null
 */
fun normalizeUnhandledStopReasonMessage(message: Any?): String? {
    val str = message as? String ?: return null
    val match = UNHANDLED_STOP_REASON_RE.matchEntire(str.trim()) ?: return null
    val stopReason = match.groupValues[1].trim()
    if (stopReason.isEmpty()) return null
    return formatUnhandledStopReasonErrorMessage(stopReason)
}

/**
 * 检测 finish_reason 是否为未处理的 stop reason。
 * OpenClaw: patchUnhandledStopReasonInAssistantMessage (stopReason check)
 *
 * @param finishReason LLM 返回的 finish_reason
 * @return 如果是异常 stop reason 则返回友好错误信息，否则返回 null
 */
fun detectUnhandledStopReason(finishReason: String?): String? {
    if (finishReason == null) return null
    return normalizeUnhandledStopReasonMessage(finishReason)
}
