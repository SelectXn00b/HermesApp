package com.ai.assistance.operit.integrations.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import kotlinx.coroutines.runBlocking
import java.util.ArrayList

/**
 * Tasker Plugin Activity for triggering workflows
 * 
 * This allows Tasker to trigger Operit workflows as part of Tasker tasks.
 */
class WorkflowTaskerActivityConfig : Activity(), TaskerPluginConfig<WorkflowTaskerInput> {
    
    override val context: Context get() = applicationContext
    
    private val taskerHelper by lazy { 
        WorkflowTaskerConfigHelper(this) 
    }

    override val inputForTasker: TaskerInput<WorkflowTaskerInput>
        get() = TaskerInput(WorkflowTaskerInput())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.onCreate()
    }

    override fun assignFromInput(input: TaskerInput<WorkflowTaskerInput>): Unit {
        // Input assignment handled by Tasker library
    }
}

/**
 * Tasker Plugin Config Helper
 */
class WorkflowTaskerConfigHelper(config: TaskerPluginConfig<WorkflowTaskerInput>) : 
    TaskerPluginConfigHelper<WorkflowTaskerInput, Unit, WorkflowTaskerRunner>(config) {
    
    override val inputClass: Class<WorkflowTaskerInput>
        get() = WorkflowTaskerInput::class.java
    
    override val outputClass: Class<Unit>
        get() = Unit::class.java
    
    override val runnerClass: Class<WorkflowTaskerRunner>
        get() = WorkflowTaskerRunner::class.java
}

/**
 * Tasker Plugin Input Data
 */
data class WorkflowTaskerInput(
    val params: ArrayList<String>? = arrayListOf()
)

/**
 * Tasker Plugin Runner
 */
class WorkflowTaskerRunner : TaskerPluginRunnerAction<WorkflowTaskerInput, Unit>() {
    
    override fun run(
        context: Context,
        input: TaskerInput<WorkflowTaskerInput>
    ): TaskerPluginResult<Unit> {
        val params = input.regular.params
        
        if (params.isNullOrEmpty()) {
            // No params passed from Tasker, nothing to do.
            return TaskerPluginResultSucess()
        }

        return try {
            val repository = WorkflowRepository(context)
            // This new repository method will find and trigger workflows based on the passed params
            runBlocking {
                repository.triggerWorkflowsByTaskerEvent(params)
            }
            TaskerPluginResultSucess()
        } catch (e: Exception) {
            TaskerPluginResultError(e) // Return error to Tasker for debugging
        }
    }
}

