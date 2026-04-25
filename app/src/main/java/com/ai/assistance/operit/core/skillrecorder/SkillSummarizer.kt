package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill 总结器：将录制帧序列发送给 LLM，生成 SKILL.md 内容。
 */
class SkillSummarizer(private val context: Context) {

    companion object {
        private const val TAG = "SkillSummarizer"
    }

    /**
     * 对录制会话进行 AI 总结，生成 SKILL.md 内容。
     * @return 生成的 SKILL.md 文本，失败返回 null
     */
    suspend fun summarize(session: RecordingSession): String? = withContext(Dispatchers.IO) {
        try {
            val frames = FrameSimplifier.condenseFrames(session.frames)
            if (frames.isEmpty()) {
                AppLogger.w(TAG, "没有帧可总结")
                return@withContext null
            }

            val mainPackage = frames
                .mapNotNull { it.packageName }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: "未知应用"

            val durationSec = session.duration / 1000
            val stepsText = FrameSimplifier.framesToPromptText(frames)

            val systemPrompt = buildSystemPrompt()
            val userPrompt = buildUserPrompt(mainPackage, durationSec, frames.size, stepsText)

            val chatHistory = listOf(
                PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
            )

            val service = createAIService() ?: return@withContext generateFallbackSkillMd(session, frames)

            val stream = service.sendMessage(
                context = context,
                chatHistory = chatHistory,
                stream = true,
                enableRetry = true
            )

            val sb = StringBuilder()
            stream.collect { chunk ->
                sb.append(chunk)
            }

            val result = sb.toString().trim()
            if (result.isBlank()) {
                AppLogger.w(TAG, "AI 返回空结果，使用 fallback")
                return@withContext generateFallbackSkillMd(session, frames)
            }

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI 总结失败", e)
            generateFallbackSkillMd(session, session.frames)
        }
    }

    private suspend fun createAIService(): AIService? {
        return try {
            val configManager = ModelConfigManager(context)
            val configs = configManager.getAllConfigSummaries()
            val firstConfigId = configs.firstOrNull()?.id ?: return null
            val config = configManager.getModelConfig(firstConfigId) ?: return null
            AIServiceFactory.createService(config, configManager, context)
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建 AI 服务失败", e)
            null
        }
    }

    private fun buildSystemPrompt(): String = """
你是一位 Android 自动化 Skill 编写专家。
你的任务是根据用户录制的手机操作帧序列，生成一份 SKILL.md 文件。
这份文件将被 AI Agent (Hermes) 读取并通过无障碍服务在手机上复现这些操作。

要求：
1. 开头使用 YAML frontmatter，包含 name, description, category: recorded, platform: android
2. 用清晰的自然语言逐步描述操作流程
3. 每个步骤包含足够的 UI 元素上下文（文本标签、描述、元素类型）让 Agent 能定位元素
4. 如果发现有重复或循环操作，用概括性描述代替逐一列举
5. 使用中文编写
""".trimIndent()

    private fun buildUserPrompt(
        mainPackage: String,
        durationSec: Long,
        stepCount: Int,
        stepsText: String
    ): String = """
## 录制概要
- 主要应用: $mainPackage
- 时长: ${durationSec}秒
- 步骤数: $stepCount

## 录制的操作步骤:

$stepsText

请根据以上录制数据生成 SKILL.md 文件内容。
""".trimIndent()

    /**
     * AI 不可用时的 fallback：直接生成简单的步骤列表
     */
    private fun generateFallbackSkillMd(
        session: RecordingSession,
        frames: List<com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame>
    ): String {
        val mainPackage = frames.mapNotNull { it.packageName }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: "未知应用"

        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("name: recorded-skill-${session.id.take(8)}")
        sb.appendLine("description: 录制的操作流程 ($mainPackage)")
        sb.appendLine("category: recorded")
        sb.appendLine("platform: android")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# 录制的操作流程")
        sb.appendLine()
        sb.appendLine("应用: $mainPackage")
        sb.appendLine()
        sb.appendLine("## 操作步骤")
        sb.appendLine()

        for (frame in frames) {
            val desc = when (frame.eventType) {
                "CLICK" -> "点击 \"${frame.eventDetails.text ?: frame.eventDetails.contentDescription ?: "元素"}\""
                "LONG_CLICK" -> "长按 \"${frame.eventDetails.text ?: "元素"}\""
                "TEXT_INPUT" -> "输入 \"${frame.eventDetails.inputText ?: frame.eventDetails.text ?: ""}\""
                "SCROLL" -> "滚动页面"
                "SCREEN_CHANGE" -> "页面切换到 ${frame.activityName ?: "新页面"}"
                else -> frame.eventType
            }
            sb.appendLine("${frame.index + 1}. $desc")
        }

        return sb.toString()
    }
}
