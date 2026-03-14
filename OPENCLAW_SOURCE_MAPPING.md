# AndroidForClaw ↔ OpenClaw 源码映射

> 说明：本文件记录 AndroidForClaw 核心手写源码与同级 `../openclaw/` 项目的主要参照关系。
> 目标：方便后续持续对齐 OpenClaw 架构与实现。

## 核心映射（第一批）

| AndroidForClaw 文件 | OpenClaw 参照路径 | 说明 |
|---|---|---|
| `app/src/main/java/com/xiaomo/androidforclaw/core/MainEntryNew.kt` | `openclaw/src/agents/*`, `openclaw/src/gateway/*` | 主执行入口、Agent 调度、消息流转 |
| `app/src/main/java/com/xiaomo/androidforclaw/core/MyApplication.kt` | `openclaw/src/gateway/*`, `openclaw/src/channels/*` | 应用初始化、通道启动、全局生命周期 |
| `app/src/main/java/com/xiaomo/androidforclaw/config/ConfigLoader.kt` | `openclaw/src/config/*`, `openclaw/docs/gateway/configuration-reference.md` | 配置读取、解析、落盘 |
| `app/src/main/java/com/xiaomo/androidforclaw/config/OpenClawConfig.kt` | `openclaw/src/config/*`, `openclaw/types/*.d.ts` | OpenClaw 配置数据结构 |
| `app/src/main/java/com/xiaomo/androidforclaw/config/ProviderRegistry.kt` | `openclaw/src/agents/*`, `openclaw/docs/providers/openai.md` | Provider 目录与默认模型能力 |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/UnifiedLLMProvider.kt` | `openclaw/src/agents/pi-embedded-runner/*`, `openclaw/src/agents/model-*` | LLM 请求拼装、模型调用适配 |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ContextBuilder.kt` | `openclaw/src/agents/*`, `openclaw/src/config/*` | system prompt、tool/skill 上下文构造 |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/loop/AgentLoop.kt` | `openclaw/src/agents/*` | Agent loop、工具调用、迭代流程 |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillsLoader.kt` | `openclaw/src/skills/*` | skill 发现、加载、缓存 |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ToolRegistry.kt` | `openclaw/src/agents/tools/*`, `openclaw/src/gateway/*` | 工具注册与暴露 |

| `app/src/main/java/com/xiaomo/androidforclaw/core/AgentMessageReceiver.kt` | `../openclaw/src/channels/*` | AndroidForClaw adaptation: bridge inbound external messages into app agent flow. |
| `app/src/main/java/com/xiaomo/androidforclaw/config/BuiltInKeyProvider.kt` | `../openclaw/src/config/*` | AndroidForClaw adaptation: built-in/provider key resolution for local Android runtime. |
| `app/src/main/java/com/xiaomo/androidforclaw/config/ModelConfig.kt` | `../openclaw/src/agents/*, ../openclaw/types/*.d.ts` | AndroidForClaw adaptation: provider/model config structures. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/ApiAdapter.kt` | `../openclaw/src/agents/pi-embedded-runner/*` | AndroidForClaw adaptation: provider auth/header/body request shaping. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/LegacyRepository.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: legacy model/provider compatibility bridge. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillParser.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: parse SKILL.md metadata and requirements. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillMetadata.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: in-app skill metadata model. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillInstaller.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: install and manage packaged skills. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ReadFileTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: low-level file read tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/WriteFileTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: low-level file write tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/EditFileTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: surgical file edit tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/WebFetchTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: web fetch tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ConfigGetTool.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: explicit config read tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ConfigSetTool.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: explicit config write tool. |

| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/JavaScriptTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: JavaScript execution tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ListDirTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: directory listing tool. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/Tool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: common agent tool interface. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/Skill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: common skill execution result contract. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ContextManager.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: manage context growth and recovery. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/MessageCompactor.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: compact prior conversation context. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ToolResultContextGuard.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: bound tool result size within context limits. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ToolResultTruncator.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: truncate tool outputs before prompt injection. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ContextWindowGuard.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: context budget guard. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/memory/ContextCompressor.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: compress message history to fit context windows. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/memory/MemoryManager.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: manage local memory lifecycle and summarization. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/memory/TokenEstimator.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: estimate token usage for Android agent context. |

| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/BrowserForClawSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser-related packaged skill integration. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserGetContentSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser content extraction skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserWaitSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser wait skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserClickSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser click skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserGenericSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: generic browser action skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserTypeSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser typing skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/browser/BrowserNavigateSkill.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: browser navigation skill. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillStatusBuilder.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: build skill status/introspection responses. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillDocument.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: loaded skill document model. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/ClawHubClient.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: remote skill hub client. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillLockManager.kt` | `../openclaw/src/skills/*` | AndroidForClaw adaptation: serialized skill install/update locking. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/loop/ToolLoopDetection.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: detect repetitive tool loops. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/session/HistorySanitizer.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: sanitize history before prompt submission. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/session/SessionManager.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: persist and restore agent sessions on Android. |

| `app/src/main/java/com/xiaomo/androidforclaw/core/AndroidForClawInit.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: application init sequence and startup glue. |
| `app/src/main/java/com/xiaomo/androidforclaw/core/ForegroundService.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: keep app/runtime alive in Android foreground service mode. |
| `app/src/main/java/com/xiaomo/androidforclaw/core/MessageQueueManager.kt` | `../openclaw/src/channels/*` | AndroidForClaw adaptation: queue and dispatch inbound/outbound channel messages. |
| `app/src/main/java/com/xiaomo/androidforclaw/core/KeyedAsyncQueue.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: serialized keyed async work queue. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/LegacyProviderOpenAI.kt` | `../openclaw/src/agents/model-*` | AndroidForClaw adaptation: legacy OpenAI provider compatibility layer. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/LegacyProviderAnthropic.kt` | `../openclaw/src/agents/model-*` | AndroidForClaw adaptation: legacy Anthropic provider compatibility layer. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/LLMException.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: unify LLM/provider failure reporting. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/AnthropicModels.kt` | `../openclaw/docs/providers/*` | AndroidForClaw adaptation: Anthropic model catalog constants. |
| `app/src/main/java/com/xiaomo/androidforclaw/config/FeishuConfigAdapter.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: bridge Feishu config into app channel model. |
| `app/src/main/java/com/xiaomo/androidforclaw/config/ConfigBackupManager.kt` | `../openclaw/src/config/*` | AndroidForClaw adaptation: config backup/restore helpers. |

## 自动补全映射（批量）

| `app/src/main/java/com/xiaomo/androidforclaw/agent/Prompt.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: agent runtime support. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/StartActivityTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ExecTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/FeishuToolAdapter.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/LongPressSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/FeishuSendImageSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ListInstalledAppsSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/TermuxBridgeTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/LogSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/AndroidToolRegistry.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/BackSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/AdbImeInputSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/SwipeSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/JavaScriptExecutorTool.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/ScreenshotSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/HomeSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/OpenAppSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/TypeSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/GetViewTreeSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/WaitSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/InstallAppSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/StopSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/TapSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/context/ContextErrors.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: agent context construction and budget control. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/functions/FunctionExecutor.kt` | `../openclaw/src/agents/*` | AndroidForClaw adaptation: function/tool execution bridge. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/memory/MemoryGetSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/agent/tools/memory/MemorySearchSkill.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: agent tool implementation. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/LegacyModels.kt` | `../openclaw/src/agents/model-*` | AndroidForClaw adaptation: provider dispatch and compatibility. |
| `app/src/main/java/com/xiaomo/androidforclaw/providers/llm/Models.kt` | `../openclaw/src/agents/model-*` | AndroidForClaw adaptation: LLM request/response model definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuChannel.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuProbe.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuWebSocketHandler.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuConfig.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuDirectory.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuClient.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuAccounts.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/FeishuWebhookHandler.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/FeishuToolBase.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/FeishuToolRegistry.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/messaging/FeishuSender.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu messaging transport. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/messaging/FeishuMention.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu messaging transport. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/messaging/FeishuMedia.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu messaging transport. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/messaging/FeishuReactions.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu messaging transport. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/messaging/FeishuTyping.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu messaging transport. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/policy/FeishuPolicy.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/session/FeishuDedup.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/session/FeishuHistoryManager.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/session/FeishuSessionManager.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel runtime. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/bitable/FeishuBitableTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/chat/FeishuChatTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/perm/FeishuPermTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/wiki/FeishuWikiTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/urgent/FeishuUrgentTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/task/FeishuTaskTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/doc/FeishuDocTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/drive/FeishuDriveTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/media/FeishuImageUploadTool.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/feishu/src/main/java/com/xiaomo/feishu/tools/media/FeishuMediaTools.kt` | `../openclaw/src/channels/feishu/*` | AndroidForClaw adaptation: Feishu channel tool definitions. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/ObserverForegroundService.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: observer permission and projection flow. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/PermissionActivity.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: observer permission and projection flow. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/MediaProjectionHelper.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: observer permission and projection flow. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/service/AccessibilityBinder.java` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: in-app accessibility/observer service layer. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/service/AccessibilityBinderService.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: in-app accessibility/observer service layer. |
| `extensions/observer/src/main/java/com/xiaomo/androidforclaw/accessibility/service/PhoneAccessibilityService.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: in-app accessibility/observer service layer. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SelfControlService.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SelfControlReceiver.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/NavigationSkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SelfControlRegistry.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/ServiceControlSkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SkillInterface.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SelfControlProvider.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/SelfControlDemoActivity.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/InternalSelfControlSkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/ADBSelfControlSkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/ConfigSkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `self-control/src/main/java/com/xiaomo/androidforclaw/selfcontrol/LogQuerySkill.kt` | `../openclaw/src/gateway/*` | AndroidForClaw adaptation: self-control runtime support. |
| `quickjs-executor/src/main/java/com/xiaomo/quickjs/QuickJSBridge.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: QuickJS execution support. |
| `quickjs-executor/src/main/java/com/xiaomo/quickjs/QuickJSExecutor.kt` | `../openclaw/src/agents/tools/*` | AndroidForClaw adaptation: QuickJS execution support. |
