package com.xiaomo.androidforclaw.agent.loop

/**
 * Incomplete Turn Detector — 对齐 OpenClaw incomplete-turn.ts
 *
 * 检测三种场景：
 * 1. Incomplete Turn: LLM stop_reason 为 toolUse/error 但没有 payload → 注入警告
 * 2. Planning-Only: LLM 只描述计划但没调用工具 → 注入重试指令
 * 3. ACK Fast Path: 用户说"ok"/"go ahead"等 → 注入执行指令
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/incomplete-turn.ts
 */

// ── 正则常量 ──

/** 匹配规划承诺语句 */
private val PLANNING_ONLY_PROMISE_RE = Regex(
    """\b(?:i(?:'ll| will)|let me|going to|first[, ]+i(?:'ll| will)|next[, ]+i(?:'ll| will)|i can do that)\b""",
    RegexOption.IGNORE_CASE
)

/** 匹配完成语句（不应该触发重试） */
private val PLANNING_ONLY_COMPLETION_RE = Regex(
    """\b(?:done|finished|implemented|updated|fixed|changed|ran|verified|found|here(?:'s| is) what|blocked by|the blocker is)\b""",
    RegexOption.IGNORE_CASE
)

/** ACK 执行确认词集合 */
private val ACK_EXECUTION_NORMALIZED_SET = setOf(
    "ok", "okay", "ok do it", "okay do it", "do it", "go ahead",
    "please do", "sounds good", "sounds good do it", "ship it",
    "fix it", "make it so", "yes do it", "yep do it",
    // Arabic
    "تمام", "حسنا", "حسنًا", "امض قدما", "نفذها",
    // German
    "mach es", "leg los", "los geht s", "weiter",
    // Japanese
    "やって", "進めて", "そのまま進めて",
    // French
    "allez y", "vas y", "fais le", "continue",
    // Spanish
    "hazlo", "adelante", "sigue",
    // Portuguese
    "faz isso", "vai em frente", "pode fazer",
    // Korean
    "해줘", "진행해", "계속해",
)

// ── 指令常量 ──

/** Planning-Only 重试指令。OpenClaw: PLANNING_ONLY_RETRY_INSTRUCTION */
const val PLANNING_ONLY_RETRY_INSTRUCTION =
    "The previous assistant turn only described the plan. Do not restate the plan. Act now: take the first concrete tool action you can. If a real blocker prevents action, reply with the exact blocker in one sentence."

/** ACK 执行快速路径指令。OpenClaw: ACK_EXECUTION_FAST_PATH_INSTRUCTION */
const val ACK_EXECUTION_FAST_PATH_INSTRUCTION =
    "The latest user message is a short approval to proceed. Do not recap or restate the plan. Start with the first concrete tool action immediately. Keep any user-facing follow-up brief and natural."

// ── Data Classes ──

/**
 * 计划详情。
 * OpenClaw: PlanningOnlyPlanDetails
 */
data class PlanningOnlyPlanDetails(
    val explanation: String,
    val steps: List<String>
)

/**
 * 回放元数据。
 * OpenClaw: buildAttemptReplayMetadata
 */
data class AttemptReplayMetadata(
    val hadPotentialSideEffects: Boolean,
    val replaySafe: Boolean
)

/**
 * Incomplete Turn 检测参数。
 * OpenClaw: resolveIncompleteTurnPayloadText params
 */
data class IncompleteTurnParams(
    val payloadCount: Int,
    val aborted: Boolean,
    val timedOut: Boolean,
    val hasClientToolCall: Boolean,
    val yieldDetected: Boolean,
    val didSendDeterministicApprovalPrompt: Boolean,
    val lastToolError: String?,
    val stopReason: String?,
    val hadPotentialSideEffects: Boolean
)

/**
 * Planning-Only 检测参数。
 * OpenClaw: resolvePlanningOnlyRetryInstruction params
 */
data class PlanningOnlyParams(
    val provider: String?,
    val modelId: String?,
    val aborted: Boolean,
    val timedOut: Boolean,
    val hasClientToolCall: Boolean,
    val yieldDetected: Boolean,
    val didSendDeterministicApprovalPrompt: Boolean,
    val didSendViaMessagingTool: Boolean,
    val lastToolError: String?,
    val startedToolCount: Int,
    val hadPotentialSideEffects: Boolean,
    val stopReason: String?,
    val assistantText: String?
)

/**
 * ACK Fast Path 参数。
 * OpenClaw: resolveAckExecutionFastPathInstruction params
 */
data class AckExecutionParams(
    val provider: String?,
    val modelId: String?,
    val prompt: String
)

// ── GPT-5 检测（Planning-Only 仅对 GPT-5 生效）──

/**
 * OpenClaw: shouldApplyPlanningOnlyRetryGuard
 * 仅对 openai/openai-codex provider + gpt-5* 模型生效
 */
private fun shouldApplyPlanningOnlyRetryGuard(provider: String?, modelId: String?): Boolean {
    val p = provider?.lowercase()?.trim() ?: return false
    if (p != "openai" && p != "openai-codex") return false
    return Regex("""^gpt-5(?:[.-]|$)""", RegexOption.IGNORE_CASE).matches(modelId ?: "")
}

// ── NFKC 规范化 ──

/**
 * OpenClaw: normalizeAckPrompt
 */
private fun normalizeAckPrompt(text: String): String {
    return text
        .trim()
        .lowercase()
        .replace(Regex("""[\p{P}\p{S}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

// ── 步骤提取 ──

/**
 * OpenClaw: extractPlanningOnlySteps
 */
private fun extractPlanningOnlySteps(text: String): List<String> {
    val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val bulletLines = lines
        .map { it.replace(Regex("""^[-*•]\s+|^\d+[.)]\s+"""), "").trim() }
        .filter { it.isNotEmpty() }
    if (bulletLines.size >= 2) return bulletLines.take(4)
    return text.split(Regex("""(?<=[.!?])\s+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(4)
}

// ── 公开函数 ──

/**
 * 检测用户输入是否为执行确认。
 * OpenClaw: isLikelyExecutionAckPrompt
 */
fun isLikelyExecutionAckPrompt(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 80 || trimmed.contains("\n") || trimmed.contains("?")) {
        return false
    }
    return ACK_EXECUTION_NORMALIZED_SET.contains(normalizeAckPrompt(trimmed))
}

/**
 * 解析 ACK 执行快速路径指令。
 * OpenClaw: resolveAckExecutionFastPathInstruction
 *
 * @return 快速路径指令，如果不符合条件则返回 null
 */
fun resolveAckExecutionFastPathInstruction(params: AckExecutionParams): String? {
    if (!shouldApplyPlanningOnlyRetryGuard(params.provider, params.modelId)) return null
    if (!isLikelyExecutionAckPrompt(params.prompt)) return null
    return ACK_EXECUTION_FAST_PATH_INSTRUCTION
}

/**
 * 提取计划详情。
 * OpenClaw: extractPlanningOnlyPlanDetails
 */
fun extractPlanningOnlyPlanDetails(text: String): PlanningOnlyPlanDetails? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val steps = extractPlanningOnlySteps(trimmed)
    return PlanningOnlyPlanDetails(explanation = trimmed, steps = steps)
}

/**
 * 检测不完整回复。
 * OpenClaw: resolveIncompleteTurnPayloadText
 *
 * @return 警告文本，如果不满足条件则返回 null
 */
fun resolveIncompleteTurnPayloadText(params: IncompleteTurnParams): String? {
    if (params.payloadCount != 0 ||
        params.aborted ||
        params.timedOut ||
        params.hasClientToolCall ||
        params.yieldDetected ||
        params.didSendDeterministicApprovalPrompt ||
        params.lastToolError != null
    ) {
        return null
    }

    val stopReason = params.stopReason
    if (stopReason != "toolUse" && stopReason != "error") {
        return null
    }

    return if (params.hadPotentialSideEffects) {
        "⚠️ Agent couldn't generate a response. Note: some tool actions may have already been executed — please verify before retrying."
    } else {
        "⚠️ Agent couldn't generate a response. Please try again."
    }
}

/**
 * 解析 Planning-Only 重试指令。
 * OpenClaw: resolvePlanningOnlyRetryInstruction
 *
 * @return 重试指令，如果不满足条件则返回 null
 */
fun resolvePlanningOnlyRetryInstruction(params: PlanningOnlyParams): String? {
    if (!shouldApplyPlanningOnlyRetryGuard(params.provider, params.modelId)) return null
    if (params.aborted || params.timedOut || params.hasClientToolCall ||
        params.yieldDetected || params.didSendDeterministicApprovalPrompt ||
        params.didSendViaMessagingTool || params.lastToolError != null ||
        params.startedToolCount > 0 || params.hadPotentialSideEffects
    ) {
        return null
    }

    val stopReason = params.stopReason
    if (!stopReason.isNullOrEmpty() && stopReason != "stop") return null

    val text = params.assistantText?.trim() ?: return null
    if (text.isEmpty() || text.length > 700 || text.contains("```")) return null
    if (!PLANNING_ONLY_PROMISE_RE.containsMatchIn(text)) return null
    if (PLANNING_ONLY_COMPLETION_RE.containsMatchIn(text)) return null

    return PLANNING_ONLY_RETRY_INSTRUCTION
}

/**
 * 构建回放元数据。
 * OpenClaw: buildAttemptReplayMetadata
 */
fun buildAttemptReplayMetadata(toolNames: List<String>, didSendViaMessagingTool: Boolean, successfulCronAdds: Int = 0): AttemptReplayMetadata {
    val hadMutatingTools = toolNames.any { isLikelyMutatingToolName(it) }
    val hadPotentialSideEffects = hadMutatingTools || didSendViaMessagingTool || successfulCronAdds > 0
    return AttemptReplayMetadata(
        hadPotentialSideEffects = hadPotentialSideEffects,
        replaySafe = !hadPotentialSideEffects
    )
}

/**
 * 判断工具名是否可能是修改性的。
 * OpenClaw: isLikelyMutatingToolName (from tool-mutation.ts)
 * Android: 简化实现 — exec/write/edit 都视为修改性
 */
private fun isLikelyMutatingToolName(toolName: String): Boolean {
    val mutatingTools = setOf("exec", "write", "edit", "delete", "move", "upload", "create", "update", "patch", "batch_create", "batch_update", "batch_delete")
    return mutatingTools.contains(toolName.lowercase())
}
