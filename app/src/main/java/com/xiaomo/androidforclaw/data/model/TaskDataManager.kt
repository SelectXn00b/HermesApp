/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: data models.
 */
package com.xiaomo.androidforclaw.data.model

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Task data manager
 * Responsible for managing TaskData creation, replacement and access
 */
class TaskDataManager {
    companion object {
        private const val TAG = "TaskDataManager"

        @Volatile
        private var INSTANCE: TaskDataManager? = null

        fun getInstance(): TaskDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskDataManager().also { INSTANCE = it }
            }
        }
    }

    private val _currentTaskData = MutableStateFlow<TaskData?>(null)
    val currentTaskData: StateFlow<TaskData?> = _currentTaskData.asStateFlow()

    /**
     * Start new task, create new TaskData
     */
    fun startNewTask(taskId: String,packageName: String) {
        Log.d(TAG, "启动新任务: $taskId")
        val newTaskData = TaskData(taskId,packageName)
        _currentTaskData.value = newTaskData
    }

    /**
     * Get current task data
     */
    fun getCurrentTaskData(): TaskData? = _currentTaskData.value

    /**
     * Clear current task data
     */
    fun clearCurrentTask() {
        Log.d(TAG, "清理当前任务数据")
        _currentTaskData.value = null
    }

    /**
     * Check if there is a current task
     */
    fun hasCurrentTask(): Boolean = _currentTaskData.value != null
}
