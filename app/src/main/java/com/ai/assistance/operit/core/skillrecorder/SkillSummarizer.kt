package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.EventDetails
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Skill 总结器：将构建器步骤（录制帧 + 思考逻辑）发送给 LLM，生成 SKILL.md 内容。
 */
class SkillSummarizer(private val context: Context) {

    companion object {
        private const val TAG = "SkillSummarizer"
        private const val AI_TIMEOUT_MS = 60_000L // 60 seconds
    }

    /**
     * 对录制会话进行 AI 总结，生成 SKILL.md 内容。
     * @param configId 用户选择的模型配置 ID，为 null 时使用第一个可用配置
     * @return 生成的 SKILL.md 文本，失败返回 null
     */
    suspend fun summarize(session: RecordingSession, configId: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            if (session.steps.isEmpty()) {
                AppLogger.w(TAG, "没有步骤可总结")
                return@withContext null
            }

            val stepsText = buildStepsPromptText(session.steps)
            val systemPrompt = buildSystemPrompt(session.draftText)
            val userPrompt = buildUserPrompt(session, stepsText)

            val chatHistory = listOf(
                PromptTurn(kind = PromptTurnKind.SYSTEM, content = systemPrompt),
                PromptTurn(kind = PromptTurnKind.USER, content = userPrompt)
            )

            val service = createAIService(configId) ?: return@withContext generateFallbackSkillMd(session)

            val result = withTimeoutOrNull(AI_TIMEOUT_MS) {
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
                sb.toString().trim()
            }

            if (result.isNullOrBlank()) {
                AppLogger.w(TAG, if (result == null) "AI 总结超时，使用 fallback" else "AI 返回空结果，使用 fallback")
                return@withContext generateFallbackSkillMd(session)
            }

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI 总结失败", e)
            generateFallbackSkillMd(session)
        }
    }

    private suspend fun createAIService(configId: String? = null): AIService? {
        return try {
            val configManager = ModelConfigManager(context)
            val configs = configManager.getAllConfigSummaries()
            val targetId = configId ?: configs.firstOrNull()?.id ?: return null
            val config = configManager.getModelConfig(targetId) ?: return null
            AIServiceFactory.createService(config, configManager, context)
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建 AI 服务失败", e)
            null
        }
    }

    /**
     * 将步骤列表转换为 LLM prompt 文本，交替输出录制帧和思考文本。
     */
    private fun buildStepsPromptText(steps: List<BuilderStep>): String {
        val sb = StringBuilder()
        steps.forEachIndexed { index, step ->
            when (step) {
                is BuilderStep.Record -> {
                    sb.appendLine("=== 步骤 ${index + 1}: 录制操作 ===")
                    val condensed = FrameSimplifier.condenseFrames(step.frames)
                    sb.appendLine(FrameSimplifier.framesToPromptText(condensed))
                }
                is BuilderStep.Think -> {
                    sb.appendLine("=== 步骤 ${index + 1}: 推理逻辑 ===")
                    sb.appendLine(step.content)
                    sb.appendLine()
                }
            }
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(draftText: String? = null): String {
        val base = """
你是一位 Android 自动化 Skill 编写专家。
你的任务是根据用户提供的步骤序列，生成一份 SKILL.md 文件。
这份文件将被 AI Agent (Hermes) 读取并**通过无障碍服务在手机上精确复现这些操作**。

⚠️ 最重要的原则：Agent 是通过无障碍服务操作手机的程序，不是人类。它需要**精确的 UI 元素定位信息**才能找到并操作目标元素。

输入包含两种类型的步骤：
- **录制步骤（录制操作）**：用户在设备上实际执行的操作，以帧序列形式提供，每帧包含事件类型、目标元素信息和当前页面的 UI 层级树
- **思考步骤（推理逻辑）**：用户手动编写的推理逻辑和条件判断，应自然融入操作流程

要求：
1. 开头使用 YAML frontmatter，包含 name, description, category: recorded, platform: android
2. 逐步描述操作流程，保持步骤的原始顺序
3. **录制步骤必须保留具体的 UI 元素定位信息**，这是最关键的要求：
   - 每个操作必须明确指出目标元素的 **text**（显示文字）、**contentDescription**（无障碍描述）、**className**（元素类型如 Button/TextView）
   - 如果输入数据中包含 **resourceId**（如 `com.example:id/btn_submit`），必须保留
   - 写成 Agent 可直接执行的格式，例如：
     - `点击 text="房态房量" 的 [TextView] 元素`
     - `点击 resourceId="com.meituan.hotel:id/tab_status" 的按钮`
     - `在 [EditText] className="android.widget.EditText" 中输入 "搜索内容"`
     - `向下滚动页面`
   - **不要**把具体元素信息概括成模糊的自然语言（如"找到并进入房态房量页面"）
4. 每个操作步骤需注明所在的 **Activity**（从帧数据的 activityName 获取）和 **包名**（packageName）
5. 思考步骤：将用户的推理逻辑自然地融入流程中，作为条件判断或决策说明
6. 如果发现有连续滚动操作，可以合并为一条（如"向下滚动 3 次"）；但点击、输入等操作不要合并
7. 使用中文编写""".trimIndent()

        return if (!draftText.isNullOrBlank()) {
            base + "\n8. 用户在构建前提供了意图描述（草稿），请优先参考该描述来理解操作目的，并据此优化 Skill 的 name、description 和步骤描述"
        } else {
            base
        }
    }

    private fun buildUserPrompt(
        session: RecordingSession,
        stepsText: String
    ): String {
        val draftSection = if (!session.draftText.isNullOrBlank()) {
            "## 用户意图描述\n${session.draftText}\n\n"
        } else ""

        val recordSteps = session.steps.filterIsInstance<BuilderStep.Record>()
        val thinkSteps = session.steps.filterIsInstance<BuilderStep.Think>()
        val totalFrames = recordSteps.sumOf { it.frames.size }
        val durationSec = session.duration / 1000

        return """
${draftSection}## 构建概要
- 总步骤数: ${session.steps.size} (${recordSteps.size} 个录制步骤, ${thinkSteps.size} 个思考步骤)
- 总帧数: $totalFrames
- 时长: ${durationSec}秒

## 步骤详情:

$stepsText

请根据以上步骤生成 SKILL.md 文件内容。
""".trimIndent()
    }

    /**
     * AI 不可用时的 fallback：直接从步骤生成结构化的 SKILL.md，保留 UI 元素定位信息
     */
    private fun generateFallbackSkillMd(session: RecordingSession): String {
        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("name: recorded-skill-${session.id.take(8)}")
        sb.appendLine("description: ${session.draftText?.takeIf { it.isNotBlank() } ?: "录制的操作流程"}")
        sb.appendLine("category: recorded")
        sb.appendLine("platform: android")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# 录制的操作流程")
        sb.appendLine()

        var stepNum = 1
        for (step in session.steps) {
            when (step) {
                is BuilderStep.Record -> {
                    sb.appendLine("## 步骤 $stepNum: 录制操作")
                    sb.appendLine()
                    val mainPackage = step.frames.mapNotNull { it.packageName }
                        .groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }?.key
                    if (mainPackage != null) {
                        sb.appendLine("应用: $mainPackage")
                        sb.appendLine()
                    }
                    for (frame in step.frames) {
                        val details = frame.eventDetails
                        val activity = frame.activityName ?: "unknown"
                        val desc = when (frame.eventType) {
                            "CLICK" -> {
                                val target = buildElementSelector(details)
                                "点击 $target"
                            }
                            "LONG_CLICK" -> {
                                val target = buildElementSelector(details)
                                "长按 $target"
                            }
                            "TEXT_INPUT" -> {
                                val input = details.inputText ?: details.text ?: ""
                                val cls = details.className ?: "EditText"
                                "在 [$cls] 中输入 \"$input\""
                            }
                            "SCROLL" -> "向下滚动页面"
                            "SCREEN_CHANGE" -> "页面切换到 ${frame.activityName ?: "新页面"}"
                            else -> frame.eventType
                        }
                        sb.appendLine("${frame.index + 1}. $desc (Activity: $activity)")
                    }
                    sb.appendLine()
                }
                is BuilderStep.Think -> {
                    sb.appendLine("## 步骤 $stepNum: 推理逻辑")
                    sb.appendLine()
                    sb.appendLine(step.content)
                    sb.appendLine()
                }
            }
            stepNum++
        }

        return sb.toString()
    }

    /**
     * 构建元素选择器描述，保留尽可能多的定位信息
     */
    private fun buildElementSelector(details: EventDetails): String {
        val parts = mutableListOf<String>()
        val cls = details.className
        if (cls != null) parts.add("[$cls]")
        if (!details.text.isNullOrBlank()) parts.add("text=\"${details.text}\"")
        if (!details.contentDescription.isNullOrBlank()) parts.add("contentDescription=\"${details.contentDescription}\"")
        return if (parts.isEmpty()) "元素" else parts.joinToString(" ")
    }
}
