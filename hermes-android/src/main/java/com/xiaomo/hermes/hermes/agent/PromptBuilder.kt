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
