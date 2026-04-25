package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Hook for observing tool call lifecycle events.
 *
 * This hook is notify-only and must not intercept or block execution.
 */
interface AIToolHook {
    /** Called when a tool call request is received. */
    fun onToolCallRequested(tool: AITool) {}

    /** Called after permission check is completed (if applicable). */
    fun onToolPermissionChecked(tool: AITool, granted: Boolean, reason: String? = null) {}

    /** Called when actual tool execution is about to start. */
    fun onToolExecutionStarted(tool: AITool) {}

    /** Called when a tool execution result is produced. */
    fun onToolExecutionResult(tool: AITool, result: ToolResult) {}

    /** Called when execution throws an exception. */
    fun onToolExecutionError(tool: AITool, throwable: Throwable) {}

    /** Called when the tool request lifecycle is finished. */
    fun onToolExecutionFinished(tool: AITool) {}
}
