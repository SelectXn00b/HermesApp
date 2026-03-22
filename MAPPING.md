# OpenClaw ↔ AndroidForClaw 映射表

**纯粹的文件和文件夹映射关系,方便快速查找对应实现。**

> 最后更新: 2026-03-20
> OpenClaw 版本: 2026.3.11 (29dc654)

## 对齐统计

| 模块 | 对齐度 | 说明 |
|------|--------|------|
| 常量 | 95% | 核心常量完全一致 |
| System Prompt | 85% | 22 段中 16 段已实现,3 段平台不适用 |
| Agent Loop | 85% | 核心循环/溢出恢复完整,缺 streaming/subagent |
| Tools | 80% | 文件/exec/web 工具完整,缺 PDF/TTS/Canvas/Sessions |
| Cron | 90% | 调度/载荷/重试完整,实现方式不同 (WorkManager) |
| Skills | 90% | 文档格式/加载/ClawHub 完整 |
| Bootstrap | 100% | 8 个文件、预算、截断策略完全一致 |
| Context 管理 | 85% | 窗口/裁剪/压缩完整,缺 compaction safeguard |
| Channels | 60% | Feishu/Discord 完整,其余为框架 |
| Security | 15% | 仅 TokenAuth,缺 Pairing/DM-Policy/External-Content |
| **总体** | **~78%** | 核心 Agent 路径高度对齐,安全/渠道/高级工具待补 |

---

## 顶层目录映射

| OpenClaw | AndroidForClaw | 说明 |
|----------|----------------|------|
| `~/file/forclaw/OpenClaw/` | `~/file/forclaw/phoneforclaw/` | 项目根目录 |
| `src/` | `app/src/main/java/com/xiaomo/androidforclaw/` | 主代码目录 |
| `skills/` | `app/src/main/assets/skills/` | 内置 Skills |
| - | `/sdcard/androidforclaw-workspace/skills/` | 工作区 Skills |
| `extensions/` | `extensions/` | 扩展模块 |
| `test/` | `app/src/test/` | 单元测试 |
| - | `app/src/androidTest/` | Android 测试 |
| `docs/` | `docs/` | 文档目录 |
| `apps/` | - | 多应用 (AClaw 单应用) |

---

## 核心代码目录映射

### Agent Runtime

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/` | `agent/` | |
| `src/agents/pi-embedded-runner/run.ts` | `agent/loop/AgentLoop.kt` | ✅ 核心: 迭代循环、overflow recovery |
| `src/agents/agent-command.ts` | `agent/loop/AgentLoop.kt` | ✅ 会话入口、model fallback |
| `src/agents/pi-embedded-subscribe.ts` | `agent/loop/AgentLoop.kt` | ⚠️ 流式/tool回调 (Android 为非流式) |
| `src/agents/pi-embedded-runner/run/attempt.ts` | `providers/UnifiedLLMProvider.kt` | ✅ 单次 LLM 调用 |
| `src/agents/tool-loop-detection.ts` | `agent/loop/ToolLoopDetection.kt` | ✅ |
| `src/agents/tool-catalog.ts` | `agent/tools/ToolRegistry.kt` + `AndroidToolRegistry.kt` | ✅ |
| `src/agents/openclaw-tools.ts` | `agent/tools/ToolRegistry.kt` | ✅ 工具注册 |
| `src/agents/pi-tools.ts` | `agent/tools/ToolCallDispatcher.kt` | ✅ 工具构建/策略 |
| `src/agents/skills.ts` | `agent/skills/SkillsLoader.kt` | ✅ |
| `src/agents/system-prompt.ts` | `agent/context/ContextBuilder.kt` | ✅ 核心: 22段 system prompt |
| `src/agents/pi-embedded-runner/system-prompt.ts` | `agent/context/ContextBuilder.kt` | ✅ 嵌入式包装 |
| `src/agents/context.ts` | `agent/context/ContextWindowGuard.kt` | ✅ context window token 解析 |
| `src/agents/context-window-guard.ts` | `agent/context/ContextWindowGuard.kt` | ✅ warn/block 阈值 |
| `src/agents/compaction.ts` | `agent/context/MessageCompactor.kt` | ✅ |
| `src/agents/session-tool-result-guard.ts` | `agent/context/ToolResultContextGuard.kt` | ✅ |
| `src/agents/pi-embedded-runner/tool-result-truncation.ts` | `agent/context/ToolResultTruncator.kt` | ✅ |
| `src/agents/pi-embedded-runner/tool-result-context-guard.ts` | `agent/context/ToolResultContextGuard.kt` | ✅ per-run guard |
| `src/agents/session-transcript-repair.ts` | `agent/session/HistorySanitizer.kt` | ✅ |
| `src/agents/pi-embedded-utils.ts` | `agent/session/HistorySanitizer.kt` | ✅ 部分合并 |
| `src/agents/pi-embedded-payloads.ts` | `providers/ApiAdapter.kt` | ✅ provider payload |
| `src/agents/models-config.ts` | `config/ModelConfig.kt` + `ProviderRegistry.kt` | ✅ |
| `src/agents/model-catalog.ts` | `config/ProviderRegistry.kt` | ✅ |
| `src/agents/bootstrap-budget.ts` | `agent/context/ContextBuilder.kt` | ✅ 内联 |
| `src/agents/pi-embedded-helpers.ts` | `agent/context/ContextBuilder.kt` | ✅ bootstrap 文件加载 |
| `src/agents/memory-search.ts` | `agent/tools/memory/MemorySearchSkill.kt` | ✅ |
| `src/agents/skills-status.ts` | `agent/skills/SkillStatusBuilder.kt` | ✅ |
| `src/agents/skills-install.ts` | `agent/skills/SkillInstaller.kt` | ✅ |
| `src/agents/agent-scope.ts` | - | ❌ 未实现 |
| `src/agents/acp-spawn.ts` | - | ❌ 未实现 |
| `src/agents/subagent-*.ts` | - | ❌ 未实现 (Subagent 体系) |
| `src/agents/sandbox.ts` | - | ❌ 不适用 (Android 无沙箱) |
| - | `agent/tools/device/` | Android 独有 (统一设备工具) |
| - | `agent/skills/browser/` | Android 独有 (浏览器子技能) |

### Gateway

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/gateway/` | `gateway/` | |
| `src/gateway/server.ts` + `server.impl.ts` | `gateway/GatewayServer.kt` | ✅ |
| `src/gateway/boot.ts` | `gateway/GatewayController.kt` | ✅ |
| `src/gateway/server-methods.ts` | `gateway/GatewayService.kt` | ✅ |
| `src/gateway/server-methods-list.ts` | `gateway/methods/*.kt` | ✅ |
| `src/gateway/server-chat.ts` | `gateway/MainEntryAgentHandler.kt` | ✅ |
| `src/gateway/auth.ts` | `gateway/security/TokenAuth.kt` | ✅ |
| `src/gateway/server-ws-runtime.ts` | `gateway/websocket/GatewayWebSocketServer.kt` | ✅ |
| `src/gateway/server-cron.ts` | `gateway/methods/CronMethods.kt` | ✅ |
| `src/gateway/session-utils.ts` | `gateway/methods/SessionMethods.kt` | ✅ |
| `src/gateway/server-channels.ts` | - | ❌ |
| `src/gateway/server-plugins.ts` | - | ❌ |
| `src/gateway/device-auth.ts` | - | ❌ 未实现 |

### Config

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/config/` | `config/` | |
| `src/config/config.ts` | `config/OpenClawConfig.kt` | ✅ |
| `src/config/io.ts` | `config/ConfigLoader.kt` | ✅ |
| `src/config/types.openclaw.ts` | `config/OpenClawConfig.kt` | ✅ |
| `src/config/types.agents.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.channels.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.gateway.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.models.ts` | `config/ModelConfig.kt` | ✅ |
| `src/config/types.skills.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.memory.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.tools.ts` | `config/OpenClawConfig.kt` | ✅ 内联 |
| `src/config/types.cron.ts` | `cron/CronTypes.kt` | ✅ |
| `~/.openclaw/openclaw.json` | `/sdcard/.androidforclaw/openclaw.json` | ✅ |
| `~/.openclaw/config/models.json` | `/sdcard/.androidforclaw/config/models.json` | ✅ |
| - | `config/BuiltInKeyProvider.kt` | Android 独有 |
| - | `config/ConfigBackupManager.kt` | Android 独有 |
| - | `config/FeishuConfigAdapter.kt` | Android 独有 |

### Memory

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/memory/` | `agent/memory/` | |
| `src/memory/manager.ts` | `agent/memory/MemoryManager.kt` | ✅ |
| `src/memory/sqlite.ts` | `agent/memory/MemoryIndex.kt` | ✅ |
| `src/memory/sqlite-vec.ts` | `agent/memory/MemoryIndex.kt` | ✅ 合并 |
| `src/memory/search-manager.ts` | `agent/memory/MemoryIndex.kt` | ✅ 合并 |
| `src/memory/hybrid.ts` | `agent/memory/MemoryIndex.kt` | ✅ 合并 |
| `src/memory/embeddings.ts` | `agent/memory/EmbeddingProvider.kt` | ✅ |
| `src/memory/types.ts` | `agent/memory/MemoryManager.kt` | ✅ 内联 |
| - | `agent/memory/ChunkUtils.kt` | ✅ 对齐 chunkMarkdown |
| - | `agent/memory/ContextCompressor.kt` | ✅ 对齐 compaction |
| - | `agent/memory/TokenEstimator.kt` | Android 独有 |

### Sessions

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/sessions/` | `session/` + `agent/session/` | |
| `src/agents/session-dirs.ts` | `session/JsonlSessionStorage.kt` | ✅ |
| `src/agents/command/session-store.ts` | `session/JsonlSessionStorage.kt` | ✅ |
| `src/sessions/session-id.ts` | `agent/session/SessionManager.kt` | ✅ |
| `src/sessions/session-lifecycle-events.ts` | `agent/session/SessionManager.kt` | ✅ 内联 |
| - | `gateway/methods/SessionMethods.kt` | Android 独有 |

### Channels

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/channels/` | `extensions/` + `channel/` | |
| `src/channels/registry.ts` | `channel/ChannelDefinition.kt` + `ChannelManager.kt` | ✅ |
| `src/channels/session.ts` | `channel/ChannelManager.kt` | ✅ |
| `src/channels/mention-gating.ts` | `extensions/feishu/` (内联) | ✅ |
| `src/channels/plugins/` | `extensions/feishu/` + `extensions/discord/` | ✅ 部分 |
| `src/channels/typing.ts` | - | ❌ |
| `src/channels/draft-stream-*.ts` | - | ❌ |
| `src/whatsapp/` | `extensions/whatsapp/` | 框架 |
| `src/line/` | - | ❌ |

### Providers

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/pi-embedded-runner.ts` | `providers/UnifiedLLMProvider.kt` | ✅ LLM 调用 |
| `src/agents/pi-embedded-payloads.ts` | `providers/ApiAdapter.kt` | ✅ |
| `src/agents/pi-embedded-helpers.ts` | `providers/UnifiedLLMProvider.kt` | ✅ 部分 |
| `src/providers/github-copilot-auth.ts` | - | ❌ |
| `src/providers/google-shared.*.ts` | - | ❌ |
| - | `providers/LegacyRepository.kt` | Android 遗留 |
| - | `providers/LegacyProviderOpenAI.kt` | Android 遗留 |
| - | `providers/LegacyProviderAnthropic.kt` | Android 遗留 |

### CLI / Entry

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/cli/` | `core/` | |
| `src/cli/agent.ts` (不存在,实际为 `src/agents/agent-command.ts`) | `core/MainEntryNew.kt` | ✅ |
| `src/entry.ts` | `core/MyApplication.kt` | ✅ |
| `openclaw.mjs` | - | 不适用 |
| - | `core/AgentMessageReceiver.kt` | Android 独有 |
| - | `core/MessageQueueManager.kt` | Android 独有 |
| - | `core/KeyedAsyncQueue.kt` | Android 独有 |
| - | `core/ForegroundService.kt` | Android 独有 |

### Cron

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/cron/` | `cron/` | |
| `src/cron/service.ts` | `cron/CronService.kt` | ✅ |
| `src/cron/store.ts` | `cron/CronStore.kt` | ✅ |
| `src/cron/types.ts` | `cron/CronTypes.kt` | ✅ |
| `src/cron/schedule.ts` | `cron/CronScheduleParser.kt` | ✅ |
| `src/cron/parse.ts` | `cron/CronScheduleParser.kt` | ✅ |
| `src/cron/run-log.ts` | `cron/CronRunLog.kt` | ✅ |
| `src/cron/delivery.ts` | - | ❌ |

### Logging

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/logger.ts` | `logging/Log.kt` | ✅ |
| `src/logging/` | `logging/FileLogger.kt` | ✅ |

### Utils

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/utils/` | `util/` | |
| `src/utils.ts` | `util/` (多个文件) | ✅ |

---

## Tools 文件映射

### 基础 Tool 接口

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/openclaw-tools.ts` | `agent/tools/ToolRegistry.kt` + `AndroidToolRegistry.kt` | ✅ |
| `src/agents/pi-tools.ts` | `agent/tools/ToolCallDispatcher.kt` | ✅ |
| - | `agent/tools/Tool.kt` | Android 独有接口 |
| - | `agent/tools/Skill.kt` | Android 独有接口 |

### 文件操作

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/pi-tools.read.ts` | `agent/tools/ReadFileTool.kt` | ✅ |
| `src/agents/apply-patch.ts` | `agent/tools/EditFileTool.kt` | ✅ |
| `src/agents/apply-patch.ts` | `agent/tools/WriteFileTool.kt` | ✅ |
| - | `agent/tools/ListDirTool.kt` | ✅ (OpenClaw 内联) |

### Shell 执行

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/bash-tools.exec.ts` | `agent/tools/ExecTool.kt` | ✅ |
| `src/agents/bash-tools.ts` | `agent/tools/ExecFacadeTool.kt` | ✅ |
| `src/agents/bash-tools.process.ts` | - | ❌ (进程管理) |

### 浏览器

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/browser/` | `extensions/BrowserForClaw/` + `browser/` | |
| `src/browser/client.ts` | `browser/BrowserToolClient.kt` | ✅ |
| `src/browser/pw-session.ts` | - | ❌ (Playwright 不适用) |
| `src/browser/pw-tools-core.ts` | - | ❌ (Playwright 不适用) |
| `src/agents/tools/browser-tool.ts` | `agent/tools/device/DeviceTool.kt` | ✅ Android 适配 |

### 网络

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/tools/web-fetch.ts` | `agent/tools/WebFetchTool.kt` | ✅ |
| `src/agents/tools/web-search.ts` | `agent/tools/WebSearchTool.kt` | ✅ |
| `src/agents/tools/web-tools.ts` | - | ❌ (汇总注册) |

### 记忆工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/tools/memory-tool.ts` | `agent/tools/memory/MemorySearchSkill.kt` + `MemoryGetSkill.kt` | ✅ |

### 消息/会话工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/tools/message-tool.ts` | `agent/tools/FeishuSendImageSkill.kt` | ⚠️ 部分 |
| `src/agents/tools/sessions-spawn-tool.ts` | - | ❌ |
| `src/agents/tools/sessions-send-tool.ts` | - | ❌ |
| `src/agents/tools/sessions-list-tool.ts` | - | ❌ |
| `src/agents/tools/sessions-history-tool.ts` | - | ❌ |
| `src/agents/tools/sessions-yield-tool.ts` | - | ❌ |
| `src/agents/tools/session-status-tool.ts` | - | ❌ |
| `src/agents/tools/subagents-tool.ts` | - | ❌ |
| `src/agents/tools/agents-list-tool.ts` | - | ❌ |

### 多媒体工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/tools/pdf-tool.ts` | - | ❌ |
| `src/agents/tools/tts-tool.ts` | - | ❌ |
| `src/agents/tools/canvas-tool.ts` | - | ❌ |
| `src/agents/tools/image-tool.ts` | - | ❌ |
| `src/agents/tools/image-generate-tool.ts` | - | ❌ |

### 其他工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| `src/agents/tools/cron-tool.ts` | - | ❌ (通过 RPC 操作) |
| `src/agents/tools/gateway-tool.ts` | - | ❌ |
| `src/agents/tools/nodes-tool.ts` | - | ❌ |

### 系统工具

| OpenClaw | AndroidForClaw | 状态 |
|----------|----------------|------|
| - | `agent/tools/WaitSkill.kt` | ✅ |
| - | `agent/tools/StopSkill.kt` | ✅ |
| - | `agent/tools/LogSkill.kt` | ✅ |
| - | `agent/tools/ConfigGetTool.kt` | Android 独有 |
| - | `agent/tools/ConfigSetTool.kt` | Android 独有 |
| - | `agent/tools/SkillsHubTool.kt` | Android 独有 (对齐 ClawHub) |

### Android 特有工具 (无 OpenClaw 对应)

| AndroidForClaw | 说明 |
|----------------|------|
| `agent/tools/device/DeviceTool.kt` | 统一设备工具 (对齐 browser-tool 架构) |
| `agent/tools/ScreenshotSkill.kt` | 截图 |
| `agent/tools/GetViewTreeSkill.kt` | UI 树 |
| `agent/tools/TapSkill.kt` | 点击 (遗留,DeviceTool 替代) |
| `agent/tools/SwipeSkill.kt` | 滑动 (遗留,DeviceTool 替代) |
| `agent/tools/TypeSkill.kt` | 输入 |
| `agent/tools/LongPressSkill.kt` | 长按 (遗留,DeviceTool 替代) |
| `agent/tools/HomeSkill.kt` | Home 键 |
| `agent/tools/BackSkill.kt` | 返回键 |
| `agent/tools/OpenAppSkill.kt` | 打开应用 |
| `agent/tools/ListInstalledAppsSkill.kt` | 列出应用 |
| `agent/tools/InstallAppSkill.kt` | 安装应用 |
| `agent/tools/StartActivityTool.kt` | 启动 Activity |
| `agent/tools/ClawImeInputSkill.kt` | 自定义输入法输入 |

---

## Skills 目录映射

| OpenClaw | AndroidForClaw |
|----------|----------------|
| `skills/` | `app/src/main/assets/skills/` |
| `skills/core/` | `assets/skills/core/` |
| `skills/browser/` | - |
| `skills/coding/` | - |
| `~/.openclaw/workspace/skills/` | `/sdcard/.androidforclaw/workspace/skills/` |
| `~/.openclaw/.skills/` | `/sdcard/.androidforclaw/.skills/` |

---

## 存储映射

| OpenClaw | AndroidForClaw |
|----------|----------------|
| `~/.openclaw/` | `/sdcard/.androidforclaw/` |
| `~/.openclaw/workspace/` | `/sdcard/.androidforclaw/workspace/` |
| `~/.openclaw/config/` | `/sdcard/.androidforclaw/config/` |
| `~/.openclaw/agents/main/sessions/` | `/sdcard/.androidforclaw/agents/main/sessions/` |
| `~/.openclaw/memory/` | `/sdcard/.androidforclaw/workspace/memory/` |

---

## UI / Service 映射 (Android 特有)

### Services

| AndroidForClaw | 说明 |
|----------------|------|
| `service/ClawIME.java` | 自定义输入法 |
| `service/ClawIMEManager.kt` | 输入法管理 |
| `service/WebService.kt` | Web 服务 (遗留) |

### Accessibility

| AndroidForClaw | 说明 |
|----------------|------|
| `accessibility/AccessibilityProxy.kt` | 无障碍代理 |
| `accessibility/AccessibilityHealthMonitor.kt` | 健康监控 |

### UI 层

| AndroidForClaw | 说明 |
|----------------|------|
| `ui/activity/` | Activity 层 |
| `ui/adapter/` | Adapter 层 |
| `ui/compose/` | Compose UI |
| `ui/float/` | 悬浮窗 |
| `ui/session/` | 会话 UI |
| `ui/view/` | 视图层 |
| `ui/viewmodel/` | ViewModel 层 |

### Extensions

| AndroidForClaw | 说明 |
|----------------|------|
| `extensions/feishu/` | 飞书集成 |
| `extensions/discord/` | Discord 集成 |
| `extensions/observer/` | Observer APK |
| `extensions/BrowserForClaw/` | BClaw 浏览器 |
| `extensions/telegram/` | Telegram 框架 |
| `extensions/slack/` | Slack 框架 |
| `extensions/signal/` | Signal 框架 |
| `extensions/whatsapp/` | WhatsApp 框架 |

---

## 配置文件映射

| OpenClaw | AndroidForClaw |
|----------|----------------|
| `package.json` | `app/build.gradle` / `settings.gradle` / `build.gradle` |
| `tsconfig.json` | - |
| `.env.example` | - |
| `docker-compose.yml` | - |

### 文档

| OpenClaw | AndroidForClaw |
|----------|----------------|
| `README.md` | `README.md` |
| `AGENTS.md` (= CLAUDE.md) | `CLAUDE.md` |
| `CONTRIBUTING.md` | `CONTRIBUTING.md` |
| `SECURITY.md` | `SECURITY.md` |
| `VISION.md` | - |
| - | `ARCHITECTURE.md` |
| - | `MAPPING.md` |

---

## OpenClaw 有但 AndroidForClaw 缺失的重要组件

### 协议与集成

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/acp/` | ACP (Agent Client Protocol) | P1 | - |
| `src/plugin-sdk/` | 插件 SDK | P2 | - |
| `src/hooks/` | 生命周期钩子 | P2 | - |
| `src/context-engine/` | Context Engine (注册/委托) | P2 | - |

### 系统功能

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/cron/` | 定时任务 | P1 | ✅ `cron/` |
| `src/daemon/` | 守护进程 | P1 | ✅ Android Service 替代 |
| `src/wizard/` | 配置向导 | P2 | - |
| `src/pairing/` | **设备配对** | **P0** | ❌ **待实现** |
| `src/security/` | **安全管理** | **P0** | 🟡 `TokenAuth.kt` (15%) |
| `src/secrets/` | 密钥管理 | P1 | - |

### 安全功能详细映射

| OpenClaw Security | AndroidForClaw | 状态 | 优先级 |
|------------------|----------------|------|--------|
| `security/audit.ts` | - | ❌ | P2 |
| `security/dangerous-tools.ts` | `ExecTool.kt` (部分) | 🟡 15% | P1 |
| `security/external-content.ts` | - | ❌ | **P0** |
| `security/dm-policy-shared.ts` | - | ❌ | **P0** |
| `security/skill-scanner.ts` | - | ❌ | P1 |
| `security/safe-regex.ts` | - | ❌ | P2 |
| `security/dangerous-config-flags.ts` | - | ❌ | P2 |
| `pairing/pairing-store.ts` | - | ❌ | **P0** |
| `pairing/setup-code.ts` | - | ❌ | **P0** |
| `gateway/auth.ts` | ✅ `TokenAuth.kt` | ✅ 完整 | - |

### 媒体与理解

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/media/` | 媒体处理 | P2 | - |
| `src/media-understanding/` | 媒体理解 | P2 | - |
| `src/link-understanding/` | 链接理解 | P3 | - |
| `src/tts/` | 文字转语音 | P2 | - |
| `src/image-generation/` | 图片生成 | P2 | - |

### 基础设施

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/infra/` | 基础设施工具 | P1 | `util/` (部分) |
| `src/logging/` | 日志系统 | P1 | `logging/FileLogger.kt` |
| `src/routing/` | 路由系统 | P1 | - |
| `src/process/` | 进程管理 | P2 | - |
| `src/shared/` | 共享代码 | P2 | - |
| `src/bindings/` | 绑定记录 | P2 | - |
| `src/plugins/` | 插件打包 | P2 | - |

### 渠道 (未完整实现)

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/channels/` (完整插件架构) | 统一渠道 | P1 | ✅ 部分 |
| `src/whatsapp/` | WhatsApp | P2 | 框架 |
| `src/line/` | LINE | P3 | - |
| `src/web-search/` | Web 搜索运行时 | P2 | ✅ 内联 |

### UI 与交互

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/tui/` | 终端 UI | P3 | - (不适用) |
| `src/terminal/` | 终端集成 | P3 | - (不适用) |
| `src/canvas-host/` | Canvas 主机 | P3 | - |
| `src/interactive/` | 交互载荷 | P3 | - |

### 开发工具

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/test-helpers/` | 测试辅助 | P2 | `app/src/test/` (部分) |
| `src/test-utils/` | 测试工具 | P2 | - |
| `src/compat/` | 兼容层 | P3 | - |

### 其他

| OpenClaw | 说明 | 优先级 | AClaw 对应 |
|----------|------|--------|-----------|
| `src/auto-reply/` | 自动回复 | P2 | - |
| `src/markdown/` | Markdown 处理 | P2 | - |
| `src/i18n/` | 国际化 | P3 | - |
| `src/node-host/` | Node 主机 | P3 | - (不适用) |

### 优先级说明

- **P0**: 核心功能,必须实现
- **P1**: 重要功能,强烈建议实现
- **P2**: 有用功能,可选实现
- **P3**: 低优先级或平台不适用

---

## 快速查找

### 从 OpenClaw 找 AndroidForClaw

**示例 1**: `src/agents/agent-command.ts`
- → `agent/loop/AgentLoop.kt`

**示例 2**: `src/agents/models-config.ts`
- → `config/ModelConfig.kt` + `config/ProviderRegistry.kt`

**示例 3**: `src/agents/pi-tools.read.ts`
- → `agent/tools/ReadFileTool.kt`

### 从 AndroidForClaw 找 OpenClaw

**示例 1**: `agent/loop/AgentLoop.kt`
- → `src/agents/agent-command.ts`

**示例 2**: `config/ConfigLoader.kt`
- → `src/config/io.ts`

**示例 3**: `agent/tools/ScreenshotSkill.kt`
- → 无对应 (Android 特有)

---

## 符号说明

| 符号 | 含义 |
|------|------|
| ✅ | 已对齐 |
| ⚠️ | 部分对齐 |
| ❌ | 未实现 |
| `path/to/file` | 文件路径 |
| `path/to/dir/` | 目录路径 |
| `-` | 无对应实现 |
