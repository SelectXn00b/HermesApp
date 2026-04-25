package com.ai.assistance.operit.core.skillrecorder

import android.content.Context
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.data.model.skillrecorder.EventDetails
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 帧捕获器：监听无障碍事件，抓取 UI 层级，构建 RecordingFrame。
 */
class FrameCapture(private val context: Context) {

    companion object {
        private const val TAG = "FrameCapture"
        /** 同类型事件去抖间隔 (ms) */
        private const val DEBOUNCE_MS = 300L
        /** SCROLL 事件最大频率 (ms) */
        private const val SCROLL_THROTTLE_MS = 500L
        /** 最大帧数 */
        private const val MAX_FRAMES = 500
        /** 自身包名，过滤掉 */
        private const val SELF_PACKAGE = "com.ai.assistance.operit"
        private const val PROVIDER_PACKAGE = "com.ai.assistance.operit.provider"

        private val SIGNIFICANT_EVENTS = setOf(
            ActionListener.ActionType.CLICK,
            ActionListener.ActionType.LONG_CLICK,
            ActionListener.ActionType.TEXT_INPUT,
            ActionListener.ActionType.SCROLL,
            ActionListener.ActionType.SCREEN_CHANGE
        )
    }

    private val frameIndex = AtomicInteger(0)
    private val mutex = Mutex()
    private var lastEventTime = 0L
    private var lastEventType = ""
    private var lastScrollTime = 0L

    /**
     * 处理一个 ActionEvent，决定是否捕获帧并加入 session。
     * 在协程中调用。
     */
    suspend fun processEvent(
        event: ActionListener.ActionEvent,
        session: RecordingSession
    ): RecordingFrame? = withContext(Dispatchers.IO) {
        // 帧数限制
        if (session.frames.size >= MAX_FRAMES) return@withContext null

        // 过滤自身事件
        val pkg = event.elementInfo?.packageName
        if (pkg == SELF_PACKAGE || pkg == PROVIDER_PACKAGE) return@withContext null

        val eventType = event.actionType.name

        // 只关注有意义的事件
        if (event.actionType !in SIGNIFICANT_EVENTS) return@withContext null

        // 去抖
        mutex.withLock {
            val now = System.currentTimeMillis()

            // SCROLL 降频
            if (event.actionType == ActionListener.ActionType.SCROLL) {
                if (now - lastScrollTime < SCROLL_THROTTLE_MS) return@withContext null
                lastScrollTime = now
            }

            // 同类型事件去抖
            if (eventType == lastEventType && now - lastEventTime < DEBOUNCE_MS) {
                return@withContext null
            }

            lastEventTime = now
            lastEventType = eventType
        }

        try {
            // 抓取 UI 层级
            val uiHierarchy = try {
                UIHierarchyManager.getUIHierarchy(context) ?: ""
            } catch (e: Exception) {
                AppLogger.w(TAG, "获取UI层级失败: ${e.message}")
                ""
            }

            // 获取当前 Activity
            val activityName = try {
                UIHierarchyManager.getCurrentActivityName(context)
            } catch (e: Exception) {
                null
            }

            val details = EventDetails(
                className = event.elementInfo?.className,
                text = event.elementInfo?.text,
                contentDescription = event.elementInfo?.contentDescription,
                inputText = event.inputText,
                additionalData = emptyMap()
            )

            val frame = RecordingFrame(
                index = frameIndex.getAndIncrement(),
                timestamp = event.timestamp,
                eventType = eventType,
                eventDetails = details,
                activityName = activityName,
                packageName = pkg,
                uiHierarchySummary = uiHierarchy
            )

            session.frames.add(frame)
            frame
        } catch (e: Exception) {
            AppLogger.e(TAG, "构建帧失败", e)
            null
        }
    }

    fun reset() {
        frameIndex.set(0)
        lastEventTime = 0L
        lastEventType = ""
        lastScrollTime = 0L
    }
}
