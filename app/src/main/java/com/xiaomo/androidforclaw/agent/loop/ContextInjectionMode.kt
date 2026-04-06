package com.xiaomo.androidforclaw.agent.loop

/**
 * Context Injection Mode — 对齐 OpenClaw bootstrap-files.ts
 *
 * 控制系统 prompt 注入策略：
 * - always: 每次都注入（默认）
 * - continuation-skip: 首次注入后，后续续对话跳过重新注入（省 tokens）
 * - lightweight: 轻量模式，只注入核心内容
 *
 * OpenClaw 源: ../openclaw/src/agents/bootstrap-files.ts
 */

// ── Types ──

/** 上下文注入模式。OpenClaw: AgentContextInjection */
enum class ContextInjectionMode {
    /** 每次都注入（默认） */
    ALWAYS,
    /** 首次注入后，续对话跳过 */
    CONTINUATION_SKIP,
    /** 轻量模式 */
    LIGHTWEIGHT
}

/** 引导完成标记 custom type。OpenClaw: FULL_BOOTSTRAP_COMPLETED_CUSTOM_TYPE */
const val BOOTSTRAP_COMPLETED_CUSTOM_TYPE = "openclaw:bootstrap-context:full"

/**
 * 续跳检测状态。
 * OpenClaw: hasCompletedBootstrapTurn
 */
data class ContinuationState(
    /** 是否已完成首次引导 */
    val hasCompletedBootstrap: Boolean = false,
    /** 是否应跳过本次注入 */
    val shouldSkipInjection: Boolean = false,
    /** 注入模式 */
    val mode: ContextInjectionMode = ContextInjectionMode.ALWAYS
)

/**
 * 解析注入模式。
 * OpenClaw: resolveContextInjectionMode
 */
fun resolveContextInjectionMode(modeString: String?): ContextInjectionMode {
    return when (modeString?.lowercase()?.trim()) {
        "continuation-skip", "continuation_skip", "continuationskip" -> ContextInjectionMode.CONTINUATION_SKIP
        "lightweight", "light" -> ContextInjectionMode.LIGHTWEIGHT
        "always" -> ContextInjectionMode.ALWAYS
        null, "" -> ContextInjectionMode.ALWAYS
        else -> ContextInjectionMode.ALWAYS
    }
}

/**
 * 检测是否应跳过上下文注入。
 * OpenClaw: isContinuationTurn check
 *
 * @param mode 注入模式
 * @param isHeartbeat 是否心跳触发
 * @param isFirstTurn 是否首次对话（会话为空）
 * @return 续跳状态
 */
fun resolveContinuationState(
    mode: ContextInjectionMode,
    isHeartbeat: Boolean = false,
    isFirstTurn: Boolean = false
): ContinuationState {
    if (mode != ContextInjectionMode.CONTINUATION_SKIP) {
        return ContinuationState(
            hasCompletedBootstrap = !isFirstTurn,
            shouldSkipInjection = false,
            mode = mode
        )
    }

    if (isHeartbeat) {
        return ContinuationState(
            hasCompletedBootstrap = !isFirstTurn,
            shouldSkipInjection = false,
            mode = mode
        )
    }

    if (isFirstTurn) {
        return ContinuationState(
            hasCompletedBootstrap = false,
            shouldSkipInjection = false,
            mode = mode
        )
    }

    // Subsequent turn — skip re-injection
    return ContinuationState(
        hasCompletedBootstrap = true,
        shouldSkipInjection = true,
        mode = mode
    )
}

/**
 * 构建轻量模式系统 prompt（仅核心指令）。
 * OpenClaw: lightweight prompt mode
 *
 * @param coreInstructions 核心指令（system prompt 精简版）
 * @return 轻量 prompt
 */
fun buildLightweightSystemPrompt(coreInstructions: String): String {
    return buildString {
        appendLine(coreInstructions)
        appendLine()
        appendLine("## Lightweight Mode")
        appendLine("This is a lightweight session. Context files (AGENTS.md, SKILL.md, etc.) were not re-injected.")
        appendLine("If you need workspace context, ask the user or check relevant files manually.")
    }
}
