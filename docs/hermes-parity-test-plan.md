# Hermes Feature-Parity 落地 + 测试用例文档

> **状态**: 2026-04-26 初版
> **范围**: HermesApp (Android) 对齐 Hermes Python agent 全量特性（除客户端无法实现的 RL 训练 / datagen pipeline）
> **用途**: 指导后续迭代。每一项落地完成后在对应"验收"勾上，测试补齐后 `--tests "XxxTest"` 跑全绿

---

## 0. 使用方法

- 每轮推进先看 §1 汇总表找"红/黄"项
- 新写代码前先看对应域的 §落地策略，避免踩 Android 特有坑
- 每个 stub/partial 项至少补齐测试矩阵里标 **[必做]** 的用例才算完工
- 测试用 `./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.xxxx.YyyTest"` 单类跑（CLAUDE.md §0.1 #6 —— 禁止全量）
- E2E 用 emulator + `ADB_DEVICE=emulator-5554 ./scripts/e2e/xxx.sh`

**状态标记**：
- 🟢 **live** 函数体完整
- 🟡 **partial** 有骨架但部分路径 stub / SDK 缺失
- 🔴 **stub** 基本空壳 / 返回默认值
- ⚫ **missing** 文件不存在

---

## 1. 汇总表

| 域 | 主要文件 LOC | 实现状态 | 测试状态 | 下一步优先级 |
|---|---|---|---|---|
| 1. Core Agent Loop | ~13K | 🟢 大部分 live，2 个 adapter partial | 🟢 高 | 低 |
| 2. Gateway Platforms | ~9K | 🟢 Feishu/Weixin/Telegram，🔴 10 个 stub 平台 | 🟡 Feishu/Weixin 深，其他平台 0 | **高** |
| 3. Gateway Infrastructure | ~6K | 🟢 全 live | 🟡 缺 Preferences/Service/Controller | 中 |
| 4. Tools | ~18K | 🟢 大部分 live，🔴 McpTool/Browser/Cronjob/DelegateTool partial | 🟡 广但浅 | 中 |
| 5. Skills & Plugins | ~5K | 🟢 2/8 memory 已 ported，🔴 其他 6 家 missing | 🔴 零测试 | **高** |
| 6. State & Context | ~5K | 🟢 全 live | 🟡 缺压缩触发/溢出路径 | 中 |
| 7. Protocols (ACP / MCP) | ~3K | 🔴 ACP Server + MCP Tool 大量 stub，MCP Serve 缺 | 🔴 零测试 | 中 |
| 8. Cron / Safety / Profiles | ~3K | 🟢 Scheduler/Jobs/Safety live，🔴 CronjobTools handler stub | 🟡 Safety 高，Cron 浅 | 中 |

**三条 E2E 基线**（`scripts/e2e/`）都绿，不动：
- `test_api_config_e2e.sh` — 无工具 real-LLM
- `test_tool_call_e2e.sh` — 带工具 dispatch
- `test_builtin_key_e2e.sh` — 新用户内置 key

---

## 2. 域 1 — Core Agent Loop

### 现状

| 文件 | LOC | 状态 | 备注 |
|---|---|---|---|
| `hermes/AgentLoop.kt` | 515 | 🟢 | 主循环 + 工具 dispatch，1 TODO |
| `hermes/HermesState.kt` | 1770 | 🟢 | SQLite + FileChannel.lock |
| `hermes/TrajectoryCompressor.kt` | 1156 | 🟢 | |
| `agent/AnthropicAdapter.kt` | 1374 | 🟡 | 数据类 + OAuth 实，网络调用 app 注入 |
| `agent/BedrockAdapter.kt` | 889 | 🟡 | 同上，AWS SDK 调用外移 |
| `agent/CodexResponsesAdapter.kt` | 869 | 🟢 | 1 TODO |
| `agent/GeminiCloudcodeAdapter.kt` | 879 | 🟢 | 2 TODO |
| `agent/GeminiNativeAdapter.kt` | 941 | 🟢 | |
| `agent/ContextCompressor.kt` | 976 | 🟢 | |
| `agent/MemoryManager.kt` | 389 | 🟢 | |
| `agent/PromptCaching.kt` | 136 | 🟢 | |
| `agent/Insights.kt` | 1292 | 🟢 | |
| `agent/Redact.kt` | 353 | 🟢 | |
| `agent/transports/Anthropic.kt` | 140 | 🟡 | 4 TODO |

### 落地策略

- **多 LLM 适配器**：5 个适配器（Anthropic/Bedrock/Codex/Gemini×2）骨架已齐。Anthropic/Bedrock 的网络 dispatch 由 app 模块注入，**这是 Android 正确做法**（避免把 AWS SDK 打进 library）。Gradle 依赖 `com.amazonaws:aws-android-sdk-bedrockruntime` 只在 app build.gradle.kts 加。
- **Prompt Caching 不变量**：已 live，不用动。
- **Checkpoint 影子 git repo**：`tools/CheckpointManager.kt` 已 live 620 LOC；Android 下 git repo 放 `context.filesDir/checkpoints/.git/`，用 `jgit` 或 `ProcessBuilder("git", ...)` 走 Termux / busybox。**需要验证能否在 app 进程里真正写 git 对象**。
- **线程中断**：`tools/Interrupt.kt` 已 live，现有测试覆盖 OK。

### 测试矩阵

| 测试类 | 状态 | 必做 | 覆盖内容 |
|---|---|---|---|
| `AgentLoopDataTest` | ✅ 有 | | AgentResult / AssistantMessage.extractReasoning / ToolCall |
| `HermesAgentLoopBeforeNextTurnTest` | ✅ 有 | | beforeNextTurn hook |
| `HermesStateTest` | ✅ 有 | | set/get/merge/nested/persist |
| `AgentLoopMaxTurnsTest` | ❌ 无 | **[必]** | 达到 maxTurns 时终止；越界不抛异常 |
| `AgentLoopToolDispatchErrorTest` | ❌ 无 | **[必]** | tool throw → assistant 继续；多次 throw → 降级；超时 tool |
| `AgentLoopStreamingDeltasTest` | ❌ 无 | [选] | partial chunk 合并；reasoning delta 与 text delta 分离 |
| `AnthropicAdapterTest` | ❌ 无 | **[必]** | toolCalls JSON 结构 / system prompt 注入 / cache_control 点位 |
| `BedrockAdapterTest` | ❌ 无 | [选] | 凭证检测 / 地域路由（纯逻辑，不打 SDK） |
| `CodexResponsesAdapterTest` | ❌ 无 | **[必]** | 新协议 Response API 转 ChatCompletion 形状 |
| `GeminiNativeAdapterTest` | ❌ 无 | **[必]** | schema 转换（OpenAI → Gemini function_declarations） |
| `ContextCompressorTest` | ❌ 无 | **[必]** | 保 head + tail 预算；中段摘要调用；token 计算 |
| `MemoryManagerTest` | ❌ 无 | **[必]** | 单 provider + builtin 策略；schema 冲突检测 |
| `PromptCachingTest` | ❌ 无 | **[必]** | cache point 位置；system/tools 不变量检测 |
| `InsightsTest` | ❌ 无 | [选] | token/cost/tool 统计聚合 |
| `RedactTest` | ❌ 无 | **[必]** | 敏感信息识别（API key / phone / email） |
| `CheckpointManagerTest` | ✅ 有 | | turn 边界 checkpoint |
| `CheckpointManagerAndroidTest` (Robolectric) | ❌ 无 | **[必]** | 真实 filesDir 下创建 .git + commit + restore |

---

## 3. 域 2 — Gateway Platforms

### 现状

| 文件 | LOC | 状态 |
|---|---|---|
| `platforms/Base.kt` | 1283 | 🟢 |
| `platforms/Feishu.kt` | 2829 | 🟢 (WSS + cards + QR + normalize + send) |
| `platforms/FeishuComment.kt` | 730 | 🟢 |
| `platforms/Weixin.kt` | 1340 | 🟢 (AES-ECB + QR + chunked) |
| `platforms/Telegram.kt` | 724 | 🟢 |
| `platforms/TelegramNetwork.kt` | 605 | 🟢 |
| `platforms/Discord.kt` | 238 | 🟡 (REST live, WS receive 骨架只) |
| `platforms/Wecom.kt` | 125 | 🟢 (token + text) |
| `platforms/Dingtalk.kt` | 113 | 🟢 (corp token + text) |
| `platforms/Homeassistant.kt` | 125 | 🟡 (event polling 未接) |
| `platforms/qqbot/Adapter.kt` | 327 | 🟡 (helpers + connect stub) |
| `platforms/Whatsapp.kt` / `Slack.kt` / `Signal.kt` / `Matrix.kt` / `Mattermost.kt` / `Bluebubbles.kt` / `Email.kt` / `Sms.kt` / `Webhook.kt` / `ApiServer.kt` / `WecomCallback.kt` | 34-40 | 🔴 (10 个 stub) |

### 落地策略

- **Discord WS receive**：用 OkHttp WebSocket + Gateway intent 订阅 `MESSAGE_CREATE`，对齐 Python `discord.Client`。
- **Signal**：`signal-cli` 是 daemon，Android 上跑不起来。**改用 signal-rs HTTP bridge**（用户已在 Androidclaw 里做过，直接迁）。
- **WhatsApp**：wa-cli 是 Node.js。**改用 `https://github.com/whiskeysockets/baileys` 的 WebSocket 协议**，纯 OkHttp 可实现；或走 Androidclaw 的现有实现。
- **Slack**：Bolt SDK 是 JVM 的，可能可用；先 grep `com.slack:bolt`，不行就手写 Socket Mode WS。
- **Matrix / Mattermost**：纯 REST + long-poll，OkHttp 手写。
- **Email / SMS**：Android 下 Email 用 JavaMail（`com.sun.mail:android-mail`），SMS 用 `SmsManager`（需要权限）。Python 端实现简单，照抄即可。
- **BlueBubbles**：macOS iMessage 桥，Android 用不上 → **保持 stub**，函数体加一行 "not supported on Android"。
- **Webhook / ApiServer**：需要 inbound HTTP 服务器，**NanoHTTPD 已在依赖里**，可以实现。
- **WecomCallback**：WeCom 回调需要 HTTPS 反代，同 Webhook 场景，用 NanoHTTPD + Cloudflare tunnel 或放弃。

### 测试矩阵

| 测试类 | 状态 | 必做 | 覆盖内容 |
|---|---|---|---|
| `FeishuMarkdownTest` / `FeishuNormalizeTest` / `FeishuCoercionTest` / `FeishuConstantsTest` / `FeishuQrOnboardingTest` / `FeishuMediaTypeTest` / `FeishuCommentTest` / `FeishuCommentRulesTest` | ✅ 全有 | | |
| `WeixinMarkdownTest` / `WeixinDeliveryTest` / `WeixinPersistenceTest` / `WeixinCachesTest` / `WeixinHelpersTest` | ✅ 全有 | | |
| `WecomCryptoTest` | ✅ 有 | | AES 加密 |
| `TelegramNetworkTest` | ✅ 有 | | IP filter + rate limiter |
| qqbot × 3 | ✅ 有 | | Utils / Onboard / Adapter helpers |
| `TelegramAdapterTest` | ❌ 无 | **[必]** | long-poll 解析 / send 重试 / reply_to 逻辑 |
| `DiscordAdapterTest` | ❌ 无 | **[必]** | WS intent 订阅 / REST send / typing / embed |
| `WecomAdapterTest` | ❌ 无 | **[必]** | access_token 刷新 / text send / 401 处理 |
| `DingtalkAdapterTest` | ❌ 无 | **[必]** | corp token / outgoing 签名 |
| `BaseAdapterContractTest` | ✅ 有（`BaseTest`） | | UTF-16 / URL mask / retry 分类 / media 提取 |
| `HelpersTest` | ✅ 有 | | dedup / stripMarkdown / redactPhone |

> **10 个 stub 平台**（Whatsapp/Slack/Signal/Matrix/Mattermost/Bluebubbles/Email/Sms/Webhook/ApiServer/WecomCallback）现阶段不测试，实现完成后再回来补。

### E2E

| 脚本 | 状态 | 必做 |
|---|---|---|
| `scripts/e2e/test_gateway_feishu_e2e.sh` | ❌ 无 | **[必]** 广播 SEL_GATEWAY → 起 Service → mock inbound event → 看 HermesAgentLoop 命中 + outbound POST |
| `scripts/e2e/test_gateway_weixin_e2e.sh` | ❌ 无 | **[必]** 同上，iLink Bot 协议 |

---

## 4. 域 3 — Gateway Infrastructure

### 现状

所有文件 🟢 live：`Run.kt` (3822) / `Session.kt` (1021) / `StreamConsumer.kt` (1010) / `Config.kt` (515) / `Status.kt` (494) / `Pairing.kt` (293) / `Delivery.kt` (220) / `Hooks.kt` (215) / `DisplayConfig.kt` (164) / `ChannelDirectory.kt` (145) / `Mirror.kt` (137) / `SessionContext.kt` (105) / `StickerCache.kt` (81) / `Restart.kt` (54)。

Run.kt 有 12 TODO，集中在 signal handling + subprocess orchestration，这些在 Android 上本来就不适用。

### 落地策略

- **App 侧补挡**（主项目 `app/.../hermes/gateway/`）：
  - `HermesGatewayPreferences.kt` — DataStore + EncryptedSharedPreferences，已 live
  - `HermesGatewayController.kt` — 桥接 Service 和 GatewayRunner，已 live
  - `GatewayForegroundService.kt` — 前台服务，已 live
  - `GatewayBootReceiver.kt` — 开机自启
- 这些 app 侧文件**不是 1:1 Python 翻译**，是 Android 专属桥接，应在 CLAUDE.md §2 白名单里（不过它们在 app 目录不是 hermes-android 目录，不进对齐扫描，自然不算 reverse-extras）。

### 测试矩阵

| 测试类 | 状态 | 必做 | 覆盖内容 |
|---|---|---|---|
| `GatewayAgentWiringTest` | ✅ 有 | | inbound → agentRunner → DeliveryRouter → adapter.send |
| `DisplayConfigTest` | ✅ 有 | | 4-tier resolveDisplaySetting |
| `RestartTest` | ✅ 有 | | parseRestartDrainTimeout |
| `HooksTest` | ✅ 有 | | HookPipeline 优先级 / Halt / Replace |
| `ChannelDirectoryTest` | ✅ 有 | | 查询 normalise |
| `PairingTest` | ❌ 无 | **[必]** | 8 位 code 生成 / 速率限制 / 锁定 / 验证 |
| `MirrorTest` | ❌ 无 | **[必]** | A→B 镜像逻辑 / 循环检测 |
| `DeliveryRouterTest` | ❌ 无 | **[必]** | target 解析 / fallback / batch |
| `SessionContextTest` | ❌ 无 | [选] | session id 生成 / parent chain |
| `StickerCacheTest` | ❌ 无 | [选] | cache miss / eviction |
| `HermesGatewayPreferencesTest` (app) | ❌ 无 | **[必]** | DataStore round-trip / EncryptedSharedPreferences 回退到 plain / 多账号存储 |
| `HermesGatewayControllerTest` (app) | ❌ 无 | **[必]** | 三条件启停（service_enabled + platform_enabled + creds） |
| `GatewayForegroundServiceAndroidTest` (androidTest) | ❌ 无 | **[必]** | startForeground 成功 + notification 渲染 + stop 清理 |
| `GatewayBootReceiverAndroidTest` (androidTest) | ❌ 无 | [选] | BOOT_COMPLETED 触发 + 读 preferences |

---

## 5. 域 4 — Tools

### 现状

**已 live**（不列）：Registry / Approval / BudgetConfig / Interrupt / CheckpointManager / MixtureOfAgentsTool / SessionSearchTool / SkillManagerTool / SkillsHub / SkillsGuard / SkillsSync / SkillsTool / ManagedToolGateway / OpenrouterClient / TirithSecurity / OsvCheck / NeuTtsSynth / DiscordTool / FeishuDocTool / FeishuDriveTool / HomeassistantTool / TodoTool / FileOperations / FileTools / ProcessRegistry / RlTrainingTool / WebTools / VisionTools / TranscriptionTools / TtsTool / VoiceMode / MemoryTool / CodeExecutionTool / ClarifyTool / McpOauthManager。

**partial**：
- `DelegateTool.kt` — 常量实，spawn 返回 "unavailable"
- `SendMessageTool.kt` — 分发实，26 处 per-platform stub
- `TerminalTool.kt` — ProcessBuilder 实，sudo rewrite/Modal/PTY/watch stub
- `McpOAuth.kt` — 部分 OAuth flow

**stub**：
- `McpTool.kt` (705 LOC) — `_MCP_AVAILABLE=false`，17 处返回 false
- `BrowserTool.kt` (1062) / `BrowserCdpTool.kt` (234) / `BrowserCamofox.kt` (267) / `browser_providers/Base.kt` — 整个浏览器子系统
- `CronjobTools.kt` (319) — handler stub

### 落地策略

- **McpTool**：Android 能跑 MCP client。Python 用 `mcp` 包（stdio 子进程 + stdio JSON-RPC），Android 用 OkHttp + `ProcessBuilder` 起 npx/uvx；或只支持 **SSE/HTTP transport MCP server**（更简单，无子进程）。先补 SSE/HTTP，stdio 子进程做成 Termux 可选。
- **BrowserTool**：Android 没 headless Chrome。三条路：
  - (a) 用 Android WebView 封装成 "browser"（能力严重受限）
  - (b) 连远程 CDP 端点（用户在电脑跑 Chromium，手机 agent 通过 CDP 控制）—— 最实际
  - (c) Camofox（Firefox 指纹版）—— 服务器跑，手机连
  推荐 **(b) 为默认，(a) 做本地兜底**。`BrowserCdpTool` 优先实现。
- **CronjobTools handler**：Python 跑 `cron/Scheduler.kt` daemon；Android 用 **WorkManager** 定时触发 Scheduler.tick()，或用 **AlarmManager** 精确定时。用户已经用 WorkManager（libs 里有 `androidx.work:work-runtime-ktx`），优先 WorkManager。
- **DelegateTool**：spawn 独立 AIAgent 在 Android 上需要另起一个 coroutine scope + 独立 task_id/state。技术上可行，只是没人写，补一下。
- **SendMessageTool**：per-platform stub 应调用对应 PlatformAdapter.send()，现在已经有的平台补上即可，stub 平台保持 "unavailable"。

### 测试矩阵

已有 34 个测试文件，补以下**必做**：

| 测试类 | 必做 | 覆盖内容 |
|---|---|---|
| `McpToolSseTest` | **[必]** | SSE transport 连 + list_tools + call_tool + disconnect |
| `McpToolStdioTest` | [选] | ProcessBuilder 起 npx server + stdio JSON-RPC |
| `McpOAuthTest` | **[必]** | authorization_code flow / PKCE / token refresh |
| `BrowserCdpToolTest` | **[必]** | CDP 远程连 + navigate / screenshot / click / eval |
| `BrowserWebViewToolTest` | [选] | 本地 WebView 兜底 |
| `CronjobWorkManagerTest` | **[必]** | WorkManager 调度 / Scheduler.tick 触发 / DeliveryTarget 回路 |
| `DelegateToolSpawnTest` | **[必]** | 独立 scope + task_id / 屏蔽递归 delegate / 返回摘要 |
| `TerminalToolPtyTest` | [选] | PTY mode（可能无法在 Android 真跑） |
| `SendMessageToolDispatchTest` | **[必]** | 各平台 adapter 路由 / missing adapter 降级 |
| `MixtureOfAgentsToolTest` | ❌ 无 | **[必]** | 并发投票 / majority / tie-break |
| `SessionSearchToolDeepTest` | ✅ 有（浅）| [补强] | FTS5 mult-term / 摘要缓存 / chatId 过滤 |
| `RegistryDiscoveryTest` | ✅ 有 | | |
| `ApprovalTest` | ✅ 有（深） | | |

**测试浅化修复**（任务 #36 `Main-line C #1` 已在推进）：
- `DelegateToolTest` 现只验常量 → 补 spawn 成功路径 + 错误路径
- `CronjobToolsTest` 现只验 "unavailable" → 补 WorkManager 路径
- `TirithSecurityTest` 现只 fail-open → 补实际威胁检测
- `BrowserCamofoxStateTest` 21 LOC → 补 session 持久化 + cookie jar

---

## 6. 域 5 — Skills & Plugins

### 现状

| 文件 | LOC | 状态 |
|---|---|---|
| `plugins/memory/MemoryProvider.kt` | 76 | 🟢 (interface) |
| `plugins/memory/holographic/*.kt` | ~2700 | 🟢 (已 port) |
| `plugins/memory/honcho/*.kt` | ~2700 | 🟢 (已 port) |
| `plugins/memory/mem0/` | — | ⚫ missing |
| `plugins/memory/byterover/` | — | ⚫ missing |
| `plugins/memory/hindsight/` | — | ⚫ missing |
| `plugins/memory/retaindb/` | — | ⚫ missing |
| `plugins/memory/openviking/` | — | ⚫ missing |
| `plugins/memory/supermemory/` | — | ⚫ missing |
| `plugins/diskcleanup/DiskCleanup.kt` | 526 | 🟢 |
| `agent/SkillCommands.kt` | 534 | 🟢 |
| `agent/SkillUtils.kt` | 487 | 🟢 |
| `tools/SkillsHub.kt` | 2305 | 🟢 |
| `tools/SkillsGuard.kt` | 902 | 🟢 |
| `tools/SkillsSync.kt` | 317 | 🟢 |
| `tools/SkillsTool.kt` | 490 | 🟢 |
| `tools/SkillManagerTool.kt` | 265 | 🟢 |

### 落地策略

- **6 个缺失 memory backend**：都是 HTTP 客户端，没 Android 限制，直接 port（OkHttp + Gson）。
- **Skills Hub**：`context.filesDir/skills/` 存技能；ZIP 解压用 `java.util.zip`（已在用）。
- **Skill Manager Tool**：模型自建技能。Python 端写入 `~/.hermes/skills/<name>/SKILL.md`，Android 写入 `context.filesDir/skills/user/<name>/SKILL.md`。
- **Settings UI**：Hermes 设置里加 "技能" 子页，列出已装技能 + 从 Hub 拉 + 自建入口。

### 测试矩阵

**当前覆盖：0**。都必做。

| 测试类 | 必做 | 覆盖内容 |
|---|---|---|
| `HolographicMemoryProviderTest` | **[必]** | HRR 向量 op / retrieval / store round-trip |
| `HonchoClientTest` | **[必]** | OkHttp + Gson 序列化 / 错误处理 / session CRUD |
| `HonchoSessionTest` | **[必]** | session tree / parent chain / metadata |
| `Mem0ProviderTest` | [port 后必] | HTTP add/search/delete |
| `ByteroverProviderTest` | [port 后必] | 同上 |
| `HindsightProviderTest` | [port 后必] | 同上 |
| `RetaindbProviderTest` | [port 后必] | 同上 |
| `OpenvikingProviderTest` | [port 后必] | 同上 |
| `SupermemoryProviderTest` | [port 后必] | 同上 |
| `MemoryManagerPluginTest` | **[必]** | 单外挂 + builtin 策略 / 冲突检测 |
| `SkillsHubTest` | **[必]** | GitHub 源 fetch / ZIP 解压 / index 刷新 / lockfile |
| `SkillsGuardTest` | **[必]** | trust scan / quarantine / provenance 校验 |
| `SkillsSyncTest` | **[必]** | hash diff / 用户修改不覆盖 / manifest 写回 |
| `SkillsToolTest` | **[必]** | list / run / argv 解析 |
| `SkillManagerToolTest` | **[必]** | create / edit / patch / write_file |
| `DiskCleanupTest` | [选] | size 计算 / LRU eviction |

### E2E

| 脚本 | 必做 | 用途 |
|---|---|---|
| `test_skills_hub_e2e.sh` | **[必]** | 安装 app → 通过 broadcast 触发从 GitHub 拉 skill → 调 skill → 断言成功 |

---

## 7. 域 6 — State & Context

### 现状

全 🟢 live：`HermesState.kt` (1770) / `TrajectoryCompressor.kt` (1156) / `agent/Insights.kt` (1292) / `agent/Trajectory.kt` (111) / `agent/ContextEngine.kt` (201) / `agent/ContextReferences.kt` (618) / `agent/Display.kt` (953) / `agent/ContextCompressor.kt` (976)。

### 落地策略

- SQLite 用 Android 原生 `SQLiteOpenHelper`，已经在用。
- `parent_session_id` session fork：已实现，Android UI 要暴露 "查看父会话" 入口（下版本做）。
- ContextEngine 可插拔：默认 compressor，LCM 引擎待 port（Python 端是可选的，低优）。

### 测试矩阵

| 测试类 | 状态 | 必做 | 覆盖内容 |
|---|---|---|---|
| `HermesStateTest` | ✅ 有 | | kv/merge/nested/persist |
| `SessionSearchToolTest` | ✅ 有（浅） | | FTS5 query |
| `ToolResultStorageTest` | ✅ 有 | | 磁盘格式 |
| `CheckpointManagerTest` | ✅ 有 | | turn 边界 |
| `ContextCompressorTest` | ❌ 无 | **[必]** | 保头保尾 token 预算 / 中段摘要 / 迭代压缩 / tool 输出预裁剪 |
| `ContextCompressorTriggerTest` | ❌ 无 | **[必]** | budget 溢出自动触发 / 不溢出不触发 / 触发后 parent_session_id 链接 |
| `TrajectoryCompressorTest` | ❌ 无 | **[必]** | 训练轨迹后处理 / head/tail 保留 |
| `InsightsTest` | ❌ 无 | [选] | token/cost/tool 聚合 / 时段统计 |
| `HermesStateForkTest` | ❌ 无 | **[必]** | 压缩后新 session 生成 + parent_session_id 链 + 互不污染 |
| `ContextEngineSwitchTest` | ❌ 无 | [选] | config.context.engine 切换 |

---

## 8. 域 7 — Protocols (ACP / MCP)

### 现状

| 文件 | LOC | 状态 |
|---|---|---|
| `acp/Server.kt` | 884 | 🔴 (30 TODO 标记，ACP SDK 类型 alias 成 `Map<String,Any?>`) |
| `acp/Entry.kt` | 118 | 🔴 (main = no-op) |
| `acp/Tools.kt` | 483 | 🟡 (1 TODO, 1 default-return) |
| `acp/Events.kt` | 210 | 🟢 |
| `acp/Permissions.kt` | 128 | 🟢 |
| `acp/Auth.kt` | 62 | 🟢 |
| `tools/McpTool.kt` | 705 | 🔴 (`_MCP_AVAILABLE=false`) |
| `mcp_serve.kt` | — | ⚫ missing (Python 的 MCP server 未 port) |

### 落地策略

- **MCP Client (`McpTool.kt`)**：见域 4 — 先做 SSE/HTTP transport，stdio 留 Termux。
- **MCP Server (`mcp_serve.kt` 对应)**：Python 是 stdio FastMCP，把 gateway 9+1 工具暴露给 Claude Desktop / Cursor。Android 要做这个需要 **HTTP 服务器（NanoHTTPD 已在依赖）+ SSE 流**，手机要暴露到局域网/Tailscale。**用户场景**："桌面端 Claude Code 通过 MCP 连手机 gateway 管所有 IM"——这是高价值，但需要网络暴露。先做 **NanoHTTPD MCP server + 二维码配对**（手机生成 QR，桌面扫 QR 拿 token）。
- **ACP Server**：VS Code/Zed 通过 ACP 协议直连手机 Hermes——**Android 上意义小**，延后。`acp/Server.kt` 维持 stub 即可，类/方法对齐 Python 过 alignment 守卫。

### 测试矩阵

**当前覆盖：0**。

| 测试类 | 必做 | 覆盖内容 |
|---|---|---|
| `McpToolSseClientTest` | **[必]** | 连 MockWebServer 模拟 MCP SSE server → list_tools → call_tool → 断言 JSON-RPC |
| `McpToolOAuthTest` | **[必]** | OAuth flow（PKCE / code exchange / refresh） |
| `McpServerNanoHttpdTest` | **[必]** | 起 NanoHTTPD → list_tools 返回 gateway 9 工具 → call_tool(messages_send) 路由 |
| `McpServerPairingTest` | **[必]** | QR pairing + token 验证 + 过期 token 拒绝 |
| `AcpServerStubTest` | [选] | 类/方法存在 + alignment 绿 |

### E2E

| 脚本 | 必做 | 用途 |
|---|---|---|
| `test_mcp_server_e2e.sh` | **[必]** | adb reverse → curl MCP server list_tools → 断言返回 gateway 工具 |

---

## 9. 域 8 — Cron / Safety / Profiles

### 现状

| 文件 | LOC | 状态 |
|---|---|---|
| `cron/Scheduler.kt` | 618 | 🟢 (5 TODO) |
| `cron/Jobs.kt` | 815 | 🟢 (JSON 持久化) |
| `tools/CronjobTools.kt` | 319 | 🔴 (handler stub) |
| `tools/TirithSecurity.kt` | 226 | 🟢 |
| `tools/OsvCheck.kt` | 133 | 🟢 |
| `tools/Approval.kt` | 923 | 🟢 |
| `tools/PathSecurity.kt` | 41 | 🟢 |
| `tools/WebsitePolicy.kt` | 179 | 🟢 |
| `tools/UrlSafety.kt` | 81 | 🟢 |
| `tools/SkillsGuard.kt` | 902 | 🟢 |
| `Utils.kt` (HERMES_HOME) | — | 🟢 (用 filesDir) |

### 落地策略

- **Cron 执行**：`Scheduler.tick()` 现在跑在普通 coroutine；Android 应该由 **WorkManager 每分钟触发** → 调 `Scheduler.tick()`，而不是常驻 coroutine（省电）。见域 4 CronjobWorkManagerTest。
- **Profiles 多实例**：Python 用 `HERMES_HOME` 环境变量；Android 改成 `context.filesDir / "profiles" / name`。`Utils.kt::getHermesHome()` 已经这样做。**Settings UI 加 profile 切换**（下版本做）。
- **Safety**：全 live，**现有测试浅** — TirithSecurity 只 fail-open，CronjobTools 只验 "unavailable"。

### 测试矩阵

| 测试类 | 状态 | 必做 | 覆盖内容 |
|---|---|---|---|
| `ApprovalTest` | ✅ 有（深） | | 危险命令 / approval session / queue |
| `CronjobToolsTest` | ✅ 有（浅） | [补强] | 补 WorkManager 触发 + Job 执行 + Delivery 回路 |
| `TirithSecurityTest` | ✅ 有（浅，fail-open） | [补强] | 补实际威胁检测（prompt injection / data exfil） |
| `OsvCheckTest` | ✅ 有 | | |
| `UrlSafetyTest` | ✅ 有 | | |
| `WebsitePolicyTest` | ✅ 有 | | |
| `PathSecurityTest` | ✅ 有 | | |
| `CredentialFilesTest` | ✅ 有 | | |
| `InterruptTest` | ✅ 有 | | |
| `SchedulerTest` | ❌ 无 | **[必]** | tick 幂等 / 到期 job / 过期清理 / 文件锁 |
| `JobsTest` | ❌ 无 | **[必]** | JSON 持久化 / DeliveryTarget 路由 / job CRUD |
| `ProfileIsolationTest` | ❌ 无 | **[必]** | profile A 不可见 profile B 数据 / HERMES_HOME 切换生效 |
| `RedactTest` | ❌ 无 | **[必]** | (已列在域 1) |

---

## 10. 优先级建议（按投入产出比）

**本期（主线 C 推进中）**：
1. 主线 C #1 尾巴：补 `MixtureOfAgentsTool` / `DelegateTool spawn` / `McpOAuth` 测试（任务 #36）
2. 主线 C #2：Feishu/Weixin 之外平台的 adapter 测试（任务 #35 port 一个测一个）
3. `HermesGatewayPreferencesTest` / `HermesGatewayControllerTest`（app 侧，门槛低）

**下一轮**：
1. Skills & Plugins 全部测试（零覆盖是最大空白）
2. Context 域：`ContextCompressorTest` / `ContextCompressorTriggerTest` / `TrajectoryCompressorTest`
3. 多 LLM adapter 测试（Gemini/Codex/Anthropic）

**再下一轮**：
1. MCP Client SSE transport 实现 + 测试
2. MCP Server NanoHTTPD 实现 + QR pairing + E2E
3. Cron WorkManager handler 实现 + E2E

**长期**：
1. 6 家 memory provider port + 测试
2. Pairing / Mirror / Delivery infra 深测
3. BrowserCDP 实现（或验证用不上就 stub 说明）

---

## 11. 路径速查

| 目的 | 位置 |
|---|---|
| 本文档 | `HermesApp/docs/hermes-parity-test-plan.md` |
| Feature-parity 范围记忆 | `~/.claude/projects/-Users-qiao-file-HermesApp/memory/project_hermes_feature_parity.md` |
| 单元测试根 | `hermes-android/src/test/java/com/xiaomo/hermes/` |
| app 单元测试根 | `app/src/test/java/com/ai/assistance/operit/` |
| android 测试根 | `app/src/androidTest/java/com/ai/assistance/operit/` |
| E2E 脚本 | `HermesApp/scripts/e2e/` |
| 对齐四件套 | `/Users/qiao/file/HermesApp/scripts/hermes-align/scripts/` |
| Python 参考 | `/Users/qiao/file/HermesApp/reference/hermes-agent/` |

跑单测（改哪测哪）：
```bash
export JAVA_HOME="/Users/qiao/Library/Java/JavaVirtualMachines/semeru-21.0.3/Contents/Home"
cd HermesApp
./gradlew :hermes-android:compileDebugUnitTestKotlin            # 编译自检 ~30s
./gradlew :hermes-android:testDebugUnitTest --tests "com.xiaomo.hermes.hermes.tools.XxxTest"  # 单类 ~1-2min
./gradlew :app:testDebugUnitTest --tests "com.ai.assistance.operit.hermes.gateway.YyyTest"
```
