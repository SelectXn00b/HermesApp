package com.ai.assistance.operit.core.skillrecorder

import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame

/**
 * 帧压缩器：将 UI 层级 XML 压缩为紧凑文本摘要，
 * 并可合并连续相似事件以节省 LLM token。
 */
object FrameSimplifier {

    /**
     * 将 SimplifiedUINode 树转为紧凑文本。
     * 只保留有意义的节点（可点击、有文本、有描述）。
     */
    fun nodeTreeToText(root: SimplifiedUINode): String {
        return root.toTreeString()
    }

    /**
     * 合并连续相似帧：
     * - 连续 SCROLL 事件合并为一条
     * - 连续同一 Activity 的相同事件类型合并
     */
    fun condenseFrames(frames: List<RecordingFrame>): List<RecordingFrame> {
        if (frames.size <= 1) return frames

        val result = mutableListOf<RecordingFrame>()
        var i = 0

        while (i < frames.size) {
            val current = frames[i]

            // 合并连续 SCROLL 事件
            if (current.eventType == "SCROLL") {
                var scrollEnd = i
                while (scrollEnd + 1 < frames.size &&
                    frames[scrollEnd + 1].eventType == "SCROLL" &&
                    frames[scrollEnd + 1].activityName == current.activityName
                ) {
                    scrollEnd++
                }
                val scrollCount = scrollEnd - i + 1
                result.add(
                    current.copy(
                        eventDetails = current.eventDetails.copy(
                            additionalData = current.eventDetails.additionalData +
                                ("mergedCount" to scrollCount.toString())
                        )
                    )
                )
                i = scrollEnd + 1
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    /**
     * 将帧列表格式化为 LLM prompt 文本
     */
    fun framesToPromptText(frames: List<RecordingFrame>): String {
        val sb = StringBuilder()
        for (frame in frames) {
            sb.appendLine("### Step ${frame.index + 1} (${frame.activityName ?: "unknown"})")

            val eventDesc = buildEventDescription(frame)
            sb.appendLine("事件: $eventDesc")

            if (frame.uiHierarchySummary.isNotBlank()) {
                sb.appendLine("UI上下文:")
                // 限制每帧 UI 摘要长度
                val summary = if (frame.uiHierarchySummary.length > 2000) {
                    frame.uiHierarchySummary.take(2000) + "\n... (截断)"
                } else {
                    frame.uiHierarchySummary
                }
                sb.appendLine(summary)
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildEventDescription(frame: RecordingFrame): String {
        val details = frame.eventDetails
        val mergedCount = details.additionalData["mergedCount"]

        return when (frame.eventType) {
            "CLICK" -> {
                val target = details.text ?: details.contentDescription ?: details.className ?: "元素"
                "点击 [${details.className ?: ""}] \"$target\""
            }
            "LONG_CLICK" -> {
                val target = details.text ?: details.contentDescription ?: details.className ?: "元素"
                "长按 [${details.className ?: ""}] \"$target\""
            }
            "TEXT_INPUT" -> {
                "输入文本 \"${details.inputText ?: details.text ?: ""}\" 到 [${details.className ?: "EditText"}]"
            }
            "SCROLL" -> {
                if (mergedCount != null) "滚动 (${mergedCount}次)" else "滚动"
            }
            "SCREEN_CHANGE" -> {
                "页面切换到 ${frame.activityName ?: "新页面"}"
            }
            else -> frame.eventType
        }
    }
}
