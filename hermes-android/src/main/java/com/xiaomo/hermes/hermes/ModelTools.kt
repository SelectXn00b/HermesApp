package com.xiaomo.hermes.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaomo.hermes.hermes.tools.FileTools
import com.xiaomo.hermes.hermes.tools.WebTools
import com.xiaomo.hermes.hermes.tools.TerminalTool
import com.xiaomo.hermes.hermes.tools.ProcessRegistry
import com.xiaomo.hermes.hermes.tools.MemoryTool
import com.xiaomo.hermes.hermes.plugins.memory.holographic.HolographicProvider
import com.xiaomo.hermes.hermes.plugins.memory.honcho.HonchoClient
import com.xiaomo.hermes.hermes.plugins.memory.honcho.HonchoClientConfig
import com.xiaomo.hermes.hermes.plugins.memory.honcho.HonchoSessionManager
import com.xiaomo.hermes.hermes.plugins.memory.honcho.getHonchoClient
import com.xiaomo.hermes.hermes.plugins.memory.MemoryProvider
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

// ── 工具实现 ─────────────────────────────────────────────────────────────

/**
 * 注册默认工具（在 Application.onCreate 中调用）
 * 接通 FileTools / WebTools / TerminalTool / ProcessRegistry / MemoryTool 真实实现
 */
fun registerDefaultTools() {

    // ── 文件操作 ──────────────────────────────────────────────────────

    // read_file — 委托 FileTools.readFile（支持 offset/limit 分页）
    ToolRegistry.register(
        name = "read_file",
        description = "Read the contents of a file with line numbers. Supports offset and limit for large files.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "File path to read"),
                "offset" to mapOf("type" to "integer", "description" to "Line number to start from (1-indexed, default 1)"),
                "limit" to mapOf("type" to "integer", "description" to "Max lines to read (default 500)")),
            "required" to listOf("path")),
        handler = { args ->
            val path = args["path"] as? String ?: ""
            val offset = (args["offset"] as? Number)?.toInt() ?: 1
            val limit = (args["limit"] as? Number)?.toInt() ?: 500
            FileTools.readFile(path, offset, limit)
        })

    // write_file — 委托 FileTools.writeFile
    ToolRegistry.register(
        name = "write_file",
        description = "Write content to a file. Creates parent directories if needed.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "File path to write"),
                "content" to mapOf("type" to "string", "description" to "Content to write")),
            "required" to listOf("path", "content")),
        handler = { args ->
            val path = args["path"] as? String ?: ""
            val content = args["content"] as? String ?: ""
            FileTools.writeFile(path, content)
        })

    // patch — 委托 FileTools.patchFile（fuzzy find-and-replace）
    ToolRegistry.register(
        name = "patch",
        description = "Patch a file by replacing old text with new text (fuzzy match).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "File path to patch"),
                "old_string" to mapOf("type" to "string", "description" to "Text to find"),
                "new_string" to mapOf("type" to "string", "description" to "Replacement text"),
                "replace_all" to mapOf("type" to "boolean", "description" to "Replace all occurrences (default false)")),
            "required" to listOf("path", "old_string", "new_string")),
        handler = { args ->
            val path = args["path"] as? String ?: ""
            val oldString = args["old_string"] as? String ?: ""
            val newString = args["new_string"] as? String ?: ""
            val replaceAll = args["replace_all"] as? Boolean ?: false
            FileTools.patchFile(path, oldString, newString, replaceAll)
        })

    // search_files — 委托 FileTools.searchFiles
    ToolRegistry.register(
        name = "search_files",
        description = "Search for content or filenames matching a pattern.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "pattern" to mapOf("type" to "string", "description" to "Search pattern (regex)"),
                "path" to mapOf("type" to "string", "description" to "Directory to search in (default .)"),
                "file_glob" to mapOf("type" to "string", "description" to "File glob filter (e.g. *.kt)"),
                "limit" to mapOf("type" to "integer", "description" to "Max results (default 50)")),
            "required" to listOf("pattern")),
        handler = { args ->
            val pattern = args["pattern"] as? String ?: ""
            val path = args["path"] as? String ?: "."
            val fileGlob = args["file_glob"] as? String
            val limit = (args["limit"] as? Number)?.toInt() ?: 50
            FileTools.searchFiles(pattern, path, fileGlob, limit)
        })

    // ── 终端命令 ──────────────────────────────────────────────────────

    // terminal — 委托 TerminalTool.execute
    ToolRegistry.register(
        name = "terminal",
        description = "Execute a shell command and return stdout/stderr/exitCode.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf("type" to "string", "description" to "Shell command to execute"),
                "working_dir" to mapOf("type" to "string", "description" to "Working directory"),
                "timeout" to mapOf("type" to "integer", "description" to "Timeout in seconds (default 30)")),
            "required" to listOf("command")),
        handler = { args ->
            val command = args["command"] as? String ?: ""
            val workingDir = args["working_dir"] as? String
            val timeout = (args["timeout"] as? Number)?.toLong() ?: 30L
            val result = TerminalTool.execute(command, workingDir, timeout)
            modelToolsGson.toJson(mapOf(
                "stdout" to result.stdout,
                "stderr" to result.stderr,
                "exit_code" to result.exitCode
            ).let { base ->
                if (result.error != null) base + ("error" to result.error) else base
            })
        })

    // process — 委托 ProcessRegistry（后台进程管理）
    ToolRegistry.register(
        name = "process",
        description = "Manage background processes: spawn, poll, kill, list, write stdin.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "spawn/poll/kill/list/write"),
                "command" to mapOf("type" to "string", "description" to "Command for spawn"),
                "session_id" to mapOf("type" to "string", "description" to "Session ID for poll/kill/write"),
                "data" to mapOf("type" to "string", "description" to "Data for write"),
                "working_dir" to mapOf("type" to "string", "description" to "Working directory for spawn"),
                "timeout" to mapOf("type" to "integer", "description" to "Timeout for spawn")),
            "required" to listOf("action")),
        handler = { args ->
            val action = args["action"] as? String ?: ""
            when (action) {
                "spawn" -> {
                    val command = args["command"] as? String ?: ""
                    val workingDir = args["working_dir"] as? String
                    val timeout = (args["timeout"] as? Number)?.toLong() ?: 30L
                    val result = ProcessRegistry.spawnLocal(
                        command = command,
                        taskId = "default",
                        cwd = workingDir)
                    modelToolsGson.toJson(result)
                }
                "poll" -> {
                    val sessionId = args["session_id"] as? String ?: ""
                    modelToolsGson.toJson(ProcessRegistry.poll(sessionId))
                }
                "kill" -> {
                    val sessionId = args["session_id"] as? String ?: ""
                    modelToolsGson.toJson(ProcessRegistry.killProcess(sessionId))
                }
                "list" -> {
                    modelToolsGson.toJson(mapOf("sessions" to ProcessRegistry.listSessions()))
                }
                "write" -> {
                    val sessionId = args["session_id"] as? String ?: ""
                    val data = args["data"] as? String ?: ""
                    modelToolsGson.toJson(ProcessRegistry.writeStdin(sessionId, data))
                }
                else -> modelToolsGson.toJson(mapOf("error" to "Unknown action: $action"))
            }
        })

    // ── 网络搜索 ──────────────────────────────────────────────────────

    // web_search — 目前 Android 端没有搜索 API key，留 placeholder 给外部注入
    ToolRegistry.register(
        name = "web_search",
        description = "Search the web for information.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "Search query"),
                "count" to mapOf("type" to "integer", "description" to "Number of results (default 5)")),
            "required" to listOf("query")),
        handler = { args ->
            // TODO: 接入 Brave/Tavily/SerpAPI，需要 API key 配置
            modelToolsGson.toJson(mapOf("error" to "web_search requires API key configuration. Use web_extract to fetch specific URLs instead."))
        })

    // web_extract — 委托 WebTools.fetch
    ToolRegistry.register(
        name = "web_extract",
        description = "Fetch and extract content from a URL.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "url" to mapOf("type" to "string", "description" to "URL to fetch"),
                "extract_mode" to mapOf("type" to "string", "description" to "Extract mode: markdown or text (default markdown)"),
                "max_chars" to mapOf("type" to "integer", "description" to "Max characters to return (default 50000)")),
            "required" to listOf("url")),
        handler = { args ->
            val url = args["url"] as? String ?: ""
            val mode = args["extract_mode"] as? String ?: "markdown"
            val maxChars = (args["max_chars"] as? Number)?.toInt() ?: 50000
            WebTools.fetch(url, mode, maxChars)
        })

    // ── 记忆 ──────────────────────────────────────────────────────────

    // memory — 委托 MemoryTool（store/search/delete）
    ToolRegistry.register(
        name = "memory",
        description = "Persistent memory: store observations, search past memories, or delete entries.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "store / search / delete / list"),
                "content" to mapOf("type" to "string", "description" to "Content to store (for store action)"),
                "query" to mapOf("type" to "string", "description" to "Search query (for search action)"),
                "id" to mapOf("type" to "string", "description" to "Memory ID (for delete action)"),
                "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Tags for categorization")),
            "required" to listOf("action")),
        handler = { args ->
            val action = args["action"] as? String ?: "search"
            when (action) {
                "store" -> {
                    val content = args["content"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val tags = (args["tags"] as? List<String>) ?: emptyList()
                    val entry = MemoryTool.store(content, tags)
                    modelToolsGson.toJson(mapOf("stored" to true, "id" to entry.id, "content" to entry.content))
                }
                "search" -> {
                    val query = args["query"] as? String ?: ""
                    val limit = (args["limit"] as? Number)?.toInt() ?: 10
                    val results = MemoryTool.search(query, limit)
                    modelToolsGson.toJson(mapOf("results" to results.map { mapOf("id" to it.id, "content" to it.content, "tags" to it.tags) }))
                }
                "delete" -> {
                    val id = args["id"] as? String ?: ""
                    val deleted = MemoryTool.delete(id)
                    modelToolsGson.toJson(mapOf("deleted" to deleted))
                }
                "list" -> {
                    val all = MemoryTool.getAll()
                    modelToolsGson.toJson(mapOf("count" to all.size, "entries" to all.map { mapOf("id" to it.id, "content" to it.content, "tags" to it.tags) }))
                }
                else -> modelToolsGson.toJson(mapOf("error" to "Unknown memory action: $action"))
            }
        })

    // ── TTS ───────────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "text_to_speech",
        description = "Convert text to speech audio.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "text" to mapOf("type" to "string", "description" to "Text to convert to speech")),
            "required" to listOf("text")),
        handler = { args ->
            // TTS 需要 Android Context，由 app 模块的 TtsTool 处理
            // 这里返回占位响应，实际由 HermesAgentLoop bridge 转发
            modelToolsGson.toJson(mapOf("error" to "TTS requires Android context — use app module TtsTool"))
        })

    modelToolsLogger.info("Default tools registered: ${ToolRegistry.getAllToolNames().joinToString()}")
}
