package com.xiaomo.feishu.tools.task

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书任务工具集
 * 对齐 OpenClaw src/task-tools
 */
class FeishuTaskTools(config: FeishuConfig, client: FeishuClient) {
    private val createTool = TaskCreateTool(config, client)
    private val updateTool = TaskUpdateTool(config, client)
    private val listTool = TaskListTool(config, client)
    private val completeTool = TaskCompleteTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(createTool, updateTool, listTool, completeTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

/**
 * 创建任务工具
 */
class TaskCreateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_task_create"
    override val description = "创建飞书任务"

    override fun isEnabled() = config.enableTaskTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val summary = args["summary"] as? String ?: return@withContext ToolResult.error("Missing summary")
            val description = args["description"] as? String
            val dueDate = args["due_date"] as? String
            @Suppress("UNCHECKED_CAST")
            val collaborators = args["collaborators"] as? List<String>

            val body = mutableMapOf<String, Any>("summary" to summary)
            if (description != null) body["description"] = description
            if (dueDate != null) body["due"] = mapOf("timestamp" to dueDate)
            if (collaborators != null && collaborators.isNotEmpty()) {
                body["collaborator_ids"] = collaborators
            }

            val result = client.post("/open-apis/task/v2/tasks", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val task = data?.getAsJsonObject("task")
            val taskId = task?.get("guid")?.asString
                ?: return@withContext ToolResult.error("Missing task guid")

            Log.d("TaskCreateTool", "Task created: $taskId")
            ToolResult.success(mapOf(
                "task_id" to taskId,
                "summary" to summary
            ))

        } catch (e: Exception) {
            Log.e("TaskCreateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "summary" to PropertySchema("string", "任务标题"),
                    "description" to PropertySchema("string", "任务描述（可选）"),
                    "due_date" to PropertySchema("string", "截止时间戳（可选）"),
                    "collaborators" to PropertySchema("array", "协作者ID列表（可选）", items = PropertySchema("string", "协作者ID"))
                ),
                required = listOf("summary")
            )
        )
    )
}

/**
 * 更新任务工具
 */
class TaskUpdateTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_task_update"
    override val description = "更新飞书任务"

    override fun isEnabled() = config.enableTaskTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val taskId = args["task_id"] as? String ?: return@withContext ToolResult.error("Missing task_id")
            val summary = args["summary"] as? String
            val description = args["description"] as? String
            val dueDate = args["due_date"] as? String

            val body = mutableMapOf<String, Any>()
            if (summary != null) body["summary"] = summary
            if (description != null) body["description"] = description
            if (dueDate != null) body["due"] = mapOf("timestamp" to dueDate)

            if (body.isEmpty()) {
                return@withContext ToolResult.error("No update fields provided")
            }

            val result = client.patch("/open-apis/task/v2/tasks/$taskId", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("TaskUpdateTool", "Task updated: $taskId")
            ToolResult.success(mapOf("task_id" to taskId))

        } catch (e: Exception) {
            Log.e("TaskUpdateTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "task_id" to PropertySchema("string", "任务ID"),
                    "summary" to PropertySchema("string", "新标题（可选）"),
                    "description" to PropertySchema("string", "新描述（可选）"),
                    "due_date" to PropertySchema("string", "新截止时间戳（可选）")
                ),
                required = listOf("task_id")
            )
        )
    )
}

/**
 * 列出任务工具
 */
class TaskListTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_task_list"
    override val description = "列出飞书任务"

    override fun isEnabled() = config.enableTaskTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val completed = args["completed"] as? Boolean
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50

            val params = mutableListOf("page_size=$pageSize")
            if (completed != null) {
                params.add("completed=$completed")
            }

            val path = "/open-apis/task/v2/tasks?" + params.joinToString("&")
            val result = client.get(path)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val items = data?.getAsJsonArray("items") ?: return@withContext ToolResult.success(mapOf("tasks" to emptyList<Map<String, Any>>()))

            val tasks = items.map { item ->
                val obj = item.asJsonObject
                mapOf(
                    "task_id" to (obj.get("guid")?.asString ?: ""),
                    "summary" to (obj.get("summary")?.asString ?: ""),
                    "completed" to (obj.get("completed_at")?.asString != null)
                )
            }

            Log.d("TaskListTool", "Tasks listed: ${tasks.size}")
            ToolResult.success(mapOf("tasks" to tasks))

        } catch (e: Exception) {
            Log.e("TaskListTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "completed" to PropertySchema("boolean", "筛选已完成任务（可选）"),
                    "page_size" to PropertySchema("number", "每页数量（默认50）")
                ),
                required = emptyList()
            )
        )
    )
}

/**
 * 完成任务工具
 */
class TaskCompleteTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_task_complete"
    override val description = "标记飞书任务为已完成"

    override fun isEnabled() = config.enableTaskTools

    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val taskId = args["task_id"] as? String ?: return@withContext ToolResult.error("Missing task_id")

            val body = mapOf(
                "completed_at" to System.currentTimeMillis().toString()
            )

            val result = client.patch("/open-apis/task/v2/tasks/$taskId", body)

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")
            }

            Log.d("TaskCompleteTool", "Task completed: $taskId")
            ToolResult.success(mapOf("task_id" to taskId))

        } catch (e: Exception) {
            Log.e("TaskCompleteTool", "Failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "task_id" to PropertySchema("string", "任务ID")
                ),
                required = listOf("task_id")
            )
        )
    )
}
