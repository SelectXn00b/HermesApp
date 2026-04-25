package com.ai.assistance.operit.data.model

/** UI状态，用于显示AI服务在做什么 */
sealed class InputProcessingState {
    /** 空闲状态 */
    object Idle : InputProcessingState()

    /** 正在处理，例如准备请求或解析响应 */
    data class Processing(val message: String) : InputProcessingState()

    /** 正在连接到AI服务 */
    data class Connecting(val message: String) : InputProcessingState()

    /** 正在从AI服务接收数据 */
    data class Receiving(val message: String) : InputProcessingState()

    /** 新增：正在执行工具 */
    data class ExecutingTool(val toolName: String) : InputProcessingState()

    data class ToolProgress(
        val toolName: String,
        val progress: Float,
        val message: String = ""
    ) : InputProcessingState()

    /** 新增：正在处理工具结果 */
    data class ProcessingToolResult(val toolName: String) : InputProcessingState()

    /** 新增：正在总结记忆 */
    data class Summarizing(val message: String) : InputProcessingState()

    /** 新增：正在执行计划 */
    data class ExecutingPlan(val message: String) : InputProcessingState()

    /** 处理完成 */
    object Completed : InputProcessingState()

    /** 发生错误 */
    data class Error(val message: String) : InputProcessingState()
} 