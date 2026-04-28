package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
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
    val frameCount: StateFlow<Int> = SkillRecorderService.frameCount

    private val _editedSkillMd = MutableStateFlow("")
    val editedSkillMd = _editedSkillMd.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved = _isSaved.asStateFlow()

    /** true when user tried to start recording but accessibility service was not enabled */
    private val _showAccessibilityPrompt = MutableStateFlow(false)
    val showAccessibilityPrompt = _showAccessibilityPrompt.asStateFlow()

    fun dismissAccessibilityPrompt() {
        _showAccessibilityPrompt.value = false
    }

    fun startRecording() {
        viewModelScope.launch {
            val enabled = UIHierarchyManager.isAccessibilityServiceEnabled(getApplication())
            if (!enabled) {
                _showAccessibilityPrompt.value = true
                return@launch
            }
            SkillRecorderService.start(getApplication())
        }
    }

    fun pauseRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_PAUSE
        )
    }

    fun resumeRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_RESUME
        )
    }

    fun stopRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_STOP
        )
    }

    fun discardRecording() {
        SkillRecorderService.sendAction(
            getApplication(),
            SkillRecorderNotification.ACTION_DISCARD
        )
    }

    fun updateEditedSkillMd(text: String) {
        _editedSkillMd.value = text
    }

    fun initEditedSkillMd() {
        val generated = currentSession.value?.generatedSkillMd
        if (generated != null && _editedSkillMd.value.isBlank()) {
            _editedSkillMd.value = generated
        }
    }

    fun saveAsSkill(skillName: String) {
        viewModelScope.launch {
            try {
                val content = _editedSkillMd.value
                if (content.isBlank()) return@launch

                // Write to the same external storage path that SkillManager scans,
                // so saved skills appear in the AI's available packages list.
                val skillManager = SkillManager.getInstance(getApplication())
                val skillDir = File(skillManager.getSkillsDirectoryPath(), skillName)
                skillDir.mkdirs()
                File(skillDir, "SKILL.md").writeText(content)

                // Refresh so the skill is immediately discoverable
                skillManager.refreshAvailableSkills()

                currentSession.value?.savedSkillName = skillName
                _isSaved.value = true
            } catch (_: Exception) {
                _isSaved.value = false
            }
        }
    }
}
