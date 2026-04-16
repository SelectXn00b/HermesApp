# Hermes for Android 架构文档

## 📁 项目数据目录

```
/sdcard/.hermes/              ← 项目数据根目录 (Hermes 数据目录)
├── config/
│   └── openclaw.json                 ← 主配置文件
├── workspace/
│   ├── .androidforclaw/
│   │   └── workspace-state.json
│   ├── skills/                       ← 用户自定义 Skills (优先级最高)
│   ├── sessions/                     ← 会话历史 (JSONL)
│   └── memory/                       ← 持久化记忆
├── skills/                           ← 托管 Skills (ClawHub 安装)
└── logs/
```

---

## 🏗️ 总体架构

```
┌──────────────────────────────────────────┐
│  Gateway (已实现)                         │  WebSocket RPC + 多渠道 + 会话路由
├──────────────────────────────────────────┤
│  Agent Runtime (核心)                     │  AgentLoop · Tools · Skills · Memory
├──────────────────────────────────────────┤
│  Android Platform                        │  Accessibility · Termux · MediaProjection
└──────────────────────────────────────────┘
```

---

## 📐 核心组件

### 1. Agent Runtime

**核心循环**: `agent/loop/AgentLoop.kt`

```
LLM 推理 (Extended Thinking) → Tool Calls → Observations → LLM → ... → stop
```

**关键文件**:
- `core/MainEntryNew.kt` — 主入口
- `agent/context/ContextBuilder.kt` — 22 段系统提示词构建（对齐 Hermes)
- `agent/tools/ToolRegistry.kt` — 工具注册中心
- `agent/skills/SkillsLoader.kt` — Skills 三层加载器

---

### 2. Tools 系统

19 个工具，分为以下类别：

| 类别 | 工具 | 实现文件 |
|------|------|----------|
| **设备操作** | `device` (snapshot/tap/type/scroll/press/open) | `agent/tools/device/DeviceTool.kt` |
| **文件** | `read_file` / `write_file` / `edit_file` / `list_dir` | `*FileTool.kt` / `ListDirTool.kt` |
| **执行** | `exec` | `agent/tools/ExecFacadeTool.kt` (自动路由 Termux 或内置 Shell) |
| **JavaScript** | `javascript` | QuickJS 引擎 |
| **网络** | `web_search` / `web_fetch` | `WebSearchTool.kt` / `WebFetchTool.kt` |
| **技能** | `skills_search` / `skills_install` | `SkillsHubTool.kt` |
| **记忆** | `memory_search` / `memory_get` | `agent/tools/memory/` |
| **配置** | `config_get` / `config_set` | `ConfigGetTool.kt` / `ConfigSetTool.kt` |
| **Android** | `list_installed_apps` / `install_app` / `start_activity` / `stop` | 各自独立文件 |

---

### 3. Skills 系统

Skills = 教 Agent 如何使用工具的 Markdown 文档。三层优先级：

1. **工作区 Skills** (最高) — `/sdcard/.hermes/workspace/skills/`
2. **托管 Skills** (中等) — `/sdcard/.hermes/skills/` (ClawHub 安装)
3. **内置 Skills** (最低) — `app/src/main/assets/skills/`

**当前内置 Skills**: browser · debugging · data-processing · weather · feishu (含 8 个子技能) · clawhub · model-usage · session-logs · install-app · channel-config · model-config · skill-creator

---

### 4. Gateway (已实现)

基于 Hermes Protocol v3 的 WebSocket RPC 服务，运行在 `ws://127.0.0.1:8765`。

**架构**:
```
┌──────────────────────────────────┐
│         Gateway Server           │
│  ┌──────────┐  ┌──────────┐     │
│  │ Feishu   │  │ Discord  │ ... │  6 个渠道 Channel
│  └────┬─────┘  └────┬─────┘     │
│       └──────┬───────┘           │
│        Session Router            │
│              │ WebSocket RPC     │
└──────────────┼───────────────────┘
               │
┌──────────────┼───────────────────┐
│  Agent Runtime (本地)             │
│  AgentLoop + Tools + Skills      │
└──────────────────────────────────┘
```

**关键文件**:
- `gateway/GatewayWebSocketServer.kt` — WebSocket 服务端
- `gateway/GatewayController.kt` — RPC 方法注册与路由
- `gateway/GatewayService.kt` — 服务管理
- `gateway/methods/` — 各 RPC 方法实现 (Agent, Config, Health, Models, Sessions, Skills, Tools, Cron)

---

### 5. Hermes 集成

`hermes-android/` 模块提供 Hermes 的 Android 原生实现（202 个 Kotlin 文件，对齐度 93%）：
- `NodeRuntime.kt` — 连接管理（operator + node session）
- `ChatController.kt` — 聊天状态管理（health、bootstrap、history）
- `GatewayRunner (Run.kt)` — Hermes Gateway 运行器，支持 local 模式直连
- `AppChatAdapter.kt` — 进程内 Chat UI 适配器（替代 WebSocket）
- UI 组件：`ChatSheetContent`、`RootScreen`、`PostOnboardingTabs`

**桥接层** (`app/.../hermes/bridge/`)：
- `HermesGatewayChannel.kt` — IGatewayChannel → Hermes 路由（RPC 分发 + 事件回调）
- `HermesAgentLoop.kt` — Hermes AgentLoop ↔ App AgentLoop 桥接
- `HermesChatGateway.kt` — 一键启动入口

**消息流**：
```
Chat UI → ChatController → HermesGatewayChannel → AppChatAdapter
  → GatewayRunner → HermesAgentLoop → App AgentLoop → LLM API
  → AppChatAdapter.send() → eventListener → ChatController → UI
```

---

## 🔧 Android 平台集成

### Accessibility Service
- **用途**: UI 操作（点击、滑动、输入）和 UI 树遍历
- **实现**: `accessibility/AccessibilityProxy.kt` (独立模块 `extensions/observer/`)

### MediaProjection
- **用途**: 屏幕截图
- **实现**: `accessibility/MediaProjectionHelper.kt`

### 内置 Termux
- **用途**: Shell 命令执行
- **实现**: `extensions/termux/` (内嵌 Termux 运行时，无需独立安装)
- **路由**: `ExecFacadeTool.kt` 自动判断 Termux 可用性

### MCP Server
- **用途**: 暴露无障碍/截屏能力给外部 Agent (端口 8399)
- **实现**: `mcp/`

---

## 📦 包结构

```
com.xiaomo.hermes/
├── core/                    # 应用核心
│   ├── MainEntryNew.kt      # Agent 主入口
│   ├── MyApplication.kt     # Application 生命周期
│   └── ForegroundService.kt # 保活服务
├── agent/
│   ├── loop/AgentLoop.kt    # 核心执行循环
│   ├── context/ContextBuilder.kt  # 系统提示词 (22 段)
│   ├── tools/               # 19 个工具 + device/ + memory/
│   ├── skills/SkillsLoader.kt    # Skills 三层加载
│   └── session/SessionManager.kt # 会话管理
├── gateway/                 # Gateway WebSocket RPC
│   ├── GatewayWebSocketServer.kt
│   ├── GatewayController.kt
│   └── methods/             # RPC 方法实现
├── channel/                 # 多渠道管理
├── providers/               # LLM Provider (OpenRouter, Anthropic, etc.)
├── config/                  # 配置加载 (ConfigLoader, ProviderRegistry)
├── ui/
│   ├── activity/            # 15 个 Activity (Compose)
│   ├── compose/             # Compose UI 组件
│   └── float/               # 悬浮窗
├── service/                 # 系统服务 (ClawIME 输入法)
├── updater/                 # 应用更新
└── util/                    # 工具类

extensions/
├── feishu/                  # 飞书渠道 (含 8 个 Skill)
├── discord/                 # Discord 渠道
├── telegram/                # Telegram 渠道
├── slack/                   # Slack 渠道
├── signal/                  # Signal 渠道
├── whatsapp/                # WhatsApp 渠道
├── observer/                # 无障碍 + 截屏 (独立 APK)
├── termux/                  # 内嵌 Termux 运行时
└── BrowserForClaw/          # AI 浏览器

openclaw-android/            # Hermes Android 原生实现
self-control/                # AI 自我管理模块
```

---

## ⚙️ 配置

**主配置文件**: `/sdcard/.hermes/config/openclaw.json`

包含：模型 Provider 配置、Agent 默认参数、渠道配置。

**模型配置 UI**: `ModelConfigActivity` + `ModelSetupActivity`（首次启动引导）

---

## 📚 相关文档

- [README.md](README.md) — 项目概览
- [MAPPING.md](MAPPING.md) — Hermes ↔ AndroidForClaw 源码映射

---

**架构版本**: v4.0
**最后更新**: 2026-04-16
**对齐 Hermes 版本**: v0.9.x
