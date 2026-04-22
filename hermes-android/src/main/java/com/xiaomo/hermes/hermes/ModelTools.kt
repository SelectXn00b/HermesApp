package com.xiaomo.hermes.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaomo.hermes.hermes.tools.listDir
import com.xiaomo.hermes.hermes.tools.patchTool
import com.xiaomo.hermes.hermes.tools.readFileTool
import com.xiaomo.hermes.hermes.tools.searchTool
import com.xiaomo.hermes.hermes.tools.writeFileTool
import com.xiaomo.hermes.hermes.tools.terminalTool
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

    // read_file — 委托 FileTools.readFileTool（支持 offset/limit 分页）
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
            readFileTool(path, offset, limit)
        })

    // write_file — 委托 FileTools.writeFileTool
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
            writeFileTool(path, content)
        })

    // patch — 委托 FileTools.patchTool（fuzzy find-and-replace）
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
            patchTool(path, oldString, newString, replaceAll)
        })

    // search_files — 委托 FileTools.searchTool
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
            searchTool(pattern, path, fileGlob, limit)
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
            val timeout = (args["timeout"] as? Number)?.toInt()
            terminalTool(command = command, workdir = workingDir, timeout = timeout)
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

    // web_extract — 委托 webExtractTool（Android 无 Firecrawl/Tavily backend，返回 error）
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
            modelToolsGson.toJson(mapOf("error" to "web_extract requires Firecrawl/Tavily backend configuration (not available on Android)."))
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

    // ── 配置管理 ──────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "config_get",
        description = "Read a config value by key path (dot notation).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "key" to mapOf("type" to "string", "description" to "Config key path (e.g. 'model', 'agents.default')")),
            "required" to listOf("key")),
        handler = { args ->
            val key = args["key"] as? String ?: ""
            platformDelegate?.configGet(key)
                ?: modelToolsGson.toJson(mapOf("error" to "config_get not available — platform delegate not set"))
        })

    ToolRegistry.register(
        name = "config_set",
        description = "Write a config value by key path (dot notation).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "key" to mapOf("type" to "string", "description" to "Config key path"),
                "value" to mapOf("type" to "string", "description" to "Value to set (JSON)")),
            "required" to listOf("key", "value")),
        handler = { args ->
            val key = args["key"] as? String ?: ""
            val value = args["value"] as? String ?: ""
            platformDelegate?.configSet(key, value)
                ?: modelToolsGson.toJson(mapOf("error" to "config_set not available — platform delegate not set"))
        })

    // ── 会话管理 ──────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "sessions_list",
        description = "List active sessions with optional filters.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "kinds" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Session kinds filter"),
                "limit" to mapOf("type" to "integer", "description" to "Max sessions to return"),
                "active_minutes" to mapOf("type" to "integer", "description" to "Only sessions active within N minutes")),
            "required" to emptyList<String>()),
        handler = { args ->
            platformDelegate?.sessionsOp("list", args)
                ?: modelToolsGson.toJson(mapOf("error" to "sessions_list not available"))
        })

    ToolRegistry.register(
        name = "sessions_send",
        description = "Send a message into another session.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "session_key" to mapOf("type" to "string", "description" to "Target session key"),
                "message" to mapOf("type" to "string", "description" to "Message to send")),
            "required" to listOf("session_key", "message")),
        handler = { args ->
            platformDelegate?.sessionsOp("send", args)
                ?: modelToolsGson.toJson(mapOf("error" to "sessions_send not available"))
        })

    ToolRegistry.register(
        name = "sessions_spawn",
        description = "Spawn an isolated sub-agent session.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "task" to mapOf("type" to "string", "description" to "Task description for the sub-agent"),
                "model" to mapOf("type" to "string", "description" to "Model override"),
                "label" to mapOf("type" to "string", "description" to "Session label"),
                "mode" to mapOf("type" to "string", "description" to "run (one-shot) or session (persistent)")),
            "required" to listOf("task")),
        handler = { args ->
            platformDelegate?.sessionsOp("spawn", args)
                ?: modelToolsGson.toJson(mapOf("error" to "sessions_spawn not available"))
        })

    ToolRegistry.register(
        name = "sessions_history",
        description = "Fetch message history for a session.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "session_key" to mapOf("type" to "string", "description" to "Session key"),
                "limit" to mapOf("type" to "integer", "description" to "Max messages")),
            "required" to listOf("session_key")),
        handler = { args ->
            platformDelegate?.sessionsOp("history", args)
                ?: modelToolsGson.toJson(mapOf("error" to "sessions_history not available"))
        })

    ToolRegistry.register(
        name = "sessions_yield",
        description = "End current turn and wait for sub-agent results.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "message" to mapOf("type" to "string", "description" to "Optional message")),
            "required" to emptyList<String>()),
        handler = { args ->
            platformDelegate?.sessionsOp("yield", args)
                ?: modelToolsGson.toJson(mapOf("error" to "sessions_yield not available"))
        })

    ToolRegistry.register(
        name = "subagents",
        description = "List, steer, or kill spawned sub-agents.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "list / steer / kill"),
                "target" to mapOf("type" to "string", "description" to "Sub-agent target"),
                "message" to mapOf("type" to "string", "description" to "Steering message")),
            "required" to listOf("action")),
        handler = { args ->
            platformDelegate?.sessionsOp("subagents", args)
                ?: modelToolsGson.toJson(mapOf("error" to "subagents not available"))
        })

    ToolRegistry.register(
        name = "session_status",
        description = "Show session status card (usage, time, cost).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "session_key" to mapOf("type" to "string", "description" to "Session key (optional)")),
            "required" to emptyList<String>()),
        handler = { args ->
            platformDelegate?.sessionsOp("status", args)
                ?: modelToolsGson.toJson(mapOf("error" to "session_status not available"))
        })

    // ── 消息 ──────────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "message",
        description = "Send messages via channel plugins (Telegram, Discord, Feishu, etc.).",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "send / react / delete / unsend"),
                "target" to mapOf("type" to "string", "description" to "Target channel/user"),
                "message" to mapOf("type" to "string", "description" to "Message content"),
                "channel" to mapOf("type" to "string", "description" to "Channel type")),
            "required" to listOf("action")),
        handler = { args ->
            platformDelegate?.messageSend(args)
                ?: modelToolsGson.toJson(mapOf("error" to "message not available"))
        })

    // ── 文件列目录 ──────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "list_dir",
        description = "List directory contents with optional depth limit.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string", "description" to "Directory path"),
                "depth" to mapOf("type" to "integer", "description" to "Max depth (default 2)"),
                "show_hidden" to mapOf("type" to "boolean", "description" to "Show hidden files (default false)")),
            "required" to listOf("path")),
        handler = { args ->
            val path = args["path"] as? String ?: "."
            val depth = (args["depth"] as? Number)?.toInt() ?: 2
            val showHidden = args["show_hidden"] as? Boolean ?: false
            listDir(path, depth, showHidden)
        })

    // ── Android 专属 ──────────────────────────────────────────────────

    ToolRegistry.register(
        name = "start_activity",
        description = "Launch an Android Activity by package/action/intent.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "package_name" to mapOf("type" to "string", "description" to "Target app package name"),
                "activity" to mapOf("type" to "string", "description" to "Activity class name"),
                "action" to mapOf("type" to "string", "description" to "Intent action (e.g. android.intent.action.VIEW)"),
                "data" to mapOf("type" to "string", "description" to "Intent data URI"),
                "extras" to mapOf("type" to "object", "description" to "Intent extras key-value pairs")),
            "required" to emptyList<String>()),
        handler = { args ->
            platformDelegate?.androidOp("start_activity", args)
                ?: modelToolsGson.toJson(mapOf("error" to "start_activity not available — needs Android Context"))
        })

    ToolRegistry.register(
        name = "device",
        description = "Get device information and control device features.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "info / screenshot / clipboard / notification / volume / brightness"),
                "value" to mapOf("type" to "string", "description" to "Value for set actions")),
            "required" to listOf("action")),
        handler = { args ->
            platformDelegate?.androidOp("device", args)
                ?: modelToolsGson.toJson(mapOf("error" to "device not available — needs Android Context"))
        })

    ToolRegistry.register(
        name = "canvas",
        description = "Control canvas: present, hide, navigate, eval, snapshot.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf("type" to "string", "description" to "present / hide / navigate / eval / snapshot"),
                "url" to mapOf("type" to "string", "description" to "URL for navigate/present"),
                "javascript" to mapOf("type" to "string", "description" to "JS to evaluate")),
            "required" to listOf("action")),
        handler = { args ->
            platformDelegate?.androidOp("canvas", args)
                ?: modelToolsGson.toJson(mapOf("error" to "canvas not available — needs Android Context"))
        })

    ToolRegistry.register(
        name = "termux_bridge",
        description = "Execute commands via Termux:API bridge.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf("type" to "string", "description" to "Command to execute in Termux"),
                "background" to mapOf("type" to "boolean", "description" to "Run in background")),
            "required" to listOf("command")),
        handler = { args ->
            platformDelegate?.androidOp("termux_bridge", args)
                ?: modelToolsGson.toJson(mapOf("error" to "termux_bridge not available — needs Termux installed"))
        })

    ToolRegistry.register(
        name = "tasker",
        description = "Trigger Tasker tasks.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "task_name" to mapOf("type" to "string", "description" to "Tasker task name"),
                "params" to mapOf("type" to "object", "description" to "Task parameters")),
            "required" to listOf("task_name")),
        handler = { args ->
            platformDelegate?.androidOp("tasker", args)
                ?: modelToolsGson.toJson(mapOf("error" to "tasker not available — needs Tasker installed"))
        })

    // ── 技能市场 ──────────────────────────────────────────────────────

    ToolRegistry.register(
        name = "clawhub_search",
        description = "Search skills on ClawHub marketplace.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "Search query"),
                "limit" to mapOf("type" to "integer", "description" to "Max results")),
            "required" to listOf("query")),
        handler = { args ->
            platformDelegate?.skillsOp("search", args)
                ?: modelToolsGson.toJson(mapOf("error" to "clawhub_search not available"))
        })

    ToolRegistry.register(
        name = "clawhub_install",
        description = "Install a skill from ClawHub.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "skill_name" to mapOf("type" to "string", "description" to "Skill name to install")),
            "required" to listOf("skill_name")),
        handler = { args ->
            platformDelegate?.skillsOp("install", args)
                ?: modelToolsGson.toJson(mapOf("error" to "clawhub_install not available"))
        })

    modelToolsLogger.info("Default tools registered: ${ToolRegistry.getAllToolNames().joinToString()}")
}

// ── Platform Delegate（app 模块注入）─────────────────────────────────────

/**
 * 平台委托接口 — app 模块实现此接口来处理需要 Android Context 的工具。
 * 在 Application.onCreate 中通过 setPlatformDelegate() 注入。
 */
interface PlatformToolDelegate {
    /** 读取配置 */
    suspend fun configGet(key: String): String
    /** 写入配置 */
    suspend fun configSet(key: String, value: String): String
    /** 会话操作（list/send/spawn/history/yield/status/subagents） */
    suspend fun sessionsOp(action: String, args: Map<String, Any>): String
    /** 消息发送 */
    suspend fun messageSend(args: Map<String, Any>): String
    /** Android 专属操作（start_activity/device/canvas/termux_bridge/tasker） */
    suspend fun androidOp(action: String, args: Map<String, Any>): String
    /** 技能市场操作（search/install） */
    suspend fun skillsOp(action: String, args: Map<String, Any>): String
}

@Volatile
private var platformDelegate: PlatformToolDelegate? = null

/**
 * app 模块调用此方法注入平台委托
 */
fun setPlatformDelegate(delegate: PlatformToolDelegate) {
    platformDelegate = delegate
    modelToolsLogger.info("Platform delegate set: ${delegate::class.simpleName}")
}
