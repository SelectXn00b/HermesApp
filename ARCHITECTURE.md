# AndroidForClaw 架构文档

## 📁 项目目录结构

AndroidForClaw 使用 `/sdcard/.androidforclaw/` 作为项目数据根目录，所有用户数据、配置、Skills 都存储在此：

```
/sdcard/.androidforclaw/              ← 项目数据根目录 (对齐 OpenClaw ~/.openclaw/)
├── config/                           ← 配置文件目录
│   └── openclaw.json                 ← 主配置文件 (单一配置文件)
├── workspace/                        ← 用户工作区
│   ├── .androidforclaw/              ← 工作区元数据
│   │   └── workspace-state.json
│   ├── skills/                       ← 用户自定义 Skills (优先级最高)
│   ├── sessions/                     ← 会话历史记录 (JSONL 格式)
│   └── memory/                       ← 持久化记忆
├── skills/                           ← 托管 Skills (通过包管理器安装)
└── logs/                             ← 日志文件
```

**特点**:
- ✅ 用户可通过文件管理器直接访问所有文件
- ✅ 与 OpenClaw Desktop 目录结构 100% 对齐
- ✅ 支持 Git 版本控制 (workspace 目录)
- ✅ 多层 Skills 优先级: workspace > managed > bundled

---

## 🏗️ 总体架构

AndroidForClaw 采用三层设计,与 [OpenClaw](https://github.com/openclaw/openclaw) 架构对齐度约 85%:

```
┌─────────────────────────────────────┐
│      Gateway (规划中)                │  多渠道、会话管理、安全控制
├─────────────────────────────────────┤
│      Agent Runtime (核心)            │  AgentLoop, Skills, Tools
├─────────────────────────────────────┤
│      Android Platform               │  Accessibility, ADB, MediaProjection
└─────────────────────────────────────┘
```

---

## 📐 核心组件

### 1. Agent Runtime (已实现)

**核心执行循环**: `AgentLoop.kt`

```kotlin
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
                val result = executeTool(toolCall)
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

**关键特性**:
- 简洁的 LLM → Tool Call → Observation 循环
- Extended Thinking 支持 (Claude Opus 4.6)
- 自动 stop 检测
- Context 溢出处理

**相关文件**:
- `app/src/main/java/com/xiaomo/androidforclaw/agent/loop/AgentLoop.kt` - 核心循环
- `app/src/main/java/com/xiaomo/androidforclaw/core/MainEntryNew.kt` - 主入口
- `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ContextBuilder.kt` - 系统提示词构建

---

### 2. Tools 系统 (Android 特定)

**设计原则**: Tools 提供能力,Skills 教授如何使用

**Tool 接口**:

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
)
```

**已实现工具** (17个):

| 类别 | 工具 | 功能 |
|-----|------|------|
| **观察** | screenshot | 截图 (MediaProjection) |
| | get_view_tree | UI 层级树 (Accessibility) |
| **操作** | tap | 点击坐标 |
| | swipe | 滑动手势 |
| | type | 输入文本 |
| | long_press | 长按 |
| **导航** | home | Home 键 |
| | back | 返回键 |
| | open_app | 打开应用 |
| | list_installed_apps | 列出已安装应用 |
| **文件** | read_file | 读文件 |
| | write_file | 写文件 |
| | edit_file | 编辑文件 |
| | list_dir | 列出目录 |
| **执行** | exec | Shell 命令 |
| | javascript | JavaScript 执行 (QuickJS) |
| **系统** | wait | 延迟 |
| | stop | 停止执行 |
| | log | 日志记录 |
| **记忆** | memory_search | 搜索记忆 |
| | memory_get | 获取记忆内容 |

**工具注册**: `AndroidToolRegistry.kt`

```kotlin
class AndroidToolRegistry(
    context: Context,
    taskDataManager: TaskDataManager,
    memoryManager: MemoryManager,
    workspacePath: String
) {
    init {
        // 观察工具
        register(ScreenshotSkill(...))
        register(GetViewTreeSkill(...))

        // 操作工具
        register(TapSkill())
        register(SwipeSkill())
        register(TypeSkill())

        // ... 所有工具注册
    }
}
```

---

### 3. Skills 系统 (部分实现)

**概念**: Skills = 教授 agent 如何使用工具的 Markdown 文档

**Skill 格式** (AgentSkills.io 兼容):

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
- **get_view_tree()**: 获取 UI 层级

### 操作
- **tap(x, y)**: 点击坐标
- **swipe(...)**: 滑动手势

## 关键原则
1. 不要假设 - 始终先截图观察
2. 验证每一步 - 每次操作后截图
```

**Skills 位置优先级**:

1. **工作区 Skills** (最高) - `/sdcard/androidforclaw-workspace/skills/`
   - 用户可编辑
   - 类似 OpenClaw 的 `~/.openclaw/workspace/`
   - 覆盖内置和托管 skills

2. **托管 Skills** (中等) - `/sdcard/.androidforclaw/.skills/`
   - 通过包管理器安装 (未来)

3. **内置 Skills** (最低) - `app/src/main/assets/skills/`
   - 随应用打包

**SkillsLoader**: `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillsLoader.kt`

---

### 4. Session Manager (会话管理)

**功能**:
- 会话历史持久化
- Context 管理
- 多会话隔离

**文件**: `app/src/main/java/com/xiaomo/androidforclaw/agent/session/SessionManager.kt`

---

### 5. Gateway (规划中)

**设计目标**:
- 分离控制平面 (Gateway) 和执行 (Runtime)
- 多渠道支持 (Feishu, Discord, HTTP API, WhatsApp 等)
- 会话路由与管理
- 安全控制与权限

**当前实现** (临时方案):
- Feishu 集成: `extensions/feishu/`
- Discord 集成: `extensions/discord/`
- HTTP API: `app/src/main/java/com/xiaomo/androidforclaw/gateway/`

**未来架构**:
```
┌──────────────────────────────────┐
│         Gateway Server           │
│  ┌──────────┐  ┌──────────┐      │
│  │ Feishu   │  │ Discord  │      │
│  └────┬─────┘  └────┬─────┘      │
│       │             │             │
│  ┌────┴──────────────┴─────┐     │
│  │   Session Router        │     │
│  └──────────┬──────────────┘     │
└─────────────┼────────────────────┘
              │ WebSocket
┌─────────────┼────────────────────┐
│  Agent Runtime (Android)         │
└──────────────────────────────────┘
```

---

## 🔧 Android 平台集成

### 1. Accessibility Service

**用途**: UI 操作和观察

**功能**:
- 点击、滑动、输入
- UI 树遍历
- 全局操作 (Home, Back, Recent Apps)

**实现**: `app/src/main/java/com/xiaomo/androidforclaw/service/AccessibilityService.kt`

**权限**: `android.permission.BIND_ACCESSIBILITY_SERVICE`

---

### 2. MediaProjection (截图)

**用途**: 屏幕截图

**实现**: `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ScreenshotSkill.kt`

**权限**: 需要用户手动授予录屏权限

**已知限制**: 部分设备系统限制可能导致截图失败

---

### 3. ADB JNI (Shell 执行)

**用途**: 执行 shell 命令

**实现**: `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ExecSkill.kt`

**功能**:
- 文件操作
- 应用管理
- 系统信息获取

---

### 4. QuickJS (JavaScript 执行)

**用途**: 在 Android 上执行 JavaScript 代码

**实现**: `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/JavaScriptSkill.kt`

**特性**:
- ES6 支持
- 轻量级 (相比 V8)
- 快速执行 (~10ms)

---

## 📦 包结构

```
com.xiaomo.androidforclaw/
├── core/
│   ├── MainEntryNew.kt           # 🎯 主入口
│   ├── MainEntry.kt              # ⚠️ 旧版 (已废弃)
│   ├── MyApplication.kt          # 应用生命周期
│   └── AgentMessageReceiver.kt   # ADB 广播接收
├── agent/
│   ├── loop/
│   │   └── AgentLoop.kt          # 核心执行循环
│   ├── context/
│   │   └── ContextBuilder.kt     # 系统提示词
│   ├── tools/                    # Android 工具
│   │   ├── ScreenshotSkill.kt
│   │   ├── TapSkill.kt
│   │   ├── SwipeSkill.kt
│   │   └── ...
│   ├── skills/
│   │   └── SkillsLoader.kt       # Skills 加载器
│   ├── session/
│   │   └── SessionManager.kt     # 会话管理
│   └── memory/
│       └── MemoryManager.kt      # 记忆管理
├── providers/
│   └── UnifiedLLMProvider.kt     # LLM provider
├── gateway/
│   └── GatewayServer.kt          # HTTP API (未来)
├── service/
│   ├── FloatingWindowService.kt  # 悬浮窗
│   └── AccessibilityService.kt   # 无障碍服务
├── data/
│   ├── model/
│   │   ├── TaskData.kt
│   │   └── TaskDataManager.kt
│   └── repository/
│       ├── LLMRepository.kt
│       └── FeishuRepository.kt
├── ui/
│   ├── activity/
│   └── view/
│       └── ChatWindowView.kt
└── util/
    ├── AppConstants.kt
    ├── MMKVKeys.kt
    └── DeviceInfoUtils.kt
```

---

## ⚙️ 配置系统

### 1. 模型配置

**文件**: `/sdcard/.androidforclaw/config/models.json`

**格式** (与 OpenClaw 相同):
```json
{
  "mode": "merge",
  "providers": {
    "openai": {
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "${OPENAI_API_KEY}",
      "api": "openai-completions",
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
}
```

**加载器**: `app/src/main/java/com/xiaomo/androidforclaw/config/ConfigLoader.kt`

---

### 2. OpenClaw 配置

**文件**: `/sdcard/.androidforclaw/config/openclaw.json`

**内容**:
- Agent 设置
- 渠道配置 (Feishu, Discord)
- Gateway 配置

---

## 🔄 执行流程

### 完整流程

```
用户消息 (Feishu/Discord/ADB)
    ↓
Gateway (路由到 Runtime)
    ↓
MainEntryNew.kt (初始化)
    ├── TaskDataManager
    ├── MemoryManager
    ├── AndroidToolRegistry (17 tools)
    └── SkillRegistry (Skills loader)
    ↓
AgentLoop.run()
    ├── 构建系统提示词 (ContextBuilder)
    ├── 加载 Skills (SkillsLoader)
    └── 开始循环
        ↓
    ┌─────────────────────┐
    │  Iteration Loop     │
    ├─────────────────────┤
    │ 1. LLM 推理          │
    │    (Extended Think) │
    │         ↓           │
    │ 2. Tool Calls       │
    │    (execute tools)  │
    │         ↓           │
    │ 3. Observations     │
    │    (tool results)   │
    │         ↓           │
    │ 4. 判断是否完成      │
    └─────────────────────┘
        ↓
返回最终结果
    ↓
Gateway (返回用户)
```

---

## 🎯 关键设计决策

### 1. 为什么选择 AgentLoop 而非多 Agent?

**原因**:
- 简洁性: 单循环比多 agent 协调更简单
- 可靠性: 减少 agent 间通信失败
- OpenClaw 对齐: 保持架构一致性

**旧版多 agent** (`MainEntry.kt`) 已废弃

---

### 2. 为什么使用 Skills 而非系统提示词?

**优势**:
- 知识与代码分离
- 按需加载,节省 tokens
- 用户可自定义
- 社区共享 (AgentSkills.io)

---

### 3. 为什么需要 Gateway?

**痛点**:
- 多渠道代码重复
- 会话管理混乱
- 难以扩展新渠道

**解决**:
- 统一入口
- 标准化会话管理
- 插件式渠道

---

## 🚀 未来规划

### Phase 1: Skills 完善 (进行中)
- [ ] 内置 Skills 库
- [ ] Skills gating (requires.bins, requires.config)
- [ ] Skills 热重载优化

### Phase 2: Gateway 实现
- [ ] WebSocket 控制平面
- [ ] 会话路由
- [ ] 多渠道插件化

### Phase 3: 生态建设
- [ ] Skills 社区 (类似 ClawHub)
- [ ] Web UI 控制面板
- [ ] 远程监控与调试

---

## 📚 相关文档

- [README.md](README.md) - 项目概览
- [REQUIREMENTS.md](REQUIREMENTS.md) - 需求文档
- [CLAUDE.md](CLAUDE.md) - Claude Code 工作指南

---

**架构版本**: v2.4.4
**最后更新**: 2026-03-08
**对齐 OpenClaw 版本**: v0.9.x
