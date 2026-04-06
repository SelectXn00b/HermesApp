package com.xiaomo.androidforclaw.agent.loop

/**
 * Failover Policy — 对齐 OpenClaw failover-policy.ts
 *
 * 决策逻辑：当 LLM 调用失败时，下一步该做什么？
 * - continue_normal: 继续正常循环
 * - rotate_profile: 轮换 API Key（Android 暂不需要，结构保留）
 * - fallback_model: 切换到备用模型
 * - surface_error: 直接返回错误给用户
 * - return_error_payload: 返回错误 payload
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/failover-policy.ts
 */

// ── Types ──

/**
 * 故障切换原因。
 * OpenClaw: FailoverReason
 */
enum class FailoverReason {
    UNKNOWN,
    AUTH_FAILURE,
    RATE_LIMIT,
    BILLING,
    OVERLOADED,
    MODEL_UNAVAILABLE,
    TIMEOUT,
    NETWORK,
    PROVIDER_ERROR,
    CONTEXT_LENGTH
}

/**
 * 故障切换决策动作。
 * OpenClaw: RunFailoverDecisionAction
 */
sealed class FailoverDecision {
    /** 继续正常循环 */
    data object ContinueNormal : FailoverDecision()

    /** 轮换 API Key */
    data class RotateProfile(val reason: FailoverReason) : FailoverDecision()

    /** 切换到备用模型 */
    data class FallbackModel(val reason: FailoverReason) : FailoverDecision()

    /** 直接返回错误给用户 */
    data class SurfaceError(val reason: FailoverReason, val message: String? = null) : FailoverDecision()

    /** 返回错误 payload（带上下文） */
    data class ReturnErrorPayload(val message: String) : FailoverDecision()
}

// ── Decision Logic ──

/**
 * 判断是否应该升级 retry limit。
 * OpenClaw: shouldEscalateRetryLimit
 */
private fun shouldEscalateRetryLimit(reason: FailoverReason?): Boolean {
    return when (reason) {
        FailoverReason.OVERLOADED,
        FailoverReason.RATE_LIMIT,
        FailoverReason.NETWORK,
        FailoverReason.PROVIDER_ERROR -> true
        else -> false
    }
}

/**
 * 判断 prompt 阶段是否应该轮换 profile。
 * OpenClaw: shouldRotatePrompt
 */
private fun shouldRotatePrompt(reason: FailoverReason): Boolean {
    return when (reason) {
        FailoverReason.AUTH_FAILURE,
        FailoverReason.RATE_LIMIT -> true
        else -> false
    }
}

/**
 * 判断 assistant 阶段是否应该轮换。
 * OpenClaw: shouldRotateAssistant
 */
private fun shouldRotateAssistant(reason: FailoverReason?, timedOut: Boolean): Boolean {
    if (timedOut) return false  // 超时不轮换
    return when (reason) {
        FailoverReason.AUTH_FAILURE,
        FailoverReason.RATE_LIMIT -> true
        else -> false
    }
}

/**
 * Retry Limit 阶段决策。
 * OpenClaw: resolveRunFailoverDecision (stage: "retry_limit")
 */
fun resolveRetryLimitDecision(
    fallbackConfigured: Boolean,
    failoverReason: FailoverReason?
): FailoverDecision {
    if (fallbackConfigured && shouldEscalateRetryLimit(failoverReason)) {
        return FailoverDecision.FallbackModel(reason = failoverReason ?: FailoverReason.UNKNOWN)
    }
    return FailoverDecision.ReturnErrorPayload(
        message = "Max retries exceeded. Last error: ${failoverReason?.name ?: "unknown"}"
    )
}

/**
 * Prompt 阶段决策。
 * OpenClaw: resolveRunFailoverDecision (stage: "prompt")
 */
fun resolvePromptDecision(
    profileRotated: Boolean,
    fallbackConfigured: Boolean,
    failoverReason: FailoverReason,
    failoverFailure: Boolean
): FailoverDecision {
    if (!profileRotated && shouldRotatePrompt(failoverReason)) {
        return FailoverDecision.RotateProfile(reason = failoverReason)
    }
    if (failoverFailure && fallbackConfigured) {
        return FailoverDecision.FallbackModel(reason = failoverReason)
    }
    return FailoverDecision.SurfaceError(reason = failoverReason)
}

/**
 * Assistant 阶段决策。
 * OpenClaw: resolveRunFailoverDecision (stage: "assistant")
 */
fun resolveAssistantDecision(
    profileRotated: Boolean,
    fallbackConfigured: Boolean,
    failoverReason: FailoverReason?,
    timedOut: Boolean
): FailoverDecision {
    val assistantShouldRotate = shouldRotateAssistant(failoverReason, timedOut)
    if (!profileRotated && assistantShouldRotate) {
        return FailoverDecision.RotateProfile(
            reason = if (timedOut) FailoverReason.TIMEOUT else (failoverReason ?: FailoverReason.UNKNOWN)
        )
    }
    if (assistantShouldRotate && fallbackConfigured) {
        return FailoverDecision.FallbackModel(
            reason = if (timedOut) FailoverReason.TIMEOUT else (failoverReason ?: FailoverReason.UNKNOWN)
        )
    }
    if (!assistantShouldRotate) {
        return FailoverDecision.ContinueNormal
    }
    return FailoverDecision.SurfaceError(reason = failoverReason ?: FailoverReason.UNKNOWN)
}

// ── Error Classification ──

/**
 * 从错误消息分类故障切换原因。
 * OpenClaw: classifyFailoverReason
 */
fun classifyFailoverReason(errorMessage: String?, statusCode: Int? = null): FailoverReason {
    val msg = errorMessage?.lowercase() ?: ""
    return when {
        statusCode == 401 || statusCode == 403 || msg.contains("unauthorized") || msg.contains("api key") || msg.contains("authentication") -> FailoverReason.AUTH_FAILURE
        statusCode == 429 || msg.contains("rate limit") || msg.contains("too many requests") || msg.contains("rate_limit") -> FailoverReason.RATE_LIMIT
        statusCode == 402 || msg.contains("billing") || msg.contains("quota") || msg.contains("insufficient") -> FailoverReason.BILLING
        statusCode == 529 || statusCode == 503 || msg.contains("overloaded") || msg.contains("capacity") -> FailoverReason.OVERLOADED
        statusCode == 404 || msg.contains("model not found") || msg.contains("model_not_found") -> FailoverReason.MODEL_UNAVAILABLE
        msg.contains("timeout") || msg.contains("timed out") -> FailoverReason.TIMEOUT
        msg.contains("network") || msg.contains("connection") || msg.contains("socket") || msg.contains("dns") -> FailoverReason.NETWORK
        msg.contains("context length") || msg.contains("context_length") || msg.contains("token limit") -> FailoverReason.CONTEXT_LENGTH
        else -> FailoverReason.PROVIDER_ERROR
    }
}
