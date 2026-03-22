# AndroidForClaw 独有类梳理

> 更新时间: 2026-03-20
> 以下类在 OpenClaw 中无直接对应实现,为 Android 平台特有或适配层。

## 分类汇总

| 分类 | 数量 | 说明 |
|------|------|------|
| Android 平台必需 | 35 | 无障碍、Service、UI、IME 等 Android 基础设施 |
| Android 适配层 | 12 | 替代 OpenClaw 桌面端方案的移动端实现 |
| 遗留代码 | 9 | 旧架构残留,可考虑移除 |
| 调试/测试辅助 | 8 | 开发期调试工具 |

---

## 1. Android 平台必需 (保留)

这些类是 Android 平台运行所必需的,OpenClaw 作为桌面 Node.js 应用不需要这些。

### Accessibility (无障碍服务)

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `accessibility/AccessibilityProxy.kt` | AccessibilityProxy | 无障碍服务代理,提供 tap/swipe/dumpViewTree 等设备控制 | **必须保留** — 核心能力 |
| `accessibility/AccessibilityHealthMonitor.kt` | AccessibilityHealthMonitor | 每 5 秒监控无障碍服务健康状态 | **必须保留** — 防止服务异常 |

### Service / 系统服务

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `core/ForegroundService.kt` | ForegroundService | 前台服务保活,START_STICKY 重启策略 | **必须保留** — 防止进程被杀 |
| `core/AgentMessageReceiver.kt` | AgentMessageReceiver | BroadcastReceiver,接收 Gateway/ADB 的执行请求 | **必须保留** — 消息入口 |
| `core/MessageQueueManager.kt` | MessageQueueManager | 消息队列,支持 interrupt/steer/followup/collect/queue 模式 | **必须保留** — 消息调度 |
| `core/KeyedAsyncQueue.kt` | KeyedAsyncQueue | 按 key 序列化异步任务队列 | **必须保留** — 对齐 OpenClaw keyed-async-queue |
| `service/ClawIME.java` | ClawIME | 自定义输入法服务,接收广播注入文本到焦点输入框 | **必须保留** — 文本输入核心能力 |
| `service/ClawIMEManager.kt` | ClawIMEManager | ClawIME 直接方法调用管理 | **必须保留** — 配套 ClawIME |

### UI 层

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `ui/activity/MainActivity.kt` | MainActivity | 主界面 | **必须保留** |
| `ui/activity/MainActivityCompose.kt` | MainActivityCompose | Compose 版主界面 | **必须保留** |
| `ui/activity/ModelSetupActivity.kt` | ModelSetupActivity | 首次运行 Provider/Model 设置向导 | **必须保留** |
| `ui/activity/ModelConfigActivity.kt` | ModelConfigActivity | Model 配置编辑 | **必须保留** |
| `ui/activity/ConfigActivity.kt` | ConfigActivity | 通用配置编辑 | **必须保留** |
| `ui/activity/PermissionsActivity.kt` | PermissionsActivity | 权限引导 (无障碍/悬浮窗等) | **必须保留** |
| `ui/activity/ResultActivity.kt` | ResultActivity | 任务结果展示 | **必须保留** |
| `ui/activity/SkillsActivity.kt` | SkillsActivity | Skills 管理 UI | **必须保留** |
| `ui/activity/TermuxSetupActivity.kt` | TermuxSetupActivity | Termux 集成设置 | **必须保留** |
| `ui/activity/ChannelListActivity.kt` | ChannelListActivity | 渠道列表 | **必须保留** |
| `ui/activity/FeishuChannelActivity.kt` | FeishuChannelActivity | 飞书渠道配置 | **必须保留** |
| `ui/activity/DiscordChannelActivity.kt` | DiscordChannelActivity | Discord 渠道配置 | **必须保留** |
| `ui/activity/TelegramChannelActivity.kt` | TelegramChannelActivity | Telegram 渠道配置 | **保留** (框架) |
| `ui/activity/SlackChannelActivity.kt` | SlackChannelActivity | Slack 渠道配置 | **保留** (框架) |
| `ui/activity/SignalChannelActivity.kt` | SignalChannelActivity | Signal 渠道配置 | **保留** (框架) |
| `ui/activity/WhatsAppChannelActivity.kt` | WhatsAppChannelActivity | WhatsApp 渠道配置 | **保留** (框架) |
| `ui/compose/ChatScreen.kt` | ChatScreen | Compose 聊天界面 | **必须保留** |
| `ui/view/ChatWindowView.kt` | ChatWindowView | 聊天窗口自定义 View | **必须保留** |
| `ui/viewmodel/ChatViewModel.kt` | ChatViewModel | 聊天 ViewModel | **必须保留** |
| `ui/float/SessionFloatWindow.kt` | SessionFloatWindow | 悬浮窗会话显示 | **必须保留** |
| `ui/session/SessionManager.kt` | SessionManager (UI) | UI 层会话管理 | **必须保留** |
| `ui/adapter/ResultRecyclerAdapter.kt` | ResultRecyclerAdapter | 结果列表 Adapter | **必须保留** |

### Util / 工具类

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `util/AppConstants.kt` | AppConstants | 应用常量 | **必须保留** |
| `util/BuildTree.kt` | BuildTree | 构建无障碍节点树 | **必须保留** — 核心能力 |
| `util/BuildTreeNoBarrier.kt` | BuildTreeNoBarrier | 无障碍树 (无 barrier 节点) | **必须保留** — 核心能力 |
| `util/MediaProjectionHelper.kt` | MediaProjectionHelper | MediaProjection 截屏 | **必须保留** — 截图能力 |
| `util/WakeLockManager.java` | WakeLockManager | PARTIAL_WAKE_LOCK 防 Doze 断网 | **必须保留** — 飞书等长连接需要 |
| `util/GlobalExceptionHandler.kt` | GlobalExceptionHandler | 全局异常捕获 | **必须保留** |
| `util/AppInfoScanner.kt` | AppInfoScanner | 扫描已安装应用信息 | **必须保留** — Agent 需要 |
| `util/LocaleHelper.kt` | LocaleHelper | 语言/locale 辅助 | **必须保留** |
| `util/MMKVKeys.kt` | MMKVKeys | MMKV 键值常量 | **必须保留** |
| `util/SPHelper.kt` | SPHelper | SharedPreferences 辅助 | **必须保留** |
| `util/InstallManager.java` | InstallManager | APK 安装辅助 | **必须保留** |

### 其他平台必需

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `core/MyApplication.kt` | MyApplication | Application 启动入口 | **必须保留** |
| `updater/AppUpdater.kt` | AppUpdater | GitHub Releases 自动更新 | **必须保留** |
| `updater/RestartWorker.kt` | RestartWorker | 更新后 WorkManager 重启 | **必须保留** |
| `workspace/WorkspaceInitializer.kt` | WorkspaceInitializer | 初始化 /sdcard/.androidforclaw/ 目录结构 | **必须保留** — 对齐 OpenClaw workspace |
| `ext/AppExt.kt` | - | App 扩展函数 | **必须保留** |
| `ext/ViewModelExpand.kt` | - | ViewModel 扩展函数 | **必须保留** |
| `config/BuiltInKeyProvider.kt` | BuiltInKeyProvider | 内置 AES-GCM 加密 OpenRouter Key | **必须保留** — 开箱即用体验 |
| `config/ConfigBackupManager.kt` | ConfigBackupManager | 配置自动备份/恢复 | **必须保留** — 容错 |
| `config/FeishuConfigAdapter.kt` | FeishuConfigAdapter | OpenClawConfig → FeishuConfig 适配 | **必须保留** — 飞书模块桥接 |

---

## 2. Android 适配层 (保留)

这些类替代了 OpenClaw 桌面端的对应方案,是移动端的等价实现。

| 文件 | 类名 | 功能 | 对应 OpenClaw | 保留 |
|------|------|------|-------------|------|
| `agent/tools/device/DeviceTool.kt` | DeviceTool | 统一设备工具 (snapshot/act/open) | `browser-tool.ts` (Playwright) | **必须保留** — 核心 |
| `agent/tools/device/DeviceToolSkillAdapter.kt` | DeviceToolSkillAdapter | Tool→Skill 适配器 | - | **必须保留** |
| `agent/tools/device/RefManager.kt` | RefManager | Playwright-style ref ID 映射 | `pw-role-snapshot.ts` | **必须保留** |
| `agent/tools/device/SnapshotBuilder.kt` | SnapshotBuilder | Accessibility→RefNode 转换 | `pw-role-snapshot.ts` | **必须保留** |
| `agent/tools/device/SnapshotFormatter.kt` | SnapshotFormatter | 紧凑格式输出 | `pw-tools-core.snapshot.ts` | **必须保留** |
| `agent/tools/ExecFacadeTool.kt` | ExecFacadeTool | exec 门面，路由到内嵌 Termux 或内置 Shell | `bash-tools.ts` | **必须保留** |
| `agent/tools/ClawImeInputSkill.kt` | ClawImeInputSkill | 通过 ClawIME 输入文本 | - | **必须保留** — 文本输入 |
| `agent/tools/FeishuSendImageSkill.kt` | FeishuSendImageSkill | 飞书发送图片 | `message-tool.ts` (部分) | **必须保留** |
| `agent/tools/FeishuToolAdapter.kt` | FeishuToolAdapter | 飞书工具适配 | - | **必须保留** |

---

## 3. 遗留代码 (建议清理)

这些类属于旧架构残留,已被新实现替代或不再使用。

| 文件 | 类名 | 功能 | 建议 | 理由 |
|------|------|------|------|------|
| `providers/LegacyRepository.kt` | LegacyRepository | 旧版 LLM 接口包装 | **可移除** | 已被 UnifiedLLMProvider 替代 |
| `providers/LegacyProviderOpenAI.kt` | LegacyProviderOpenAI | 旧版 OpenAI 格式 Provider | **可移除** | 已被 UnifiedLLMProvider 替代 |
| `providers/LegacyProviderAnthropic.kt` | LegacyProviderAnthropic | 旧版 Anthropic 格式 Provider | **可移除** | 已被 UnifiedLLMProvider 替代 |
| `providers/LegacyModels.kt` | LegacyModels | 旧版消息/响应数据模型 | **可移除** | 已被 providers/llm/Models.kt 替代 |
| `DeviceController.kt` | DeviceController | 旧版设备控制门面 | **可移除** | 已被 DeviceTool + AccessibilityProxy 替代 |
| `ui/adapter/TodoListAdapter.kt` | TodoListAdapter | 已废弃 (代码注释: deprecated) | **可移除** | OperationStep 不再存在 |
| `service/WebService.kt` | WebService | 占位/禁用的 Web 服务 | **可移除** | 无实际功能 |
| `data/network/DifyData.kt` | DifyData | Dify AI 平台数据模型 | **可移除** | 已不使用 Dify |
| `data/model/ResultBean.kt` | ResultBean | 旧版结果数据 Bean | **评估后移除** | 检查是否还有引用 |

---

## 4. 调试/测试辅助 (保留,生产可选)

这些类用于开发期调试,生产环境可考虑通过 build variant 排除。

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `debug/AutoTestConfig.kt` | AutoTestConfig | 自动测试配置 | 保留 (debug) |
| `debug/ContextBuilderTestRunner.kt` | ContextBuilderTestRunner | ContextBuilder 应用内测试 | 保留 (debug) |
| `debug/SkillParserTestRunner.kt` | SkillParserTestRunner | SkillParser 应用内测试 | 保留 (debug) |
| `debug/SkillsLoaderTestRunner.kt` | SkillsLoaderTestRunner | SkillsLoader 应用内测试 | 保留 (debug) |
| `debug/TestLogAdapter.kt` | TestLogAdapter | 测试日志展示 Adapter | 保留 (debug) |
| `debug/TestLogItem.kt` | TestLogItem | 测试日志项模型 | 保留 (debug) |
| `debug/test/FeishuConnectionTest.kt` | FeishuConnectionTest | 飞书连接测试 | 保留 (debug) |
| `debug/test/FeishuWebSocketDirectTest.kt` | FeishuWebSocketDirectTest | 飞书 WebSocket 直连测试 | 保留 (debug) |

---

## 5. 其他独有类 (保留)

| 文件 | 类名 | 功能 | 保留 |
|------|------|------|------|
| `agent/Prompt.kt` | Prompt | system + user prompt 容器 | **保留** |
| `agent/memory/TokenEstimator.kt` | TokenEstimator | chars/4 token 估算 | **保留** |
| `agent/memory/ContextCompressor.kt` | ContextCompressor | Context 压缩触发器 | **保留** |
| `agent/tools/ConfigGetTool.kt` | ConfigGetTool | 读取 openclaw.json 配置 | **保留** — Android 无 CLI |
| `agent/tools/ConfigSetTool.kt` | ConfigSetTool | 写入 openclaw.json 配置 | **保留** — Android 无 CLI |
| `agent/tools/SkillsHubTool.kt` | SkillsSearchTool + SkillsInstallTool | ClawHub 搜索/安装 agent 工具 | **保留** |
| `agent/skills/ClawHubClient.kt` | ClawHubClient | ClawHub API HTTP 客户端 | **保留** |
| `agent/skills/SkillInstaller.kt` | SkillInstaller | Skill 下载/安装/卸载 | **保留** |
| `agent/skills/SkillLockManager.kt` | SkillLockManager | .clawhub/lock.json 版本管理 | **保留** |
| `agent/skills/BrowserForClawSkill.kt` | BrowserForClawSkill | 浏览器控制 Skill 入口 | **保留** |
| `browser/BrowserToolClient.kt` | BrowserToolClient | BrowserForClaw HTTP 客户端 | **保留** |
| `browser/BrowserMcpAdapter.kt` | BrowserMcpAdapter | MCP→REST 适配器 | **保留** |
| `mcp/McpClient.kt` | McpClient | MCP JSON-RPC 客户端 | **保留** |
| `mcp/McpProtocol.kt` | McpProtocol | MCP 协议定义 | **保留** |
| `channel/ChannelDefinition.kt` | ChannelMeta | android-app 渠道定义 | **保留** |
| `channel/ChannelManager.kt` | ChannelManager | Android App Channel 管理 | **保留** |
| `data/model/TaskData.kt` | TaskData | 任务执行数据 | **保留** |
| `data/model/TaskDataManager.kt` | TaskDataManager | 任务数据管理 | **保留** |
| `data/model/ApkInfo.java` | ApkInfo | APK 信息模型 | **保留** |
| `util/ReasoningTagFilter.kt` | ReasoningTagFilter | `<think>` 标签过滤 | **保留** — 对齐 OpenClaw |
| `util/ReplyTagFilter.kt` | ReplyTagFilter | 回复标签过滤 | **保留** |
| `util/ChatBroadcastReceiver.kt` | ChatBroadcastReceiver | 聊天广播接收 | **保留** |
| `util/LayoutExceptionLogger.kt` | LayoutExceptionLogger | 布局异常日志 | **保留** |
| `util/ResultUtil.kt` | ResultUtil | 结果格式化 | **保留** |
| `providers/AnthropicModels.kt` | AnthropicRequest 等 | Anthropic Messages API 数据模型 | **保留** — ApiAdapter 需要 |

---

## 引用检查结果 (2026-03-20)

### 可立即移除 (无外部引用)

| 文件 | 引用情况 | 结论 |
|------|----------|------|
| `ui/adapter/TodoListAdapter.kt` | **零引用** — 无任何文件 import | **删除** |
| `data/network/DifyData.kt` | **零引用** — 无任何文件 import | **删除** |

### 需小改后移除

| 文件 | 引用情况 | 结论 |
|------|----------|------|
| `service/WebService.kt` | 仅 AndroidManifest.xml 注册,代码内 `onCreate` 直接 return | **删除文件 + 移除 Manifest 注册** |

### 仍有引用,暂不移除

| 文件 | 引用方 | 结论 |
|------|--------|------|
| `DeviceController.kt` | `GetViewTreeSkill.kt`、`DeviceTool.kt`、`ScreenshotSkill.kt`、`TypeSkill.kt` 共 4 处调用 `detectIcons`/`getScreenshot`/`inputText` | **暂保留** — 需先将调用迁移到 AccessibilityProxy 后再删 |
| `LegacyRepository.kt` | `ContextCompressor.kt` (构造参数)、`MyApplication.kt` (注释中引用) | **暂保留** — ContextCompressor 仍依赖 |
| `LegacyProviderOpenAI.kt` | `LegacyRepository.kt` 内部使用 | **暂保留** — 随 LegacyRepository 一起清理 |
| `LegacyProviderAnthropic.kt` | `LegacyRepository.kt` 内部使用 | **暂保留** — 随 LegacyRepository 一起清理 |
| `LegacyModels.kt` | `LegacyProviderOpenAI.kt`、`LegacyProviderAnthropic.kt` 使用 | **暂保留** — 随 Legacy* 一起清理 |
| `ResultBean.kt` | `ResultUtil.kt`、`ResultRecyclerAdapter.kt` 共 3 处使用 | **暂保留** — UI 结果展示仍在用 |

### 清理路线图

1. **Phase 1 (现在)**: 删除 `TodoListAdapter.kt`、`DifyData.kt`、`WebService.kt`
2. **Phase 2 (后续)**: 将 `DeviceController` 的 `detectIcons`/`getScreenshot`/`inputText` 迁移到 `AccessibilityProxy`，然后删除
3. **Phase 3 (后续)**: 将 `ContextCompressor` 改用 `UnifiedLLMProvider`，然后删除整个 `Legacy*` 系列 (4 个文件)
