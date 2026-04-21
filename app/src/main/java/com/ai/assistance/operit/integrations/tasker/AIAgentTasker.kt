package com.ai.assistance.operit.integrations.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperConditionNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoInput
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied

@TaskerInputRoot
class AIAgentActionUpdate @JvmOverloads constructor(
    @field:TaskerInputField("task_type")
    var taskType: String? = null,

    @field:TaskerInputField("arg1")
    var arg1: String? = null,

    @field:TaskerInputField("arg2")
    var arg2: String? = null,

    @field:TaskerInputField("arg3")
    var arg3: String? = null,

    @field:TaskerInputField("arg4")
    var arg4: String? = null,

    @field:TaskerInputField("arg5")
    var arg5: String? = null,

    @field:TaskerInputField("args_json")
    var argsJson: String? = null
)

@TaskerOutputObject
class AIAgentActionOutput(
    private val taskType: String?,
    private val arg1: String?,
    private val arg2: String?,
    private val arg3: String?,
    private val arg4: String?,
    private val arg5: String?,
    private val argsJson: String?
) {
    @get:TaskerOutputVariable("task_type")
    val outTaskType get() = taskType ?: ""
    @get:TaskerOutputVariable("arg1")
    val outArg1 get() = arg1 ?: ""
    @get:TaskerOutputVariable("arg2")
    val outArg2 get() = arg2 ?: ""
    @get:TaskerOutputVariable("arg3")
    val outArg3 get() = arg3 ?: ""
    @get:TaskerOutputVariable("arg4")
    val outArg4 get() = arg4 ?: ""
    @get:TaskerOutputVariable("arg5")
    val outArg5 get() = arg5 ?: ""
    @get:TaskerOutputVariable("args_json")
    val outArgsJson get() = argsJson ?: ""
}

class AIAgentActionEventRunner :
    TaskerPluginRunnerConditionNoInput<AIAgentActionOutput, AIAgentActionUpdate>() {
    override val isEvent: Boolean get() = true
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: AIAgentActionUpdate?
    ): TaskerPluginResultCondition<AIAgentActionOutput> {
        val u = update ?: AIAgentActionUpdate()
        val output = AIAgentActionOutput(
            u.taskType,
            u.arg1,
            u.arg2,
            u.arg3,
            u.arg4,
            u.arg5,
            u.argsJson
        )
        return TaskerPluginResultConditionSatisfied(context, output)
    }
}

class AIAgentActionHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperConditionNoInput<AIAgentActionOutput, AIAgentActionUpdate, AIAgentActionEventRunner>(config) {
    override val runnerClass = AIAgentActionEventRunner::class.java
    override val outputClass = AIAgentActionOutput::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("AI Agent Action Event")
    }
}

class ActivityConfigAIAgentAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AIAgentActionHelper(this).finishForTasker()
    }
}

fun Context.triggerAIAgentAction(taskType: String, args: Map<String, String?> = emptyMap()) {
    val update = AIAgentActionUpdate(
        taskType = taskType,
        arg1 = args["arg1"],
        arg2 = args["arg2"],
        arg3 = args["arg3"],
        arg4 = args["arg4"],
        arg5 = args["arg5"],
        argsJson = try { com.google.gson.Gson().toJson(args) } catch (_: Exception) { null }
    )
    ActivityConfigAIAgentAction::class.java.requestQuery(this, update)
}
