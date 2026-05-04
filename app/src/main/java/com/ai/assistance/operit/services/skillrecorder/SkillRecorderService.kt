package com.ai.assistance.operit.services.skillrecorder

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.ai.assistance.operit.core.skillrecorder.FrameCapture
import com.ai.assistance.operit.core.skillrecorder.SkillSummarizer
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.core.tools.system.action.ActionManager
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Skill Recorder 前台服务。
 * 通过无障碍事件回调录制用户操作，支持分步录制模式。
 * 每次仅在 STEP_RECORDING 时运行前台服务。
 */
class SkillRecorderService : Service() {

    companion object {
        private const val TAG = "SkillRecorderService"
        private const val CALLBACK_ID = "skill_recorder"
        private const val POLL_INTERVAL_MS = 800L

        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState = _recordingState.asStateFlow()

        private val _currentSession = MutableStateFlow<RecordingSession?>(null)
        val currentSession = _currentSession.asStateFlow()

        /** 当前步骤录制的帧数 */
        private val _stepFrameCount = MutableStateFlow(0)
        val stepFrameCount = _stepFrameCount.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        /** Model config ID selected by user for AI summarization */
        @Volatile var selectedModelConfigId: String? = null

        /** Job tracking the current summarization coroutine, for cancellation */
        private var summarizationJob: Job? = null

        /** 当前步骤的帧缓冲（线程安全） */
        private val _stepFrameBuffer = java.util.Collections.synchronizedList(mutableListOf<RecordingFrame>())

        // ──── 构建器管理方法 ────

        /** 开始一个构建会话（不启动前台服务） */
        fun startBuildSession(draftText: String?) {
            val session = RecordingSession(draftText = draftText)
            _currentSession.value = session
            _recordingState.value = RecordingState.BUILDING
        }

        /** 启动前台服务开始录制一个步骤 */
        fun start(context: Context) {
            val intent = Intent(context, SkillRecorderService::class.java)
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, SkillRecorderService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }

        /** 将帧缓冲提交为一个 BuilderStep.Record */
        fun commitStepFrames(): BuilderStep.Record? {
            val session = _currentSession.value ?: run {
                AppLogger.w(TAG, "commitStepFrames: no session")
                return null
            }
            val framesCopy: List<RecordingFrame>
            synchronized(_stepFrameBuffer) {
                if (_stepFrameBuffer.isEmpty()) {
                    AppLogger.w(TAG, "commitStepFrames: buffer empty, nothing to commit")
                    return null
                }
                AppLogger.i(TAG, "commitStepFrames: committing ${_stepFrameBuffer.size} frames as step #${session.steps.size}")
                framesCopy = ArrayList(_stepFrameBuffer)
                _stepFrameBuffer.clear()
            }
            val step = BuilderStep.Record(
                orderIndex = session.steps.size,
                frames = framesCopy,
                startTime = framesCopy.first().timestamp,
                endTime = framesCopy.last().timestamp
            )
            // 创建新的 steps 列表再赋值，避免先修改原 session 导致 StateFlow equals() 去重不触发更新
            val newSteps = ArrayList(session.steps).apply { add(step) }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
            _stepFrameCount.value = 0
            AppLogger.i(TAG, "commitStepFrames: session now has ${newSteps.size} steps")
            return step
        }

        /** 添加一个思考步骤 */
        fun addThinkStep(content: String) {
            val session = _currentSession.value ?: return
            val step = BuilderStep.Think(
                orderIndex = session.steps.size,
                content = content
            )
            val newSteps = ArrayList(session.steps).apply { add(step) }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /** 更新思考步骤内容 */
        fun updateThinkStep(stepId: String, newContent: String) {
            val session = _currentSession.value ?: return
            val idx = session.steps.indexOfFirst { it.id == stepId }
            if (idx < 0) return
            val old = session.steps[idx]
            if (old is BuilderStep.Think) {
                val newSteps = ArrayList(session.steps)
                newSteps[idx] = old.copy(content = newContent)
                _currentSession.value = session.copy(steps = newSteps.toMutableList())
            }
        }

        /** 删除一个步骤 */
        fun removeStep(stepId: String) {
            val session = _currentSession.value ?: return
            val newSteps = ArrayList(session.steps)
            newSteps.removeAll { it.id == stepId }
            // 重新编号
            newSteps.forEachIndexed { i, step ->
                when (step) {
                    is BuilderStep.Record -> newSteps[i] = step.copy(orderIndex = i)
                    is BuilderStep.Think -> newSteps[i] = step.copy(orderIndex = i)
                }
            }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /** 移动步骤位置 */
        fun moveStep(fromIndex: Int, toIndex: Int) {
            val session = _currentSession.value ?: return
            if (fromIndex < 0 || fromIndex >= session.steps.size ||
                toIndex < 0 || toIndex >= session.steps.size) return
            val newSteps = ArrayList(session.steps)
            val step = newSteps.removeAt(fromIndex)
            newSteps.add(toIndex, step)
            // 重新编号
            newSteps.forEachIndexed { i, s ->
                when (s) {
                    is BuilderStep.Record -> newSteps[i] = s.copy(orderIndex = i)
                    is BuilderStep.Think -> newSteps[i] = s.copy(orderIndex = i)
                }
            }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /** 从 BUILDING 状态触发 AI 总结 */
        fun startSummarization(context: Context, configId: String?) {
            val session = _currentSession.value ?: return
            if (_recordingState.value != RecordingState.BUILDING) return
            if (session.steps.isEmpty()) return
            session.endTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.SUMMARIZING
            selectedModelConfigId = configId
            summarizationJob?.cancel()
            summarizationJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                val summarizer = SkillSummarizer(context.applicationContext)
                val skillMd = summarizer.summarize(session, configId)
                if (_recordingState.value == RecordingState.SUMMARIZING) {
                    session.generatedSkillMd = skillMd
                    _currentSession.value = session.copy()
                    _recordingState.value = RecordingState.REVIEW
                    AppLogger.i(TAG, "总结完成")
                }
            }
        }

        /**
         * Re-run AI summarization on the existing session.
         */
        fun regenerateSummary(context: Context) {
            val session = _currentSession.value ?: return
            if (_recordingState.value != RecordingState.REVIEW) return
            _recordingState.value = RecordingState.SUMMARIZING
            summarizationJob?.cancel()
            summarizationJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                val summarizer = SkillSummarizer(context.applicationContext)
                val skillMd = summarizer.summarize(session, selectedModelConfigId)
                if (_recordingState.value == RecordingState.SUMMARIZING) {
                    session.generatedSkillMd = skillMd
                    _currentSession.value = session.copy()
                    _recordingState.value = RecordingState.REVIEW
                    AppLogger.i(TAG, "重新生成总结完成")
                }
            }
        }

        /**
         * Reset state to IDLE.
         */
        fun resetToIdle() {
            _currentSession.value = null
            _stepFrameCount.value = 0
            _stepFrameBuffer.clear()
            _recordingState.value = RecordingState.IDLE
        }

        /**
         * Skip AI summarization and go directly to REVIEW with fallback content.
         */
        fun skipSummarization() {
            if (_recordingState.value != RecordingState.SUMMARIZING) return
            summarizationJob?.cancel()
            summarizationJob = null
            val session = _currentSession.value ?: return
            // Generate fallback content from steps
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
                        for (frame in step.frames) {
                            val desc = when (frame.eventType) {
                                "CLICK" -> "点击 \"${frame.eventDetails.text ?: frame.eventDetails.contentDescription ?: "元素"}\""
                                "LONG_CLICK" -> "长按 \"${frame.eventDetails.text ?: "元素"}\""
                                "TEXT_INPUT" -> "输入 \"${frame.eventDetails.inputText ?: frame.eventDetails.text ?: ""}\""
                                "SCROLL" -> "滚动页面"
                                "SCREEN_CHANGE" -> "页面切换到 ${frame.activityName ?: "新页面"}"
                                else -> frame.eventType
                            }
                            sb.appendLine("- $desc")
                        }
                        sb.appendLine()
                    }
                    is BuilderStep.Think -> {
                        sb.appendLine("## 步骤 $stepNum: 推理逻辑")
                        sb.appendLine(step.content)
                        sb.appendLine()
                    }
                }
                stepNum++
            }
            session.generatedSkillMd = sb.toString()
            _currentSession.value = session.copy()
            _recordingState.value = RecordingState.REVIEW
            AppLogger.i(TAG, "跳过AI总结，使用 fallback")
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var frameCapture: FrameCapture
    private var timerJob: Job? = null
    private var pollingJob: Job? = null
    private var startTimeMs = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var lastActivityName: String? = null
    private var lastUiHierarchyHash: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        frameCapture = FrameCapture(this)
        SkillRecorderNotification.createChannel(this)
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SkillRecorderNotification.ACTION_PAUSE -> pauseRecording()
            SkillRecorderNotification.ACTION_RESUME -> resumeRecording()
            SkillRecorderNotification.ACTION_STOP -> stopRecording()
            SkillRecorderNotification.ACTION_DISCARD -> discardRecording()
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_recordingState.value == RecordingState.STEP_RECORDING) return

        // 清空步骤帧缓冲
        _stepFrameBuffer.clear()
        _stepFrameCount.value = 0
        _recordingState.value = RecordingState.STEP_RECORDING
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        frameCapture.reset()

        // 启动前台通知
        startForeground(
            SkillRecorderNotification.NOTIFICATION_ID,
            SkillRecorderNotification.buildRecordingNotification(this, 0, 0, false)
        )

        // 注册 ActionManager 事件回调
        val actionManager = ActionManager.getInstance(this)
        actionManager.registerEventCallback(CALLBACK_ID) { event ->
            onActionEvent(event)
        }

        // 启动 UI 轮询
        pollingJob = serviceScope.launch {
            lastActivityName = try {
                UIHierarchyManager.getCurrentActivityName(this@SkillRecorderService)
            } catch (_: Exception) { null }
            lastUiHierarchyHash = try {
                UIHierarchyManager.getUIHierarchy(this@SkillRecorderService)?.hashCode() ?: 0
            } catch (_: Exception) { 0 }

            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_recordingState.value != RecordingState.STEP_RECORDING) continue
                pollUiChanges()
            }
        }

        // 启动定时器更新通知
        startNotificationTimer()

        AppLogger.i(TAG, "步骤录制已开始（UI轮询模式）")
    }

    /**
     * Poll UIHierarchyManager for Activity / UI hierarchy changes.
     */
    private suspend fun pollUiChanges() {
        try {
            val ctx = this@SkillRecorderService

            val currentActivity = try {
                UIHierarchyManager.getCurrentActivityName(ctx)
            } catch (_: Exception) { null }

            val currentUiHash = try {
                UIHierarchyManager.getUIHierarchy(ctx)?.hashCode() ?: 0
            } catch (_: Exception) { 0 }

            val now = System.currentTimeMillis()

            // Activity switch → SCREEN_CHANGE
            if (currentActivity != null && currentActivity != lastActivityName) {
                val event = ActionListener.ActionEvent(
                    timestamp = now,
                    actionType = ActionListener.ActionType.SCREEN_CHANGE,
                    elementInfo = ActionListener.ElementInfo(
                        className = currentActivity,
                        packageName = currentActivity.substringBeforeLast(".", "")
                    )
                )
                lastActivityName = currentActivity
                lastUiHierarchyHash = currentUiHash
                onActionEvent(event)
                return
            }

            // Same activity but UI content changed → CLICK
            if (currentUiHash != 0 && currentUiHash != lastUiHierarchyHash) {
                val event = ActionListener.ActionEvent(
                    timestamp = now,
                    actionType = ActionListener.ActionType.CLICK,
                    elementInfo = ActionListener.ElementInfo(
                        className = currentActivity,
                        packageName = currentActivity?.substringBeforeLast(".", "")
                    )
                )
                lastUiHierarchyHash = currentUiHash
                onActionEvent(event)
            }

            lastActivityName = currentActivity
        } catch (e: Exception) {
            AppLogger.w(TAG, "UI轮询失败: ${e.message}")
        }
    }

    private fun pauseRecording() {
        if (_recordingState.value != RecordingState.STEP_RECORDING) return
        _recordingState.value = RecordingState.STEP_PAUSED
        pauseStartMs = System.currentTimeMillis()
        updateNotification()
        AppLogger.i(TAG, "步骤录制已暂停")
    }

    private fun resumeRecording() {
        if (_recordingState.value != RecordingState.STEP_PAUSED) return
        pausedDurationMs += System.currentTimeMillis() - pauseStartMs
        _recordingState.value = RecordingState.STEP_RECORDING
        updateNotification()
        AppLogger.i(TAG, "步骤录制已恢复")
    }

    private fun stopRecording() {
        if (_recordingState.value != RecordingState.STEP_RECORDING &&
            _recordingState.value != RecordingState.STEP_PAUSED
        ) return

        // 先将状态改为非 STEP_RECORDING，防止 onActionEvent 再产生新帧
        _recordingState.value = RecordingState.STEP_PAUSED

        // 停止轮询和事件回调（不再产生新事件）
        pollingJob?.cancel()
        pollingJob = null
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()

        // 提交帧到 BuilderStep.Record（buffer 是 synchronizedList，commitStepFrames 内部用 synchronized）
        commitStepFrames()

        _recordingState.value = RecordingState.BUILDING

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "步骤录制完成，返回构建器")
    }

    private fun discardRecording() {
        pollingJob?.cancel()
        pollingJob = null
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()

        // 清空帧缓冲但不丢弃 session
        _stepFrameBuffer.clear()
        _stepFrameCount.value = 0

        _recordingState.value = RecordingState.BUILDING
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "步骤录制已丢弃")
    }

    private fun onActionEvent(event: ActionListener.ActionEvent) {
        if (_recordingState.value != RecordingState.STEP_RECORDING) return

        serviceScope.launch {
            val frame = frameCapture.processEvent(event, _stepFrameBuffer)
            if (frame != null) {
                _stepFrameCount.value = _stepFrameBuffer.size
            }
        }
    }

    private fun startNotificationTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        val elapsed = getElapsedSeconds()
        val isPaused = _recordingState.value == RecordingState.STEP_PAUSED
        val notification = SkillRecorderNotification.buildRecordingNotification(
            this, _stepFrameCount.value, elapsed, isPaused
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SkillRecorderNotification.NOTIFICATION_ID, notification)
    }

    private fun getElapsedSeconds(): Long {
        val now = System.currentTimeMillis()
        val totalPaused = pausedDurationMs +
            if (_recordingState.value == RecordingState.STEP_PAUSED) (now - pauseStartMs) else 0L
        return ((now - startTimeMs - totalPaused) / 1000).coerceAtLeast(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        pollingJob?.cancel()
        pollingJob = null
        try {
            ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        } catch (_: Exception) { }
        serviceScope.cancel()
        _isServiceRunning.value = false
        if (_recordingState.value != RecordingState.REVIEW &&
            _recordingState.value != RecordingState.BUILDING) {
            _recordingState.value = RecordingState.IDLE
        }
    }
}
