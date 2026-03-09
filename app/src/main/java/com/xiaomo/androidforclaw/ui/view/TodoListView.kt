package com.xiaomo.androidforclaw.ui.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.ext.setMarkdownText
import com.draco.ladb.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

/**
 * TodosView
 * Specially displays instructionProgress content from MobileOperationAgent
 * Uses TaskDataManager and TaskData Flow for reactive updates
 */
class TodosView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TodosView"
    }

    private lateinit var progressContent: TextView
    private lateinit var expectedContent: TextView
    private val taskDataManager: TaskDataManager = TaskDataManager.getInstance()
    private var progressCollectJob: Job? = null
    private var taskDataCollectJob: Job? = null
    private var expectedSet: Boolean = false

    init {
        orientation = VERTICAL
        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        // Load layout
        inflate(context, R.layout.view_todo_list_simplified, this)
        progressContent = findViewById(R.id.progressContent)

        // Set initial state
        updateProgressDisplay("Waiting")

        // Append an “Expected after operation” card after progress content (static, not continuously updated)
        val parentContainer = progressContent.parent as? LinearLayout ?: this
        expectedContent = TextView(context).apply {
            textSize = 14f
            setTextColor(context.getColor(android.R.color.black))
            setPadding(8, 16, 8, 8)
            text = "操作后的预期：\n(等待任务开始)"
        }
        parentContainer.addView(expectedContent)
    }

    private fun setupObservers() {
        Log.d(TAG, "设置TodosView观察者...")

        // ✅ Use GlobalScope to avoid missing LifecycleOwner issue in floating window
        taskDataCollectJob = kotlinx.coroutines.GlobalScope.launch {
            taskDataManager.currentTaskData.collect { taskData ->
                Log.d(TAG, "TaskData变化: ${taskData?.taskId}")

                // Cancel old subscription
                progressCollectJob?.cancel()

                if (taskData != null) {
                    // ✅ Fix: Reset expectedSet flag when new task starts
                    expectedSet = false
                    Log.d(TAG, "新任务开始，重置expectedSet标志")

                    // TODO: conversationFlow removed (old architecture)
                    // New architecture uses SessionManager to manage conversation history, no longer stored in TaskData
                    // Progress info displayed via ProgressUpdate → SessionFloatWindow
                    updateProgressDisplay("等待任务执行...")

                    // TODO: currentTestCaseFlow removed (old architecture concept)
                    // New architecture has no pre-generated test cases, no need to display "Expected after operation"
                    if (!expectedSet) {
                        post {
                            expectedContent.setMarkdownText("### 操作后的预期\n\n（新架构暂无）")
                            expectedSet = true
                        }
                    }
                } else {
                    // Display default state when TaskData is null
                    Log.d(TAG, "TaskData为null，显示默认状态")
                    updateProgressDisplay("等待任务开始...")
                    expectedContent.text = "操作后的预期：\n(等待任务开始)"
                    expectedSet = false
                }
            }
        }

        Log.d(TAG, "已设置TaskData观察者")
    }

    private fun updateProgressDisplay(progress: String) {
        Log.d(TAG, "更新进度显示: ${progress.take(100)}...")
        post {
            if (progress.isNullOrEmpty()) {
                progressContent.text = "等待任务开始..."
            } else {
                progressContent.setMarkdownText(progress)
            }
        }

    }

    fun cleanup() {
        Log.d(TAG, "清理TodosView资源")
        progressCollectJob?.cancel()
        progressCollectJob = null
        taskDataCollectJob?.cancel()
        taskDataCollectJob = null
    }
} 