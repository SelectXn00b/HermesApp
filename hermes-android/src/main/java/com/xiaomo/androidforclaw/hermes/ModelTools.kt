package com.xiaomo.androidforclaw.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaomo.androidforclaw.hermes.plugins.memory.holographic.HolographicProvider
import com.xiaomo.androidforclaw.hermes.plugins.memory.honcho.HonchoClient
import com.xiaomo.androidforclaw.hermes.plugins.memory.honcho.HonchoClientConfig
import com.xiaomo.androidforclaw.hermes.plugins.memory.honcho.HonchoSessionManager
import com.xiaomo.androidforclaw.hermes.plugins.memory.honcho.getHonchoClient
import com.xiaomo.androidforclaw.hermes.plugins.memory.MemoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Model Tools Module
 * 1:1 对齐 hermes-agent/model_tools.py
 *
 * Thin orchestration layer over the tool registry。
 * Android 版本：简化为 Kotlin coroutine + Gson，移除 Python-specific 的 threading/asyncio。
 */

private val modelToolsLogger = getLogger("model_tools")
private val modelToolsGson = Gson()

// ── Tool Schema ────────────────────────────────────────────────────────────

/**
 * OpenAI function tool definition
 */
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>)

// ── Tool Registry ──────────────────────────────────────────────────────────

/**
 * 工具注册表（Android 简化版）
 * Python: tools.registry
 */
object ToolRegistry {

    private val tools = mutableMapOf<String, ToolDefinition>()
    private val handlers = mutableMapOf<String, suspend (Map<String, Any>) -> String>()

    /**
     * 注册工具
     */
    fun register(
        name: String,
        description: String,
        parameters: Map<String, Any>,
        handler: suspend (Map<String, Any>) -> String) {
        tools[name] = ToolDefinition(
            function = ToolFunction(name, description, parameters)
        )
        handlers[name] = handler
    }

    /**
     * 获取工具定义
     */
    fun getDefinitions(toolNames: Set<String>? = null): List<ToolDefinition> {
        return if (toolNames != null) {
            tools.filter { it.key in toolNames }.values.toList()
        } else {
            tools.values.toList()
        }
    }

    /**
     * 分发工具调用
     */
    suspend fun dispatch(toolName: String, args: Map<String, Any>): String {
        val handler = handlers[toolName]
            ?: return modelToolsGson.toJson(mapOf("error" to "Unknown tool: $toolName"))
        return try {
            handler(args)
        } catch (e: Exception) {
            modelToolsLogger.error("Tool $toolName failed: ${e.message}")
            modelToolsGson.toJson(mapOf("error" to "${e.message}"))
        }
    }

    /**
     * 获取所有工具名称
     */
    fun getAllToolNames(): List<String> = tools.keys.toList()

    /**
     * 获取工具 schema
     */
    fun getSchema(toolName: String): ToolDefinition? = tools[toolName]
}

// ── Toolset 定义 ──────────────────────────────────────────────────────────

/**
 * 工具集定义
 * Python: toolsets.py
 */
object Toolsets {

    private val toolsets = mutableMapOf<String, Set<String>>()

    init {
        // 注册默认工具集
        registerDefaultToolsets()
    }

    private fun registerDefaultToolsets() {
        toolsets["web"] = setOf("web_search", "web_extract")
        toolsets["terminal"] = setOf("terminal")
        toolsets["file"] = setOf("read_file", "write_file", "patch", "search_files")
        toolsets["vision"] = setOf("vision_analyze")
        toolsets["memory"] = setOf("memory")
        toolsets["browser"] = setOf(
            "browser_navigate", "browser_snapshot", "browser_click",
            "browser_type", "browser_scroll", "browser_back",
            "browser_press", "browser_get_images",
            "browser_vision", "browser_console"
        )
        toolsets["cronjob"] = setOf("cronjob")
        toolsets["tts"] = setOf("text_to_speech")
    }

    /**
     * 解析工具集名称为工具列表
     */
    fun resolve(toolsetName: String): Set<String> {
        return toolsets[toolsetName] ?: emptySet()
    }

    /**
     * 验证工具集是否存在
     */
    fun validate(toolsetName: String): Boolean {
        return toolsetName in toolsets
    }

    /**
     * 获取所有工具集
     */
    fun getAll(): Map<String, Set<String>> = toolsets.toMap()
}

// ── 公开 API ──────────────────────────────────────────────────────────────

/**
 * 获取工具定义（带过滤）
 * Python: get_tool_definitions(enabled_toolsets, disabled_toolsets, quiet_mode)
 */
fun getToolDefinitions(
    enabledToolsets: List<String>? = null,
    disabledToolsets: List<String>? = null): List<ToolDefinition> {
    val toolsToInclude = mutableSetOf<String>()

    when {
        enabledToolsets != null -> {
            for (toolsetName in enabledToolsets) {
                if (Toolsets.validate(toolsetName)) {
                    toolsToInclude.addAll(Toolsets.resolve(toolsetName))
                } else {
                    modelToolsLogger.warning("Unknown toolset: $toolsetName")
                }
            }
        }
        disabledToolsets != null -> {
            // 包含所有工具集
            for ((_, tools) in Toolsets.getAll()) {
                toolsToInclude.addAll(tools)
            }
            // 排除禁用的
            for (toolsetName in disabledToolsets) {
                if (Toolsets.validate(toolsetName)) {
                    toolsToInclude.removeAll(Toolsets.resolve(toolsetName))
                }
            }
        }
        else -> {
            // 包含所有工具集
            for ((_, tools) in Toolsets.getAll()) {
                toolsToInclude.addAll(tools)
            }
        }
    }

    return ToolRegistry.getDefinitions(toolsToInclude)
}

/**
 * 处理函数调用
 * Python: handle_function_call(function_name, function_args, ...)
 */
suspend fun handleFunctionCall(
    functionName: String,
    functionArgs: Map<String, Any>,
    taskId: String? = null,
    sessionId: String? = null): String {
    return withContext(Dispatchers.IO) {
        try {
            val result = ToolRegistry.dispatch(functionName, functionArgs)
            result
        } catch (e: Exception) {
            val errorMsg = "Error executing $functionName: ${e.message}"
            modelToolsLogger.error(errorMsg)
            modelToolsGson.toJson(mapOf("error" to errorMsg))
        }
    }
}

/**
 * 获取所有工具名称
 */
fun getAllToolNames(): List<String> = ToolRegistry.getAllToolNames()

/**
 * 检查工具集需求
 */
fun checkToolsetRequirements(): Map<String, Boolean> {
    return Toolsets.getAll().keys.associateWith { true }
}

/**
 * 获取可用工具集
 */
fun getAvailableToolsets(): Map<String, Map<String, Any>> {
    return Toolsets.getAll().mapValues { (name, tools) ->
        mapOf(
            "name" to name,
            "tools" to tools.toList(),
            "count" to tools.size,
            "available" to true)
    }
}

// ── 工具实现（示例）─────────────────────────────────────────────────────────

/**
 * 注册默认工具（在 Application.onCreate 中调用）
 */
fun registerDefaultTools() {

    // web_search
    ToolRegistry.register(
        name = "web_search",
        description = "Search the web for information",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "Search query"),
                "count" to mapOf("type" to "integer", "description" to "Number of results")),
            "required" to listOf("query")),
        handler = { args ->
            // 简化实现
            modelToolsGson.toJson(mapOf("results" to emptyList<String>()))
        })

    // memory
    ToolRegistry.register(
        name = "memory",
        description = "Store and retrieve memories",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "store/retrieve/delete"),
                "content" to mapOf("type" to "string", "description" to "Memory content"),
                "query" to mapOf("type" to "string", "description" to "Search query")),
            "required" to listOf("action")),
        handler = { args ->
            val action = args["action"] as? String ?: "retrieve"
            when (action) {
                "store" -> {
                    val content = args["content"] as? String ?: ""
                    modelToolsGson.toJson(mapOf("result" to "stored", "content" to content))
                }
                "retrieve" -> {
                    val query = args["query"] as? String ?: ""
                    modelToolsGson.toJson(mapOf("results" to emptyList<String>()))
                }
                else -> modelToolsGson.toJson(mapOf("error" to "Unknown action: $action"))
            }
        })

    // read_file
    ToolRegistry.register(
        name = "read_file",
        description = "Read the contents of a file",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "File path")),
            "required" to listOf("path")),
        handler = { args ->
            val path = args["path"] as? String ?: ""
            val file = File(path)
            if (file.exists()) {
                modelToolsGson.toJson(mapOf("content" to file.readText(Charsets.UTF_8)))
            } else {
                modelToolsGson.toJson(mapOf("error" to "File not found: $path"))
            }
        })

    // write_file
    ToolRegistry.register(
        name = "write_file",
        description = "Write content to a file",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "File path"),
                "content" to mapOf("type" to "string", "description" to "File content")),
            "required" to listOf("path", "content")),
        handler = { args ->
            val path = args["path"] as? String ?: ""
            val content = args["content"] as? String ?: ""
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            modelToolsGson.toJson(mapOf("result" to "written", "path" to path))
        })

    // terminal
    ToolRegistry.register(
        name = "terminal",
        description = "Execute a shell command",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf("type" to "string", "description" to "Command to execute")),
            "required" to listOf("command")),
        handler = { args ->
            val command = args["command"] as? String ?: ""
            // 简化实现：Android 不直接执行 shell
            modelToolsGson.toJson(mapOf("error" to "Terminal not available on Android"))
        })

    modelToolsLogger.info("Default tools registered: ${ToolRegistry.getAllToolNames().joinToString()}")


}
