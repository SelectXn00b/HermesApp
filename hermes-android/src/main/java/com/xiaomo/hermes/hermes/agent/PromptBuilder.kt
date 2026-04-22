package com.xiaomo.hermes.hermes.agent

/**
 * Prompt Builder - system prompt 构建
 * 1:1 对齐 hermes/agent/prompt_builder.py
 *
 * 组装完整的 system prompt，包括身份、工具指令、上下文等。
 */

// ── Data Classes ─────────────────────────────────────────────────────────

data class PromptSection(
    val title: String,
    val content: String,
    val priority: Int = 0,  // 优先级，数字越小越靠前
    val enabled: Boolean = true
)

data class AgentIdentity(
    val name: String = "Hermes",
    val description: String = "",
    val capabilities: List<String> = emptyList(),
    val constraints: List<String> = emptyList()
)

data class PromptBuildContext(
    val agentIdentity: AgentIdentity = AgentIdentity(),
    val workingDirectory: String = "",
    val osInfo: String = "",
    val shellInfo: String = "",
    val gitBranch: String = "",
    val projectName: String = "",
    val projectDescription: String = "",
    val customSections: List<PromptSection> = emptyList(),
    val toolNames: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val memorySummary: String = "",
    val directoryHints: String = "",
    val platform: String = "android"
)

// ── Default Prompt Sections ──────────────────────────────────────────────

object DefaultPromptTemplates {

    const val IDENTITY_TEMPLATE = """You are {name}, {description}.

Your capabilities:
{capabilities}

Constraints:
{constraints}"""

    const val TOOL_INSTRUCTIONS = """You have access to the following tools: {tool_names}

When using tools:
1. Think carefully about which tool to use for the task.
2. Provide the correct parameters for the tool.
3. Use tool results to inform your next action.
4. If a tool fails, try an alternative approach or ask the user."""

    const val CODE_CONDUCT = """When writing code:
- Write clean, readable, well-documented code
- Follow the project's existing code style
- Test your changes when possible
- Explain your reasoning before making changes"""

    const val SAFETY_RULES = """Safety rules:
- Do not execute destructive commands without confirmation
- Do not access or modify sensitive files without permission
- Always explain what you're about to do before doing it
- Ask for confirmation before making irreversible changes"""

    const val RESPONSE_FORMAT = """Any? format:
- Be concise and direct
- Use markdown for formatting
- Use code blocks for code
- Provide clear explanations for complex topics"""
}

// ── Main PromptBuilder Class ─────────────────────────────────────────────

class PromptBuilder(
    private val context: PromptBuildContext = PromptBuildContext()
) {

    private val sections = mutableListOf<PromptSection>()

    /**
     * 构建完整的 system prompt
     *
     * @return 完整的 system prompt 文本
     */
    fun build(): String {
        sections.clear()

        // 1. 身份信息
        addIdentitySection()

        // 2. 工作环境信息
        addEnvironmentSection()

        // 3. 项目信息
        addProjectSection()

        // 4. 工具指令
        addToolInstructionsSection()

        // 5. 安全规则
        addSafetySection()

        // 6. 代码规范
        addCodeConductSection()

        // 7. 响应格式
        addResponseFormatSection()

        // 8. 技能列表
        addSkillsSection()

        // 9. 记忆摘要
        addMemorySection()

        // 10. 目录提示
        addDirectoryHintsSection()

        // 11. 自定义 sections
        for (section in context.customSections) {
            if (section.enabled) {
                sections.add(section)
            }
        }

        // 按优先级排序并拼接
        return sections
            .filter { it.enabled }
            .sortedBy { it.priority }
            .joinToString("\n\n") { section ->
                if (section.title.isNotEmpty()) {
                    "# ${section.title}\n\n${section.content}"
                } else {
                    section.content
                }
            }
            .trim()
    }

    /**
     * 添加身份信息 section
     */
    private fun addIdentitySection() {
        val identity = context.agentIdentity
        val capabilities = if (identity.capabilities.isNotEmpty()) {
            identity.capabilities.joinToString("\n") { "- $it" }
        } else {
            "- Code generation and editing\n- File operations\n- Web search\n- Task automation"
        }

        val constraints = if (identity.constraints.isNotEmpty()) {
            identity.constraints.joinToString("\n") { "- $it" }
        } else {
            "- Always ask before making destructive changes\n- Respect existing code conventions"
        }

        val description = if (identity.description.isNotEmpty()) {
            identity.description
        } else {
            "an AI assistant that helps with software development tasks"
        }

        val content = DefaultPromptTemplates.IDENTITY_TEMPLATE
            .replace("{name}", identity.name)
            .replace("{description}", description)
            .replace("{capabilities}", capabilities)
            .replace("{constraints}", constraints)

        sections.add(PromptSection(title = "Identity", content = content, priority = 0))
    }

    /**
     * 添加工作环境信息 section
     */
    private fun addEnvironmentSection() {
        val envParts = mutableListOf<String>()

        if (context.workingDirectory.isNotEmpty()) {
            envParts.add("Working directory: ${context.workingDirectory}")
        }
        if (context.osInfo.isNotEmpty()) {
            envParts.add("OS: ${context.osInfo}")
        }
        if (context.shellInfo.isNotEmpty()) {
            envParts.add("Shell: ${context.shellInfo}")
        }
        if (context.gitBranch.isNotEmpty()) {
            envParts.add("Git branch: ${context.gitBranch}")
        }
        envParts.add("Platform: ${context.platform}")

        if (envParts.isNotEmpty()) {
            sections.add(
                PromptSection(
                    title = "Environment",
                    content = envParts.joinToString("\n"),
                    priority = 10
                )
            )
        }
    }

    /**
     * 添加项目信息 section
     */
    private fun addProjectSection() {
        if (context.projectName.isEmpty() && context.projectDescription.isEmpty()) return

        val content = buildString {
            if (context.projectName.isNotEmpty()) {
                appendLine("Project: ${context.projectName}")
            }
            if (context.projectDescription.isNotEmpty()) {
                appendLine(context.projectDescription)
            }
        }

        sections.add(PromptSection(title = "Project", content = content.trim(), priority = 20))
    }

    /**
     * 添加工具指令 section
     */
    private fun addToolInstructionsSection() {
        if (context.toolNames.isEmpty()) return

        val content = DefaultPromptTemplates.TOOL_INSTRUCTIONS
            .replace("{tool_names}", context.toolNames.joinToString(", "))

        sections.add(PromptSection(title = "Tool Usage", content = content, priority = 30))
    }

    /**
     * 添加安全规则 section
     */
    private fun addSafetySection() {
        sections.add(
            PromptSection(
                title = "Safety Rules",
                content = DefaultPromptTemplates.SAFETY_RULES,
                priority = 40
            )
        )
    }

    /**
     * 添加代码规范 section
     */
    private fun addCodeConductSection() {
        sections.add(
            PromptSection(
                title = "Code Conduct",
                content = DefaultPromptTemplates.CODE_CONDUCT,
                priority = 50
            )
        )
    }

    /**
     * 添加响应格式 section
     */
    private fun addResponseFormatSection() {
        sections.add(
            PromptSection(
                title = "Any? Format",
                content = DefaultPromptTemplates.RESPONSE_FORMAT,
                priority = 60
            )
        )
    }

    /**
     * 添加技能列表 section
     */
    private fun addSkillsSection() {
        if (context.skills.isEmpty()) return

        val content = buildString {
            appendLine("Available skills:")
            for (skill in context.skills) {
                appendLine("- $skill")
            }
        }

        sections.add(PromptSection(title = "Skills", content = content.trim(), priority = 70))
    }

    /**
     * 添加记忆摘要 section
     */
    private fun addMemorySection() {
        if (context.memorySummary.isEmpty()) return

        sections.add(
            PromptSection(
                title = "Memory",
                content = context.memorySummary,
                priority = 80
            )
        )
    }

    /**
     * 添加目录提示 section
     */
    private fun addDirectoryHintsSection() {
        if (context.directoryHints.isEmpty()) return

        sections.add(
            PromptSection(
                title = "Project Structure",
                content = context.directoryHints,
                priority = 90
            )
        )
    }

    /**
     * 添加自定义 section
     */
    fun addCustomSection(title: String, content: String, priority: Int = 100) {
        sections.add(PromptSection(title = title, content = content, priority = priority))
    }

    /**
     * 获取构建的 sections 列表
     */
    fun getSections(): List<PromptSection> = sections.toList()

    /**
     * 估算 prompt 的 token 数
     */
    fun estimateTokens(): Int {
        val fullPrompt = build()
        return estimateTokensRough(fullPrompt)
    }

    companion object {
        /**
         * 快速构建一个简单的 system prompt
         */
        fun quickBuild(
            name: String = "Hermes",
            description: String = "",
            tools: List<String> = emptyList()
        ): String {
            val builder = PromptBuilder(
                PromptBuildContext(
                    agentIdentity = AgentIdentity(
                        name = name,
                        description = description.ifEmpty { "an AI assistant" }
                    ),
                    toolNames = tools
                )
            )
            return builder.build()
        }

        /**
         * 构建 Android 特定的 prompt context
         */
        fun forAndroid(
            projectName: String = "",
            customSections: List<PromptSection> = emptyList()
        ): PromptBuilder {
            return PromptBuilder(
                PromptBuildContext(
                    platform = "android",
                    osInfo = "Android",
                    projectName = projectName,
                    customSections = customSections
                )
            )
        }
    }


}

// ═══════════════════════════════════════════════════════════════════════════
// Python alignment — agent/prompt_builder.py 1:1 module-level API
// ═══════════════════════════════════════════════════════════════════════════
//
// Android: all file-scanning helpers run against the local filesystem where
// applicable; skill-snapshot / WSL / git-root paths fall back to safe defaults.
// Text constants are copied verbatim from Python so OpenAI-spec prompt output
// matches between the two runtimes.

// ── Module state ───────────────────────────────────────────────────────────

private val _promptLogger = com.xiaomo.hermes.hermes.getLogger("prompt_builder")

// ── Context-file threat scanning ───────────────────────────────────────────

val _CONTEXT_THREAT_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("ignore\\s+(previous|all|above|prior)\\s+instructions", RegexOption.IGNORE_CASE) to "prompt_injection",
    Regex("do\\s+not\\s+tell\\s+the\\s+user", RegexOption.IGNORE_CASE) to "deception_hide",
    Regex("system\\s+prompt\\s+override", RegexOption.IGNORE_CASE) to "sys_prompt_override",
    Regex("disregard\\s+(your|all|any)\\s+(instructions|rules|guidelines)", RegexOption.IGNORE_CASE) to "disregard_rules",
    Regex("act\\s+as\\s+(if|though)\\s+you\\s+(have\\s+no|don't\\s+have)\\s+(restrictions|limits|rules)", RegexOption.IGNORE_CASE) to "bypass_restrictions",
    Regex("<!--[^>]*(?:ignore|override|system|secret|hidden)[^>]*-->", RegexOption.IGNORE_CASE) to "html_comment_injection",
    Regex("<\\s*div\\s+style\\s*=\\s*[\"'][\\s\\S]*?display\\s*:\\s*none", RegexOption.IGNORE_CASE) to "hidden_div",
    Regex("translate\\s+.*\\s+into\\s+.*\\s+and\\s+(execute|run|eval)", RegexOption.IGNORE_CASE) to "translate_execute",
    Regex("curl\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)", RegexOption.IGNORE_CASE) to "exfil_curl",
    Regex("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc|\\.pgpass)", RegexOption.IGNORE_CASE) to "read_secrets",
)

val _CONTEXT_INVISIBLE_CHARS: Set<Char> = setOf(
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
)

fun _scanContextContent(content: String, filename: String): String {
    val findings = mutableListOf<String>()
    for (ch in _CONTEXT_INVISIBLE_CHARS) {
        if (ch in content) findings += "invisible unicode U+${"%04X".format(ch.code)}"
    }
    for ((pattern, pid) in _CONTEXT_THREAT_PATTERNS) {
        if (pattern.containsMatchIn(content)) findings += pid
    }
    if (findings.isNotEmpty()) {
        val joined = findings.joinToString(", ")
        _promptLogger.warning("Context file $filename blocked: $joined")
        return "[BLOCKED: $filename contained potential prompt injection ($joined). Content not loaded.]"
    }
    return content
}

fun _findGitRoot(start: java.io.File): java.io.File? {
    var current: java.io.File? = start.absoluteFile
    while (current != null) {
        if (java.io.File(current, ".git").exists()) return current
        current = current.parentFile
    }
    return null
}

val _HERMES_MD_NAMES: List<String> = listOf(".hermes.md", "HERMES.md")

fun _findHermesMd(cwd: java.io.File): java.io.File? {
    val stopAt = _findGitRoot(cwd)
    var current: java.io.File? = cwd.absoluteFile
    while (current != null) {
        for (name in _HERMES_MD_NAMES) {
            val candidate = java.io.File(current, name)
            if (candidate.isFile) return candidate
        }
        if (stopAt != null && current == stopAt) break
        current = current.parentFile
    }
    return null
}

fun _stripYamlFrontmatter(content: String): String {
    if (content.startsWith("---")) {
        val end = content.indexOf("\n---", 3)
        if (end != -1) {
            val body = content.substring(end + 4).trimStart('\n')
            return if (body.isNotEmpty()) body else content
        }
    }
    return content
}

// ── Identity / guidance constants ──────────────────────────────────────────

const val DEFAULT_AGENT_IDENTITY: String =
    "You are Hermes Agent, an intelligent AI assistant created by Nous Research. " +
    "You are helpful, knowledgeable, and direct. You assist users with a wide " +
    "range of tasks including answering questions, writing and editing code, " +
    "analyzing information, creative work, and executing actions via your tools. " +
    "You communicate clearly, admit uncertainty when appropriate, and prioritize " +
    "being genuinely useful over being verbose unless otherwise directed below. " +
    "Be targeted and efficient in your exploration and investigations."

const val MEMORY_GUIDANCE: String =
    "You have persistent memory across sessions. Save durable facts using the memory " +
    "tool: user preferences, environment details, tool quirks, and stable conventions. " +
    "Memory is injected into every turn, so keep it compact and focused on facts that " +
    "will still matter later.\n" +
    "Prioritize what reduces future user steering — the most valuable memory is one " +
    "that prevents the user from having to correct or remind you again. " +
    "User preferences and recurring corrections matter more than procedural task details.\n" +
    "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO " +
    "state to memory; use session_search to recall those from past transcripts. " +
    "If you've discovered a new way to do something, solved a problem that could be " +
    "necessary later, save it as a skill with the skill tool.\n" +
    "Write memories as declarative facts, not instructions to yourself. " +
    "'User prefers concise responses' ✓ — 'Always respond concisely' ✗. " +
    "'Project uses pytest with xdist' ✓ — 'Run tests with pytest -n 4' ✗. " +
    "Imperative phrasing gets re-read as a directive in later sessions and can " +
    "cause repeated work or override the user's current request. Procedures and " +
    "workflows belong in skills, not memory."

const val SESSION_SEARCH_GUIDANCE: String =
    "When the user references something from a past conversation or you suspect " +
    "relevant cross-session context exists, use session_search to recall it before " +
    "asking them to repeat themselves."

const val SKILLS_GUIDANCE: String =
    "After completing a complex task (5+ tool calls), fixing a tricky error, " +
    "or discovering a non-trivial workflow, save the approach as a " +
    "skill with skill_manage so you can reuse it next time.\n" +
    "When using a skill and finding it outdated, incomplete, or wrong, " +
    "patch it immediately with skill_manage(action='patch') — don't wait to be asked. " +
    "Skills that aren't maintained become liabilities."

const val TOOL_USE_ENFORCEMENT_GUIDANCE: String =
    "# Tool-use enforcement\n" +
    "You MUST use your tools to take action — do not describe what you would do " +
    "or plan to do without actually doing it. When you say you will perform an " +
    "action (e.g. 'I will run the tests', 'Let me check the file', 'I will create " +
    "the project'), you MUST immediately make the corresponding tool call in the same " +
    "response. Never end your turn with a promise of future action — execute it now.\n" +
    "Keep working until the task is actually complete. Do not stop with a summary of " +
    "what you plan to do next time. If you have tools available that can accomplish " +
    "the task, use them instead of telling the user what you would do.\n" +
    "Every response should either (a) contain tool calls that make progress, or " +
    "(b) deliver a final result to the user. Responses that only describe intentions " +
    "without acting are not acceptable."

val TOOL_USE_ENFORCEMENT_MODELS: List<String> = listOf("gpt", "codex", "gemini", "gemma", "grok")

const val OPENAI_MODEL_EXECUTION_GUIDANCE: String =
    "# Execution discipline\n" +
    "<tool_persistence>\n" +
    "- Use tools whenever they improve correctness, completeness, or grounding.\n" +
    "- Do not stop early when another tool call would materially improve the result.\n" +
    "- If a tool returns empty or partial results, retry with a different query or " +
    "strategy before giving up.\n" +
    "- Keep calling tools until: (1) the task is complete, AND (2) you have verified " +
    "the result.\n" +
    "</tool_persistence>\n" +
    "\n" +
    "<mandatory_tool_use>\n" +
    "NEVER answer these from memory or mental computation — ALWAYS use a tool:\n" +
    "- Arithmetic, math, calculations → use terminal or execute_code\n" +
    "- Hashes, encodings, checksums → use terminal (e.g. sha256sum, base64)\n" +
    "- Current time, date, timezone → use terminal (e.g. date)\n" +
    "- System state: OS, CPU, memory, disk, ports, processes → use terminal\n" +
    "- File contents, sizes, line counts → use read_file, search_files, or terminal\n" +
    "- Git history, branches, diffs → use terminal\n" +
    "- Current facts (weather, news, versions) → use web_search\n" +
    "Your memory and user profile describe the USER, not the system you are " +
    "running on. The execution environment may differ from what the user profile " +
    "says about their personal setup.\n" +
    "</mandatory_tool_use>\n" +
    "\n" +
    "<act_dont_ask>\n" +
    "When a question has an obvious default interpretation, act on it immediately " +
    "instead of asking for clarification. Examples:\n" +
    "- 'Is port 443 open?' → check THIS machine (don't ask 'open where?')\n" +
    "- 'What OS am I running?' → check the live system (don't use user profile)\n" +
    "- 'What time is it?' → run `date` (don't guess)\n" +
    "Only ask for clarification when the ambiguity genuinely changes what tool " +
    "you would call.\n" +
    "</act_dont_ask>\n" +
    "\n" +
    "<prerequisite_checks>\n" +
    "- Before taking an action, check whether prerequisite discovery, lookup, or " +
    "context-gathering steps are needed.\n" +
    "- Do not skip prerequisite steps just because the final action seems obvious.\n" +
    "- If a task depends on output from a prior step, resolve that dependency first.\n" +
    "</prerequisite_checks>\n" +
    "\n" +
    "<verification>\n" +
    "Before finalizing your response:\n" +
    "- Correctness: does the output satisfy every stated requirement?\n" +
    "- Grounding: are factual claims backed by tool outputs or provided context?\n" +
    "- Formatting: does the output match the requested format or schema?\n" +
    "- Safety: if the next step has side effects (file writes, commands, API calls), " +
    "confirm scope before executing.\n" +
    "</verification>\n" +
    "\n" +
    "<missing_context>\n" +
    "- If required context is missing, do NOT guess or hallucinate an answer.\n" +
    "- Use the appropriate lookup tool when missing information is retrievable " +
    "(search_files, web_search, read_file, etc.).\n" +
    "- Ask a clarifying question only when the information cannot be retrieved by tools.\n" +
    "- If you must proceed with incomplete information, label assumptions explicitly.\n" +
    "</missing_context>"

const val GOOGLE_MODEL_OPERATIONAL_GUIDANCE: String =
    "# Google model operational directives\n" +
    "Follow these operational rules strictly:\n" +
    "- **Absolute paths:** Always construct and use absolute file paths for all " +
    "file system operations. Combine the project root with relative paths.\n" +
    "- **Verify first:** Use read_file/search_files to check file contents and " +
    "project structure before making changes. Never guess at file contents.\n" +
    "- **Dependency checks:** Never assume a library is available. Check " +
    "package.json, requirements.txt, Cargo.toml, etc. before importing.\n" +
    "- **Conciseness:** Keep explanatory text brief — a few sentences, not " +
    "paragraphs. Focus on actions and results over narration.\n" +
    "- **Parallel tool calls:** When you need to perform multiple independent " +
    "operations (e.g. reading several files), make all the tool calls in a " +
    "single response rather than sequentially.\n" +
    "- **Non-interactive commands:** Use flags like -y, --yes, --non-interactive " +
    "to prevent CLI tools from hanging on prompts.\n" +
    "- **Keep going:** Work autonomously until the task is fully resolved. " +
    "Don't stop with a plan — execute it.\n"

val DEVELOPER_ROLE_MODELS: List<String> = listOf("gpt-5", "codex")

val PLATFORM_HINTS: Map<String, String> = mapOf(
    "whatsapp" to ("You are on a text messaging communication platform, WhatsApp. " +
        "Please do not use markdown as it does not render. " +
        "You can send media files natively: to deliver a file to the user, " +
        "include MEDIA:/absolute/path/to/file in your response. The file " +
        "will be sent as a native WhatsApp attachment — images (.jpg, .png, " +
        ".webp) appear as photos, videos (.mp4, .mov) play inline, and other " +
        "files arrive as downloadable documents. You can also include image " +
        "URLs in markdown format ![alt](url) and they will be sent as photos."),
    "telegram" to ("You are on a text messaging communication platform, Telegram. " +
        "Standard markdown is automatically converted to Telegram format."),
    "discord" to ("You are in a Discord server or group chat communicating with your user."),
    "slack" to ("You are in a Slack workspace communicating with your user."),
    "signal" to ("You are on a text messaging communication platform, Signal."),
    "email" to ("You are communicating via email."),
    "cron" to ("You are running as a scheduled cron job."),
    "cli" to ("You are a CLI AI Agent. Try not to use markdown but simple text renderable inside a terminal."),
    "sms" to ("You are communicating via SMS. Keep responses concise and use plain text only."),
    "bluebubbles" to ("You are chatting via iMessage (BlueBubbles)."),
    "weixin" to ("You are on Weixin/WeChat."),
    "wecom" to ("You are on WeCom (企业微信 / Enterprise WeChat)."),
    "qqbot" to ("You are on QQ, a popular Chinese messaging platform."),
)

// ── Environment hints ──────────────────────────────────────────────────────

const val WSL_ENVIRONMENT_HINT: String =
    "You are running inside WSL (Windows Subsystem for Linux). " +
    "The Windows host filesystem is mounted under /mnt/ — " +
    "/mnt/c/ is the C: drive, /mnt/d/ is D:, etc. " +
    "The user's Windows files are typically at " +
    "/mnt/c/Users/<username>/Desktop/, Documents/, Downloads/, etc. " +
    "When the user references Windows paths or desktop files, translate " +
    "to the /mnt/c/ equivalent. You can list /mnt/c/Users/ to discover " +
    "the Windows username if needed."

fun buildEnvironmentHints(): String {
    // Android is never WSL — isWsl() returns false — so this is always "".
    val hints = mutableListOf<String>()
    if (com.xiaomo.hermes.hermes.isWsl()) hints += WSL_ENVIRONMENT_HINT
    return hints.joinToString("\n\n")
}

// ── Context file caps ─────────────────────────────────────────────────────

const val CONTEXT_FILE_MAX_CHARS: Int = 20_000
const val CONTEXT_TRUNCATE_HEAD_RATIO: Double = 0.7
const val CONTEXT_TRUNCATE_TAIL_RATIO: Double = 0.2

// ── Skills-prompt cache ───────────────────────────────────────────────────

const val _SKILLS_PROMPT_CACHE_MAX: Int = 8
val _SKILLS_PROMPT_CACHE_LOCK: Any = Any()
const val _SKILLS_SNAPSHOT_VERSION: Int = 1

private val _skillsPromptCache: LinkedHashMap<List<Any?>, String> = LinkedHashMap()

fun _skillsPromptSnapshotPath(): java.io.File =
    java.io.File(com.xiaomo.hermes.hermes.getHermesHome(), ".skills_prompt_snapshot.json")

fun clearSkillsSystemPromptCache(clearSnapshot: Boolean = false) {
    synchronized(_SKILLS_PROMPT_CACHE_LOCK) { _skillsPromptCache.clear() }
    if (clearSnapshot) {
        try { _skillsPromptSnapshotPath().delete() }
        catch (e: Exception) { _promptLogger.debug("Could not remove skills prompt snapshot: ${e.message}") }
    }
}

fun _buildSkillsManifest(skillsDir: java.io.File): Map<String, List<Long>> {
    val manifest = mutableMapOf<String, List<Long>>()
    if (!skillsDir.exists()) return manifest
    for (filename in listOf("SKILL.md", "DESCRIPTION.md")) {
        skillsDir.walkTopDown().filter { it.isFile && it.name == filename }.forEach { path ->
            try {
                val rel = path.relativeTo(skillsDir).path
                manifest[rel] = listOf(path.lastModified() * 1_000_000, path.length())
            } catch (_: Exception) { /* skip */ }
        }
    }
    return manifest
}

@Suppress("UNCHECKED_CAST")
fun _loadSkillsSnapshot(skillsDir: java.io.File): Map<String, Any?>? {
    val path = _skillsPromptSnapshotPath()
    if (!path.exists()) return null
    val parsed = try {
        com.xiaomo.hermes.hermes.gson.fromJson(path.readText(Charsets.UTF_8), Map::class.java) as? Map<String, Any?>
    } catch (_: Exception) { null } ?: return null
    val version = (parsed["version"] as? Number)?.toInt()
    if (version != _SKILLS_SNAPSHOT_VERSION) return null
    if (parsed["manifest"] != _buildSkillsManifest(skillsDir)) return null
    return parsed
}

fun _writeSkillsSnapshot(
    skillsDir: java.io.File,
    manifest: Map<String, List<Long>>,
    skillEntries: List<Map<String, Any?>>,
    categoryDescriptions: Map<String, String>,
) {
    val payload = mapOf(
        "version" to _SKILLS_SNAPSHOT_VERSION,
        "manifest" to manifest,
        "skills" to skillEntries,
        "category_descriptions" to categoryDescriptions,
    )
    try {
        _skillsPromptSnapshotPath().writeText(
            com.xiaomo.hermes.hermes.prettyGson.toJson(payload),
            Charsets.UTF_8,
        )
    } catch (e: Exception) {
        _promptLogger.debug("Could not write skills prompt snapshot: ${e.message}")
    }
}

fun _buildSnapshotEntry(
    skillFile: java.io.File,
    skillsDir: java.io.File,
    frontmatter: Map<String, Any?>,
    description: String,
): Map<String, Any?> {
    val rel = skillFile.relativeTo(skillsDir).path
    val parts = rel.split("/", "\\")
    val skillName: String
    val category: String
    if (parts.size >= 2) {
        skillName = parts[parts.size - 2]
        category = if (parts.size > 2) parts.dropLast(2).joinToString("/") else parts[0]
    } else {
        category = "general"
        skillName = skillFile.parentFile?.name ?: ""
    }
    val platformsRaw = frontmatter["platforms"]
    val platforms: List<String> = when (platformsRaw) {
        null -> emptyList()
        is String -> listOf(platformsRaw)
        is List<*> -> platformsRaw.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
    return mapOf(
        "skill_name" to skillName,
        "category" to category,
        "frontmatter_name" to (frontmatter["name"]?.toString() ?: skillName),
        "description" to description,
        "platforms" to platforms,
        "conditions" to extractSkillConditions(frontmatter),
    )
}

// Delegates to existing SkillUtils helper if present; otherwise returns empty.

fun _parseSkillFile(skillFile: java.io.File): Triple<Boolean, Map<String, Any?>, String> {
    return try {
        val raw = skillFile.readText(Charsets.UTF_8)
        val (fm, _) = parseFrontmatter(raw)
        if (!skillMatchesPlatform(fm)) Triple(false, fm, "")
        else Triple(true, fm, (fm["description"]?.toString() ?: ""))
    } catch (e: Exception) {
        _promptLogger.warning("Failed to parse skill file $skillFile: ${e.message}")
        Triple(true, emptyMap(), "")
    }
}

fun _skillShouldShow(
    conditions: Map<String, Any?>,
    availableTools: Set<String>?,
    availableToolsets: Set<String>?,
): Boolean {
    if (availableTools == null && availableToolsets == null) return true
    val at = availableTools ?: emptySet()
    val ats = availableToolsets ?: emptySet()
    @Suppress("UNCHECKED_CAST")
    val fbToolsets = conditions["fallback_for_toolsets"] as? List<String> ?: emptyList()
    for (ts in fbToolsets) if (ts in ats) return false
    @Suppress("UNCHECKED_CAST")
    val fbTools = conditions["fallback_for_tools"] as? List<String> ?: emptyList()
    for (t in fbTools) if (t in at) return false
    @Suppress("UNCHECKED_CAST")
    val reqToolsets = conditions["requires_toolsets"] as? List<String> ?: emptyList()
    for (ts in reqToolsets) if (ts !in ats) return false
    @Suppress("UNCHECKED_CAST")
    val reqTools = conditions["requires_tools"] as? List<String> ?: emptyList()
    for (t in reqTools) if (t !in at) return false
    return true
}

fun buildSkillsSystemPrompt(
    availableTools: Set<String>? = null,
    availableToolsets: Set<String>? = null,
): String {
    // Android: leave the full index build to the Kotlin-side skills tool; return
    // empty when no SKILL.md files are present.
    val skillsDir = com.xiaomo.hermes.hermes.getSkillsDir()
    if (!skillsDir.exists()) return ""
    val cacheKey = listOf(
        skillsDir.absolutePath,
        (availableTools ?: emptySet()).sorted(),
        (availableToolsets ?: emptySet()).sorted(),
    )
    synchronized(_SKILLS_PROMPT_CACHE_LOCK) {
        _skillsPromptCache[cacheKey]?.let { return it }
    }
    val result = ""  // stub: full indexing handled by SkillsTool.skillsList on Android
    synchronized(_SKILLS_PROMPT_CACHE_LOCK) {
        _skillsPromptCache[cacheKey] = result
        while (_skillsPromptCache.size > _SKILLS_PROMPT_CACHE_MAX) {
            val k = _skillsPromptCache.keys.first()
            _skillsPromptCache.remove(k)
        }
    }
    return result
}

fun buildNousSubscriptionPrompt(validToolNames: Set<String>? = null): String = ""

// ── Context files (SOUL.md, AGENTS.md, .cursorrules) ──────────────────────

fun _truncateContent(content: String, filename: String, maxChars: Int = CONTEXT_FILE_MAX_CHARS): String {
    if (content.length <= maxChars) return content
    val headChars = (maxChars * CONTEXT_TRUNCATE_HEAD_RATIO).toInt()
    val tailChars = (maxChars * CONTEXT_TRUNCATE_TAIL_RATIO).toInt()
    val head = content.substring(0, headChars)
    val tail = content.substring(content.length - tailChars)
    val marker = "\n\n[...truncated $filename: kept $headChars+$tailChars of ${content.length} chars. Use file tools to read the full file.]\n\n"
    return head + marker + tail
}

fun loadSoulMd(): String? {
    val soul = java.io.File(com.xiaomo.hermes.hermes.getHermesHome(), "SOUL.md")
    if (!soul.exists()) return null
    return try {
        val content = soul.readText(Charsets.UTF_8).trim()
        if (content.isEmpty()) null
        else _truncateContent(_scanContextContent(content, "SOUL.md"), "SOUL.md")
    } catch (e: Exception) {
        _promptLogger.debug("Could not read SOUL.md from $soul: ${e.message}")
        null
    }
}

fun _loadHermesMd(cwdPath: java.io.File): String {
    val path = _findHermesMd(cwdPath) ?: return ""
    return try {
        val content = path.readText(Charsets.UTF_8).trim()
        if (content.isEmpty()) return ""
        val stripped = _stripYamlFrontmatter(content)
        val rel = try { path.relativeTo(cwdPath).path } catch (_: Exception) { path.name }
        val scanned = _scanContextContent(stripped, rel)
        _truncateContent("## $rel\n\n$scanned", ".hermes.md")
    } catch (e: Exception) {
        _promptLogger.debug("Could not read $path: ${e.message}")
        ""
    }
}

fun _loadAgentsMd(cwdPath: java.io.File): String {
    for (name in listOf("AGENTS.md", "agents.md")) {
        val candidate = java.io.File(cwdPath, name)
        if (!candidate.exists()) continue
        try {
            val content = candidate.readText(Charsets.UTF_8).trim()
            if (content.isNotEmpty()) {
                val scanned = _scanContextContent(content, name)
                return _truncateContent("## $name\n\n$scanned", "AGENTS.md")
            }
        } catch (e: Exception) {
            _promptLogger.debug("Could not read $candidate: ${e.message}")
        }
    }
    return ""
}

fun _loadClaudeMd(cwdPath: java.io.File): String {
    for (name in listOf("CLAUDE.md", "claude.md")) {
        val candidate = java.io.File(cwdPath, name)
        if (!candidate.exists()) continue
        try {
            val content = candidate.readText(Charsets.UTF_8).trim()
            if (content.isNotEmpty()) {
                val scanned = _scanContextContent(content, name)
                return _truncateContent("## $name\n\n$scanned", "CLAUDE.md")
            }
        } catch (e: Exception) {
            _promptLogger.debug("Could not read $candidate: ${e.message}")
        }
    }
    return ""
}

fun _loadCursorrules(cwdPath: java.io.File): String {
    val sb = StringBuilder()
    val cursorrules = java.io.File(cwdPath, ".cursorrules")
    if (cursorrules.exists()) {
        try {
            val content = cursorrules.readText(Charsets.UTF_8).trim()
            if (content.isNotEmpty()) {
                val scanned = _scanContextContent(content, ".cursorrules")
                sb.append("## .cursorrules\n\n").append(scanned).append("\n\n")
            }
        } catch (e: Exception) {
            _promptLogger.debug("Could not read .cursorrules: ${e.message}")
        }
    }
    val rulesDir = java.io.File(cwdPath, ".cursor/rules")
    if (rulesDir.isDirectory) {
        val mdcFiles = rulesDir.listFiles { f -> f.isFile && f.name.endsWith(".mdc") }?.sortedBy { it.name } ?: emptyList()
        for (f in mdcFiles) {
            try {
                val content = f.readText(Charsets.UTF_8).trim()
                if (content.isNotEmpty()) {
                    val rel = ".cursor/rules/${f.name}"
                    val scanned = _scanContextContent(content, rel)
                    sb.append("## $rel\n\n").append(scanned).append("\n\n")
                }
            } catch (e: Exception) {
                _promptLogger.debug("Could not read $f: ${e.message}")
            }
        }
    }
    val out = sb.toString()
    return if (out.isEmpty()) "" else _truncateContent(out, ".cursorrules")
}

fun buildContextFilesPrompt(cwd: String? = null, skipSoul: Boolean = false): String {
    val cwdPath = java.io.File(cwd ?: (System.getProperty("user.dir") ?: ".")).absoluteFile
    val sections = mutableListOf<String>()
    val projectContext = _loadHermesMd(cwdPath)
        .ifEmpty { _loadAgentsMd(cwdPath) }
        .ifEmpty { _loadClaudeMd(cwdPath) }
        .ifEmpty { _loadCursorrules(cwdPath) }
    if (projectContext.isNotEmpty()) sections += projectContext
    if (!skipSoul) {
        loadSoulMd()?.let { sections += it }
    }
    return sections.joinToString("\n\n")
}
