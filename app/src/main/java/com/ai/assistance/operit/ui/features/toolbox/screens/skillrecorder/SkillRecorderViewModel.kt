package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.services.skillrecorder.SkillRecorderNotification
import com.ai.assistance.operit.services.skillrecorder.SkillRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SkillRecorderViewModel(application: Application) : AndroidViewModel(application) {

    val recordingState: StateFlow<RecordingState> = SkillRecorderService.recordingState
    val currentSession: StateFlow<RecordingSession?> = SkillRecorderService.currentSession
    val stepFrameCount: StateFlow<Int> = SkillRecorderService.stepFrameCount

    private val _editedSkillMd = MutableStateFlow("")
    val editedSkillMd = _editedSkillMd.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved = _isSaved.asStateFlow()

    /** Name of the most recently saved skill, for UI confirmation display */
    private val _lastSavedSkillName = MutableStateFlow<String?>(null)
    val lastSavedSkillName = _lastSavedSkillName.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText = _draftText.asStateFlow()

    /** true when user tried to start recording but provider APK is not installed */
    private val _showInstallProviderPrompt = MutableStateFlow(false)
    val showInstallProviderPrompt = _showInstallProviderPrompt.asStateFlow()

    /** true when provider APK is installed but accessibility service is not enabled */
    private val _showAccessibilityPrompt = MutableStateFlow(false)
    val showAccessibilityPrompt = _showAccessibilityPrompt.asStateFlow()

    /** Available model configurations for AI summarization */
    private val _modelConfigs = MutableStateFlow<List<ModelConfigSummary>>(emptyList())
    val modelConfigs = _modelConfigs.asStateFlow()

    /** Currently selected model config ID for summarization */
    private val _selectedModelConfigId = MutableStateFlow<String?>(null)
    val selectedModelConfigId = _selectedModelConfigId.asStateFlow()

    /** Builder steps, synced from session */
    private val _builderSteps = MutableStateFlow<List<BuilderStep>>(emptyList())
    val builderSteps = _builderSteps.asStateFlow()

    /** Whether the think step editor is shown */
    private val _showThinkEditor = MutableStateFlow(false)
    val showThinkEditor = _showThinkEditor.asStateFlow()

    /** Content of the think step being edited */
    private val _thinkStepText = MutableStateFlow("")
    val thinkStepText = _thinkStepText.asStateFlow()

    /** ID of the think step being edited (null = adding new step) */
    private val _editingThinkStepId = MutableStateFlow<String?>(null)
    val editingThinkStepId = _editingThinkStepId.asStateFlow()

    init {
        loadModelConfigs()
        // Sync builder steps from session changes
        viewModelScope.launch {
            SkillRecorderService.currentSession.collect { session ->
                _builderSteps.value = session?.steps?.toList() ?: emptyList()
            }
        }
    }

    private fun loadModelConfigs() {
        viewModelScope.launch {
            try {
                val configManager = ModelConfigManager(getApplication())
                val configs = configManager.getAllConfigSummaries()
                _modelConfigs.value = configs
                // Default to first config if none selected
                if (_selectedModelConfigId.value == null && configs.isNotEmpty()) {
                    _selectedModelConfigId.value = configs.first().id
                }
            } catch (_: Exception) { }
        }
    }

    fun selectModelConfig(configId: String) {
        _selectedModelConfigId.value = configId
    }

    fun dismissInstallProviderPrompt() {
        _showInstallProviderPrompt.value = false
    }

    fun dismissAccessibilityPrompt() {
        _showAccessibilityPrompt.value = false
    }

    fun installProvider() {
        _showInstallProviderPrompt.value = false
        UIHierarchyManager.launchProviderInstall(getApplication())
    }

    // ──── 构建器方法 ────

    /** 进入 BUILDING 状态 */
    fun startBuilding() {
        SkillRecorderService.startBuildSession(_draftText.value.takeIf { it.isNotBlank() })
    }

    /** 开始录制一个步骤 */
    fun startStepRecording() {
        viewModelScope.launch {
            // 检查 provider 是否已安装
            val providerInstalled = UIHierarchyManager.isProviderAppInstalled(getApplication())
            if (!providerInstalled) {
                _showInstallProviderPrompt.value = true
                return@launch
            }

            // 检查无障碍服务是否启用
            val providerReady = try {
                UIHierarchyManager.isAccessibilityServiceEnabled(getApplication())
            } catch (_: Exception) { false }

            if (!providerReady) {
                _showAccessibilityPrompt.value = true
                return@launch
            }

            SkillRecorderService.start(getApplication())
        }
    }

    fun pauseStepRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_PAUSE
        )
    }

    fun resumeStepRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_RESUME
        )
    }

    fun stopStepRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_STOP
        )
    }

    fun discardStepRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_DISCARD
        )
    }

    // ──── 思考步骤编辑 ────

    /** 显示添加新思考步骤的编辑器 */
    fun showThinkStepEditor() {
        _editingThinkStepId.value = null
        _thinkStepText.value = ""
        _showThinkEditor.value = true
    }

    /** 显示编辑现有思考步骤的编辑器 */
    fun editThinkStep(step: BuilderStep.Think) {
        _editingThinkStepId.value = step.id
        _thinkStepText.value = step.content
        _showThinkEditor.value = true
    }

    fun updateThinkStepText(text: String) {
        _thinkStepText.value = text
    }

    /** 提交思考步骤（添加或更新） */
    fun commitThinkStep() {
        val text = _thinkStepText.value.trim()
        if (text.isNotBlank()) {
            val editingId = _editingThinkStepId.value
            if (editingId != null) {
                SkillRecorderService.updateThinkStep(editingId, text)
            } else {
                SkillRecorderService.addThinkStep(text)
            }
        }
        _showThinkEditor.value = false
        _thinkStepText.value = ""
        _editingThinkStepId.value = null
    }

    fun cancelThinkEditor() {
        _showThinkEditor.value = false
        _thinkStepText.value = ""
        _editingThinkStepId.value = null
    }

    // ──── 步骤管理 ────

    fun removeStep(stepId: String) {
        SkillRecorderService.removeStep(stepId)
    }

    fun moveStep(fromIndex: Int, toIndex: Int) {
        SkillRecorderService.moveStep(fromIndex, toIndex)
    }

    // ──── 生成与总结 ────

    /** 触发 AI 总结 */
    fun generateSkill() {
        SkillRecorderService.startSummarization(
            getApplication(),
            _selectedModelConfigId.value
        )
    }

    fun skipSummarization() {
        SkillRecorderService.skipSummarization()
    }

    fun regenerateSummary() {
        _editedSkillMd.value = ""
        SkillRecorderService.selectedModelConfigId = _selectedModelConfigId.value
        SkillRecorderService.regenerateSummary(getApplication())
    }

    // ──── 丢弃/重置 ────

    /** 丢弃全部步骤，回到 IDLE */
    fun discardAll() {
        SkillRecorderService.resetToIdle()
        _builderSteps.value = emptyList()
        _draftText.value = ""
    }

    fun newRecording() {
        _editedSkillMd.value = ""
        _draftText.value = ""
        _isSaved.value = false
        _lastSavedSkillName.value = null
        SkillRecorderService.resetToIdle()
    }

    // ──── 编辑/保存 ────

    fun updateEditedSkillMd(text: String) {
        _editedSkillMd.value = text
    }

    fun updateDraftText(text: String) {
        _draftText.value = text
    }

    fun clearSavedState() {
        _isSaved.value = false
        _lastSavedSkillName.value = null
    }

    /** Reset recording state to IDLE after save, preserving saved name for Snackbar display */
    fun finishAfterSave() {
        _editedSkillMd.value = ""
        _draftText.value = ""
        SkillRecorderService.resetToIdle()
    }

    fun initEditedSkillMd() {
        val generated = currentSession.value?.generatedSkillMd
        if (generated != null && _editedSkillMd.value.isBlank()) {
            _editedSkillMd.value = generated
        }
    }

    /**
     * Replace (or insert) the `name:` field in YAML frontmatter so that it matches
     * the directory name the user chose.
     */
    private fun syncFrontmatterName(content: String, skillName: String): String {
        val lines = content.lines().toMutableList()
        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIdx >= 0) {
                val fmEnd = endIdx + 1
                var nameReplaced = false
                for (i in 1..fmEnd) {
                    val trimmed = lines[i].trim()
                    if (trimmed.startsWith("name:")) {
                        lines[i] = "name: $skillName"
                        nameReplaced = true
                        break
                    }
                }
                if (!nameReplaced) {
                    lines.add(1, "name: $skillName")
                }
                return lines.joinToString("\n")
            }
        }
        return "---\nname: $skillName\n---\n$content"
    }

    fun saveAsSkill(skillName: String) {
        viewModelScope.launch {
            try {
                val content = _editedSkillMd.value
                if (content.isBlank()) return@launch

                val finalContent = syncFrontmatterName(content, skillName)

                val skillManager = SkillManager.getInstance(getApplication())
                val skillDir = File(skillManager.getSkillsDirectoryPath(), skillName)
                skillDir.mkdirs()
                File(skillDir, "SKILL.md").writeText(finalContent)

                skillManager.refreshAvailableSkills()

                currentSession.value?.savedSkillName = skillName
                _lastSavedSkillName.value = skillName
                _isSaved.value = true
            } catch (_: Exception) {
                _isSaved.value = false
            }
        }
    }
}
