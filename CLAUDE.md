# CLAUDE.md

本文件为 Claude Code 提供在此代码库中工作的指导。

## 🎯 项目概览

**androidforclaw** (原 AndroidForClaw) 是 [forClaw](../README.md) 项目家族的一部分,为 [OpenClaw](https://github.com/openclaw/openclaw) 提供手机控制能力。这是一个 Android AI Agent Runtime,使 AI 能够通过自然语言指令观察和控制 Android 设备。

**项目定位**: OpenClaw 是大脑,androidforclaw 是手机执行器。

**核心目的**: 赋予 AI 使用 Android 手机的能力 - 观察屏幕、UI 交互、执行任务、数据处理等。应用场景包括但不限于:移动自动化、应用测试、数据采集、任务执行、设备交互。

**核心理念** (源自 OpenClaw):
- **知识与代码分离** - Tools 提供能力,Skills 教授如何使用
- **Agent Loop** - 简洁的 LLM → Tool Call → Observation 循环
- **Skills 系统** - 用 Markdown 文档实现可扩展的知识库
- **Gateway 架构** - (规划中) 多渠道接入和远程控制

**技术栈**: Kotlin + Java, MVVM + Repository, OpenAI 兼容 API (Claude Opus 4.6), Accessibility Service, ADB JNI, MMKV 存储。

### 🔗 与 OpenClaw 对齐

**重要原则**: 当前 Android 项目功能和底层逻辑要尽量向 OpenClaw 对齐, 实施的时候不要偷懒

**OpenClaw 路径**: `~/file/forclaw/OpenClaw`

**对齐指南**:
- ✅ **核心架构**: Agent Loop, Skills System, Tools Registry 必须对齐
- ✅ **配置系统**: models.json, openclaw.json 格式必须一致
- ✅ **Skills 格式**: AgentSkills.io 兼容格式
- ⚠️ **Android 特有**: Accessibility, MediaProjection 等 Android 特有功能是合理的,不要死搬
- 📋 **对照表**: 参考 [MAPPING.md](MAPPING.md) 快速找到对应实现

**工作流程**:
1. 在 OpenClaw 中发现功能/架构
2. 查阅 [MAPPING.md](MAPPING.md) 找到对应位置
3. 如果缺失,实现后更新 MAPPING.md
4. 如果是 Android 特有,在 MAPPING.md 中标注

---

## 🏗️ 架构

### 三层设计

```
┌─────────────────────────────────────┐
│      Gateway (规划中)                │  多渠道、会话、安全
├─────────────────────────────────────┤
│      Agent Runtime (核心)            │  AgentLoop, Skills, Tools
├─────────────────────────────────────┤
│      Android Platform               │  Accessibility, ADB, UI
└─────────────────────────────────────┘
```

### 核心组件 (当前实现)

**Agent Runtime** (已实现)
- `AgentLoop.kt` - 核心执行循环 (LLM → Tools → Observation)
- `ToolRegistry.kt` - 通用工具注册表
- `SkillRegistry.kt` - Android 特定技能 (tap, screenshot 等)
- `ContextBuilder.kt` - 系统提示词构建器
- `SessionManager.kt` - 会话管理

**Skills 系统** (部分实现)
- 内置 Skills: `assets/skills/` (随应用打包)
- 工作区 Skills: `/sdcard/AndroidForClaw/workspace/skills/` (用户自定义)
- AgentSkills.io 兼容格式

**Gateway** (规划中)
- WebSocket 控制平面
- 多渠道支持 (WhatsApp, Telegram, Web 等)
- 远程控制与监控

---

## ⚙️ 配置系统

### 配置文件说明

**与 OpenClaw 对齐**: AndroidForClaw 使用与 OpenClaw 相同的单配置文件结构

**配置文件**: `/sdcard/AndroidForClaw/config/openclaw.json` (唯一配置文件)

包含所有配置:
- **Agent 配置**: maxIterations, defaultModel, timeout, mode
- **Thinking 配置**: enabled, budgetTokens, showInUI
- **Skills 配置**: paths, autoLoad, disabled
- **Tools 配置**: screenshot, accessibility, exec, browser
- **Gateway 配置**: port, security, channels
- **Channels 配置**: Feishu, Discord 等
- **Models 配置**: LLM Providers 和模型定义
- **UI 配置**: theme, language, floatingWindow
- **Logging 配置**: level, logToFile, logPath

**注意**:
- `models.json` 仅用于向后兼容,不推荐使用
- 新项目应将所有配置放在 `openclaw.json` 中

### 配置格式

**配置示例**:
```json
{
  "version": "1.0.0",
  "agent": {
    "name": "androidforclaw",
    "defaultModel": "claude-opus-4-6",
    "maxIterations": 50,
    "timeout": 300000,
    "mode": "exploration"
  },
  "thinking": {
    "enabled": true,
    "budgetTokens": 10000,
    "showInUI": true
  },
  "skills": {
    "bundledPath": "assets://skills/",
    "workspacePath": "/sdcard/AndroidForClaw/workspace/skills/",
    "autoLoad": ["mobile-operations"]
  },
  "models": {
    "mode": "merge",
    "providers": {
      "anthropic": {
        "baseUrl": "https://api.anthropic.com/v1",
        "apiKey": "${ANTHROPIC_API_KEY}",
        "api": "anthropic",
        "models": [
          {
            "id": "claude-opus-4-6",
            "name": "Claude Opus 4.6",
            "reasoning": true,
            "input": ["text", "image"],
            "contextWindow": 200000,
            "maxTokens": 16384
          }
        ]
      }
    }
  },
  "gateway": {
    "enabled": true,
    "port": 8080,
    "feishu": {
      "enabled": true,
      "appId": "${FEISHU_APP_ID}",
      "appSecret": "${FEISHU_APP_SECRET}"
    }
  }
}
```

**主要特性**:
- ✅ JSON 配置格式 (与 OpenClaw 100% 对齐)
- ✅ 单配置文件 (openclaw.json)
- ✅ 环境变量支持 (`${VAR_NAME}`)
- ✅ 多 provider 支持
- ✅ 模型级配置 (reasoning, context window, cost)
- ✅ 自动验证
- ✅ 热重载支持

**配置类**:
- `app/src/main/java/com/xiaomo/androidforclaw/config/OpenClawConfig.kt` - OpenClaw 配置数据类
- `app/src/main/java/com/xiaomo/androidforclaw/config/ModelConfig.kt` - 模型配置数据类 (嵌套在 OpenClawConfig 中)
- `app/src/main/java/com/xiaomo/androidforclaw/config/ConfigLoader.kt` - 配置加载器

**代码使用**:
```kotlin
val configLoader = ConfigLoader(context)

// 加载配置
val config = configLoader.loadOpenClawConfig()

// 获取特定 provider
val providerConfig = config.providers["anthropic"]

// 获取特定模型
val model = configLoader.getModelDefinition("anthropic", "claude-opus-4-6")

// 列出所有模型
val allModels = configLoader.listAllModels()

// 获取 Feishu 配置
val feishuConfig = configLoader.getFeishuConfig()
```

**详见**: [模型配置指南](doc/模型配置指南.md)

---

## 🛠️ 开发命令

### 构建与运行
```bash
# 构建 debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 构建 release (需要 keystore.jks)
./gradlew assembleRelease

# 清理
./gradlew clean
```

### 测试
```bash
# 运行单元测试
./gradlew test

# 运行特定测试
./gradlew test --tests "ClassName"
```

### 代码质量
```bash
# Lint 检查
./gradlew lint
```

---

## 📐 Agent Loop (核心执行)

### 工作原理

```kotlin
// 简化流程
suspend fun AgentLoop.run(systemPrompt: String, userMessage: String): AgentResult {
    val messages = mutableListOf(
        Message("system", systemPrompt),
        Message("user", userMessage)
    )

    var iteration = 0
    while (iteration < maxIterations && !shouldStop) {
        iteration++

        // 1. LLM 推理 (Extended Thinking)
        val response = llmRepository.chatWithTools(
            messages = messages,
            tools = getAllToolDefinitions(),
            reasoningEnabled = true
        )

        // 2. 执行 tool calls
        if (response.toolCalls != null) {
            messages.add(AssistantMessage(toolCalls = response.toolCalls))

            for (toolCall in response.toolCalls) {
                val result = executeTool(toolCall)  // 来自 ToolRegistry 或 SkillRegistry
                messages.add(ToolResultMessage(result))

                if (toolCall.name == "stop") {
                    shouldStop = true
                    break
                }
            }
            continue
        }

        // 3. 无 tool calls = 任务完成
        return AgentResult(finalContent = response.content, iterations = iteration)
    }
}
```

### 关键文件

- `app/src/main/java/com/xiaomo/androidforclaw/agent/loop/AgentLoop.kt` - 核心循环实现
- `app/src/main/java/com/xiaomo/androidforclaw/core/MainEntryNew.kt` - 主入口 (基于 AgentLoop)
- `app/src/main/java/com/xiaomo/androidforclaw/core/MainEntry.kt` - 旧版入口 (多 agent,已废弃)

**重要**: 新功能使用 `MainEntryNew.kt`。`MainEntry.kt` 是遗留代码。

---

## 🧩 Skills 系统

### 概念 (来自 OpenClaw)

**Skills = 教授 agent 如何使用工具的 Markdown 文档**

```
Tools (工具)              Skills (技能)
─────────────────────     ─────────────────────
Kotlin 代码               Markdown 文档
提供能力                  教授使用方法
启动时加载                按需加载
执行操作                  提供策略
```

### Skill 格式 (AgentSkills.io 兼容)

```markdown
---
name: mobile-operations
description: 核心移动设备操作技能
metadata:
  {
    "openclaw": {
      "always": true,
      "emoji": "📱"
    }
  }
---

# Mobile Operations Skill

核心循环: 观察 → 思考 → 行动 → 验证

## 可用工具

### 观察
- **screenshot()**: 捕获当前屏幕
  - 每次操作前后都要使用

### 操作
- **tap(x, y)**: 点击坐标
- **swipe(startX, startY, endX, endY, duration)**: 滑动
- **type(text)**: 输入文本

### 导航
- **home()**: 返回主屏幕
- **back()**: 返回上一页
- **open_app(package_name)**: 打开应用

## 关键原则

1. **不要假设** - 始终先截图观察
2. **验证每一步** - 每次操作后截图
3. **保持灵活** - 遇到阻碍尝试不同方法
4. **处理超时** - 加载时使用 wait()
```

### Skill 位置 (优先级)

**与 OpenClaw 架构对齐**:

1. **工作区 Skills** (最高) - `/sdcard/androidforclaw-workspace/skills/`
   - 用户可编辑技能 (类似 OpenClaw 的 `~/.openclaw/workspace/`)
   - 通过文件管理器可访问
   - 支持热重载
   - 覆盖内置和托管 skills

2. **托管 Skills** (中等) - `/sdcard/AndroidForClaw/.skills/`
   - 通过包管理器安装 (未来功能)
   - 覆盖内置 skills

3. **内置 Skills** (最低) - `app/src/main/assets/skills/`
   - 随应用打包
   - 只读

**目录结构**:
```
/sdcard/androidforclaw-workspace/     ← 用户工作区 (类似 ~/.openclaw/workspace/)
├── skills/                           ← 用户技能
│   ├── my-custom-skill/
│   │   └── SKILL.md
│   └── wechat-automation/
│       └── SKILL.md
└── sessions/                         ← 会话数据 (未来)

/sdcard/AndroidForClaw/               ← 应用数据目录
├── config/                           ← 配置文件
│   ├── openclaw.json                 ← 主配置
│   └── models.json                   ← 模型 providers
└── .skills/                          ← 托管技能 (未来)
```

### 当前实现状态

- [x] SkillRegistry (加载 Skills)
- [x] 基础技能执行
- [x] SkillsLoader (从 assets + workspace 加载)
- [x] 按需加载技能
- [x] 热重载支持
- [ ] Skill gating (requires.bins, requires.config)

---

## 🔧 Tools 系统

### Android Tools (SkillRegistry)

**观察**
- `screenshot` - 通过 MediaProjection 捕获屏幕
- `get_ui_tree` - 获取 UI 层级 (未来)

**操作**
- `tap` - 通过 AccessibilityService 点击坐标
- `swipe` - 滑动手势
- `type` - 输入文本
- `long_press` - 长按

**导航**
- `home` - 按 Home 键
- `back` - 按返回键
- `open_app` - 根据包名启动应用

**系统**
- `wait` - 休眠/延迟
- `stop` - 停止执行
- `notification` - 发送通知

### Tool 接口

```kotlin
interface Skill {
    val name: String
    val description: String

    fun getToolDefinition(): ToolDefinition
    suspend fun execute(args: Map<String, Any?>): SkillResult
}

data class SkillResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any?> = emptyMap()) =
            SkillResult(true, content, metadata)

        fun error(message: String) =
            SkillResult(false, "Error: $message")
    }
}
```

### 添加新工具

1. 在 `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/` 创建工具类
2. 实现 `Skill` 接口
3. 在 `SkillRegistry.kt` 中注册

```kotlin
// 示例: YourSkill.kt
class YourSkill : Skill {
    override val name = "your_skill"
    override val description = "你的技能描述"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "param1" to PropertySchema("string", "参数描述")
                    ),
                    required = listOf("param1")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val param1 = args["param1"] as? String ?: return SkillResult.error("缺少 param1")

        try {
            // 你的逻辑
            return SkillResult.success("操作成功")
        } catch (e: Exception) {
            return SkillResult.error("失败: ${e.message}")
        }
    }
}

// 在 SkillRegistry.kt 中注册
class SkillRegistry(...) {
    init {
        register(ScreenshotSkill(...))
        register(TapSkill())
        register(YourSkill())  // 在这里添加
    }
}
```

### 重要:Tool Description 格式

**关键规则**: Tool 的 `description` 字段必须是单行文本,不能包含多行字符串!

❌ **错误示例** (会导致 JSON 序列化错误):
```kotlin
override val description = """
    这是一个多行描述
    第二行
    第三行
""".trimIndent()
```

✅ **正确示例**:
```kotlin
override val description = "这是一个单行描述,包含所有必要信息但不换行"
```

**原因**: Gson 序列化时,多行字符串中的换行符会导致生成的 JSON 格式无效,引发 `JSONException: Unterminated object` 错误,导致 Agent 第一次迭代就失败。

---

## 📦 包结构

```
com.xiaomo.androidforclaw/
├── core/
│   ├── MainEntryNew.kt           # 🎯 主入口 (基于 AgentLoop)
│   ├── MainEntry.kt              # ⚠️ 旧版入口 (已废弃)
│   └── MyApplication.kt          # 应用生命周期
├── agent/
│   ├── loop/
│   │   └── AgentLoop.kt          # 🎯 核心执行循环
│   ├── context/
│   │   └── ContextBuilder.kt     # 系统提示词构建器
│   ├── tools/                    # Android 工具
│   │   ├── ScreenshotSkill.kt
│   │   ├── TapSkill.kt
│   │   ├── SwipeSkill.kt
│   │   └── ...
│   ├── session/
│   │   └── SessionManager.kt     # 会话管理
│   └── [legacy agents]           # ⚠️ 旧多 agent 代码 (已废弃)
├── providers/
│   └── UnifiedLLMProvider.kt     # LLM provider (Claude Opus 4.6)
├── service/
│   ├── FloatingWindowService.kt  # UI 悬浮窗
│   └── DocSync.kt                # 飞书同步 (遗留)
├── data/
│   ├── model/
│   │   ├── TaskData.kt           # 状态容器
│   │   └── TaskDataManager.kt    # 任务生命周期
│   ├── repository/
│   │   ├── DifyRepository.kt     # ⚠️ 遗留 (使用 UnifiedLLMProvider)
│   │   └── FeishuRepository.kt   # 飞书集成
│   └── network/
│       └── NetworkProvider.kt    # HTTP 客户端
├── ui/
│   ├── activity/
│   │   └── deprecated/           # 废弃的测试Activity
│   ├── adapter/
│   └── view/
│       └── ChatWindowView.kt     # 聊天 UI
├── util/
│   ├── AppConstants.kt           # API keys, URLs
│   ├── MMKVKeys.kt               # 配置键
│   └── DeviceInfoUtils.kt        # 设备信息
├── debug/                         # 🧪 调试和测试工具
│   ├── AutoTestConfig.kt          # 自动测试配置
│   ├── ContextBuilderTestRunner.kt
│   ├── SkillsLoaderTestRunner.kt
│   ├── ConfigLoaderTestRunner.kt
│   ├── TestLogAdapter.kt          # 测试日志适配器
│   ├── TestLogItem.kt
│   └── test/                      # 临时测试代码
│       ├── FeishuConnectionTest.kt
│       └── FeishuWebSocketDirectTest.kt
└── extensions/                    # 扩展模块
    ├── feishu/                    # 飞书集成
    ├── discord/                   # Discord 集成
    ├── observer/                  # 观察能力 (UI Tree + Screenshot)
    └── BrowserForClaw/            # BClaw 浏览器
```

### 测试文件组织

测试相关文件按以下目录组织:

| 类型 | 目录 | 说明 |
|------|------|------|
| **单元测试** | `app/src/test/java/` | JUnit 测试,不依赖 Android 框架 |
| **UI 测试** | `app/src/androidTest/java/` | Instrumented 测试,UI 自动化和集成测试 |
| **调试工具** | `app/src/main/java/.../debug/` | 开发时的临时测试代码和调试工具 |
| **测试脚本** | `scripts/` | Shell 脚本用于批量测试 |
| **测试文档** | `docs/tests/` | 测试相关文档和报告 |

注意:
- 测试脚本统一放在 `scripts/` 目录
- 调试代码放在 `debug/` 包,不要散落在 `main/` 其他位置
- 废弃的测试 Activity 放在 `deprecated/` 包

---

## 💡 开发指南

### 代码风格

1. **优先编辑现有文件** 而非创建新文件
2. **遵循现有模式** - 跟随 SkillRegistry, AgentLoop 模式
3. **保持简洁** - 避免过度抽象
4. **注释非显而易见的逻辑** - 添加简短注释

### 架构模式

1. **Agent Loop 是核心** - 所有执行流经 AgentLoop
2. **Tools 提供能力** - Skills 教授如何使用
3. **无硬编码工作流** - LLM 决定工具序列
4. **Skills 优于系统提示词** - 将知识移至 Skills,而非系统提示词

### 关键模式

**悬浮窗管理**
```kotlin
MyApplication.manageFloatingWindow(
    shouldShow: Boolean,     // true=显示, false=隐藏
    delayMs: Long = 0,       // 延迟 (毫秒)
    reason: String = "",     // 日志原因
    callback: (() -> Unit)? = null
)
```

**模式**: 截图前隐藏悬浮窗,完成后显示。

**Tool 执行**
```kotlin
// 通过 SkillRegistry 执行工具
val result: SkillResult = skillRegistry.execute(toolName, args)

// 始终检查结果
if (result.success) {
    // 处理成功
} else {
    // 处理错误: result.content 包含错误信息
}
```

### 开发约束

1. 完成修改后直接结束,不生成总结
2. 测试使用 adb 发送消息,监听日志观察对话
3. 修改代码后流程: clean → build → install → test
4. 启用功能前检查 MMKV 特性标志
5. 所有操作通过 AccessibilityService

### 安全与隐私

敏感信息不要提交到 Git:
- API 密钥、签名文件、个人配置等放在 `~/file/forclaw/` (Git 外)
- Git 仓库只提交代码,配置使用 `.example` 模板
- 密钥使用占位符 `${YOUR_API_KEY}`,不要硬编码

存储位置:
```
~/file/forclaw/               ← 个人信息 (不提交)
├── keys/                     ← API 密钥
├── configs/                  ← 配置备份
└── keystore/                 ← 签名文件

~/file/forclaw/phoneforclaw/  ← Git 仓库
└── config/
    └── models.json.example   ← 配置模板
```

### 文档创建原则

完成工作后不要创建新的总结文档 (`*_REPORT.md`, `*_SUMMARY.md` 等)。

输出方式:
- 测试结果、结论直接输出到终端
- 只在架构变更时更新现有文档 (README.md, ARCHITECTURE.md 等)

更新现有文档的时机:
- 添加/移除核心组件
- 修改 Agent Loop 执行流程
- 变更包结构或配置系统

---

## 🧪 测试要求

### 设备要求

- **Android**: 5.0+ (API 21+)
- **权限**: Accessibility, Display Over Apps, MediaProjection
- **推荐**: 厂商/定制 OS 设备以完整系统集成

### 网络

需要活跃的互联网连接:
- LLM API (Claude Opus 4.6 或兼容)
- 飞书集成 (可选)

---

## 📚 关键文档

**核心文档** (必读):
- [README.md](README.md) - 项目概览、快速开始、使用方法
- [ARCHITECTURE.md](ARCHITECTURE.md) - 详细架构设计、核心组件、包结构
- [REQUIREMENTS.md](REQUIREMENTS.md) - 用户需求、开发约束、已完成任务
- [MAPPING.md](MAPPING.md) - OpenClaw ↔ AndroidForClaw 对照速查表

**其他文档**:
- [README_CN.md](README_CN.md) - 中文版 README
- [CONTRIBUTING.md](CONTRIBUTING.md) - 贡献指南

**OpenClaw 参考**:
- `~/file/forclaw/OpenClaw/README.md` - OpenClaw 项目概览
- `~/file/forclaw/OpenClaw/AGENTS.md` - OpenClaw Agent 文档

---

## 🚀 路线图

### ✅ 已完成

- [x] AgentLoop 核心执行
- [x] ToolRegistry + SkillRegistry
- [x] Android 工具 (screenshot, tap, swipe, type 等)
- [x] OpenAI 兼容 API 集成 (Claude Opus 4.6)
- [x] 悬浮窗 UI
- [x] Accessibility Service 集成
- [x] 飞书/Discord 集成
- [x] BClaw 浏览器集成
- [x] Context 溢出处理机制

### 🚧 进行中

- [ ] Skills 系统 (SkillsLoader, 按需加载)
- [ ] 内置 Skills 库 (mobile-operations, app-testing 等)
- [ ] 用户自定义 Skills 支持

### 📅 计划中

- [ ] Gateway 架构 (多渠道)
- [ ] Web UI 控制面板
- [ ] 远程控制与监控
- [ ] Skills 社区 (类似 ClawHub)

---

## 🎓 从 OpenClaw 学习

**关键要点**:

1. **关注点分离**
   - 代码提供工具 (能力)
   - Skills 教授如何使用工具 (知识)
   - 系统提示词提供身份 (上下文)

2. **按需加载**
   - 不要将所有东西注入系统提示词
   - 根据任务类型加载 Skills
   - 最小化 token 使用

3. **Gateway 模式**
   - 分离控制平面 (Gateway) 和执行 (Runtime)
   - 多渠道支持
   - 会话管理

4. **AgentSkills.io 兼容性**
   - 使用标准 Skill 格式
   - 启用社区共享
   - Skills 市场潜力

---

**AndroidForClaw** - 移动 AI Agent 平台 🧠📱

灵感来自 [OpenClaw](https://github.com/openclaw/openclaw)
