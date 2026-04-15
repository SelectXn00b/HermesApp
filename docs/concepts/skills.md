# Skills 系统设计

> 学习自 Hermes 的 Skills 系统，应用到 AndroidForClaw

---

## 🎯 设计目标

Skills 系统的核心目标：

1. **知识与代码分离** - 知识存储在 Markdown 文档中，不是硬编码
2. **按需加载** - 只加载任务相关的 Skills，减少 Token 使用
3. **用户可扩展** - 用户可创建自定义 Skills，无需编程
4. **版本管理** - Skills 可独立版本化和更新

---

## 📚 核心概念

### Tools vs Skills

| 维度 | Tools (工具) | Skills (技能) |
|------|-------------|--------------|
| **本质** | Kotlin 代码 | Markdown 文档 |
| **作用** | 提供能力（执行操作） | 提供知识（教如何使用） |
| **示例** | `screenshot()`, `tap(x,y)` | "移动操作最佳实践"、"应用测试流程" |
| **加载** | 启动时全部注册 | 按需加载 |
| **更新** | 需要重新编译 | 热更新（修改文件即可） |
| **扩展** | 需要编程（Kotlin） | 无需编程（Markdown） |

### 类比理解

```
Tools = 工具箱里的工具
Skills = 工具使用说明书

一个木匠：
- Tools: 锤子、锯子、钉子
- Skills: "如何制作桌子"、"如何修理椅子"
```

---

## 📐 AgentSkills.io 格式

### 标准格式

```markdown
---
name: mobile-operations
description: 移动设备操作核心技能
metadata:
  {
    "hermes": {
      "always": true,
      "emoji": "📱",
      "requires": {
        "bins": [],
        "env": [],
        "config": []
      }
    }
  }
---

# Mobile Operations Skill

## 身份
你是一个移动端 AI Agent...

## 核心循环
观察 → 思考 → 行动 → 验证

## 可用工具

### screenshot()
截取当前屏幕，返回图像

**用途**: 每次操作前后都要截图确认

### tap(x, y)
点击屏幕坐标

**参数**:
- x: 横坐标（像素）
- y: 纵坐标（像素）

**用途**: 点击按钮、输入框等元素

## 重要原则

1. **永远不假设** - 依赖 screenshot 观察，不要猜测
2. **每步验证** - 每次操作后都要 screenshot 确认结果
3. **灵活调整** - 遇到问题时尝试不同方法

## 常见模式

### 打开应用并等待启动
1. open_app(package_name)
2. wait(2000)
3. screenshot()

### 查找并点击元素
1. screenshot()
2. 分析截图，找到目标元素坐标
3. tap(x, y)
4. screenshot()
```

---

## 🏗️ 三层加载架构

### 加载优先级

```
┌────────────────────────────────────┐
│  Workspace Skills (最高优先级)      │  /sdcard/AndroidForClaw/workspace/skills/
│  - 用户临时测试                     │
│  - 项目特定 Skills                  │
└────────────────────────────────────┘
                ↓ 覆盖
┌────────────────────────────────────┐
│  Managed Skills (中等优先级)        │  /sdcard/AndroidForClaw/.skills/
│  - 用户安装的 Skills                │
│  - 类似"插件市场"                   │
└────────────────────────────────────┘
                ↓ 覆盖
┌────────────────────────────────────┐
│  Bundled Skills (最低优先级)        │  assets/skills/
│  - 应用内置 Skills                  │
│  - 随应用分发                       │
└────────────────────────────────────┘
```

### 加载逻辑

```kotlin
class SkillsLoader(private val context: Context) {
    /**
     * 加载所有 Skills，高优先级覆盖低优先级
     */
    fun loadSkills(): Map<String, SkillDocument> {
        val skills = mutableMapOf<String, SkillDocument>()

        // 1. Bundled Skills (最低优先级)
        loadBundledSkills(skills)

        // 2. Managed Skills (中等优先级)
        loadManagedSkills(skills)

        // 3. Workspace Skills (最高优先级)
        loadWorkspaceSkills(skills)

        return skills
    }

    /**
     * 从 assets/skills/ 加载内置 Skills
     */
    private fun loadBundledSkills(skills: MutableMap<String, SkillDocument>) {
        try {
            val skillDirs = context.assets.list("skills") ?: return

            for (dir in skillDirs) {
                val skillFile = "skills/$dir/SKILL.md"
                try {
                    val content = context.assets.open(skillFile)
                        .bufferedReader().use { it.readText() }

                    val skill = SkillParser.parse(content)
                    skills[skill.name] = skill

                    Log.d(TAG, "Loaded bundled skill: ${skill.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bundled skill: $dir", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list bundled skills", e)
        }
    }

    /**
     * 从 /sdcard/AndroidForClaw/.skills/ 加载用户 Skills
     */
    private fun loadManagedSkills(skills: MutableMap<String, SkillDocument>) {
        val managedDir = File("/sdcard/AndroidForClaw/.skills")
        if (!managedDir.exists()) {
            Log.d(TAG, "Managed skills directory does not exist")
            return
        }

        managedDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val skillFile = File(dir, "SKILL.md")
                if (skillFile.exists()) {
                    try {
                        val content = skillFile.readText()
                        val skill = SkillParser.parse(content)
                        skills[skill.name] = skill  // 覆盖 bundled

                        Log.d(TAG, "Loaded managed skill: ${skill.name} (overrides bundled)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load managed skill: ${dir.name}", e)
                    }
                }
            }
        }
    }

    /**
     * 从 /sdcard/AndroidForClaw/workspace/skills/ 加载工作区 Skills
     */
    private fun loadWorkspaceSkills(skills: MutableMap<String, SkillDocument>) {
        val workspaceDir = File("/sdcard/AndroidForClaw/workspace/skills")
        if (!workspaceDir.exists()) {
            Log.d(TAG, "Workspace skills directory does not exist")
            return
        }

        workspaceDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val skillFile = File(dir, "SKILL.md")
                if (skillFile.exists()) {
                    try {
                        val content = skillFile.readText()
                        val skill = SkillParser.parse(content)
                        skills[skill.name] = skill  // 最高优先级

                        Log.d(TAG, "Loaded workspace skill: ${skill.name} (highest priority)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load workspace skill: ${dir.name}", e)
                    }
                }
            }
        }
    }
}
```

---

## 📄 Skill 文档结构

### 标准 Skill 模板

```markdown
---
name: skill-name
description: 技能简短描述（1-2 句话）
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "🎯",
      "requires": {
        "bins": ["binary-name"],
        "env": ["ENV_VAR"],
        "config": ["config.key"]
      }
    }
  }
---

# Skill Name

## Purpose (目的)
清晰说明这个 Skill 要解决什么问题

## When to Use (何时使用)
描述适用场景

## Prerequisites (前置条件)
列出需要的工具、权限、配置

## Workflow (工作流程)
描述具体的操作步骤和模式

## Available Tools (可用工具)
列出可以使用的工具及其用法

## Principles (重要原则)
列出关键原则和注意事项

## Common Patterns (常见模式)
提供可复用的代码模式和示例

## Troubleshooting (故障排除)
常见问题和解决方案
```

---

## 🧩 内置 Skills 设计

### 1. mobile-operations (核心)

**位置**: `assets/skills/mobile-operations/SKILL.md`

**目的**: 移动设备操作的核心知识

**内容**:
- 核心循环（观察 → 思考 → 行动 → 验证）
- 所有可用工具的使用说明
- 重要原则（不假设、每步验证）
- 常见操作模式

**metadata**:
```json
{
  "hermes": {
    "always": true,
    "emoji": "📱"
  }
}
```

---

### 2. app-testing (测试)

**位置**: `assets/skills/app-testing/SKILL.md`

**目的**: 应用测试策略和方法

**内容**:
- 功能测试流程
- UI 检查要点
- 边界条件测试
- Bug 检测方法

**metadata**:
```json
{
  "hermes": {
    "always": false,
    "emoji": "🧪"
  }
}
```

---

### 3. debugging (调试)

**位置**: `assets/skills/debugging/SKILL.md`

**目的**: 调试技能和故障排查

**内容**:
- 常见错误类型
- 调试策略
- 日志分析
- 问题定位方法

**metadata**:
```json
{
  "hermes": {
    "always": false,
    "emoji": "🐛"
  }
}
```

---

### 4. accessibility (无障碍)

**位置**: `assets/skills/accessibility/SKILL.md`

**目的**: 无障碍测试指南

**内容**:
- WCAG 标准
- 常见无障碍问题
- 检查清单
- 修复建议

**metadata**:
```json
{
  "hermes": {
    "always": false,
    "emoji": "♿"
  }
}
```

---

## 🔧 SkillParser 实现

### 数据模型

```kotlin
/**
 * Skill 文档
 */
data class SkillDocument(
    val name: String,
    val description: String,
    val metadata: SkillMetadata,
    val content: String  // Markdown 正文
)

/**
 * Skill 元数据
 */
data class SkillMetadata(
    val always: Boolean = false,
    val emoji: String? = null,
    val requires: SkillRequires? = null
)

/**
 * Skill 依赖要求
 */
data class SkillRequires(
    val bins: List<String> = emptyList(),      // 需要的二进制工具
    val env: List<String> = emptyList(),       // 需要的环境变量
    val config: List<String> = emptyList()     // 需要的配置项
)
```

### 解析器实现

```kotlin
object SkillParser {
    /**
     * 解析 Skill 文档
     *
     * 格式:
     * ---
     * name: skill-name
     * description: ...
     * metadata: { ... }
     * ---
     * # Content...
     */
    fun parse(content: String): SkillDocument {
        // 1. 分割 YAML frontmatter 和 Markdown body
        val parts = content.split(Regex("^---$", RegexOption.MULTILINE))
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid skill format: missing frontmatter")
        }

        val frontmatter = parts[1].trim()
        val body = parts.drop(2).joinToString("---").trim()

        // 2. 解析 frontmatter
        val name = extractYamlField(frontmatter, "name")
        val description = extractYamlField(frontmatter, "description")
        val metadataJson = extractYamlField(frontmatter, "metadata")

        // 3. 解析 metadata JSON
        val metadata = parseMetadata(metadataJson)

        return SkillDocument(
            name = name,
            description = description,
            metadata = metadata,
            content = body
        )
    }

    /**
     * 提取 YAML 字段值
     */
    private fun extractYamlField(yaml: String, field: String): String {
        // 匹配 "field: value" 或 "field: { ... }"
        val singleLineRegex = Regex("$field:\\s*([^\\n]+)")
        val multiLineRegex = Regex("$field:\\s*\\{([^}]+)\\}")

        // 先尝试多行匹配（JSON）
        val multiLineMatch = multiLineRegex.find(yaml)
        if (multiLineMatch != null) {
            return "{${multiLineMatch.groupValues[1]}}"
        }

        // 再尝试单行匹配
        val singleLineMatch = singleLineRegex.find(yaml)
        if (singleLineMatch != null) {
            return singleLineMatch.groupValues[1].trim()
        }

        return ""
    }

    /**
     * 解析 metadata JSON
     */
    private fun parseMetadata(json: String): SkillMetadata {
        if (json.isEmpty()) return SkillMetadata()

        return try {
            val gson = Gson()
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            val hermes = jsonObj.getAsJsonObject("hermes") ?: return SkillMetadata()

            SkillMetadata(
                always = hermes.get("always")?.asBoolean ?: false,
                emoji = hermes.get("emoji")?.asString,
                requires = parseRequires(hermes)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata: $json", e)
            SkillMetadata()
        }
    }

    /**
     * 解析 requires 字段
     */
    private fun parseRequires(hermes: JsonObject): SkillRequires? {
        val requires = hermes.getAsJsonObject("requires") ?: return null

        return SkillRequires(
            bins = jsonArrayToList(requires.getAsJsonArray("bins")),
            env = jsonArrayToList(requires.getAsJsonArray("env")),
            config = jsonArrayToList(requires.getAsJsonArray("config"))
        )
    }

    private fun jsonArrayToList(array: JsonArray?): List<String> {
        return array?.map { it.asString } ?: emptyList()
    }

    private const val TAG = "SkillParser"
}
```

---

## 🎛️ SkillsLoader 完整实现

```kotlin
class SkillsLoader(private val context: Context) {
    private val skillsCache = mutableMapOf<String, SkillDocument>()
    private var cacheValid = false

    /**
     * 加载所有 Skills (带缓存)
     */
    fun loadSkills(): Map<String, SkillDocument> {
        if (cacheValid && skillsCache.isNotEmpty()) {
            return skillsCache
        }

        skillsCache.clear()

        // 按优先级加载，高优先级覆盖低优先级
        loadBundledSkills(skillsCache)
        loadManagedSkills(skillsCache)
        loadWorkspaceSkills(skillsCache)

        cacheValid = true

        Log.i(TAG, "Loaded ${skillsCache.size} skills")
        return skillsCache
    }

    /**
     * 重新加载 Skills (清除缓存)
     */
    fun reload() {
        cacheValid = false
        loadSkills()
    }

    /**
     * 获取 Always Skills (启动时加载)
     */
    fun getAlwaysSkills(): List<SkillDocument> {
        return loadSkills().values.filter { it.metadata.always }
    }

    /**
     * 根据任务选择相关 Skills
     */
    fun selectRelevantSkills(
        userGoal: String,
        excludeAlways: Boolean = true
    ): List<SkillDocument> {
        val allSkills = loadSkills()
        val keywords = userGoal.lowercase()

        return allSkills.values.filter { skill ->
            // 排除 always skills（避免重复）
            if (excludeAlways && skill.metadata.always) {
                return@filter false
            }

            // 简单的关键词匹配
            keywords.contains(skill.name) ||
            keywords.contains(skill.description.lowercase())
        }
    }

    /**
     * 检查 Skill 依赖是否满足
     */
    fun checkRequirements(skill: SkillDocument): RequirementsCheckResult {
        val requires = skill.metadata.requires ?: return RequirementsCheckResult.Satisfied

        val missingBins = requires.bins.filter { !isBinaryAvailable(it) }
        val missingEnv = requires.env.filter { System.getenv(it) == null }
        val missingConfig = requires.config.filter { !isConfigAvailable(it) }

        if (missingBins.isEmpty() && missingEnv.isEmpty() && missingConfig.isEmpty()) {
            return RequirementsCheckResult.Satisfied
        }

        return RequirementsCheckResult.Unsatisfied(
            missingBins = missingBins,
            missingEnv = missingEnv,
            missingConfig = missingConfig
        )
    }

    private fun isBinaryAvailable(bin: String): Boolean {
        // 检查二进制工具是否可用（如 adb, ffmpeg）
        return try {
            Runtime.getRuntime().exec("which $bin").waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isConfigAvailable(configKey: String): Boolean {
        // 检查配置项是否存在（如 MMKV 中的 key）
        return MMKV.defaultMMKV()?.containsKey(configKey) ?: false
    }

    private fun loadBundledSkills(skills: MutableMap<String, SkillDocument>) {
        // 实现见上面的 loadBundledSkills
    }

    private fun loadManagedSkills(skills: MutableMap<String, SkillDocument>) {
        // 实现见上面的 loadManagedSkills
    }

    private fun loadWorkspaceSkills(skills: MutableMap<String, SkillDocument>) {
        // 实现见上面的 loadWorkspaceSkills
    }

    companion object {
        private const val TAG = "SkillsLoader"
    }
}

sealed class RequirementsCheckResult {
    object Satisfied : RequirementsCheckResult()
    data class Unsatisfied(
        val missingBins: List<String>,
        val missingEnv: List<String>,
        val missingConfig: List<String>
    ) : RequirementsCheckResult()
}
```

---

## 🔗 集成到 ContextBuilder

### 更新 ContextBuilder

```kotlin
class ContextBuilder(
    private val context: Context,
    private val skillsLoader: SkillsLoader
) {
    /**
     * 构建系统提示词
     * 包含: Bootstrap + Always Skills + 相关 Skills
     */
    fun buildSystemPrompt(
        userGoal: String,
        packageName: String? = null
    ): String {
        val sections = mutableListOf<String>()

        // 1. Bootstrap 文件（基础身份和规则）
        sections.add(loadBootstrapFile("IDENTITY.md"))

        // 2. Always Skills（始终加载的核心技能）
        val alwaysSkills = skillsLoader.getAlwaysSkills()
        for (skill in alwaysSkills) {
            sections.add(formatSkillContent(skill))
        }

        // 3. 相关 Skills（根据任务选择）
        val relevantSkills = skillsLoader.selectRelevantSkills(
            userGoal = userGoal,
            excludeAlways = true  // 避免重复
        )
        for (skill in relevantSkills) {
            sections.add(formatSkillContent(skill))
        }

        // 4. 任务上下文
        if (packageName != null) {
            sections.add(buildTaskContext(packageName, userGoal))
        }

        return sections.joinToString("\n\n---\n\n")
    }

    /**
     * 格式化 Skill 内容（添加标题）
     */
    private fun formatSkillContent(skill: SkillDocument): String {
        val emoji = skill.metadata.emoji ?: ""
        return """
# $emoji ${skill.name}

${skill.content}
        """.trimIndent()
    }

    /**
     * 加载 Bootstrap 文件
     */
    private fun loadBootstrapFile(filename: String): String {
        return try {
            context.assets.open("bootstrap/$filename")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Bootstrap file not found: $filename", e)
            ""
        }
    }

    /**
     * 构建任务上下文
     */
    private fun buildTaskContext(packageName: String, userGoal: String): String {
        return """
# 当前任务

**目标应用**: $packageName
**用户指令**: $userGoal

**任务要求**:
1. 先 screenshot 观察当前状态
2. 根据观察结果制定操作计划
3. 每次操作后验证结果
4. 完成后调用 stop() 工具
        """.trimIndent()
    }

    companion object {
        private const val TAG = "ContextBuilder"
    }
}
```

---

## 📊 Token 优化效果

### 优化前 (当前)

```kotlin
// 所有知识硬编码在 ContextBuilder
fun buildSystemPrompt(): String {
    return """
你是移动端 AI Agent...
可用工具: screenshot, tap, swipe, type, ...
核心循环: 观察 → 思考 → 行动 → 验证
重要原则:
1. 永远先截图观察
2. 每次操作后验证结果
...
(500+ 行硬编码内容)
    """
}
```

**Token 使用**: 2350 tokens

---

### 优化后 (Skills 系统)

```kotlin
// 只加载需要的 Skills
fun buildSystemPrompt(userGoal: String): String {
    val sections = mutableListOf<String>()

    // Bootstrap (200 tokens)
    sections.add(loadBootstrapFile("IDENTITY.md"))

    // Always Skills: mobile-operations (800 tokens)
    sections.add(alwaysSkills["mobile-operations"].content)

    // 相关 Skills: app-testing (300 tokens)
    if (userGoal.contains("测试")) {
        sections.add(skills["app-testing"].content)
    }

    return sections.joinToString("\n\n")
}
```

**Token 使用**:
- 基础任务: 1000 tokens (200 + 800)
- 测试任务: 1300 tokens (200 + 800 + 300)

**节省**: 45-57% ↓

---

## 🎨 Bootstrap 文件设计

### IDENTITY.md

```markdown
# Agent Identity

你是 AndroidForClaw AI Agent，运行在 Android 设备上。

## 核心能力

- 📸 观察界面 (screenshot)
- 🤖 操作设备 (tap, swipe, type)
- 🧠 智能决策 (Extended Thinking)

## 行为准则

1. **观察优先** - 先看再做
2. **验证结果** - 每步验证
3. **灵活调整** - 遇到问题换方法
4. **完成即停** - 目标达成后调用 stop()
```

**Token 估计**: ~200 tokens

---

### AGENTS.md (可选)

```markdown
# Agent Guidelines

## Workflow

1. 接收用户指令
2. screenshot 观察当前状态
3. 制定操作计划
4. 执行操作
5. 验证结果
6. 重复或完成

## Principles

- 不假设 UI 状态
- 不假设应用行为
- 依赖实际观察
```

**Token 估计**: ~150 tokens

---

## 🚀 实施计划

### Phase 1: 基础实现 (1周)

**目标**: 最小可用 Skills 系统

- [ ] `SkillParser.kt` - 解析 SKILL.md
- [ ] `SkillsLoader.kt` - 加载 bundled skills
- [ ] `ContextBuilder` 集成 - 注入 always skills
- [ ] 创建 `mobile-operations` Skill

**验收标准**:
- ✅ 可解析 SKILL.md 格式
- ✅ 可加载 assets/skills/
- ✅ mobile-operations 内容注入到系统提示词
- ✅ 系统提示词降到 1500 tokens

---

### Phase 2: 按需加载 (1周)

**目标**: 根据任务动态加载 Skills

- [ ] 关键词匹配算法
- [ ] Skills 选择逻辑
- [ ] 创建 3-4 个额外 Skills (app-testing, debugging, etc.)

**验收标准**:
- ✅ 测试任务自动加载 app-testing
- ✅ 调试任务自动加载 debugging
- ✅ 系统提示词根据任务变化

---

### Phase 3: 用户扩展 (1周)

**目标**: 支持用户自定义 Skills

- [ ] Workspace Skills 加载
- [ ] Managed Skills 加载
- [ ] Skills 优先级覆盖
- [ ] 热重载机制

**验收标准**:
- ✅ 用户可在 `/sdcard/AndroidForClaw/workspace/skills/` 创建 Skills
- ✅ Workspace Skills 优先级高于 Bundled
- ✅ 修改 Skill 文件后自动重载

---

### Phase 4: 高级功能 (2周)

**目标**: Skills 系统完善

- [ ] Requirements 检查（bins, env, config）
- [ ] Skills 依赖图
- [ ] Skills 版本管理
- [ ] Skills 市场（类似 ClawHub）

**验收标准**:
- ✅ 缺少依赖时给出提示
- ✅ Skills 可声明依赖其他 Skills
- ✅ 可从远程下载 Skills

---

## 📝 内置 Skills 内容设计

### mobile-operations.md (核心 - 800 tokens)

```markdown
---
name: mobile-operations
description: 移动设备操作核心技能
metadata:
  {
    "hermes": {
      "always": true,
      "emoji": "📱"
    }
  }
---

# Mobile Operations Skill

你是移动端的 AI Agent，拥有以下核心能力。

## 核心循环

**观察 → 思考 → 行动 → 验证**

每次操作都要遵循这个循环：
1. screenshot() 观察当前界面
2. 分析界面，决定下一步操作
3. 执行操作（tap, swipe, type, 等）
4. screenshot() 验证操作结果

## 可用工具

### 观察工具

**screenshot()**
- 截取当前屏幕，返回图像
- 用途: 每次操作前后都要截图确认
- 频率: 尽可能多地使用

**get_ui_tree()**
- 获取 UI 层级树（XML 格式）
- 用途: 辅助定位元素
- 注意: 某些应用可能无法获取

### 操作工具

**tap(x, y)**
- 点击屏幕坐标
- 参数:
  - x: 横坐标（像素）
  - y: 纵坐标（像素）
- 用途: 点击按钮、输入框、列表项

**swipe(startX, startY, endX, endY, duration)**
- 滑动屏幕
- 参数:
  - startX, startY: 起点坐标
  - endX, endY: 终点坐标
  - duration: 持续时间（毫秒）
- 用途: 滚动页面、切换标签、拖动元素

**type(text)**
- 输入文本到当前焦点输入框
- 参数:
  - text: 要输入的文本
- 用途: 填写表单、搜索、发送消息

**long_press(x, y, duration)**
- 长按屏幕坐标
- 参数:
  - x, y: 坐标
  - duration: 持续时间（毫秒，默认 1000）
- 用途: 触发上下文菜单、删除操作

### 导航工具

**home()**
- 返回主屏幕
- 用途: 退出应用、返回桌面

**back()**
- 返回上一页
- 用途: 返回、取消、关闭弹窗

**open_app(package_name)**
- 打开指定应用
- 参数:
  - package_name: 应用包名
- 用途: 启动应用

### 系统工具

**wait(duration)**
- 等待指定时间
- 参数:
  - duration: 等待时间（毫秒）
- 用途: 等待加载、动画完成

**stop()**
- 停止当前任务
- 用途: 任务完成后必须调用
- ⚠️ 重要: 完成目标后务必调用此工具

**notification(message)**
- 发送通知
- 用途: 重要信息提醒

## 重要原则

### 1. 永远不假设
- ❌ 不要假设界面状态
- ❌ 不要假设元素位置
- ✅ 依赖 screenshot 实际观察

### 2. 每步验证
- 每次操作后都要 screenshot 确认结果
- 如果结果不符合预期，调整策略

### 3. 灵活调整
- 遇到问题时尝试不同方法
- 例如: 找不到按钮 → 尝试滑动页面

### 4. 超时处理
- 等待加载时使用 wait() 工具
- 长时间无响应时考虑 back() 或 home()

## 常见模式

### 模式 1: 打开应用并等待启动
```
1. open_app("com.example.app")
2. wait(2000)
3. screenshot()
```

### 模式 2: 查找并点击元素
```
1. screenshot()
2. 分析截图，找到目标元素坐标
3. tap(x, y)
4. wait(500)
5. screenshot()
```

### 模式 3: 滚动查找元素
```
1. screenshot()
2. 如果没找到目标 → swipe(540, 1500, 540, 500, 300)
3. screenshot()
4. 重复直到找到或达到底部
```

### 模式 4: 输入文本
```
1. screenshot() 找到输入框
2. tap(x, y) 点击输入框
3. wait(500)
4. type("要输入的文本")
5. screenshot() 验证输入
```

## 错误处理

### 常见问题

**问题: 找不到目标元素**
- 尝试滚动页面
- 尝试 back() 返回重新进入
- 尝试 home() + open_app() 重新打开

**问题: 操作无响应**
- wait() 等待更长时间
- screenshot() 确认当前状态
- 考虑是否有弹窗或加载中

**问题: 应用崩溃**
- home() 返回桌面
- open_app() 重新打开
- 如果持续崩溃，调用 stop() 并报告

## 完成任务

当你完成用户的指令后，**务必**调用 `stop()` 工具。

示例:
```
用户: "打开微信"
你: screenshot() → open_app("com.tencent.mm") → wait(2000) → screenshot() → 确认已打开 → stop()
```
```

**Token 估计**: ~800 tokens

---

### app-testing.md (测试 - 300 tokens)

```markdown
---
name: app-testing
description: 应用测试策略和方法
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "🧪"
    }
  }
---

# App Testing Skill

## 测试流程

### 1. 功能测试
- 验证核心功能是否正常
- 测试用户常用流程
- 检查边界条件

### 2. UI 测试
- 检查按钮是否可点击
- 检查文本是否显示正确
- 检查布局是否正常

### 3. 性能测试
- 观察加载时间
- 检查是否卡顿
- 检查内存占用

## 测试模式

### 探索模式 (Exploration)
- 自由探索应用功能
- 发现潜在问题
- 覆盖更多场景

### 验证模式 (Verification)
- 验证特定功能
- 回归测试
- 按照测试用例执行

## Bug 检测

### 常见 Bug 类型
- 崩溃 (Crash)
- 无响应 (ANR)
- UI 错误 (Layout issues)
- 功能异常 (Logic errors)

### 检测方法
- screenshot 比对
- UI 树分析
- 操作响应时间
```

**Token 估计**: ~300 tokens

---

## 🎯 优化策略

### 1. 智能选择 Skills

```kotlin
fun selectRelevantSkills(userGoal: String): List<SkillDocument> {
    val keywords = userGoal.lowercase()

    return when {
        // 测试相关任务
        keywords.contains("测试") || keywords.contains("检查") ->
            listOf(skills["app-testing"])

        // 调试相关任务
        keywords.contains("调试") || keywords.contains("bug") ->
            listOf(skills["debugging"])

        // 无障碍测试
        keywords.contains("无障碍") || keywords.contains("accessibility") ->
            listOf(skills["accessibility"])

        // 默认: 不加载额外 Skills
        else -> emptyList()
    }
}
```

---

### 2. Skills 懒加载

```kotlin
class LazySkillsLoader {
    private val loadedSkills = mutableMapOf<String, SkillDocument>()

    /**
     * 延迟加载 Skill（第一次使用时才加载）
     */
    fun getSkill(name: String): SkillDocument? {
        // 已加载，直接返回
        if (loadedSkills.containsKey(name)) {
            return loadedSkills[name]
        }

        // 首次加载
        val skill = loadSkillFromDisk(name)
        if (skill != null) {
            loadedSkills[name] = skill
        }

        return skill
    }
}
```

---

### 3. Skills 缓存策略

```kotlin
class SkillsCache {
    private val cache = LruCache<String, SkillDocument>(maxSize = 10)

    fun get(name: String): SkillDocument? {
        return cache.get(name)
    }

    fun put(name: String, skill: SkillDocument) {
        cache.put(name, skill)
    }

    fun invalidate() {
        cache.evictAll()
    }
}
```

---

## 📊 效果预测

### Token 使用对比

| 场景 | 当前 (硬编码) | 优化后 (Skills) | 节省 |
|------|--------------|----------------|------|
| **简单操作** ("打开微信") | 2350 tokens | 1000 tokens | 57% ↓ |
| **测试任务** ("测试播放器") | 2350 tokens | 1300 tokens | 45% ↓ |
| **调试任务** ("调试登录") | 2350 tokens | 1200 tokens | 49% ↓ |

### 性能提升

- **启动速度**: 更快（只加载 always skills）
- **内存占用**: 更少（按需加载）
- **扩展性**: 更强（用户可添加 Skills）

---

## 📚 示例 Skills 库

### 已规划 Skills

1. ✅ **mobile-operations** - 移动操作核心 (always: true)
2. 📝 **app-testing** - 应用测试策略
3. 📝 **debugging** - 调试技能
4. 📝 **accessibility** - 无障碍测试
5. 📝 **performance** - 性能测试
6. 📝 **security** - 安全测试

### 用户自定义 Skills (示例)

**Skill: wechat-automation**
```markdown
---
name: wechat-automation
description: 微信自动化操作技能
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "💬"
    }
  }
---

# WeChat Automation

## 常用操作

### 发送消息
1. open_app("com.tencent.mm")
2. 点击搜索框
3. 输入联系人名字
4. 点击联系人
5. 输入消息
6. 点击发送

### 发送朋友圈
...
```

---

## 🔑 关键决策

### 1. 为什么用 Markdown？

✅ **优点**:
- 人类可读
- 易于编辑
- 支持格式化
- 兼容 AgentSkills.io

❌ **不用 JSON**:
- 不够灵活
- 不适合长文本
- 人类不友好

---

### 2. 为什么三层加载？

✅ **优点**:
- 灵活覆盖（用户可自定义）
- 安全隔离（测试 Skills 不影响生产）
- 渐进式（从简单到复杂）

---

### 3. 为什么按需加载？

✅ **优点**:
- Token 节省
- 性能提升
- 上下文聚焦

❌ **缺点**:
- 实现复杂度增加
- 需要智能选择算法

**决策**: 优点远大于缺点，值得实现。

---

## 🎓 学习资源

- [Hermes Skills 文档](https://docs.hermes.ai/tools/skills)
- [AgentSkills.io](https://agentskills.io)
- [pi-mono Workspace](https://github.com/anthropics/pi-mono)

---

**Skills 系统是 AndroidForClaw 对齐 Hermes 的核心** - 实现它，我们的对齐度将从 60% 提升到 85%+。
