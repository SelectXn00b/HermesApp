# Self-Control Module

**PhoneForClaw Self-Control Skills** - 让 AI Agent 能够控制和管理 PhoneForClaw 自身。

## 📋 概述

Self-Control Module 是一个独立的 Android Library Module，提供了一组专门用于控制 PhoneForClaw 应用自身的 Skills。这些 Skills 让 AI Agent 能够：

- 🧭 **导航应用页面** - 跳转到配置、日志、历史等页面
- ⚙️ **管理配置** - 读取和修改运行时配置参数
- 🎛️ **控制服务** - 管理悬浮窗、后台服务
- 📊 **查询日志** - 读取应用日志进行自我诊断

**核心理念**：让 AI Agent 能够自我开发、自我调优、自我诊断。

---

## 🏗️ 架构设计

### Skills 列表

| Skill | 功能 | 使用场景 |
|-------|------|----------|
| `NavigationSkill` | 页面导航 | 打开配置页面、查看历史记录 |
| `ConfigSkill` | 配置管理 | 修改 API 设置、切换功能开关 |
| `ServiceControlSkill` | 服务控制 | 隐藏悬浮窗（截图前）、管理后台服务 |
| `LogQuerySkill` | 日志查询 | 查看错误日志、诊断问题 |

### 依赖关系

```
┌─────────────────────────────────┐
│   self-control (Library)        │
│   - NavigationSkill             │
│   - ConfigSkill                 │
│   - ServiceControlSkill         │
│   - LogQuerySkill               │
│   - SelfControlRegistry         │
└───────────┬─────────────────────┘
            │ compileOnly
            ↓
┌─────────────────────────────────┐
│   app (Main Application)        │
│   - Skill interface             │
│   - SkillRegistry               │
│   - AgentLoop                   │
└─────────────────────────────────┘
```

**关键点**：
- `self-control` 作为 Library Module，`compileOnly` 依赖 `app`
- 不会循环依赖，因为只依赖接口定义
- 运行时通过 `SelfControlRegistry` 统一管理

---

## 📦 集成方式

### 1. 添加 Module 依赖

**`settings.gradle`**:
```gradle
include ':app'
include ':self-control'
```

**`app/build.gradle`**:
```gradle
dependencies {
    // 主应用依赖 self-control module
    implementation project(':self-control')
}
```

### 2. 在 SkillRegistry 中集成

**`app/src/main/java/.../agent/tools/SkillRegistry.kt`**:

```kotlin
import com.xiaomo.hermes.selfcontrol.SelfControlRegistry

class SkillRegistry(private val context: Context) {
    // 现有的 Skills
    private val skills = mutableMapOf<String, Skill>()

    // Self-Control Registry
    private val selfControlRegistry = SelfControlRegistry(context)

    init {
        // 注册现有 Skills
        register(ScreenshotSkill(context))
        register(TapSkill())
        // ... 其他 Skills

        Log.d(TAG, "Self-Control Skills loaded: ${selfControlRegistry.getAllSkillNames()}")
    }

    /**
     * 获取所有工具定义（包括 Self-Control）
     */
    fun getAllToolDefinitions(): List<ToolDefinition> {
        val baseTools = skills.values.map { it.getToolDefinition() }
        val selfControlTools = selfControlRegistry.getAllToolDefinitions()
        return baseTools + selfControlTools
    }

    /**
     * 执行 Skill（优先检查 Self-Control）
     */
    suspend fun execute(name: String, args: Map<String, Any?>): SkillResult {
        // 1. 尝试 Self-Control Skills
        val selfControlResult = selfControlRegistry.execute(name, args)
        if (selfControlResult != null) {
            return selfControlResult
        }

        // 2. 回退到基础 Skills
        val skill = skills[name]
            ?: return SkillResult.error("Unknown skill: $name")

        return skill.execute(args)
    }
}
```

### 3. 可选：添加到 System Prompt

**`app/src/main/java/.../agent/context/ContextBuilder.kt`**:

```kotlin
fun buildSystemPrompt(): String {
    return buildString {
        // ... 现有 system prompt

        // 添加 Self-Control 能力说明
        appendLine()
        appendLine(selfControlRegistry.getSummary())
    }
}
```

---

## 🎯 使用示例

### 示例 1: 截图前隐藏悬浮窗

```kotlin
// Agent 在执行截图前自动隐藏悬浮窗
val tools = listOf(
    ToolCall(
        name = "control_service",
        arguments = mapOf("operation" to "hide_float")
    ),
    ToolCall(
        name = "screenshot",
        arguments = emptyMap()
    ),
    ToolCall(
        name = "control_service",
        arguments = mapOf("operation" to "show_float")
    )
)
```

### 示例 2: 修改配置并重启

```kotlin
// 查看当前配置
execute("manage_config", mapOf(
    "operation" to "get",
    "key" to "exploration_mode"
))

// 修改配置
execute("manage_config", mapOf(
    "operation" to "set",
    "key" to "exploration_mode",
    "value" to "true"
))

// 打开配置页面让用户确认
execute("navigate_app", mapOf(
    "page" to "config"
))
```

### 示例 3: 诊断错误

```kotlin
// 操作失败后查看日志
execute("query_logs", mapOf(
    "level" to "E",
    "filter" to "AgentLoop",
    "lines" to 50
))
```

### 示例 4: 完整的自我调优流程

```kotlin
// 1. 检查服务状态
execute("control_service", mapOf("operation" to "check_status"))

// 2. 查看错误日志
execute("query_logs", mapOf("level" to "E", "lines" to 100))

// 3. 分析问题后调整配置
execute("manage_config", mapOf(
    "operation" to "set",
    "key" to "screenshot_delay",
    "value" to "100"
))

// 4. 打开配置页面确认
execute("navigate_app", mapOf("page" to "config"))
```

---

## 🔧 开发指南

### 添加新的 Self-Control Skill

1. **创建 Skill 类**

```kotlin
// self-control/src/main/java/.../YourSkill.kt
class YourSkill(private val context: Context) : Skill {
    override val name = "your_skill"
    override val description = "Your skill description"

    override fun getToolDefinition(): ToolDefinition {
        // 定义参数
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        // 实现逻辑
    }
}
```

2. **注册到 SelfControlRegistry**

```kotlin
// self-control/src/main/java/.../SelfControlRegistry.kt
private val skills: Map<String, Skill> = mapOf(
    "navigate_app" to NavigationSkill(context),
    "manage_config" to ConfigSkill(context),
    "control_service" to ServiceControlSkill(context),
    "query_logs" to LogQuerySkill(context),
    "your_skill" to YourSkill(context)  // 添加这里
)
```

3. **测试**

```kotlin
val registry = SelfControlRegistry(context)
val result = registry.execute("your_skill", mapOf("arg" to "value"))
Log.d(TAG, "Result: ${result.content}")
```

### 扩展建议

未来可以添加的 Skills：

- **PermissionSkill** - 检查和请求权限
- **PackageManageSkill** - 安装/卸载应用（需要 system uid）
- **NetworkSkill** - 网络诊断和配置
- **StorageSkill** - 清理缓存、管理文件
- **NotificationSkill** - 发送/管理通知
- **TaskSkill** - 创建定时任务

---

## 🧪 测试

### 单元测试

```kotlin
class NavigationSkillTest {
    @Test
    fun testNavigateToConfig() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val skill = NavigationSkill(context)

        val result = skill.execute(mapOf("page" to "config"))

        assertTrue(result.success)
        assertTrue(result.content.contains("配置"))
    }
}
```

### 集成测试

```bash
# 通过 adb 测试 Self-Control Skills
adb shell am start -n com.xiaomo.hermes/.ui.activity.ConfigActivity
```

---

## 📝 配置说明

### MMKV 配置键

Self-Control Skills 使用的主要配置键：

| 键名 | 类型 | 描述 |
|------|------|------|
| `exploration_mode` | Boolean | 探索模式开关 |
| `reasoning_enabled` | Boolean | Extended Thinking 开关 |
| `screenshot_delay` | Int | 截图延迟（毫秒）|
| `ui_tree_enabled` | Boolean | UI 树功能开关 |
| `max_iterations` | Int | AgentLoop 最大迭代次数 |
| `api_base_url` | String | API Base URL |
| `api_key` | String | API Key |
| `model_id` | String | 模型 ID |

### 页面列表

可通过 `navigate_app` 访问的页面：

| 页面 ID | Activity | 用途 |
|---------|----------|------|
| `main` | MainActivity | 主界面 |
| `config` | ConfigActivity | 配置页面 |
| `permissions` | PermissionsActivity | 权限管理 |
| `chat_history` | ChatHistoryActivity | 对话历史 |
| `chat_log` | ChatLogActivity | 详细日志 |
| `feishu` | FeishuChannelActivity | 飞书通道 |
| `channels` | ChannelListActivity | 通道列表 |
| `result` | ResultActivity | 结果展示 |

---

## 🔒 安全考虑

### 权限要求

- **READ_LOGS**: `LogQuerySkill` 读取 logcat 需要 system uid
- **WRITE_SETTINGS**: 修改系统设置（如果未来扩展）
- **PACKAGE_USAGE_STATS**: 查询应用使用情况（如果未来扩展）

### 访问控制

建议添加以下安全措施：

1. **白名单机制** - 限制可修改的配置键
2. **操作日志** - 记录所有 Self-Control 操作
3. **用户确认** - 关键操作需要用户确认
4. **Rate Limiting** - 限制调用频率

示例（未来扩展）：

```kotlin
class SecureSelfControlRegistry(context: Context) {
    private val allowedConfigKeys = setOf(
        "exploration_mode",
        "screenshot_delay",
        "ui_tree_enabled"
    )

    suspend fun execute(name: String, args: Map<String, Any?>): SkillResult? {
        // 记录操作
        auditLog.log("SelfControl: $name with $args")

        // 检查白名单
        if (name == "manage_config") {
            val key = args["key"] as? String
            if (key !in allowedConfigKeys) {
                return SkillResult.error("Config key not allowed: $key")
            }
        }

        // 执行
        return super.execute(name, args)
    }
}
```

---

## 📚 参考

- **OpenClaw Skills System**: [AgentSkills.io](https://agentskills.io)
- **CLAUDE.md**: `../CLAUDE.md`
- **Skill Interface**: `app/src/main/java/.../agent/tools/Skill.kt`

---

## 🎯 设计理念

**Self-Control Module** 的核心理念源自 OpenClaw 的 Skills System：

> **Tools provide capabilities, Skills teach how to use them.**

Self-Control Skills 让 AI Agent 拥有了 **自我认知** 和 **自我管理** 的能力：

1. **自我认知** - 通过日志查询了解自己的运行状态
2. **自我调优** - 通过配置管理调整运行参数
3. **自我开发** - 通过页面导航进入配置界面
4. **自我诊断** - 通过错误日志分析问题根因

这使得 AI Agent 能够：
- 在执行任务时动态调整策略
- 遇到问题时自动诊断和修复
- 根据用户反馈自我优化
- 实现真正的自主闭环

**未来愿景**：AI Agent 能够通过 Self-Control Skills 实现完全自主的开发和迭代，最终达到 "AI 开发 AI" 的目标。

---

**AndroidForClaw** - Self-Control Module 🧠🔧

让 AI Agent 具备自我管理能力
