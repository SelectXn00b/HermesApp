package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.agent.ActionHandler
import com.ai.assistance.operit.core.tools.agent.AgentConfig
import com.ai.assistance.operit.core.tools.agent.PhoneAgent
import com.ai.assistance.operit.core.tools.agent.ToolImplementations
import com.ai.assistance.operit.core.tools.agent.StepResult
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.agent.ShowerServerManager
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.Job

class AutoGlmViewModel(private val context: Context) : ViewModel() {

    private var executionJob: Job? = null

    private val sessionAgentId: String = java.util.UUID.randomUUID().toString().take(8)

    private val _uiState = MutableStateFlow(AutoGlmUiState())
    val uiState: StateFlow<AutoGlmUiState> = _uiState.asStateFlow()

    fun executeTask(task: String, useVirtualScreen: Boolean = false) {
        if (task.isBlank()) return

        executionJob?.cancel()

        executionJob = viewModelScope.launch {
            val logBuilder = StringBuilder()
            appendWithTimestamp(logBuilder, "Initializing agent...")
            _uiState.value = AutoGlmUiState(isLoading = true, log = logBuilder.toString().trimEnd())

            try {
                val agentIdForRun = if (useVirtualScreen) sessionAgentId else "default"
                if (useVirtualScreen) {
                    appendWithTimestamp(logBuilder, "[VirtualScreen] Ensuring Shower virtual display...")
                    _uiState.value = AutoGlmUiState(isLoading = true, log = logBuilder.toString().trimEnd())

                    val okServer = try {
                        ShowerServerManager.ensureServerStarted(context)
                    } catch (e: Exception) {
                        false
                    }

                    if (!okServer) {
                        appendWithTimestamp(logBuilder, "[VirtualScreen] Failed to start Shower server.")
                        _uiState.value = AutoGlmUiState(
                            isLoading = false,
                            log = logBuilder.toString().trimEnd()
                        )
                        return@launch
                    }

                    val metrics = context.resources.displayMetrics
                    val width = metrics.widthPixels
                    val height = metrics.heightPixels
                    val dpi = metrics.densityDpi

                    val okDisplay = try {
                        ShowerController.ensureDisplay(agentIdForRun, context, width, height, dpi)
                    } catch (e: Exception) {
                        false
                    }

                    val displayId = try {
                        ShowerController.getDisplayId(agentIdForRun)
                    } catch (_: Exception) {
                        null
                    }

                    if (!okDisplay || displayId == null) {
                        appendWithTimestamp(logBuilder, "[VirtualScreen] Failed to create virtual display for agentId=$agentIdForRun.")
                        _uiState.value = AutoGlmUiState(
                            isLoading = false,
                            log = logBuilder.toString().trimEnd()
                        )
                        return@launch
                    }

                    appendWithTimestamp(logBuilder, "[VirtualScreen] Virtual display ready. displayId=$displayId")
                    _uiState.value = AutoGlmUiState(isLoading = true, log = logBuilder.toString().trimEnd())
                }

                val uiService = EnhancedAIService.getAIServiceForFunction(context, com.ai.assistance.operit.data.model.FunctionType.UI_CONTROLLER)
                val systemPrompt = buildUiAutomationSystemPrompt()

                val agentConfig = AgentConfig(maxSteps = 25)
                // Get the real UI tools implementation based on the user's preferred permission level.
                val uiTools = ToolGetter.getUITools(context)
                val actionHandler = ActionHandler(
                    context = context,
                    screenWidth = context.resources.displayMetrics.widthPixels,
                    screenHeight = context.resources.displayMetrics.heightPixels,
                    // Use the real UI tools implementation to ensure Tap/Swipe/PressKey/Screenshot actions are executed.
                    toolImplementations = uiTools
                )

                val agent = PhoneAgent(
                    context = context,
                    config = agentConfig,
                    uiService = uiService, // Directly pass the specialized AIService
                    actionHandler = actionHandler,
                    agentId = agentIdForRun,
                    cleanupOnFinish = false
                )

                // Header section，尽量贴近官方 CLI
                appendWithTimestamp(logBuilder, "==================================================")
                appendWithTimestamp(logBuilder, "Task: $task")
                appendWithTimestamp(logBuilder, "Max Steps: ${agentConfig.maxSteps}")
                appendWithTimestamp(logBuilder, "Use Virtual Screen: $useVirtualScreen")
                appendWithTimestamp(logBuilder, "==================================================")
                appendWithTimestamp(logBuilder, "")

                // 先把头部显示出来
                _uiState.value = AutoGlmUiState(isLoading = true, log = logBuilder.toString())

                var stepIndex = 1
                val pausedState = kotlinx.coroutines.flow.MutableStateFlow(false)

                withContext(Dispatchers.IO) {
                    val finalMessage = agent.run(
                        task = task,
                        systemPrompt = systemPrompt,
                        onStep = { stepResult: StepResult ->
                            appendStepLog(logBuilder, stepIndex, stepResult)
                            stepIndex++

                            _uiState.value = AutoGlmUiState(
                                isLoading = true,
                                log = logBuilder.toString().trimEnd()
                            )
                        },
                        isPausedFlow = pausedState
                    )

                    // 追加最终结果，使用 🎉 / ✅ 样式
                    val finalTime = currentTimeString()
                    fun appendFinal(line: String) {
                        logBuilder.append("[")
                        logBuilder.append(finalTime)
                        logBuilder.append("] ")
                        logBuilder.appendLine(line)
                    }

                    appendFinal("🎉 ==================================================")

                    val finalLines = finalMessage.lines()
                    if (finalLines.isNotEmpty()) {
                        appendFinal("✅ 任务完成: ${finalLines.first().trim()}")
                        finalLines.drop(1).forEach { line ->
                            if (line.isNotBlank()) {
                                appendFinal(line.trim())
                            }
                        }
                    }

                    _uiState.value = AutoGlmUiState(
                        isLoading = false,
                        log = logBuilder.toString().trimEnd()
                    )
                }

            } catch (e: Exception) {
                AppLogger.e("AutoGlmViewModel", "Error executing task", e)
                _uiState.value = AutoGlmUiState(isLoading = false, log = "Error: ${e.message}")
            }
        }
    }

    fun cancelTask() {
        executionJob?.cancel()
        _uiState.value = AutoGlmUiState(isLoading = false, log = _uiState.value.log + "[Execution Cancelled by User]")
    }

    private fun buildUiAutomationSystemPrompt(): String {
        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val formattedDate =
            if (useEnglish) {
                SimpleDateFormat("yyyy-MM-dd EEEE", Locale.ENGLISH).format(Date())
            } else {
                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                val datePart = sdf.format(Date())
                val weekdayNames = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
                val weekday = weekdayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
                "$datePart $weekday"
            }
        return FunctionalPrompts.buildUiAutomationAgentPrompt(formattedDate, useEnglish)
    }
    
    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun appendStepLog(builder: StringBuilder, stepIndex: Int, stepResult: StepResult) {
        val time = currentTimeString()

        fun append(line: String) {
            builder.append("[")
            builder.append(time)
            builder.append("] ")
            builder.appendLine(line)
        }

        // 步骤分隔线
        append("==================================================")

        // 💭 思考过程
        stepResult.thinking?.takeIf { it.isNotBlank() }?.let { thinking ->
            append("💭 思考过程:")
            append("--------------------------------------------------")
            thinking.trim().lines().forEach { line ->
                if (line.isNotBlank()) {
                    append(line.trim())
                }
            }
        }

        // 🎯 执行动作
        stepResult.action?.let { action ->
            append("--------------------------------------------------")
            append("🎯 执行动作:")

            val jsonLines = mutableListOf<String>()
            action.actionName?.let { name ->
                jsonLines += "\"action\": \"$name\""
            }
            jsonLines += "\"_metadata\": \"${action.metadata}\""
            action.fields.forEach { (key, value) ->
                if (key != "action") {
                    jsonLines += "\"$key\": \"$value\""
                }
            }

            append("{")
            jsonLines.forEachIndexed { index, line ->
                val suffix = if (index == jsonLines.lastIndex) "" else ","
                append("  $line$suffix")
            }
            append("}")
        }

        // 对于非 finish 步骤，如果有额外消息则补充一段说明
        stepResult.message
            ?.takeIf { it.isNotBlank() && stepResult.action?.metadata != "finish" }
            ?.let { msg ->
                append("--------------------------------------------------")
                msg.trim().lines().forEach { line ->
                    if (line.isNotBlank()) {
                        append(line.trim())
                    }
                }
            }

        append("==================================================")
    }

    private fun currentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun appendWithTimestamp(builder: StringBuilder, line: String) {
        val time = currentTimeString()
        builder.append("[")
        builder.append(time)
        builder.append("] ")
        builder.appendLine(line)
    }
}

data class AutoGlmUiState(
    val isLoading: Boolean = false,
    val log: String = "Ready to execute task."
)
