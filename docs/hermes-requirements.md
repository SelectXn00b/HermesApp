# Hermes Requirements (R-Doc)

> **状态**: 2026-04-26（§0.1 三阶段文档的第 ① 阶段）
> **下游**: 本文件描述"**需求是什么**"，**不写**具体断言、数值、枚举映射、字段值——这些属于 `docs/hermes-test-cases.md` 的测试用例层。
> **链路**: R-ID（本文件，需求行为）→ TC-ID（test-cases.md，可测断言）→ JUnit（hermes-android/app 的 `src/test/`）
> **来源标注**: Python 上游时引用 `reference/hermes-agent/.../xxx.py:line`；纯 Android 侧标注"无 Python 上游"。
>
> **ID 规则**: `R-<DOMAIN>-<NNN>`，`NNN` 全局递增不回收；删掉的需求标 `[DELETED]` 保留占位。
>
> **DOMAIN**: `CORE` / `AGENT` / `TOOL` / `PARSER` / `ACP` / `MCP` / `GATEWAY` / `STATE` / `SKILL` / `CRON` / `SAFETY` / `CONFIG` / `UI`

---

## 域 CORE — 项目核心要求

本域不针对单个 Python 文件，而是描述整个 HermesApp 的立项目标与冲突仲裁规则。CORE 是其他所有域的顶层约束——具体域的需求与 CORE 冲突时，以 CORE 为准。

### R-CORE-001: HermesApp 是 Hermes agent 的 Android 版本，必须与上游最大程度对齐

**来源**: 项目立项目标 + CLAUDE.md §1 / §2
**行为**: HermesApp 是 Python `reference/hermes-agent/` 在 Android 上的 Kotlin 实现。对齐 **尽最大可能** 在以下维度 1:1：

- **类名**：Python `class FooBar` → Kotlin `class FooBar`（保留原名，不加 `Adapter`/`Client`/`Impl` 等后缀）
- **方法名**：Python `def _save_trajectory` → Kotlin `_saveTrajectory`（前导 `_` 保留，snake_case → camelCase）
- **变量名**：同方法命名规则
- **文件名**：Python `snake_case.py` → Kotlin `PascalCase.kt` 严格 1:1
- **常量名**：全大写原样保留

**范围**: `hermes-android/` 模块强制对齐；`app/` 宿主壳层（Compose UI、DataStore、Foreground Service 等 Android 独有层）不强制。

**平台差异容许**: 函数体允许替换实现（Python SDK → OkHttp / Kotlin idiom / 显式 "not supported on Android" stub），**但签名与结构必须保持**。

**回归守卫**: CLAUDE.md §2 三件套（verify_align / scan_stubs / deep_align）持续零。

### R-CORE-002: 与 Hermes 冲突时以 Hermes 为准

**来源**: 项目立项目标 + CLAUDE.md §0.0 #3
**行为**: HermesApp 的 `app/` 模块包含较早从其他开源项目（Androidclaw / Operit）继承的存量代码。这些存量代码目前很多能力没有和 Hermes 对接。冲突仲裁规则：

- **Hermes 有 / app 没** → 在 app 侧把 Hermes 能力对接进来（走 `hermes-android` 的接口）
- **app 有 / Hermes 没 / 能力适合 Hermes** → 把这个能力对接到 Hermes 合约上（不是独立保留在 app 侧）
- **app 有 / Hermes 没 / 能力不属于 Hermes 范畴** → 保留在 app 侧（例如 Android 独有的 UI 壳）
- **两边都有且行为冲突** → **以 Hermes 为标准**，app 侧改到匹配 Hermes 行为。不允许出现"app 走自己一套，hermes-android 走另一套"的分裂实现

**范围**: 覆盖所有能力域——agent loop / tools / state / skills / gateway / MCP / cron / safety。

**不允许**: 在 app 侧保留与 Hermes 同名但语义不同的实现；保留 app 专属 fallback 路径绕过 Hermes。

---

## 索引

| 域 | 编号范围 | 覆盖状态 |
|---|---|---|
| CORE | R-CORE-001 .. 002 | 🟢 两条顶层约束 |
| PARSER | R-PARSER-001 .. 090 | 🟢 10 parser 家族（Longcat / Qwen / Qwen3-Coder / Llama / GLM-4.5 / GLM-4.7 / DeepSeek-V3 / DeepSeek-V3.1 / Kimi-K2 / Mistral + Hermes 通用基类） |
| AGENT | R-AGENT-001 .. 008 | 🟡 4 条（turn-loop / 错误处理合并 / 辅助合并 / 凭证池轮转） |
| ACP | R-ACP-001 .. 004 | 🟡 4 条（server 协议 / tool-kind 映射 / 事件生命周期 / client 连 Copilot） |
| TOOL | R-TOOL-001 .. 003 | 🟡 3 条（内置工具集 / 审批 / 预算） |
| GATEWAY | R-GW-001 .. 006 | 🟡 6 个子系统级能力（base+runner+config / Feishu / Weixin / QQ / 其他平台 / 前台服务） |
| STATE | R-STATE-001 .. 003 | 🟡 3 个子系统级能力 |
| SKILL | R-SKILL-001 .. 003 | 🟡 3 条（发现+hub+loader / 启用+guard / 同步） |
| MCP | R-MCP-001 .. 003 | 🟡 3 条（client / server / OAuth） |
| CRON | R-CRON-001 | 🟡 1 个子系统级能力 |
| SAFETY | R-SAFETY-001 .. 002 | 🟡 2 个子系统级能力（审批 / 清洗+脱敏） |
| CONFIG | — | ⚫ 整域删除（2026-04-26 二次剪裁），能力归 R-UI-001 / R-GW-001 |
| UI | R-UI-001 | 🟡 1 条家族级（Hermes Settings hub） |

> **注**: R 条目只写"能力是什么"。断言、枚举映射、字段值、边界数字等测试用例层内容在 `docs/hermes-test-cases.md`。R→TC 的反向索引由 test-cases.md 的"验 R"列保证。
>
> **2026-04-26 aggressive prune（一轮）**: 从 ~250 条剪到 57 条家族级。
>
> **2026-04-26 aggressive prune（二轮）**: 57 → 34 条。二轮剪除 CONFIG 全域（R-CONFIG-001..003 代码结构），TOOL 15 → 3（内置工具集 / 审批 / 预算），AGENT 7 → 3（turn-loop / 错误处理合并 / 辅助合并），UI 5 → 1（Settings hub 一站式）。按 §0.1 ID 不回收规则，旧 ID（R-TOOL-004..015、R-AGENT-004..007、R-UI-002..005、R-CONFIG-001..003）视为 `[DELETED]` 占位。
>
> **2026-04-26 完善补漏（三轮）**: 34 → 42 条。对照 Python 上游 (`reference/hermes-agent/`) 与 Kotlin 侧 (`hermes-android/`) 的实际实现补缺：补 4 条 parser（R-PARSER-060 DeepSeek-V3 / 070 GLM-4.5 / 080 Kimi-K2 / 090 Mistral），补 R-AGENT-008 凭证池与轮转、R-ACP-004 Copilot ACP client、R-SKILL-003 增量同步、R-MCP-003 OAuth 流程。同时修正 Python 源路径（`hermes/agent/*` → `agent/*`，`hermes/utils/*` → `agent/*`，`hermes/state/*` → 顶层 `hermes_state.py` / `trajectory_compressor.py`，`hermes/skills/*` → `agent/skill_*` + `tools/skills_*`，`hermes/mcp/*` → `tools/mcp_*` + `mcp_serve.py`，`hermes/cron/*` → `cron/` + `tools/cronjob_tools.py`，parser 路径由 `hermes/*_parser.py` → `environments/tool_call_parsers/*.py`）并按实际 Kotlin 实现补充每条 R 的子系统列表。

---

## 域 PARSER — Tool-call parsers

HermesApp 必须为主流开源模型家族提供 tool-call 文本格式解析。Python 解析器位于 `reference/hermes-agent/environments/tool_call_parsers/`；Kotlin 解析器位于 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/*Parser.kt`。

### R-PARSER-001: HermesApp 支持 Longcat 模型的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/longcat_parser.py`
**行为**: 识别 Longcat 模型响应里的 `<longcat_tool_call>…</longcat_tool_call>` 内联 JSON 标签，将 `name` + `arguments` 提取为统一 ToolCall；无 tag 的纯文本响应不视为错误。

### R-PARSER-010: HermesApp 支持 Qwen3-Coder 模型的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/qwen3_coder_parser.py`
**行为**: 识别 `<tool_call><function=name><parameter=k>v</parameter>…</function></tool_call>` 这一 XML-like 嵌套语法，把每个 function 块转成 ToolCall，其 parameter 子标签的值按字面量自动转为对应类型（布尔/数字）。

### R-PARSER-020: HermesApp 支持 Llama 3 / 4 JSON 格式的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/llama_parser.py`
**行为**: 识别 `<|python_tag|>{…JSON…}` 或纯 JSON 响应，提取 `name` + `arguments`（接受 `parameters` 作为 arguments 同义词）；畸形 JSON / 无 tag 时按 "无 tool-call" 处理，不抛异常。

### R-PARSER-030: HermesApp 支持 GLM-4.7 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/glm47_parser.py`
**行为**: 识别 `<tool_call>name\n<arg_key>k</arg_key>\n<arg_value>v</arg_value></tool_call>` 的 GLM 专用 k/v 语法，转为 ToolCall；无 tag 时按"无 tool-call"处理。

### R-PARSER-040: HermesApp 支持 Qwen / Hermes 通用格式的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/qwen_parser.py` + `hermes_parser.py`
**行为**: 识别 `<tool_call>{"name":..,"arguments":{..}}</tool_call>` 这一 Qwen 与 Hermes 通用格式；`hermes_parser.py` 作为 `ToolCallParser` 抽象基类提供共享解析流程，`supportedModels` 包含 `qwen`；Kotlin 对应 `QwenParser.kt` + `HermesParser.kt`（基类）。

### R-PARSER-050: HermesApp 支持 DeepSeek-V3.1 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/deepseek_v3_1_parser.py`
**行为**: 识别 DeepSeek-V3.1 用心形 emoji `❤️` 作为分隔符的 tool-call 语法（`❤️<name>❤️` 形式，可多次出现），按出现顺序产出 ToolCall 列表；无分隔符时返回空列表而非 null。

### R-PARSER-060: HermesApp 支持 DeepSeek-V3 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/deepseek_v3_parser.py`
**行为**: 识别 DeepSeek-V3（非 V3.1）早期 tool-call 语法；Kotlin `DeepseekV3Parser.kt` 与 V3.1 并存，保留独立解析路径以支持 V3 系列模型部署。

### R-PARSER-070: HermesApp 支持 GLM-4.5 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/glm45_parser.py`
**行为**: 识别 GLM-4.5 tool-call 语法（与 GLM-4.7 k/v 标签语法存在差异），产出 ToolCall 列表；Kotlin `Glm45Parser.kt` 与 `Glm47Parser.kt` 并存。

### R-PARSER-080: HermesApp 支持 Kimi-K2 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/kimi_k2_parser.py`
**行为**: 识别 Moonshot Kimi-K2 系列模型的 tool-call 语法，产出 ToolCall 列表；Kotlin `KimiK2Parser.kt` 1:1 对齐。

### R-PARSER-090: HermesApp 支持 Mistral 的 tool-call 解析
**来源**: `reference/hermes-agent/environments/tool_call_parsers/mistral_parser.py`
**行为**: 识别 Mistral 系列模型的 tool-call 语法，产出 ToolCall 列表；Kotlin `MistralParser.kt` 1:1 对齐。

---

## 域 AGENT — Agent turn-loop 与辅助

HermesApp 必须提供与 Python Hermes 等价的 agent turn-loop 内核。Python 源位于 `reference/hermes-agent/agent/`（子系统模块）+ `reference/hermes-agent/run_agent.py`（AIAgent 顶层编排器）；Kotlin 对应 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/agent/` + `hermes/AgentLoop.kt`。

### R-AGENT-001: HermesApp 提供 Hermes agent turn-loop 内核
**来源**: `reference/hermes-agent/run_agent.py` + `agent/prompt_builder.py` + `agent/display.py` + `agent/transports/`
**行为**: 驱动多轮 tool-calling；每轮由 `PromptBuilder` 组装 system/user/tool 消息 → 通过 provider 特定 transport（Anthropic / Bedrock / Codex Responses / Gemini native / Gemini CloudCode / Copilot ACP 等）发送 → 解析 tool_calls → dispatch tool → 收 tool_result → 进下一轮；达到 max_turns / 收到 stop 信号 / 最终回复无 tool_call 时终止；所有一轮内产生的事件（ContentDelta / ToolCallStart / ToolCallEnd / TurnComplete 等）以 `AgentEvent` 流给调用方。

### R-AGENT-002: 错误处理——分类、重试、模型路由、failover 合一
**来源**: `reference/hermes-agent/agent/error_classifier.py` + `retry_utils.py` + `rate_limit_tracker.py` + `nous_rate_guard.py` + `model_metadata.py` + `models_dev.py`
**行为**: API 错误按 HTTP 状态 + provider error_code + 文本归入统一类别（retry / rotate / fallback / compress / non-retriable），驱动指数退避 / Retry-After / 立即失败等重试策略；`rate_limit_tracker` 跟踪每 provider 限流窗口、`nous_rate_guard` 防超配；primary 返回 fallback 类错误时按 `model_metadata` + `models.dev` 的模型能力/成本表自动切换到下一个 provider；所有分类、退避参数、切换逻辑与 Python 上游 1:1。

### R-AGENT-003: Agent 辅助工具——标题、FileSafety、上下文压缩、memory 合一
**来源**: `reference/hermes-agent/agent/title_generator.py` + `file_safety.py` + `context_compressor.py` + `memory_manager.py` + `memory_provider.py` + `manual_compression_feedback.py` + `context_references.py` + `redact.py` + `prompt_caching.py`
**行为**: 对话结束时自动生成标题（用户首问 + LLM 压缩，失败回退截断）；文件写类工具调用前统一经 FileSafety 层检查路径合法性；长对话接近 context window 时自动压缩早期轮次（保留系统提示 + 最近 N 轮 + 摘要），`/compact` 用户手动触发时走同一压缩链并记录反馈；memory manager 负责 pin / unpin / 摘要化生命周期；上下文 @-引用（`@file` / `@url`）由 `context_references` 解析并展开；secret/PII 按 `redact.py` 规则从日志 / trajectory 中抹除；Anthropic / Gemini 支持 prompt cache breakpoint 插入——以上辅助能力行为与 Python 上游 1:1。

### R-AGENT-008: 凭证池与轮转
**来源**: `reference/hermes-agent/agent/credential_pool.py` + `credential_sources.py` + `account_usage.py` + `usage_pricing.py`
**行为**: 同一 provider 支持多 key 凭证池；按健康度 / 配额 / 成本轮转；key 出现鉴权 / 限流错误时按 R-AGENT-002 分类从池中标记并切换到下一条；凭证来源（env / 文件 / keychain / EncryptedSharedPreferences）由 `credential_sources` 统一解析；`account_usage` 聚合每凭证 token / 金额消耗，`usage_pricing` 提供模型 → 成本表；轮转策略与 Python 上游 1:1。

---

## 域 ACP — Agent Client Protocol

HermesApp 支持 ACP 双向：作为 **server** 暴露自身 agent 给外部 client（Zed / CLI）；作为 **client** 连到外部 ACP server（如 GitHub Copilot）。Python 源：`reference/hermes-agent/acp_adapter/` + `acp_registry/` + `agent/copilot_acp_client.py`。

### R-ACP-001: HermesApp 实现 ACP server 协议
**来源**: `reference/hermes-agent/acp_adapter/server.py` + `session.py` + `entry.py` + `auth.py` + `permissions.py`
**行为**: JSON-RPC over stdio / HTTP / WebSocket，实现 ACP 标准方法（session 生命周期、消息发送、工具事件流、中止、auth 握手、permission 提示）；消息格式与 ACP 上游 schema 对齐；`acp_registry/agent.json` 描述 agent 能力与 icon 供 client 发现。

### R-ACP-002: Tool-kind 映射 HermesApp 工具到 ACP 工具类别
**来源**: `reference/hermes-agent/acp_adapter/tools.py`
**行为**: HermesApp 的每个内部工具映射到 ACP 规范的 tool-kind（read / edit / execute / search 等），供 client 侧做 UI 分类与权限决策。

### R-ACP-003: ACP 工具事件生命周期与 agent 事件流对齐
**来源**: `reference/hermes-agent/acp_adapter/events.py`
**行为**: agent 内部的 ToolCallStart / ToolCallEnd / ContentDelta 事件映射为 ACP session notification；外部 client 看到的事件序列与内部一致。

### R-ACP-004: HermesApp 作为 ACP client 连接外部 server（GitHub Copilot）
**来源**: `reference/hermes-agent/agent/copilot_acp_client.py`
**行为**: agent 作为 ACP 客户端连接 GitHub Copilot 等外部 ACP server，把远端工具能力注册进本地工具列表；消息与事件格式按 ACP 协议；与 R-ACP-001 的 server 路径并存。

---

## 域 TOOL — Built-in tools

HermesApp 提供 Hermes agent 可调用的内建工具集。Python 源位于 `reference/hermes-agent/tools/`；Kotlin 对应 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/`（skills / mcp 家族与工具同目录，不单立子包）。

### R-TOOL-001: 内置工具集与 Python 上游对齐
**来源**: `reference/hermes-agent/tools/` 全体（registry / file_operations / file_tools / terminal_tool / code_execution_tool / process_registry / memory_tool / todo_tool / checkpoint_manager / clarify_tool / send_message_tool / web_tools / browser_tool / browser_cdp_tool / vision_tools / image_generation_tool / voice_mode / tts_tool / transcription_tools / delegate_tool / mixture_of_agents_tool / session_search_tool / skill_manager_tool / skills_tool / feishu_doc_tool / feishu_drive_tool / discord_tool / homeassistant_tool / cronjob_tools / tool_result_storage 等）
**行为**: Hermes agent 可调用的工具族按 Python 上游的签名、参数、返回结构、错误码 1:1 暴露；涵盖文件读写编辑 / 终端执行（前台+后台 `process_registry`）/ 代码执行 / 浏览器（Playwright CDP + Camoufox）/ web fetch+search / 多媒体（vision / image-gen / TTS / STT / voice mode）/ 子 agent 委派（delegate + mixture-of-agents）/ memory / todo / checkpoint / clarify / send_message / 会话历史搜索 / skill 调用 / MCP 调用 / 平台集成（Feishu Doc/Drive、Discord、HomeAssistant）/ cron；未注册 name 与异常路径均返回结构化错误而非抛异常；超大 tool_result 经 `tool_result_storage` 分页存储；注册表同时供 ACP / skill / MCP 层枚举能力。Android 平台独有的浮窗 / 通知 / 系统设置等工具以平台特供方式注册进同一 registry。

### R-TOOL-002: 敏感操作统一审批
**来源**: `reference/hermes-agent/tools/approval.py` + `path_security.py` + `url_safety.py` + `website_policy.py`
**行为**: 文件写 / 命令执行 / 网络请求 / 路径敏感参数在执行前汇总到统一审批层；审批模式（always / once / never / auto-accept）由配置决定；被拒调用返回明确的 declined 错误；路径规则（敏感目录、路径遍历、符号链接、项目外绝对路径等）+ URL / 网站白名单策略与 Python 上游一致。FileSafety 的 agent 侧拦截面由 R-AGENT-003 承载，此处只负责 tool 层审批门控。

### R-TOOL-003: 工具预算约束
**来源**: `reference/hermes-agent/tools/budget_config.py` + `managed_tool_gateway.py`
**行为**: 按工具类别跟踪单轮 / 会话累计调用次数、字节、token；超预算拒绝继续调用并返回预算用尽的结构化错误；`managed_tool_gateway` 负责远程管理型工具的配额 / 限流；配额阈值与 Python 上游一致。

---

## 域 GATEWAY — 外部平台网关

HermesApp 作为网关把 agent 能力接到外部 IM / 协作平台（飞书、微信、Telegram、QQ、企业微信、Slack、Discord、Matrix、WhatsApp、Signal、钉钉、SMS、邮件、ApiServer、Webhook、HomeAssistant、Mattermost、BlueBubbles）。

### R-GW-001: Gateway 基础类与通用能力
**来源**: `reference/hermes-agent/gateway/base.py` + `helpers.py` + `run.py` + `config.py` + `session.py` + `session_context.py` + `stream_consumer.py` + `delivery.py` + `pairing.py` + `hooks.py` + `status.py` + `restart.py` + `channel_directory.py` + `display_config.py` + `mirror.py` + `sticker_cache.py`
**行为**: 统一的 platform adapter 基类；规范化消息 / DM 策略 / 群策略 / 去重 / 批量合并 / 重连等通用能力集中在 base + helpers；`GatewayRunner`（run.py，~513k 一等编排器）负责 adapter 生命周期；`config.py` 负责加载 / 校验配置；`session.py` / `session_context.py` 维护每 channel 的会话上下文；`stream_consumer.py` 把 agent 事件流消费为平台可渲染 chunk，`delivery.py` 管出站派发；`pairing.py` 处理平台账号配对；`hooks.py` 分发 gateway 级钩子（如 builtin boot.md）；`status.py` 暴露健康状态供 UI 订阅；`restart.py` 做 supervised 重启；`channel_directory.py` 管 channel 白名单；`display_config.py` 按平台解析表情 / 明文 / markdown；`mirror.py` 做跨平台会话镜像；`sticker_cache.py` 缓存表情素材。

### R-GW-002: Feishu 平台功能完整对齐
**来源**: `reference/hermes-agent/gateway/platforms/feishu.py` + `feishu_comment.py` + `feishu_comment_rules.py`
**行为**: 完整实现 Feishu 长连接 WSS / 消息收发 / 卡片交互 / reaction / batching / drive comment 机器人 / 管理员与群 ACL / QR onboarding；webhook 模式在 Android 上显式标注为 "not supported without reverse proxy"（R-CORE-001 允许的平台差异）。

### R-GW-003: Weixin 平台功能完整对齐
**来源**: `reference/hermes-agent/gateway/platforms/weixin.py`
**行为**: QR 登录 / AES-128-ECB CDN 加解密 / 账号状态持久化 / 分段发送 / DM 群策略 / typing ticket / context token cache / 批量发送——均与 Python 上游对齐。

### R-GW-004: QQ 机器人平台功能对齐
**来源**: `reference/hermes-agent/gateway/platforms/qqbot/`
**行为**: QQ 官方 bot API 长连接 / 消息收发 / 群与私聊路径 / 权限与别名处理。

### R-GW-005: 其余平台维持类方法级对齐
**来源**: `reference/hermes-agent/gateway/platforms/{telegram,slack,discord,wecom,matrix,whatsapp,signal,dingtalk,email,sms,api_server,webhook,homeassistant,mattermost,bluebubbles}.py`
**行为**: 以上平台保持与 Python 上游的类 / 方法签名对齐（verify_align 零违规）；Android 上不可用的路径在方法体里返回显式"not supported on Android"；用户后续明确要完整跑通时按 R-GW-002 / 003 的方式补充实现。

### R-GW-006: Gateway 运行时前台服务
**来源**: 无 Python 直接上游（Android 特有）；对应 Python runner 的生命周期
**行为**: Gateway 在 Android 上通过前台服务运行（`GatewayForegroundService`），随应用/开机自启策略、电量白名单引导、存活通知均由前台服务负责；服务状态可被 UI 实时订阅。

---

## 域 STATE — 会话状态 / trajectory

### R-STATE-001: Hermes 会话状态结构与 Python 上游一致
**来源**: `reference/hermes-agent/hermes_state.py` + `agent/trajectory.py`
**行为**: 会话包含 messages / tool events / token usage / metadata 等字段；支持 resume / branching 操作；序列化 / 反序列化对齐 Python `HermesState`；版本升级有向前兼容路径；trajectory 记录模型由 `agent/trajectory.py` 提供。

### R-STATE-002: Trajectory 压缩与 Python 上游一致
**来源**: `reference/hermes-agent/trajectory_compressor.py`
**行为**: 长 trajectory 按既定策略压缩（保留边界事件 / 汇总中间步骤）；压缩前后事件序列仍可被 agent 正确重放。

### R-STATE-003: 持久化到 Android 本地
**来源**: Android 平台实现，对齐 Python 文件系统持久化语义
**行为**: 会话 state 持久化到应用私有目录；读写符合 DataStore / 文件 API 规范；断电 / 进程重启后可恢复。

---

## 域 SKILL — Skill 加载 / 启用 / 同步

### R-SKILL-001: Skill 发现、加载、hub 索引
**来源**: `reference/hermes-agent/agent/skill_utils.py` + `tools/skills_hub.py` + `tools/skills_tool.py` + `tools/skill_manager_tool.py` + `skills/` + `optional-skills/`
**行为**: 扫描 skill 目录（内建 `skills/` + `optional-skills/` + 用户导入）；解析 frontmatter + 正文；由 `skills_hub` 构造中心索引加入全局注册表；非法 skill 给出明确错误；skill 调用通过 `skills_tool` 工具执行，`skill_manager_tool` 负责 install / uninstall / list。

### R-SKILL-002: Skill 启用 / 禁用 + 执行护栏
**来源**: `reference/hermes-agent/agent/skill_commands.py` + `tools/skills_guard.py`
**行为**: 支持按 name / glob 启用或禁用 skill；禁用 skill 不出现在 R-TOOL-001 枚举结果里，调用也被拒绝；`skills_guard` 在执行时做权限 / 签名 / 策略校验；slash-command 调度由 `skill_commands` 承载。

### R-SKILL-003: Skill 增量同步
**来源**: `reference/hermes-agent/tools/skills_sync.py`
**行为**: 支持从上游仓库增量同步新 / 更新的 skill 定义到本地；冲突 / 签名失败拒绝覆盖；同步后刷新 R-SKILL-001 的 hub 索引。

---

## 域 MCP — Model Context Protocol

### R-MCP-001: HermesApp 作为 MCP client 连接外部 MCP server
**来源**: `reference/hermes-agent/tools/mcp_tool.py`
**行为**: 支持 stdio / SSE / WebSocket 传输；能列举 / 调用 server 暴露的 tools / resources / prompts；错误 / 超时结构化返回；被调工具注册进 R-TOOL-001 registry。

### R-MCP-002: HermesApp 可选作为 MCP server 暴露自身工具
**来源**: `reference/hermes-agent/mcp_serve.py`
**行为**: 将 R-TOOL-001 注册表按 MCP tools 协议暴露；可选启用；与 ACP 服务可并存。

### R-MCP-003: MCP OAuth 流程
**来源**: `reference/hermes-agent/tools/mcp_oauth.py` + `mcp_oauth_manager.py`
**行为**: 对需要 OAuth 的 MCP server 走标准授权码流程；token 由 `mcp_oauth_manager` 管理（获取 / 刷新 / 失效）；授权请求由用户审批（UI 侧），令牌存储经加密。

---

## 域 CRON — 定时任务

### R-CRON-001: Cron 定时任务子系统
**来源**: `reference/hermes-agent/cron/scheduler.py` + `cron/jobs.py` + `tools/cronjob_tools.py`
**行为**: 支持创建 / 列举 / 删除定时任务；支持一次性与周期性；到点触发 agent prompt；持久化到本地；与 Python 上游一致的 cron 表达式解析与下一次触发时间计算；agent 可通过 `cronjob_tools` 工具族自管 cron。

---

## 域 SAFETY — 安全护栏

### R-SAFETY-001: 敏感操作统一需要审批
**来源**: 跨越多个工具实现的统一安全策略（对应 R-AGENT-003 FileSafety / R-TOOL-002 approval + path_security）
**行为**: 文件写、命令执行、网络请求等敏感操作在执行前汇总到统一的审批层；审批策略可配置；被拒操作返回结构化 declined 错误。

### R-SAFETY-002: 外部输入清洗与 secret 脱敏
**来源**: `reference/hermes-agent/agent/redact.py` + `tools/url_safety.py` + `tools/website_policy.py` + `tools/tirith_security.py` + `tools/osv_check.py` + 各工具参数校验
**行为**: 工具参数 / 外部消息在进入 agent 前做 sanitize（控制字符、超长字符串、过深 JSON）；日志 / trajectory 写入前经 `redact.py` 规则抹除 API key / token / PII；URL 与 website 按 `url_safety` + `website_policy` 策略准入；依赖漏洞 / 恶意代码检查通过 `osv_check` 与 `tirith_security`；不合规输入被拒绝或截断并标注。

---

## 域 CONFIG — [DELETED 2026-04-26]

CONFIG 原先的三条 R-CONFIG-001..003（Preferences / ConfigBuilder / Controller）是代码结构、不是用户可见需求，整域剪除。用户可见的配置能力由 R-UI-001 的 Hermes Settings hub 承载；gateway 配置落盘归属 R-GW-001 base。

---

## 域 UI — Hermes Settings hub

### R-UI-001: Hermes Settings hub 承载 gateway 与 agent 的全部用户可见配置
**来源**: 无 Python 上游；Android UI
**行为**: 应用 Settings hub 新增 "Hermes / 墨思" 入口，子屏集中覆盖——gateway 平台凭证录入 / 清除、DM / 群 / 批量 / 去重 / 重连等策略、agent 参数（max_turns / persona / memory 策略 / 工具 allow-deny）、服务开关 + 电量白名单 + 开机自启 + 实时状态、QR 绑定（Feishu probe_bot / qr_register、Weixin qr_login）。敏感字段经加密存储；保存后运行时组件读取该配置而非硬编码。
