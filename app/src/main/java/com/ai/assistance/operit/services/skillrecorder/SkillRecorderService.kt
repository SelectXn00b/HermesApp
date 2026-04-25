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
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
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
 * 通过无障碍事件回调录制用户操作，录制完成后调用 AI 总结生成 SKILL.md。
 */
class SkillRecorderService : Service() {

    companion object {
        private const val TAG = "SkillRecorderService"
        private const val CALLBACK_ID = "skill_recorder"

        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState = _recordingState.asStateFlow()

        private val _currentSession = MutableStateFlow<RecordingSession?>(null)
        val currentSession = _currentSession.asStateFlow()

        private val _frameCount = MutableStateFlow(0)
        val frameCount = _frameCount.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

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
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var frameCapture: FrameCapture
    private var timerJob: Job? = null
    private var startTimeMs = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L

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
        if (_recordingState.value == RecordingState.RECORDING) return

        val session = RecordingSession()
        _currentSession.value = session
        _frameCount.value = 0
        _recordingState.value = RecordingState.RECORDING
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        frameCapture.reset()

        // 启动前台通知
        startForeground(
            SkillRecorderNotification.NOTIFICATION_ID,
            SkillRecorderNotification.buildRecordingNotification(this, 0, 0, false)
        )

        // 注册事件回调
        val actionManager = ActionManager.getInstance(this)
        actionManager.registerEventCallback(CALLBACK_ID) { event ->
            onActionEvent(event)
        }

        // 如果 ActionManager 还没在监听，启动监听
        serviceScope.launch {
            if (!actionManager.isListening.value) {
                actionManager.startListeningWithHighestPermission { /* primary callback, events also broadcast to registered callbacks */ }
            }
        }

        // 启动定时器更新通知
        startNotificationTimer()

        AppLogger.i(TAG, "录制已开始")
    }

    private fun pauseRecording() {
        if (_recordingState.value != RecordingState.RECORDING) return
        _recordingState.value = RecordingState.PAUSED
        pauseStartMs = System.currentTimeMillis()
        updateNotification()
        AppLogger.i(TAG, "录制已暂停")
    }

    private fun resumeRecording() {
        if (_recordingState.value != RecordingState.PAUSED) return
        pausedDurationMs += System.currentTimeMillis() - pauseStartMs
        _recordingState.value = RecordingState.RECORDING
        updateNotification()
        AppLogger.i(TAG, "录制已恢复")
    }

    private fun stopRecording() {
        if (_recordingState.value != RecordingState.RECORDING &&
            _recordingState.value != RecordingState.PAUSED
        ) return

        _currentSession.value?.endTime = System.currentTimeMillis()
        _recordingState.value = RecordingState.SUMMARIZING

        // 注销事件回调
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()

        // 更新通知为"正在总结"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            SkillRecorderNotification.NOTIFICATION_ID,
            SkillRecorderNotification.buildSummarizingNotification(this)
        )

        // AI 总结
        serviceScope.launch {
            val session = _currentSession.value ?: return@launch
            val summarizer = SkillSummarizer(this@SkillRecorderService)
            val skillMd = summarizer.summarize(session)
            session.generatedSkillMd = skillMd
            _currentSession.value = session
            _recordingState.value = RecordingState.REVIEW

            // 停止前台服务但不销毁（等用户审阅）
            stopForeground(STOP_FOREGROUND_REMOVE)
            AppLogger.i(TAG, "录制总结完成，等待用户审阅")
        }
    }

    private fun discardRecording() {
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()
        _currentSession.value = null
        _frameCount.value = 0
        _recordingState.value = RecordingState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "录制已丢弃")
    }

    private fun onActionEvent(event: ActionListener.ActionEvent) {
        if (_recordingState.value != RecordingState.RECORDING) return
        val session = _currentSession.value ?: return

        serviceScope.launch {
            val frame = frameCapture.processEvent(event, session)
            if (frame != null) {
                _frameCount.value = session.frames.size
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
        val isPaused = _recordingState.value == RecordingState.PAUSED
        val notification = SkillRecorderNotification.buildRecordingNotification(
            this, _frameCount.value, elapsed, isPaused
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SkillRecorderNotification.NOTIFICATION_ID, notification)
    }

    private fun getElapsedSeconds(): Long {
        val now = System.currentTimeMillis()
        val totalPaused = pausedDurationMs +
            if (_recordingState.value == RecordingState.PAUSED) (now - pauseStartMs) else 0L
        return ((now - startTimeMs - totalPaused) / 1000).coerceAtLeast(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        try {
            ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        } catch (_: Exception) { }
        serviceScope.cancel()
        _isServiceRunning.value = false
        if (_recordingState.value != RecordingState.REVIEW) {
            _recordingState.value = RecordingState.IDLE
        }
    }
}
