# Hermes Test Cases (TC-Doc)

> **状态**: 2026-04-26（§0.1 三阶段文档的第 ② 阶段）
> **上游**: `docs/hermes-requirements.md`（每条 TC 引用一个 R-ID）
> **下游**: `hermes-android/src/test/**` 的 JUnit 方法（每条 TC 至少一个 `@Test`，注释里必须写 `TC-XXX-NNN-x`）
>
> **ID 规则**: `TC-<DOMAIN>-<NNN>-<letter>`，NNN 对齐需求编号，letter 枚举分支 / 变体。TC ID 本身不回收；R-ID 自 2026-04-26 aggressive prune 起用家族 / 子系统级新编号（见 requirements.md 顶部 note）。
>
> **状态**: ✅ 已落地（注明 test 类#方法名）/ 🟡 待写 / 🔴 阻塞
>
> **2026-04-26 验-R cascade（一轮）**: requirements.md 从 ~250 条实现细节级 R 剪裁为 57 条家族 R；本文件 ~494 条 TC 的"验 R"列重指到新家族 R-ID。
>
> **2026-04-26 验-R cascade（二轮）**: requirements.md 57 → 34 条（删 CONFIG 域 / TOOL 15→3 / AGENT 7→3 / UI 5→1）；本文件再批量重指死 ID（R-TOOL-004..015 / R-AGENT-004..007 / R-UI-002..005 / R-CONFIG-001..003）到新合并 R-ID。TC 本身（输入 / 期望 / 测试方法）未动。
>
> **2026-04-26 验-R cascade（三轮）**: requirements.md 34 → 42 条（补 R-PARSER-060/070/080/090 / R-AGENT-008 / R-ACP-004 / R-MCP-003 / R-SKILL-003；修 Python 源路径）。本文件对应补：PARSER 新增 14 TC（4 家 parser，对应已落地的 `DeepseekV3ParserTest` / `Glm45ParserTest` / `KimiK2ParserTest` / `MistralParserTest`）；新增 CORE / AGENT-TurnLoop / AGENT-CredentialPool / ACP-CopilotClient 段；MCP 把 OAuth 相关 TC 从 R-MCP-001 改指 R-MCP-003，补 R-MCP-002；SKILL 补 R-SKILL-003 覆盖（复用 `SkillsSync*` 已有 TC）。

---

## 索引

| 域 | 已落地 TC | 待写 TC | 测试类 |
|---|---|---|---|
| CORE | 4 (alignment) | 0 | §2 三件套对齐脚本 |
| PARSER | 36 | 0 | `MissingParsersTest.kt` + `DeepseekV3ParserTest.kt` + `Glm45ParserTest.kt` + `KimiK2ParserTest.kt` + `MistralParserTest.kt` |
| AGENT (ErrorClassifier) | 48 | 0 | `ErrorClassifierTest.kt` |
| AGENT (Helpers) | 22 | 0 | `AgentHelpersTest.kt` |
| AGENT (FileSafety) | 10 | 0 | `FileSafetyTest.kt` |
| AGENT (TurnLoop) | 3 (E2E) + 6 (unit) | 0 | `scripts/e2e/*.sh` + `HermesAgentLoopBeforeNextTurnTest.kt` |
| AGENT (CredentialPool) | 0 | 7 | `CredentialPoolTest` (待建) |
| ACP | 49 | 4 (Copilot client) | `AcpToolsTest.kt`, `AcpAuthTest.kt` + `CopilotAcpClientTest`（待建）|
| MCP | 13 | 1 | `McpToolTest.kt`, `McpOAuthTest.kt`, `ManagedToolGatewayTest.kt` |
| 其他域 | 详见各域表 | 详见各域表 | — |

跑当前所有已落地：
```bash
./gradlew :hermes-android:testDebugUnitTest \
  --tests "com.xiaomo.hermes.hermes.MissingParsersTest" \
  --tests "com.xiaomo.hermes.hermes.DeepseekV3ParserTest" \
  --tests "com.xiaomo.hermes.hermes.Glm45ParserTest" \
  --tests "com.xiaomo.hermes.hermes.KimiK2ParserTest" \
  --tests "com.xiaomo.hermes.hermes.MistralParserTest" \
  --tests "com.xiaomo.hermes.hermes.HermesAgentLoopBeforeNextTurnTest" \
  --tests "com.xiaomo.hermes.hermes.agent.ErrorClassifierTest" \
  --tests "com.xiaomo.hermes.hermes.agent.AgentHelpersTest" \
  --tests "com.xiaomo.hermes.hermes.agent.FileSafetyTest" \
  --tests "com.xiaomo.hermes.hermes.acp.AcpToolsTest" \
  --tests "com.xiaomo.hermes.hermes.acp.AcpAuthTest" \
  --tests "com.xiaomo.hermes.hermes.tools.McpToolTest" \
  --tests "com.xiaomo.hermes.hermes.tools.McpOAuthTest" \
  --tests "com.xiaomo.hermes.hermes.tools.ManagedToolGatewayTest"
```

---

## 域 CORE

CORE 域的两条顶层约束（R-CORE-001 1:1 对齐 / R-CORE-002 冲突以 Hermes 为准）是**元需求**，不走 JUnit 单测——由 CLAUDE.md §2 的三件套对齐脚本守护。此处登记三个脚本作为 CORE 的验收手段。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CORE-001-a | R-CORE-001 | `python3.11 scripts/verify_align.py --hermes $H --android $A` | 缺失文件数 0 | alignment | `scripts/hermes-align/scripts/verify_align.py` ✅ |
| TC-CORE-001-b | R-CORE-001 | `python3.11 scripts/scan_stubs.py --android $A` | stub 数 0 | alignment | `scripts/hermes-align/scripts/scan_stubs.py` ✅ |
| TC-CORE-001-c | R-CORE-001 | `python3.11 references/deep_align.py --pybase $H --kbase $A --json` | findings 0 | alignment | `scripts/hermes-align/references/deep_align.py` ✅ |
| TC-CORE-002-a | R-CORE-002 | **[DELETED 2026-04-26]** 反向冗余守卫（多余类/方法/常量）移除；`check_reverse.py` 已删除，CORE-002 由 TC-CORE-002-b（语义对齐）单独覆盖。 | — | — | — |
| TC-CORE-002-b | R-CORE-002 | HermesApp 与 Hermes 存在同名类 | 语义与 Hermes 一致（非 Kotlin 自家分叉） | alignment | 由 `verify_align.py` 的结构对齐 + `deep_align.py` body 字面对齐双重保障 ✅ |

---

## 域 PARSER

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/MissingParsersTest.kt`（Longcat / Qwen3Coder / Llama / Glm47 / Qwen / DeepseekV31）+ `DeepseekV3ParserTest.kt` / `Glm45ParserTest.kt` / `KimiK2ParserTest.kt` / `MistralParserTest.kt`（各一个独立测试类）

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-PARSER-001-a | R-PARSER-001 | `Longcat parses single tool call` | ✅ |
| TC-PARSER-002-a | R-PARSER-001 | `Longcat returns null when no tag` | ✅ |
| TC-PARSER-003-a | R-PARSER-001 | `Longcat skips entries with empty name` | ✅ |
| TC-PARSER-004-a | R-PARSER-001 | `Longcat supportedModels includes longcat` | ✅ |
| TC-PARSER-010-a | R-PARSER-010 | `Qwen3Coder parses function with parameters` | ✅ |
| TC-PARSER-011-a | R-PARSER-010 | `Qwen3Coder converts boolean parameter` | ✅ |
| TC-PARSER-012-a | R-PARSER-010 | `Qwen3Coder no tool_call returns null` | ✅ |
| TC-PARSER-013-a | R-PARSER-010 | `Qwen3Coder _parseFunctionCall returns null when no gt` | ✅ |
| TC-PARSER-014-a | R-PARSER-010 | `Qwen3Coder supportedModels` | ✅ |
| TC-PARSER-020-a | R-PARSER-020 | `Llama parses arguments object` | ✅ |
| TC-PARSER-021-a | R-PARSER-020 | `Llama accepts parameters key as synonym` | ✅ |
| TC-PARSER-022-a | R-PARSER-020 | `Llama no json and no token returns null` | ✅ |
| TC-PARSER-023-a | R-PARSER-020 | `Llama invalid json returns null` | ✅ |
| TC-PARSER-024-a | R-PARSER-020 | `Llama supportedModels` | ✅ |
| TC-PARSER-030-a | R-PARSER-030 | `Glm47 supportedModels` | ✅ |
| TC-PARSER-031-a | R-PARSER-030 | `Glm47 uses arg_key arg_value syntax` | ✅ |
| TC-PARSER-032-a | R-PARSER-030 | `Glm47 no tool call returns null` | ✅ |
| TC-PARSER-040-a | R-PARSER-040 | `Qwen supportedModels contains qwen` + `Qwen parses Hermes-format tool call` | ✅ |
| TC-PARSER-050-a | R-PARSER-050 | `DeepseekV31 supportedModels` | ✅ |
| TC-PARSER-051-a | R-PARSER-050 | `DeepseekV31 parses heart-emoji delimited call` | ✅ |
| TC-PARSER-052-a | R-PARSER-050 | `DeepseekV31 no delimiter returns empty list` | ✅ |
| TC-PARSER-053-a | R-PARSER-050 | `DeepseekV31 parses multiple calls` | ✅ |
| TC-PARSER-060-a | R-PARSER-060 | `parse no tool call returns original content and null` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-061-a | R-PARSER-060 | `parse single tool call` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-062-a | R-PARSER-060 | `parse multiple tool calls` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-063-a | R-PARSER-060 | `parse tool call with preceding text` (DeepseekV3ParserTest) | ✅ |
| TC-PARSER-070-a | R-PARSER-070 | `parse single tool call with arg tags` (Glm45ParserTest) | ✅ |
| TC-PARSER-071-a | R-PARSER-070 | `parse no tool call` (Glm45ParserTest) | ✅ |
| TC-PARSER-072-a | R-PARSER-070 | `supportedModels includes glm45` (Glm45ParserTest) | ✅ |
| TC-PARSER-080-a | R-PARSER-080 | `parse single tool call` (KimiK2ParserTest) | ✅ |
| TC-PARSER-081-a | R-PARSER-080 | `parse multiple tool calls` (KimiK2ParserTest) | ✅ |
| TC-PARSER-082-a | R-PARSER-080 | `parse no tool calls` (KimiK2ParserTest) | ✅ |
| TC-PARSER-090-a | R-PARSER-090 | `parse v11 format - single tool call` (MistralParserTest) | ✅ |
| TC-PARSER-090-b | R-PARSER-090 | `parse v11 format - multiple tool calls` + `parse pre-v11 format - JSON array` + `parse pre-v11 format - single JSON object` (MistralParserTest) | ✅ |
| TC-PARSER-091-a | R-PARSER-090 | `parse no tool calls` (MistralParserTest) | ✅ |
| TC-PARSER-092-a | R-PARSER-090 | `tool call IDs are 9 chars` (MistralParserTest) | ✅ |

---

## 域 AGENT — ErrorClassifier

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/ErrorClassifierTest.kt`

### 状态码分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-001-a | R-AGENT-002 | `401 routes to auth with rotate and fallback` | ✅ |
| TC-AGENT-002-a | R-AGENT-002 | `403 plain routes to auth without rotate` | ✅ |
| TC-AGENT-003-a | R-AGENT-002 | `403 with key limit text routes to billing` | ✅ |
| TC-AGENT-004-a | R-AGENT-002 | `402 generic routes to billing` | ✅ |
| TC-AGENT-005-a | R-AGENT-002 | `402 with transient usage limit text routes to rate_limit` | ✅ |
| TC-AGENT-006-a | R-AGENT-002 | `404 is model_not_found` | ✅ |
| TC-AGENT-007-a | R-AGENT-002 | `413 is payload_too_large with compress` | ✅ |
| TC-AGENT-008-a | R-AGENT-002 | `429 is rate_limit with rotate and fallback` | ✅ |
| TC-AGENT-009-a | R-AGENT-002 | `long context tier trips before 429 rate_limit` | ✅ |
| TC-AGENT-010-a | R-AGENT-002 | `thinking signature trips before generic 400 classification` | ✅ |
| TC-AGENT-011-a | R-AGENT-002 | `400 with context_length msg routes to context_overflow` | ✅ |
| TC-AGENT-012-a | R-AGENT-002 | `400 with invalid model msg routes to model_not_found` | ✅ |
| TC-AGENT-013-a | R-AGENT-002 | `400 rate-limit text beats billing pattern order` | ✅ |
| TC-AGENT-014-a | R-AGENT-002 | `400 billing pattern routes to billing` | ✅ |
| TC-AGENT-015-a | R-AGENT-002 | `400 with generic short body + large session infers context_overflow` | ✅ |
| TC-AGENT-016-a | R-AGENT-002 | `400 with unknown text falls to format_error` | ✅ |
| TC-AGENT-017-a | R-AGENT-002 | `500 is server_error retryable` | ✅ |
| TC-AGENT-018-a | R-AGENT-002 | `503 is overloaded retryable` + `529 is overloaded (Anthropic-style)` | ✅ |
| TC-AGENT-019-a | R-AGENT-002 | `other 4xx falls into format_error` | ✅ |
| TC-AGENT-020-a | R-AGENT-002 | `other 5xx falls into server_error retryable` | ✅ |

### Error code 分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-021-a | R-AGENT-002 | `error code resource_exhausted maps to rate_limit` | ✅ |
| TC-AGENT-022-a | R-AGENT-002 | `error code insufficient_quota maps to billing` | ✅ |
| TC-AGENT-023-a | R-AGENT-002 | `error code context_length_exceeded maps to context_overflow` | ✅ |
| TC-AGENT-024-a | R-AGENT-002 | `error code model_not_found maps to model_not_found` | ✅ |

### 消息模式分支

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-030-a | R-AGENT-002 | `msg-only payload too large routes to payload_too_large` | ✅ |
| TC-AGENT-031-a | R-AGENT-002 | `msg-only billing pattern routes to billing` | ✅ |
| TC-AGENT-032-a | R-AGENT-002 | `msg-only rate_limit pattern routes to rate_limit` | ✅ |
| TC-AGENT-033-a | R-AGENT-002 | `msg-only context overflow pattern routes to context_overflow` | ✅ |
| TC-AGENT-034-a | R-AGENT-002 | `msg-only auth pattern routes to auth` | ✅ |
| TC-AGENT-035-a | R-AGENT-002 | `msg-only model not found pattern routes to model_not_found` | ✅ |

### Transport / Disconnect / Unknown

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-040-a | R-AGENT-002 | `IOException is classified as timeout` + `SocketTimeoutException is classified as timeout` | ✅ |
| TC-AGENT-041-a | R-AGENT-002 | `disconnect plus large session infers context_overflow` | ✅ |
| TC-AGENT-042-a | R-AGENT-002 | `disconnect alone routes to timeout` | ✅ |
| TC-AGENT-043-a | R-AGENT-002 | `totally unknown error falls through to unknown` | ✅ |

### 辅助提取器

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-050-a | R-AGENT-002 | `_extractErrorCode reads error dot code` | ✅ |
| TC-AGENT-051-a | R-AGENT-002 | `_extractErrorCode falls back to top-level code` | ✅ |
| TC-AGENT-052-a | R-AGENT-002 | `_extractErrorCode reads integer code as string` | ✅ |
| TC-AGENT-053-a | R-AGENT-002 | `_extractErrorCode returns empty for empty body` | ✅ |
| TC-AGENT-054-a | R-AGENT-002 | `_extractMessage prefers body error message over throwable` + `_extractMessage trims and caps at 500 chars` | ✅ |
| TC-AGENT-055-a | R-AGENT-002 | `_extractMessage falls back to throwable when body empty` | ✅ |
| TC-AGENT-056-a | R-AGENT-002 | `_extractStatusCode returns null when absent` + `_extractStatusCode reads reflective getStatusCode` | ✅ |
| TC-AGENT-057-a | R-AGENT-002 | `_extractErrorBody parses body on the exception` | ✅ |
| TC-AGENT-060-a | R-AGENT-002 | `isAuth true for auth and auth_permanent only` | ✅ |
| TC-AGENT-061-a | R-AGENT-002 | `FailoverReason enum values match python names` | ✅ |

---

## 域 AGENT — Helpers (SmartModelRouting / RetryUtils / TitleGenerator)

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/AgentHelpersTest.kt`

### SmartModelRouting

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-100-a | R-AGENT-002 | `smart routing short simple message goes cheap` | ✅ |
| TC-AGENT-101-a | R-AGENT-002 | `smart routing code fence bumps complexity` | ✅ |
| TC-AGENT-102-a | R-AGENT-002 | `smart routing long detailed analysis goes expensive` | ✅ |
| TC-AGENT-103-a | R-AGENT-002 | `smart routing mid-length hands off to current model` | ✅ |

### RetryUtils

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-110-a | R-AGENT-003 | `calculateRetryDelayMs caps at maxMs` | ✅ |
| TC-AGENT-111-a | R-AGENT-003 | `calculateRetryDelayMs exponential no jitter` | ✅ |
| TC-AGENT-112-a | R-AGENT-003 | `calculateRetryDelayMs jitter stays within 1x-1_5x` | ✅ |
| TC-AGENT-113-a | R-AGENT-003 | `shouldRetry caps at maxRetries` | ✅ |
| TC-AGENT-114-a | R-AGENT-003 | `shouldRetry accepts IO exceptions` | ✅ |
| TC-AGENT-115-a | R-AGENT-003 | `shouldRetry rejects unrelated exception` | ✅ |
| TC-AGENT-116-a | R-AGENT-003 | `jitteredBackoff stays within cap` | ✅ |
| TC-AGENT-117-a | R-AGENT-003 | `CountIterator always hasNext and increments` | ✅ |
| TC-AGENT-118-a | R-AGENT-003 | `withRetry returns on first success` | ✅ |
| TC-AGENT-119-a | R-AGENT-003 | `withRetry rethrows non-retriable exception immediately` | ✅ |

### TitleGenerator

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-130-a | R-AGENT-003 | `generate returns New Chat for empty input` | ✅ |
| TC-AGENT-131-a | R-AGENT-003 | `generate normalizes whitespace and returns short title as-is` | ✅ |
| TC-AGENT-132-a | R-AGENT-003 | `generate strips stop words when truncating long input` | ✅ |
| TC-AGENT-133-a | R-AGENT-003 | `generate falls back to truncation when all words are stop words` | ✅ |
| TC-AGENT-134-a | R-AGENT-003 | `generateFromMessages picks first user message` | ✅ |
| TC-AGENT-135-a | R-AGENT-003 | `generateFromMessages New Chat when no user` | ✅ |
| TC-AGENT-136-a | R-AGENT-003 | `TITLE_PROMPT contains instructions` | ✅ |
| TC-AGENT-137-a | R-AGENT-003 | `generateTitle returns null stub` | ✅ |

---

## 域 AGENT — FileSafety

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/agent/FileSafetyTest.kt`

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-AGENT-160-a | R-AGENT-003 | `buildWriteDeniedPaths includes ssh private keys` | ✅ |
| TC-AGENT-161-a | R-AGENT-003 | `buildWriteDeniedPaths includes shell rc files` | ✅ |
| TC-AGENT-162-a | R-AGENT-003 | `buildWriteDeniedPrefixes ends with separator` | ✅ |
| TC-AGENT-163-a | R-AGENT-003 | `isWriteDenied for absolute blocked path` | ✅ |
| TC-AGENT-164-a | R-AGENT-003 | `isWriteDenied for prefix matches sub path` | ✅ |
| TC-AGENT-165-a | R-AGENT-003 | `isWriteDenied returns false for ordinary user file` | ✅ |
| TC-AGENT-166-a | R-AGENT-003 | `getSafeWriteRoot returns null when env unset` | ✅ |
| TC-AGENT-167-a | R-AGENT-003 | `getReadBlockError non-hermes path returns null` | ✅ |
| TC-AGENT-168-a | R-AGENT-003 | `getReadBlockError hermes cache path returns message` | ✅ |
| TC-AGENT-169-a | R-AGENT-003 | `getReadBlockError expands tilde` | ✅ |

---

## 域 AGENT — TurnLoop (R-AGENT-001)

R-AGENT-001 描述 agent turn-loop 内核，验收以 **E2E 为主**（§3 三脚本要求 `aiResponsePreview` 含脚本种下的 TOKEN，是 agent-level 正确性的充分信号），辅以 `HermesAgentLoopBeforeNextTurnTest` 对 hook 行为的单元覆盖。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-200-a | R-AGENT-001 | 纯聊天（无工具），广播 EXTERNAL_CHAT + TOKEN 要求 | `aiResponsePreview` 含 TOKEN；无 4xx/NonRetriable | e2e | `scripts/e2e/test_api_config_e2e.sh` ✅ |
| TC-AGENT-200-b | R-AGENT-001 | 强制 sleep 工具的 chat | `HermesBridge/Tool dispatch IN+OUT` + `aiResponsePreview` 含 TOKEN（证明 agent 读了 tool_result） | e2e | `scripts/e2e/test_tool_call_e2e.sh` ✅ |
| TC-AGENT-200-c | R-AGENT-001 | 新用户路径（清 api_settings DataStore）+ 内置 OpenRouter key | `aiResponsePreview` 含 TOKEN | e2e | `scripts/e2e/test_builtin_key_e2e.sh` ✅ |
| TC-AGENT-201-a | R-AGENT-001 | `beforeNextTurn` 返回 false on turn 0 | 首次调用前中止，无 chatCompletion 发起 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returnsFalseOnTurn0_abortsBeforeFirstCall` ✅ |
| TC-AGENT-201-b | R-AGENT-001 | `beforeNextTurn` 返回 false after turn 0 | 第 N 次调用前中止 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returnsFalseAfterTurn0_abortsBeforeNthCall` ✅ |
| TC-AGENT-201-c | R-AGENT-001 | `beforeNextTurn` 抛异常 | 被吞，视为 continue | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_throwing_isCaughtAndTreatedAsContinue` ✅ |
| TC-AGENT-201-d | R-AGENT-001 | `beforeNextTurn` 返 true | 正常继续 | unit | `HermesAgentLoopBeforeNextTurnTest#beforeNextTurn_returningTrue_proceedsNormally` ✅ |
| TC-AGENT-201-e | R-AGENT-001 | 无 hook 时 | loop 照常推进 | unit | `HermesAgentLoopBeforeNextTurnTest#noBeforeNextTurn_loopProceedsWithoutHook` ✅ |
| TC-AGENT-202-a | R-AGENT-001 | 自然 stop（无 tool_call 的 final reply） | 终止回合、发送 TurnComplete | unit | 由 `AgentLoopDataTest` + E2E 回合完成日志双重覆盖 ✅ |

---

## 域 AGENT — CredentialPool (R-AGENT-008)

测试类: `CredentialPoolTest` (21 tests) / `CredentialSourcesTest` (12) / `AccountUsageTest` (10) / `UsagePricingTest` (27) — all green 2026-04-26.

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-AGENT-220-a | R-AGENT-008 | 同 provider 配多 key | 轮转选下一个 | unit | `CredentialPoolTest#round robin across keys` 🟢 |
| TC-AGENT-220-b | R-AGENT-008 | 当前 key 401 | 标记 unhealthy + 切下一个 | unit | `CredentialPoolTest#401 marks unhealthy and rotates` 🟢 |
| TC-AGENT-220-c | R-AGENT-008 | 当前 key 429 | 标记限流 + 切下一个 | unit | `CredentialPoolTest#429 rate limited rotates` 🟢 |
| TC-AGENT-221-a | R-AGENT-008 | 池全不健康 | 触发 fallback provider（由 R-AGENT-002 接管） | unit | `CredentialPoolTest#all unhealthy falls through` 🟢 |
| TC-AGENT-222-a | R-AGENT-008 | env / 文件 / keychain / EncryptedPrefs 混合 | 按 (provider, source) 路由到对应 `RemovalStep` | unit | `CredentialSourcesTest#env source routes to env removal step` 🟢 |
| TC-AGENT-223-a | R-AGENT-008 | 统计每 key token / 金额 | `renderAccountUsageLines` 输出 title/provider/windows/details | unit | `AccountUsageTest#renderAccountUsageLines includes title provider and windows` 🟢 |
| TC-AGENT-224-a | R-AGENT-008 | 模型 → 成本查找 | 命中 `UsagePricing` 表 | unit | `UsagePricingTest#getPricingEntry resolves anthropic claude-opus-4` 🟢 |

---

## 域 ACP

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/acp/AcpToolsTest.kt` + `AcpAuthTest.kt`

### TOOL_KIND_MAP + getToolKind

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-001-a | R-ACP-002 | `TOOL_KIND_MAP has canonical file-op bindings` | ✅ |
| TC-ACP-002-a | R-ACP-002 | `TOOL_KIND_MAP has execute bindings` | ✅ |
| TC-ACP-003-a | R-ACP-002 | `TOOL_KIND_MAP has web fetch bindings` | ✅ |
| TC-ACP-004-a | R-ACP-002 | `TOOL_KIND_MAP has thinking binding` | ✅ |
| TC-ACP-005-a | R-ACP-002 | `getToolKind returns mapping when present` + `getToolKind falls back to other for unknown tool` | ✅ |

### makeToolCallId

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-006-a | R-ACP-001 | `makeToolCallId produces prefixed id` | ✅ |
| TC-ACP-007-a | R-ACP-001 | `makeToolCallId returns fresh value each call` | ✅ |

### buildToolTitle

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-010-a | R-ACP-003 | `buildToolTitle terminal short command` | ✅ |
| TC-ACP-011-a | R-ACP-003 | `buildToolTitle terminal long command truncates at 80` | ✅ |
| TC-ACP-012-a | R-ACP-003 | `buildToolTitle terminal with missing command` | ✅ |
| TC-ACP-013-a | R-ACP-003 | `buildToolTitle read_file and write_file use path` + `...with missing path uses question mark` | ✅ |
| TC-ACP-014-a | R-ACP-003 | `buildToolTitle patch includes mode` + `buildToolTitle patch defaults mode to replace` | ✅ |
| TC-ACP-015-a | R-ACP-003 | `buildToolTitle search_files` + `buildToolTitle web_search` | ✅ |
| TC-ACP-016-a | R-ACP-003 | `buildToolTitle web_extract single url` + `... multiple urls shows count` + `... empty falls back` | ✅ |
| TC-ACP-017-a | R-ACP-003 | `buildToolTitle delegate_task short goal` + `... long goal truncates at 60` + `... missing goal uses generic label` | ✅ |
| TC-ACP-018-a | R-ACP-003 | `buildToolTitle execute_code` + `buildToolTitle vision_analyze truncates question` + `... missing uses placeholder` | ✅ |
| TC-ACP-019-a | R-ACP-003 | `buildToolTitle falls back to tool name when unhandled` | ✅ |

### extractLocations

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-020-a | R-ACP-003 | `extractLocations returns empty when no path` | ✅ |
| TC-ACP-021-a | R-ACP-003 | `extractLocations picks up path only` | ✅ |
| TC-ACP-022-a | R-ACP-003 | `extractLocations picks up path plus offset as line` | ✅ |
| TC-ACP-023-a | R-ACP-003 | `extractLocations prefers offset over line when both present` | ✅ |
| TC-ACP-024-a | R-ACP-003 | `extractLocations accepts bare line when no offset` | ✅ |

### buildToolStart

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-030-a | R-ACP-003 | `buildToolStart wires toolCallId title kind locations rawInput` | ✅ |
| TC-ACP-031-a | R-ACP-003 | `buildToolStart write_file produces diff content` | ✅ |
| TC-ACP-032-a | R-ACP-003 | `buildToolStart patch replace mode produces diff content` | ✅ |
| TC-ACP-033-a | R-ACP-003 | `buildToolStart terminal renders command with dollar prefix` | ✅ |
| TC-ACP-034-a | R-ACP-003 | `buildToolStart read_file renders reading message` | ✅ |
| TC-ACP-035-a | R-ACP-003 | `buildToolStart search_files renders searching message` + `... defaults target to content` | ✅ |
| TC-ACP-036-a | R-ACP-003 | `buildToolStart generic tool falls through to json dump` | ✅ |

### buildToolComplete

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-040-a | R-ACP-003 | `buildToolComplete wires id kind status` | ✅ |
| TC-ACP-041-a | R-ACP-003 | `buildToolComplete null result becomes empty text block` | ✅ |
| TC-ACP-042-a | R-ACP-003 | `buildToolComplete truncates long result` | ✅ |
| TC-ACP-043-a | R-ACP-003 | `buildToolComplete short result passes through` | ✅ |

### Auth

| TC | 验 R | 测试方法 | 状态 |
|---|---|---|---|
| TC-ACP-050-a | R-ACP-001 | `detectProvider returns null when no credentials configured` | ✅ |
| TC-ACP-051-a | R-ACP-001 | `hasProvider mirrors detectProvider null` | ✅ |
| TC-ACP-052-a | R-ACP-001 | `detectProvider stays null across multiple invocations` | ✅ |

### Copilot ACP client (R-ACP-004)

测试类: `CopilotAcpClientTest` (19 tests) — handshake 默认常量、tool 格式化、`<tool_call>` 解析、`close()` 幂等、`_ensurePathWithinCwd` 安全。真正的 E2E 握手走 `copilot` 子进程需 integration 环境；这里覆盖 JVM 可验部分。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-ACP-060-a | R-ACP-004 | 默认构造 | `apiKey=copilot-acp`, `baseUrl=acp://copilot` | unit | `CopilotAcpClientTest#default client has acp marker base url and sentinel key` 🟢 |
| TC-ACP-061-a | R-ACP-004 | 远端 tool schema 传给 `_formatMessagesAsPrompt` | prompt 含 `Available tools` + 工具名 | unit | `CopilotAcpClientTest#formatMessagesAsPrompt includes tool schemas when tools provided` 🟢 |
| TC-ACP-062-a | R-ACP-004 | `<tool_call>{...}</tool_call>` 文本 | 解出 tool call + 剩余文本 | unit | `CopilotAcpClientTest#extractToolCallsFromText parses tool_call block` 🟢 |
| TC-ACP-063-a | R-ACP-004 | `close()` 重入 | `isClosed` 翻转一次；第二次无异常 | unit | `CopilotAcpClientTest#close flips isClosed and is idempotent` 🟢 |

---

## 域 TOOL

测试类: 散落在 `hermes-android/src/test/java/com/xiaomo/hermes/hermes/tools/*.kt`（Registry / Approval / BudgetConfig / TodoTool / ... ）+ `tools/FileOperations`、`tools/FileTools`、`tools/MemoryTool` 相关 Robolectric 补测。

### Registry.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-001-a | R-TOOL-001 | `register("foo", "a", h1)` 再 `register("foo", "b", h2)` | ERROR 日志；`get("foo").toolset=="a"` | unit | `RegistryTest#register same name different toolset keeps first` ✅ |
| TC-TOOL-002-a | R-TOOL-001 | 两次 `register("mcp-x", "mcp", h)` | 无 ERROR；后写覆盖 | unit | `RegistryTest#mcp tools allow re-register` ✅ |
| TC-TOOL-003-a | R-TOOL-001 | 同 toolset 内两次 register，后一次 checkFn 不覆盖 | `_toolsetChecks` 保留首次函数 | unit | `RegistryTest#toolset checkFn is first-write-wins` ✅ |
| TC-TOOL-004-a | R-TOOL-001 | `dispatch("nonexistent", {})` | JSON `{"error":"Unknown tool: nonexistent"}` | unit | `RegistryTest#dispatch unknown returns structured error` ✅ |
| TC-TOOL-005-a | R-TOOL-001 | 注册 entry handler=null，`dispatch(name, {})` | `{"error":"Tool '<name>' has no handler"}` | unit | `RegistryTest#dispatch null handler returns error` ✅ |
| TC-TOOL-006-a | R-TOOL-001 | handler 抛 `RuntimeException("boom")` | `{"error":"Tool execution failed: RuntimeException: boom"}` | unit | `RegistryTest#dispatch handler exception wraps` ✅ |
| TC-TOOL-007-a | R-TOOL-001 | `deregister` 最后一条 | `_toolsetChecks[toolset]` 与 alias 清空 | unit | `RegistryTest#deregister last entry clears toolset` ✅ |
| TC-TOOL-008-a | R-TOOL-001 | `getDefinitions` 当 checkFn 抛异常 | 工具被静默跳过 | unit | `RegistryTest#getDefinitions skips failing checkFn` ✅ |
| TC-TOOL-009-a | R-TOOL-001 | 多工具共享同一 toolset | checkFn 单次调用内只执行一次 | unit | `RegistryTest#getDefinitions memoizes per-call` ✅ |
| TC-TOOL-010-a | R-TOOL-001 | 默认 entry 无 pinned | 返回 default 或 50000 | unit | `RegistryTest#getMaxResultSize default hierarchy` ✅ |
| TC-TOOL-010-b | R-TOOL-001 | `read_file` 默认 | 返回 `Double.POSITIVE_INFINITY` | unit | `RegistryTest#getMaxResultSize read_file is unlimited` ✅ |
| TC-TOOL-011-a | R-TOOL-001 | `toolError("e", mapOf("k" to 1))` | 合法 JSON with `error` + `k` 字段 | unit | `RegistryTest#toolError serializes extra fields` ✅ |
| TC-TOOL-012-a | R-TOOL-001 | `toolResult(mapOf("a" to 1))` | JSON `{"a":1}`，数字不转字符串 | unit | `RegistryTest#toolResult preserves numeric types` ✅ |
| TC-TOOL-013-a | R-TOOL-001 | `registerToolsetAlias("old","new")` 再重写 alias | WARN 日志 + alias 改写 | unit | `RegistryTest#registerToolsetAlias overwrite warns` ✅ |
| TC-TOOL-014-a | R-TOOL-001 | `isToolsetAvailable` checkFn 抛异常 | 返回 false（非 throw） | unit | `RegistryTest#isToolsetAvailable exception returns false` ✅ |
| TC-TOOL-015-a | R-TOOL-001 | `getDefinitions` 结构 | 顶层 `type=="function"`、`function.name/description/parameters` | unit | `RegistryTest#getDefinitions emits OpenAI schema` ✅ |

### Approval.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-020-a | R-TOOL-002 | `_normalizeCommandForDetection("  rm   -rf  /x")` | `"rm -rf /x"` | unit | `ApprovalTest#normalize collapses whitespace` ✅ |
| TC-TOOL-020-b | R-TOOL-002 | 全宽输入 `ｒｍ　－ｒｆ　／` | NFKC 归一后被 `detectDangerousCommand` 命中 | unit | `ApprovalTest#detectDangerousCommand detects with fullwidth normalization (NFKC)` ✅ |
| TC-TOOL-021-a | R-TOOL-002 | `detectDangerousCommand("rm -rf /")` | Triple(true, pattern, action) | unit | `ApprovalTest#detectDangerousCommand classifies rm -rf` ✅ |
| TC-TOOL-022-a | R-TOOL-002 | `promptDangerousApproval(cmd, cb=null)` | 返回 false（拒绝） | unit | `ApprovalTest#promptDangerousApproval no callback denies` ✅ |
| TC-TOOL-023-a | R-TOOL-002 | 设置 `HERMES_SANDBOX_ENV=1` | approval 直接通过 | unit | `ApprovalTest#sandbox env skips approval` ✅ |
| TC-TOOL-024-a | R-TOOL-002 | cron session approveMode != "approve" | 阻塞并返回 false | unit | `ApprovalTest#cron session denies when approval disabled` ✅ |
| TC-TOOL-025-a | R-TOOL-002 | gateway approval 5 分钟未响应 | 超时拒绝 | unit | `ApprovalTest#gateway approval times out at 5 min` ✅ |
| TC-TOOL-026-a | R-TOOL-002 | 用户选 "always" | 写入 YAML 文件中 `commands.always` 列表 | unit | `ApprovalTest#always choice persists to yaml` ✅ |
| TC-TOOL-027-a | R-TOOL-002 | `_smartApprove` 未命中规则 | 调用 escalate callback | unit | `ApprovalTest#smartApprove falls through to escalate` ✅ |
| TC-TOOL-028-a | R-TOOL-002 | `isApproved(cmd)` 用别名 key | 命中 pre-approved 列表 | unit | `ApprovalTest#isApproved matches alias keys` ✅ |
| TC-TOOL-029-a | R-TOOL-002 | Tirith 返回 "always" | 降级为 "allow" | unit | `ApprovalTest#tirith always downgrades to allow` ✅ |

### BudgetConfig.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-045-a | R-TOOL-003 | `PINNED_THRESHOLDS["read_file"]` | `Double.POSITIVE_INFINITY` | unit | `BudgetConfigTest#read_file is unlimited` ✅ |
| TC-TOOL-045-b | R-TOOL-003 | `resolveThreshold("read_file", toolOverrides=mapOf("read_file" to 100.0))` | 仍返回 `POSITIVE_INFINITY`（pinned 不可被覆盖） | unit | `BudgetConfigTest#resolveThreshold returns pinned value for read_file regardless of overrides` ✅ |
| TC-TOOL-046-a | R-TOOL-003 | pinned 有值 → 优先 pinned | 返回 pinned | unit | `BudgetConfigTest#pinned overrides default` ✅ |
| TC-TOOL-047-a | R-TOOL-003 | 任意合法 threshold | 返回 Double | unit | `BudgetConfigTest#returns Double type` ✅ |
| TC-TOOL-048-a | R-TOOL-003 | 默认 fallback | 50000 | unit | `BudgetConfigTest#default is 50000` ✅ |

### TodoTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-055-a | R-TOOL-001 | `VALID_STATUSES` | `{"pending", "in_progress", "completed", "cancelled"}` | unit | `TodoToolTest#VALID_STATUSES has four values` ✅ |
| TC-TOOL-056-a | R-TOOL-001 | `merge=false`，2 条新 list | 整表被替换 | unit | `TodoToolTest#merge false replaces all` ✅ |
| TC-TOOL-057-a | R-TOOL-001 | `merge=true` 按 id 更新 | 同 id 的被覆盖，其他保留 | unit | `TodoToolTest#merge true updates by id` ✅ |
| TC-TOOL-058-a | R-TOOL-001 | `_validate` 输入异常 map | 静默跳过条目 | unit | `TodoToolTest#_validate never throws` ✅ |
| TC-TOOL-059-a | R-TOOL-001 | `_dedupeById` 同 id 两条 | 保留最后一条 | unit | `TodoToolTest#_dedupeById keeps last` ✅ |
| TC-TOOL-060-a | R-TOOL-001 | merge 模式下 status=`"foo"` | 忽略该字段（不是整条） | unit | `TodoToolTest#merge invalid status silent ignore` ✅ |
| TC-TOOL-061-a | R-TOOL-001 | `formatForInjection([])` | null | unit | `TodoToolTest#formatForInjection empty returns null` ✅ |
| TC-TOOL-062-a | R-TOOL-001 | marker 映射 pending/in_progress/etc | 对应符号 | unit | `TodoToolTest#display markers match python` ✅ |
| TC-TOOL-063-a | R-TOOL-001 | store==null | 返回 toolError | unit | `TodoToolTest#null store returns error` ✅ |
| TC-TOOL-064-a | R-TOOL-001 | set action | 返回 `{"todos":[...]}` shape | unit | `TodoToolTest#set action returns canonical shape` ✅ |

### FileOperations.kt + FileTools.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-070-a | R-TOOL-001 | `_isWriteDenied("~/.ssh/id_rsa")` | true | unit | `FileToolsTest#_isWriteDenied ssh private key` 🟢 |
| TC-TOOL-071-a | R-TOOL-001 | `_isWriteDenied("~/.ssh/config")` | true (prefix) | unit | `FileToolsTest#_isWriteDenied ssh prefix` 🟢 |
| TC-TOOL-072-a | R-TOOL-001 | 设置 `HERMES_WRITE_SAFE_ROOT=/tmp`，写 `/var/x` | 拒绝 | unit | `FileToolsTest#safe root enforces write jail` 🟢 |
| TC-TOOL-073-a | R-TOOL-001 | `readFile` 文件超行上限 | 返回 truncated 标记 | unit | `FileToolsTest#readFile honors offset and limit` 🟢 |
| TC-TOOL-074-a | R-TOOL-001 | `readFile` 扩展名 `.bin` | 拒绝读 | unit | `FileToolsTest#readFile rejects binary ext` 🟢 |
| TC-TOOL-075-a | R-TOOL-001 | `readFile` 不存在路径 | error 含 suggestions | unit | `FileToolsTest#readFile missing gives suggestions` 🟢 |
| TC-TOOL-076-a | R-TOOL-001 | `writeFile("a/b/c.txt")` 不存在父目录 | 自动创建 | unit | `FileToolsTest#writeFile creates parents` 🟢 |
| TC-TOOL-077-a | R-TOOL-001 | `moveFile` 跨分区 | 复制+删除回退 | unit | `FileToolsTest#moveFile cross-device fallback` 🟢 |
| TC-TOOL-078-a | R-TOOL-001 | `search` 超结果上限 | truncated 标记 | unit | `FileToolsTest#search truncates at cap` 🟢 |
| TC-TOOL-079-a | R-TOOL-001 | `_exec` 命令卡 30s（timeout=1s） | destroyForcibly + exit 124 | unit | `FileToolsTest#_exec timeout destroys` 🟢 |
| TC-TOOL-080-a | R-TOOL-001 | `_checkLint` 缺 linter | 静默跳过 | unit | `FileToolsTest#_checkLint skip when absent` 🟢 |

### FileOperations.kt (Android 额外)

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-095-a | R-TOOL-001 | `_isBlockedDevice("/dev/null")` | true | unit | `FileOperationsTest#blocks /dev character device` 🟢 |
| TC-TOOL-096-a | R-TOOL-001 | `_checkSensitivePath("/etc/shadow")` | 拒绝 | unit | `FileOperationsTest#checkSensitivePath etc shadow` 🟢 |
| TC-TOOL-097-a | R-TOOL-001 | 调 `notifyOtherToolCall` | read dedup 计数清零 | unit | `FileOperationsTest#notifyOtherToolCall resets dedup` 🟢 |
| TC-TOOL-098-a | R-TOOL-001 | 读后外部 `touch` mtime | `_checkFileStaleness` 报告 true | unit | `FileOperationsTest#_checkFileStaleness detects external write` 🟢 |
| TC-TOOL-099-a | R-TOOL-001 | 同 task 内 2 次 readFile 同文件 | hit cache | unit | `FileOperationsTest#per-task cache on same file` 🟢 |
| TC-TOOL-100-a | R-TOOL-001 | 塞 dedup map 超 cap | 老条目被 LRU 驱逐 | unit | `FileOperationsTest#LRU eviction bounds caches` 🟢 |
| TC-TOOL-101-a | R-TOOL-001 | handler(args=nonMap) | toolError，不抛 | unit | `FileOperationsTest#handler tolerates non-map args` 🟢 |

### MemoryTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-110-a | R-TOOL-001 | `ENTRY_DELIMITER` 值 | 完全等于 `"\n§\n"` | unit | `MemoryToolTest#delimiter constant` 🟢 |
| TC-TOOL-111-a | R-TOOL-001 | `_scanMemoryContent(U+202E)` | 返回 violation | unit | `MemoryToolTest#invisible unicode trips scan` 🟢 |
| TC-TOOL-112-a | R-TOOL-001 | `_scanMemoryContent("IGNORE PREVIOUS")` | 返回 violation | unit | `MemoryToolTest#injection pattern trips scan` 🟢 |
| TC-TOOL-113-a | R-TOOL-001 | 两 target {conversation_memory, user_memory} 并写 | 文件分离 | integration | `MemoryToolTest#two targets separate files` 🟢 |
| TC-TOOL-114-a | R-TOOL-001 | 重复 `add` 同一 token | 仅一条存在 | unit | `MemoryToolTest#add is idempotent` 🟢 |
| TC-TOOL-115-a | R-TOOL-001 | 累计 add 超 token 上限 | 拒绝并返错 | unit | `MemoryToolTest#add enforces token ceiling` 🟢 |
| TC-TOOL-116-a | R-TOOL-001 | `replace(needle,new)` 有多个匹配不同上下文 | 返回 "be more specific" 错误 | unit | `MemoryToolTest#replace ambiguous refuses` 🟢 |
| TC-TOOL-117-a | R-TOOL-001 | `replace(needle, "")` | 拒绝 | unit | `MemoryToolTest#replace empty new refused` 🟢 |
| TC-TOOL-118-a | R-TOOL-001 | 并发两次 add | 每次都独占写锁，无 race | integration | `MemoryToolTest#file lock serializes` 🟢 |
| TC-TOOL-119-a | R-TOOL-001 | system prompt snapshot 之后 再 add | snapshot 未变 | unit | `MemoryToolTest#prompt snapshot frozen` 🟢 |

### PathSecurity.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-130-a | R-TOOL-002 | `validateWithinDir("/a", "/a/../etc")` | 错误字符串 | unit | `PathSecurityTest#validateWithinDir rejects traversal` ✅ |
| TC-TOOL-131-a | R-TOOL-002 | 任意非法输入 | 返回 string，不抛 | unit | `PathSecurityTest#never throws on invalid input` ✅ |
| TC-TOOL-132-a | R-TOOL-002 | `hasTraversalComponent("a/../b")` | true，不触 IO | unit | `PathSecurityTest#hasTraversalComponent is pure` ✅ |

### SkillsHub.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-140-a | R-TOOL-001 | `normalizeBundlePath("../out")` | 拒绝 | unit | `SkillsHubTest#rejects traversal path` 🟢 |
| TC-TOOL-141-a | R-TOOL-001 | 设 env `HERMES_GITHUB_TOKEN` + session token | 优先 env | unit | `SkillsHubTest#_resolveToken env wins` 🟢 |
| TC-TOOL-142-a | R-TOOL-001 | 短时间内 5 次请求 | 第 >N 次被限流 | unit | `SkillsHubTest#_rateLimited trips` 🟢 |
| TC-TOOL-143-a | R-TOOL-001 | 同名 skill 不同 trust | 按 trust 去重 | unit | `SkillsHubTest#GitHubSource search dedupe by trust` 🟢 |
| TC-TOOL-144-a | R-TOOL-001 | fetch 无 SKILL.md | 返回错误 | unit | `SkillsHubTest#fetch requires SKILL.md` 🟢 |
| TC-TOOL-145-a | R-TOOL-001 | GitHub API `truncated=true` | 拒绝全树下载 | unit | `SkillsHubTest#truncated tree refused` 🟢 |
| TC-TOOL-146-a | R-TOOL-001 | 多 source 搜索 | Android 顺序执行 | unit | `SkillsHubTest#parallel search is sequential on android` 🟢 |
| TC-TOOL-147-a | R-TOOL-001 | `installFromQuarantine` | Android stub 返回 toolError | unit | `SkillsHubTest#install quarantine stub` 🟢 |
| TC-TOOL-148-a | R-TOOL-001 | 同 bundle 不同顺序文件 | `bundleContentHash` 相同 | unit | `SkillsHubTest#bundleContentHash deterministic` 🟢 |

### SkillManagerTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-160-a | R-TOOL-001 | dispatch skill CRUD action | 返回 `{"error":"not supported on Android"}` | unit | `SkillManagerToolTest#all CRUD android denied` 🟢 |
| TC-TOOL-161-a | R-TOOL-001 | `_validateName("a b")` 含空格 | 拒绝 | unit | `SkillManagerToolTest#name regex rejects space` 🟢 |
| TC-TOOL-161-b | R-TOOL-001 | name 超长 | 拒绝 | unit | `SkillManagerToolTest#name length cap` 🟢 |
| TC-TOOL-162-a | R-TOOL-001 | 单文件 content 超 cap | 拒绝 | unit | `SkillManagerToolTest#content size cap` 🟢 |
| TC-TOOL-163-a | R-TOOL-001 | 写中途模拟 crash | 旧文件保留（原子替换） | integration | `SkillManagerToolTest#atomic write crash safe` 🟢 |

### SkillsGuard.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-170-a | R-TOOL-001 | scanFile(`.md`) | 进入扫描 | unit | `SkillsGuardTest#scan accepts md` 🟢 |
| TC-TOOL-170-b | R-TOOL-001 | scanFile(`.zip`) | 跳过 | unit | `SkillsGuardTest#scan rejects binary` 🟢 |
| TC-TOOL-171-a | R-TOOL-001 | 同行命中两次同 pattern | 去重 | unit | `SkillsGuardTest#findings dedupe per line` 🟢 |
| TC-TOOL-172-a | R-TOOL-001 | 长匹配 > 120 char | 截断 | unit | `SkillsGuardTest#long match truncates` 🟢 |
| TC-TOOL-173-a | R-TOOL-001 | 同行多不可见字符 | 仅报一条 | unit | `SkillsGuardTest#invisible chars per-line single entry` 🟢 |
| TC-TOOL-174-a | R-TOOL-001 | 安装内含 symlink 指向 bundle 外 | 拒绝 | unit | `SkillsGuardTest#symlink escape blocked` 🟢 |
| TC-TOOL-175-a | R-TOOL-001 | `.sh` 有 exec 位 | 通过（白名单） | unit | `SkillsGuardTest#exec bit allowed for whitelist` 🟢 |
| TC-TOOL-175-b | R-TOOL-001 | `.py` 有 exec 位 | 拒绝 | unit | `SkillsGuardTest#exec bit rejected for non-whitelist` 🟢 |
| TC-TOOL-176-a | R-TOOL-001 | critical finding | verdict=block | unit | `SkillsGuardTest#_determineVerdict blocks critical` 🟢 |
| TC-TOOL-177-a | R-TOOL-001 | 策略 matrix 各组合 | 决策一致 | unit | `SkillsGuardTest#shouldAllowInstall matrix` 🟢 |
| TC-TOOL-178-a | R-TOOL-001 | trustLevel 大小写混写前缀 | 归一小写 | unit | `SkillsGuardTest#_resolveTrustLevel normalizes` 🟢 |

### SkillsSync.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-185-a | R-TOOL-001 / R-SKILL-003 | env `HERMES_BUNDLED_SKILLS=/custom/path` | 用该路径替代默认 `getHermesHome()/bundled_skills` 作为 bundled 源（Python `tools/skills_sync.py:46-48`：**路径覆盖**，非 CSV allow-list；TC 原期望被修正为与 Python 一致） | unit | `SkillsSyncTest#respects env filter` 🟢 |
| TC-TOOL-186-a | R-TOOL-001 / R-SKILL-003 | manifest 非预期格式 | 报错拒绝 | unit | `SkillsSyncTest#manifest format enforced` 🟢 |
| TC-TOOL-187-a | R-TOOL-001 / R-SKILL-003 | `_readSkillName` 目标超 4000 byte | 只读前 4000 | unit | `SkillsSyncTest#frontmatter read capped` 🟢 |
| TC-TOOL-188-a | R-TOOL-001 / R-SKILL-003 | 用户改过 skill 再 sync | 不覆盖 | integration | `SkillsSyncTest#user modification protected` 🟢 |
| TC-TOOL-189-a | R-TOOL-001 / R-SKILL-003 | dest 首次存在 | skip | unit | `SkillsSyncTest#skip when dest exists first time` 🟢 |
| TC-TOOL-190-a | R-TOOL-001 / R-SKILL-003 | 复制中途异常 | `.bak` 回滚 | integration | `SkillsSyncTest#copy failure rolls back bak` 🟢 |
| TC-TOOL-191-a | R-TOOL-001 / R-SKILL-003 | `resetBundledSkill(name)` | stub 返回错误 | unit | `SkillsSyncTest#reset is stubbed on android` 🟢 |

### SkillsTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-195-a | R-TOOL-001 | `loadEnv` 空行 / 注释 / `a=b` | 仅 `a=b` 入表 | unit | `SkillsToolTest#loadEnv parses` 🟢 |
| TC-TOOL-196-a | R-TOOL-001 | `_parseFrontmatter` 无前导 `---` | 返回空 | unit | `SkillsToolTest#frontmatter must start with ---` 🟢 |
| TC-TOOL-197-a | R-TOOL-001 | env var 名非法 char | 过滤 | unit | `SkillsToolTest#env var name regex` 🟢 |
| TC-TOOL-198-a | R-TOOL-001 | env 文件有 key | 满足 | unit | `SkillsToolTest#env file satisfies` 🟢 |
| TC-TOOL-198-b | R-TOOL-001 | 进程 env 有 key | 满足 | unit | `SkillsToolTest#process env satisfies` 🟢 |
| TC-TOOL-199-a | R-TOOL-001 | skills dir 含 `node_modules/` | 扫描跳过 | unit | `SkillsToolTest#findAllSkills excludes noise dirs` 🟢 |

### TerminalTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-200-a | R-TOOL-001 | `_getSessionPlatform` 无 session | 默认 "android" | unit | `TerminalToolTest#platform defaults android` 🟢 |
| TC-TOOL-201-a | R-TOOL-001 | 远端 env 字段被中性化 | 返回固定 stub | unit | `TerminalToolTest#remote env neutralized` 🟢 |
| TC-TOOL-210-a | R-TOOL-001 | `background=true` | toolError | unit | `TerminalToolTest#background not supported on android` 🟢 |
| TC-TOOL-211-a | R-TOOL-001 | timeout=99999 | clamp 到上限 | unit | `TerminalToolTest#timeout double clamp` 🟢 |
| TC-TOOL-212-a | R-TOOL-001 | shell 路径 | 固定 `/system/bin/sh` | unit | `TerminalToolTest#android shell path fixed` 🟢 |
| TC-TOOL-213-a | R-TOOL-001 | 超时进程 | exit=124 | unit | `TerminalToolTest#timeout returns 124` 🟢 |
| TC-TOOL-214-a | R-TOOL-001 | `_validateWorkdir("a; b")` | 拒绝 | unit | `TerminalToolTest#validateWorkdir rejects shell chars` 🟢 |
| TC-TOOL-215-a | R-TOOL-001 | `_parseEnvVar("bad input")` | 默认 | unit | `TerminalToolTest#parseEnvVar tolerates bad` 🟢 |
| TC-TOOL-216-a | R-TOOL-001 | 调用 sudo 路径 | toolError stub | unit | `TerminalToolTest#sudo stub` 🟢 |

### CodeExecutionTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-225-a | R-TOOL-001 | 任意 `executeCode` 入参 | 返回 toolError | unit | `CodeExecutionToolTest#always toolError on android` 🟢 |
| TC-TOOL-226-a | R-TOOL-001 | `_rpcServerLoop()` | 抛 `UnsupportedOperation` | unit | `CodeExecutionToolTest#rpc loops throw` 🟢 |
| TC-TOOL-227-a | R-TOOL-001 | `buildExecuteCodeSchema("python")` / `("bash")` | description 不同 | unit | `CodeExecutionToolTest#schema description varies by mode` 🟢 |
| TC-TOOL-228-a | R-TOOL-001 | 设 env `TERMINAL_CWD=/tmp/x` | 返回 `/tmp/x`（Python `tools/code_execution_tool.py:1417-1428`：env 名为 TERMINAL_CWD，原 TC 写 HERMES_CHILD_CWD 是错的，以 Python 上游为准） | unit | `CodeExecutionToolTest#_resolveChildCwd env wins` 🟢 |

### ClarifyTool.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-235-a | R-TOOL-001 | `question=""` | toolError | unit | `ClarifyToolTest#empty question refused` ✅ |
| TC-TOOL-236-a | R-TOOL-001 | `choices=[""," "]` | 视作 null | unit | `ClarifyToolTest#all-blank choices become null` ✅ |
| TC-TOOL-237-a | R-TOOL-001 | 超 `MAX_CHOICES` | 截断 | unit | `ClarifyToolTest#MAX_CHOICES truncates` ✅ |
| TC-TOOL-238-a | R-TOOL-001 | callback=null | error | unit | `ClarifyToolTest#no callback returns error` ✅ |
| TC-TOOL-239-a | R-TOOL-001 | callback throw | toolError 包装 | unit | `ClarifyToolTest#callback exception wraps` ✅ |

### Interrupt.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-245-a | R-TOOL-001 | 两线程各 `setInterrupted` | 独立状态 | unit | `InterruptTest#thread-local state` ✅ |
| TC-TOOL-246-a | R-TOOL-001 | 线程 A 设，线程 B 查 | B 得 false | unit | `InterruptTest#isInterrupted only current thread` ✅ |
| TC-TOOL-247-a | R-TOOL-001 | `_ThreadAwareEventProxy.wait(timeout=0)` | 立即返回 | unit | `InterruptTest#wait is non-blocking when disabled` ✅ |

### CheckpointManager.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-255-a | R-TOOL-001 | `enabled` 默认 | false；所有接口 no-op | unit | `CheckpointManagerTest#default disabled noop` ✅ |
| TC-TOOL-256-a | R-TOOL-001 | 首次探 `git` 再次探 | 进程级缓存命中 | unit | `CheckpointManagerTest#_gitAvailable cached` ✅ |
| TC-TOOL-257-a | R-TOOL-001 | cwd=`/` 启用 | 拒绝 | unit | `CheckpointManagerTest#rejects root cwd` ✅ |
| TC-TOOL-257-b | R-TOOL-001 | cwd=home | 拒绝 | unit | `CheckpointManagerTest#rejects home cwd` ✅ |
| TC-TOOL-258-a | R-TOOL-001 | 同 turn 对同文件两次 checkpoint | 第二次 skip | unit | `CheckpointManagerTest#per-turn dedupe` ✅ |
| TC-TOOL-259-a | R-TOOL-001 | 一次 checkpoint 超文件数上限 | 拒绝 | unit | `CheckpointManagerTest#file count ceiling` ✅ |
| TC-TOOL-260-a | R-TOOL-001 | 空 diff | 不 commit | unit | `CheckpointManagerTest#no diff no commit` ✅ |
| TC-TOOL-261-a | R-TOOL-001 | 环境隔离：GIT_*、config author | 使用内部常量 | unit | `CheckpointManagerTest#git env isolated` ✅ |
| TC-TOOL-262-a | R-TOOL-001 | `_validateCommitHash("notahash")` | false | unit | `CheckpointManagerTest#_validateCommitHash strict` ✅ |
| TC-TOOL-263-a | R-TOOL-001 | `_validateFilePath("/etc/passwd")` | 拒绝 | unit | `CheckpointManagerTest#absolute path rejected` ✅ |
| TC-TOOL-264-a | R-TOOL-001 | 相同 project path 两次启动 | shadow repo path 相同 | unit | `CheckpointManagerTest#shadow repo deterministic` ✅ |
| TC-TOOL-265-a | R-TOOL-001 | `restore(id)` 先调 snapshot | snapshot 先于切换 | unit | `CheckpointManagerTest#restore snapshots first` ✅ |
| TC-TOOL-266-a | R-TOOL-001 | `listCheckpoints()` | 附带 --shortstat 数字 | unit | `CheckpointManagerTest#list includes shortstat` ✅ |

### Browser / Delegate / Cron / HomeAssistant / Discord / SendMessage / Feishu Docs / AI services（Mid / Low tier）

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-TOOL-280-a | R-TOOL-001 | `_mergeBrowserPath("/opt:/usr/bin")` | 追加 SANE_PATH 条目 | unit | `BrowserToolTest#merges browser path` 🟢 |
| TC-TOOL-281-a | R-TOOL-001 | 缺 env `BROWSER_CDP_URL` | toolError | unit | `BrowserCdpToolTest#requires env` 🟢 |
| TC-TOOL-282-a | R-TOOL-001 | Camofox 任意接口 | toolError | unit | `BrowserCamofoxToolTest#android toolError` 🟢 |
| TC-TOOL-283-a | R-TOOL-001 | 构造 state identity | 确定性 hash | unit | `BrowserCamofoxStateTest#identity deterministic` ✅ |
| TC-TOOL-290-a | R-TOOL-001 | `DelegateTool.invoke` 内部允许集 | 仅白名单 | unit | `DelegateToolTest#blocks non-allowed tools` ✅ |
| TC-TOOL-291-a | R-CRON-001 | CronjobTools.create | toolError | unit | `CronjobToolsTest#create denied on android` ✅ |
| TC-TOOL-291-b | R-CRON-001 | CronjobTools.list | toolError | unit | `CronjobToolsTest#list denied on android` ✅ |
| TC-TOOL-292-a | R-TOOL-001 | entity id `switch.foo` | 接受 | unit | `HomeassistantToolTest#entity id valid` ✅ |
| TC-TOOL-292-b | R-TOOL-001 | entity id `bad id` | 拒绝 | unit | `HomeassistantToolTest#entity id invalid` ✅ |
| TC-TOOL-293-a | R-TOOL-001 | domain=`shell_command` | 拒绝 | unit | `HomeassistantToolTest#blocked domains` ✅ |
| TC-TOOL-300-a | R-TOOL-001 | Discord intent flag 位值 | 四个位正确 | unit | `DiscordToolTest#intent flags` 🟢 |
| TC-TOOL-301-a | R-TOOL-001 | topic=`main/1234` | 接受 | unit | `SendMessageToolTest#telegram topic valid` 🟢 |
| TC-TOOL-301-b | R-TOOL-001 | topic=`main/abc` | 拒绝 | unit | `SendMessageToolTest#telegram topic invalid` 🟢 |
| TC-TOOL-302-a | R-TOOL-001 | feishu target `oc_xx` | 接受 | unit | `SendMessageToolTest#feishu target valid` 🟢 |
| TC-TOOL-303-a | R-TOOL-001 | weixin target 任意字符串 | 宽松接受 | unit | `SendMessageToolTest#weixin target permissive` 🟢 |
| TC-TOOL-304-a | R-TOOL-001 | `_PHONE_PLATFORMS` | `{signal, sms, whatsapp}` | unit | `SendMessageToolTest#_PHONE_PLATFORMS equals signal sms whatsapp` 🟢 |
| TC-TOOL-310-a | R-TOOL-001 | ImageGenerationTool 常量 | 等于 Python | unit | `ImageGenerationToolTest#constants match python` ✅ |
| TC-TOOL-311-a | R-TOOL-001 | 默认 tts 提供商 | 匹配 Python | unit | `TtsToolTest#default provider` 🟢 |
| TC-TOOL-312-a | R-TOOL-001 | 文件尺寸超上限 | 拒绝 | unit | `TranscriptionToolsTest#file size cap` 🟢 |
| TC-TOOL-313-a | R-TOOL-001 | `NeuTtsSynth.writeWav` | WAV 头 + PCM | unit | `NeuTtsSynthTest#writes wav header` ✅ |
| TC-TOOL-320-a | R-TOOL-001 | SessionSearchTool 并发调用 | 受 semaphore 限 | unit | `SessionSearchToolTest#concurrent bounded` ✅ |
| TC-TOOL-321-a | R-TOOL-001 | `AnsiStrip("\u001B[31mx\u001B[0m")` | `"x"` | unit | `AnsiStripTest#strips ansi` ✅ |
| TC-TOOL-322-a | R-TOOL-001 | `BinaryExtensions.contains(".pdf")` | false | unit | `BinaryExtensionsTest#pdf not in set` ✅ |
| TC-TOOL-323-a | R-TOOL-001 | `FuzzyMatch("ﬁle", "file")` | 匹配成功 | unit | `FuzzyMatchTest#unicode normalization` ✅ |
| TC-TOOL-324-a | R-TOOL-001 | V4A `*** Begin Patch` 解析 | 正确拆分 | unit | `PatchParserTest#V4A structure` ✅ |
| TC-TOOL-325-a | R-TOOL-001 | OpenrouterClient 两次调用 | 同一 instance；timeout=120s | unit | `OpenrouterClientTest#singleton + timeout` 🟢 |
| TC-TOOL-326-a | R-TOOL-001 | OsvCheck 网络 fail | 返回 ok 结果 | unit | `OsvCheckTest#fail-open on network error` ✅ |
| TC-TOOL-327-a | R-TOOL-001 | ProcessRegistry 塞满 cap | 最早的被驱逐 | unit | `ProcessRegistryTest#capacity cap` ✅ |
| TC-TOOL-328-a | R-TOOL-001 | ToolResultStorage 三层 | 每层在各自失败分支生效 | unit | `ToolResultStorageTest#three tier fallback` ✅ |
| TC-TOOL-329-a | R-TOOL-001 | MixtureOfAgentsTool 默认模型配置 | 4 reference + 1 aggregator | unit | `MixtureOfAgentsToolTest#4+1 defaults` 🟢 |
| TC-TOOL-330-a | R-TOOL-001 | WebTools schema | 字段 descriptions 匹配 Python | unit | `WebToolsTest#schema text matches` 🟢 |
| TC-TOOL-331-a | R-TOOL-001 | `UrlSafety.isSafe("http://10.0.0.1")` | false | unit | `UrlSafetyTest#private IP rejected` ✅ |
| TC-TOOL-332-a | R-TOOL-001 | `UrlSafety.isSafe("http://metadata.google.internal")` | false | unit | `UrlSafetyTest#cloud metadata rejected` ✅ |
| TC-TOOL-333-a | R-TOOL-001 | WebsitePolicy 默认 | disabled；30s cache | unit | `WebsitePolicyTest#default disabled + cache` ✅ |
| TC-TOOL-334-a | R-TOOL-001 | XaiHttp UA 字符串 | 含 `Hermes-Android/` | unit | `XaiHttpTest#user agent` ✅ |
| TC-TOOL-335-a | R-TOOL-001 | `EnvPassthrough("  ")` | 跳过 | unit | `EnvPassthroughTest#blank name skipped` ✅ |
| TC-TOOL-336-a | R-TOOL-001 | `CredentialFiles.loadAll` | no-op 空 map | unit | `CredentialFilesTest#android no-op` ✅ |

---

## 域 GATEWAY

测试类: `hermes-android/src/test/java/com/xiaomo/hermes/hermes/gateway/**`。Feishu / Weixin / Telegram / Helpers / WecomCrypto / qqbot 已覆盖；其他 stub 级别补 `BaseTest`。

### Base.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-001-a | R-GW-001 | `handleMessage` 未注入 handler | logcat WARN + 返回 fallback | unit | `BaseTest#handleMessage no handler logs WARN` ✅ |
| TC-GW-002-a | R-GW-001 | fatal error 触发 | 状态由 RUNNING → FAILED | unit | `BaseTest#fatal error state transition` ✅ |
| TC-GW-003-a | R-GW-001 | 两并发 `acquirePlatformLock(same key)` | 第二者等待 | unit | `BaseTest#platform lock is mutex` ✅ |
| TC-GW-004-a | R-GW-001 | callback generation 不匹配 | 丢弃 | unit | `BaseTest#callback generation enforced` ✅ |
| TC-GW-005-a | R-GW-001 | `isCommand("/help x")` | true | unit | `BaseTest#isCommand detects slash prefix` ✅ |
| TC-GW-005-b | R-GW-001 | `getCommand("/reset y")` | `"reset"` | unit | `BaseTest#getCommand name` ✅ |
| TC-GW-005-c | R-GW-001 | `getCommandArgs("/foo a b c")` | `"a b c"` | unit | `BaseTest#getCommandArgs tail` ✅ |
| TC-GW-006-a | R-GW-001 | `interruptSessionActivity` | 同时中断 loop + 流 | unit | `BaseTest#interrupt double action` ✅ |
| TC-GW-007-a | R-GW-001 | `utf16Len("🫥")` | 2 | unit | `BaseTest#utf16Len surrogate pair` ✅ |
| TC-GW-007-b | R-GW-001 | `prefixWithinUtf16Limit` 截断在代理对中间 | 回退到前一 codepoint | unit | `BaseTest#prefix never splits surrogate` ✅ |
| TC-GW-008-a | R-GW-001 | `safeUrlForLog("https://a.b?token=xxx")` | 掩码 token | unit | `BaseTest#safeUrlForLog masks` ✅ |
| TC-GW-009-a | R-GW-001 | PNG magic bytes | true | unit | `BaseTest#looksLikeImage png` ✅ |
| TC-GW-009-b | R-GW-001 | 纯文本 | false | unit | `BaseTest#looksLikeImage text` ✅ |
| TC-GW-010-a | R-GW-001 | `cacheImageFromUrl` 重复写 | 清旧落新 | unit | `BaseTest#cacheImageFromUrl dedup` ✅ |

### Helpers.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-015-a | R-GW-001 | MessageDeduplicator(`""`) | 不 dedup | unit | `HelpersTest#dedup empty id passes` ✅ |
| TC-GW-016-a | R-GW-001 | 超 cap 插入 | LRU 驱逐最老 | unit | `HelpersTest#dedup LRU eviction` ✅ |
| TC-GW-017-a | R-GW-001 | TextBatchAggregator 两条 | delay 内合并为一 | unit | `HelpersTest#batch aggregator coalesces` ✅ |
| TC-GW-018-a | R-GW-001 | 单条超 split threshold | 立即 flush | unit | `HelpersTest#split threshold forces flush` ✅ |
| TC-GW-019-a | R-GW-001 | `cancelAll()` 后继续 append | 已调度的 job 被撤 | unit | `HelpersTest#cancelAll stops scheduled` ✅ |
| TC-GW-020-a | R-GW-001 | ThreadParticipationTracker 持久化读回 | 状态一致 | integration | `HelpersTest#thread participation roundtrip` ✅ |
| TC-GW-021-a | R-GW-001 | stripMarkdown 列表 / 代码块 / link | 剥净 | unit | `HelpersTest#stripMarkdown covers syntax` ✅ |
| TC-GW-022-a | R-GW-001 | redactPhone 各 shape | 掩码一致 Python | unit | `HelpersTest#redactPhone gradient` ✅ |

### Feishu.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-030-a | R-GW-002 | 空 `appId`/`appSecret` 调 connect | 返回 false | unit | `FeishuCoercionTest#empty credentials fail` ✅ |
| TC-GW-031-a | R-GW-002 | token 5 分钟内到期前 refresh | 刷新触发 | unit | `FeishuCoercionTest#token refreshes before expiry` ✅ |
| TC-GW-032-a | R-GW-002 | URL 模板常量 | 与 Python 对齐 | unit | `FeishuConstantsTest#url templates match python` ✅ |
| TC-GW-033-a | R-GW-002 | 同 message_id 两次 | 第二次丢弃 | unit | `FeishuNormalizeTest#dedup by event_id` ✅ |
| TC-GW-033-b | R-GW-002 | sender 不在 allowlist | 丢弃 | unit | `FeishuNormalizeTest#allowlist gate` ✅ |
| TC-GW-034-a | R-GW-002 | text / image / file / audio | 映射到 Normalized 对象 | unit | `FeishuNormalizeTest#parses content types` ✅ |
| TC-GW-035-a | R-GW-002 | 40000 字符输入 | 切成 2 块 ≤30000 | unit | `FeishuNormalizeTest#splitMessage bounds` ✅ |
| TC-GW-036-a | R-GW-002 | add/remove reaction 生命周期 | 处理中添加、完成后移除 | integration | `FeishuCoercionTest#reaction lifecycle` ✅ |
| TC-GW-037-a | R-GW-002 | PNG / JPEG / GIF 字节 | 正确 mime | unit | `FeishuMediaTypeTest#mime sniff` ✅ |
| TC-GW-038-a | R-GW-002 | 同 chat 连发 2 条 | 后者等前者完成 | integration | `FeishuCoercionTest#per-chat serial` ✅ |
| TC-GW-039-a | R-GW-002 | markdown `# title` → feishu post | 正确 element 树 | unit | `FeishuMarkdownTest#post rendering` ✅ |
| TC-GW-040-a | R-GW-002 | `probeBot(appId,secret)` 成功 | 返回 BotInfo | integration | `FeishuQrOnboardingTest#probeBot ok` ✅ |
| TC-GW-040-b | R-GW-002 | `qrRegister` 失败 | 明确错误消息 | unit | `FeishuQrOnboardingTest#qrRegister error` ✅ |

### FeishuComment.kt + FeishuCommentRules.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-065-a | R-GW-002 | URL 模板替换 `{doc_id}` | 替换成功 | unit | `FeishuCommentTest#url template replace` ✅ |
| TC-GW-066-a | R-GW-002 | doc URL match regex | 捕获 doc_id | unit | `FeishuCommentTest#doc url regex captures` ✅ |
| TC-GW-067-a | R-GW-002 | chunk size 常量 | 同 Python | unit | `FeishuCommentTest#chunk size match` ✅ |
| TC-GW-068-a | R-GW-002 | prompt 超 cap | 截断 | unit | `FeishuCommentTest#prompt truncation` ✅ |
| TC-GW-069-a | R-GW-002 | agent 回 `NO_REPLY` | 不发送 | unit | `FeishuCommentTest#NO_REPLY sentinel` ✅ |
| TC-GW-070-a | R-GW-002 | session store 过期条目 | 被清理 | unit | `FeishuCommentTest#session store TTL` ✅ |
| TC-GW-071-a | R-GW-002 | notice_type=unsupported | 丢弃 | unit | `FeishuCommentTest#notice_type filter` ✅ |
| TC-GW-085-a | R-GW-002 | FeishuCommentRules 文件改动后 load | 重新解析 | unit | `FeishuCommentRulesTest#mtime cache invalidates` ✅ |
| TC-GW-086-a | R-GW-002 | 3-tier 规则解析 | 按 priority 胜出 | unit | `FeishuCommentRulesTest#three tier resolves` ✅ |
| TC-GW-087-a | R-GW-002 | policy 合法值 | `{"allow","block","ask"}` | unit | `FeishuCommentRulesTest#policy value set` ✅ |
| TC-GW-088-a | R-GW-002 | `isUserAllowed(rule, uid)` | 基于 deny/allow 列表 | unit | `FeishuCommentRulesTest#isUserAllowed` ✅ |

### Weixin.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-095-a | R-GW-003 | token="" 调 connect | false | unit | `WeixinHelpersTest#empty token fails connect` ✅ |
| TC-GW-096-a | R-GW-003 | `_headers(token)` | 含 Authorization / Content-Type | unit | `WeixinHelpersTest#headers shape` ✅ |
| TC-GW-097-a | R-GW-003 | 同 msg_id 两次 | 第二次 dedupe | unit | `WeixinCachesTest#LRU dedupe` ✅ |
| TC-GW-098-a | R-GW-003 | long poll timeout 触发 | 重试退避 | integration | `WeixinDeliveryTest#long poll retry` ✅ |
| TC-GW-099-a | R-GW-003 | typing ticket 缓存命中 | 复用 ticket | unit | `WeixinCachesTest#typing ticket cache` ✅ |
| TC-GW-100-a | R-GW-003 | AES-128-ECB encrypt/decrypt | 回环 | unit | `WeixinHelpersTest#aes128ecb roundtrip` ✅ |
| TC-GW-101-a | R-GW-003 | `_assertWeixinCdnUrl(bad)` | 抛异常 | unit | `WeixinHelpersTest#cdn url assert` ✅ |
| TC-GW-102-a | R-GW-003 | markdown >1 段 | 按段拆分 | unit | `WeixinMarkdownTest#chunk split` ✅ |
| TC-GW-103-a | R-GW-003 | `qrLogin()` 成功 | 账号持久化 + 返 state | integration | `WeixinPersistenceTest#qr login persists` ✅ |

### WeCom 簇

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-120-a | R-GW-005 | WeCom 凭证空 connect | false | unit | `WecomAdapterTest#connect returns false when corp_id and corp_secret are empty` 🟢 |
| TC-GW-121-a | R-GW-005 | WeCom token URL | `/cgi-bin/gettoken?...` | unit | `WecomAdapterTest#token url` 🟢 |
| TC-GW-122-a | R-GW-005 | send 超 2048 字符 | 截断 | unit | `WecomAdapterTest#send truncates` 🟢 |
| TC-GW-123-a | R-GW-005 | `agent_id="abc"` | 归 0 | unit | `WecomAdapterTest#agent_id non-numeric becomes 0` 🟢 |
| TC-GW-124-a | R-GW-005 | WeComCallback 所有入口 | toolError Android | unit | `WecomCallbackTest#android denies all` 🟢 |
| TC-GW-125-a | R-GW-005 | crypto 构造缺参 | 抛 | unit | `WecomCryptoTest#constructor validates` ✅ |
| TC-GW-126-a | R-GW-005 | signature 已知向量 | 匹配 Python | unit | `WecomCryptoTest#signature known answer` ✅ |
| TC-GW-127-a | R-GW-005 | decrypt payload layout | 正确 receiverId / msg | unit | `WecomCryptoTest#decrypt layout` ✅ |
| TC-GW-128-a | R-GW-005 | PKCS7 块大小 | 32 | unit | `WecomCryptoTest#PKCS7 block size` ✅ |

### Telegram.kt + TelegramNetwork.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-135-a | R-GW-005 | 空 token | connect 失败 | unit | `TelegramAdapterTest#empty token false` ✅ |
| TC-GW-136-a | R-GW-005 | Telegram URL 常量 | `api.telegram.org/bot{token}/` | unit | `TelegramAdapterTest#url constants` ✅ |
| TC-GW-137-a | R-GW-005 | offset 单调 | 第二次 ≥ 第一次 | unit | `TelegramAdapterTest#offset monotonic` ✅ |
| TC-GW-138-a | R-GW-005 | caption 超 1024 | 截断 | unit | `TelegramAdapterTest#caption cap` ✅ |
| TC-GW-139-a | R-GW-005 | `allowed_groups="a,b, ,c"` extras | 解析为 `{"a","b","c"}`（trim + drop empty） | unit | `TelegramAdapterTest#allowed_groups parse shape` ✅ |
| TC-GW-140-a | R-GW-005 | `drop_pending_updates=true` | 首次启动时 skip | unit | `TelegramAdapterTest#drop pending flag` ✅ |
| TC-GW-141-a | R-GW-005 | 429 response | 分类为 retryable | unit | `TelegramNetworkTest#classifies 429` ✅ |
| TC-GW-141-b | R-GW-005 | 403 response | non-retryable | unit | `TelegramNetworkTest#classifies 403` ✅ |
| TC-GW-142-a | R-GW-005 | 瞬发 10 个请求 | rate limiter 串行化 | unit | `TelegramNetworkTest#rate limiter serializes` ✅ |

### Discord.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-150-a | R-GW-005 | JWT token 解析 bot id | 正确 snowflake | unit | `DiscordAdapterTest#bot id parse` ✅ |
| TC-GW-151-a | R-GW-005 | `send(reply_to=msg_id)` | 消息带 `message_reference` | unit | `DiscordAdapterTest#send reply_to wired` ✅ |
| TC-GW-152-a | R-GW-005 | typing 状态 | POST `/typing` 无 body | unit | `DiscordAdapterTest#typing endpoint` ✅ |

### Stub adapters (Signal/Slack/Matrix/WhatsApp/SMS/Email/Homeassistant/Mattermost/Webhook/BlueBubbles)

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-160-a | R-GW-005 | 任意 stub adapter `connect()` | false | unit | `StubAdapterTest#all stubs return false from connect` 🟢 |
| TC-GW-161-a | R-GW-005 | `send(...)` | err 消息 "not available on Android" | unit | `StubAdapterTest#send on each stub returns unsuccessful SendResult mentioning Android` 🟢 |
| TC-GW-162-a | R-GW-005 | 两次调 `disconnect()` | 幂等无异常 | unit | `StubAdapterTest#disconnect on each stub is idempotent` 🟢 |
| TC-GW-163-a | R-GW-005 | `adapter.platform` | 与 Python 枚举一致 | unit | `StubAdapterTest#each stub's platform enum and name string match Python wire value` 🟢 |

### Dingtalk.kt + Homeassistant.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-180-a | R-GW-005 | Dingtalk token URL 常量 | `/gettoken?...` | unit | `DingtalkAdapterTest#token url` ✅ |
| TC-GW-181-a | R-GW-005 | Dingtalk send endpoint | `/topapi/message/corpconversation/asyncsend_v2` | unit | `DingtalkAdapterTest#send endpoint` ✅ |
| TC-GW-182-a | R-GW-005 | HomeAssistant 重连退避阶梯（Python `_BACKOFF_STEPS`） | `[5, 10, 30, 60]`（4 档秒数，末档后 stick） | unit | `HomeAssistantAdapterTest#backoff ladder` ✅ |
| TC-GW-183-a | R-GW-005 | auth header | `Bearer <token>` | unit | `HomeAssistantAdapterTest#auth header` ✅ |
| TC-GW-184-a | R-GW-005 | 发送通知走 `persistent_notification.create`（Python 上游选择） | POST 到 `/api/services/persistent_notification/create` | unit | `HomeAssistantAdapterTest#notify path` ✅ |

### qqbot/

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-GW-190-a | R-GW-004 | QQAdapter.connect | Android stub false | unit | `AdapterHelpersTest#connect stub` ✅ |
| TC-GW-191-a | R-GW-004 | `_guessChatType("C:xxx")` | channel | unit | `UtilsTest#_guessChatType prefixes` ✅ |
| TC-GW-192-a | R-GW-004 | `_stripAtMention` | 移除 @ 片段 | unit | `UtilsTest#_stripAtMention` ✅ |
| TC-GW-193-a | R-GW-004 | `_isVoiceContentType("audio/silk")` | true | unit | `UtilsTest#_isVoiceContentType` ✅ |
| TC-GW-193-b | R-GW-004 | `_isVoiceContentType("image/png")` | false | unit | `UtilsTest#_isVoiceContentType image false` ✅ |
| TC-GW-194-a | R-GW-004 | `_guessExtFromData(JPEG bytes)` | `.jpg` | unit | `UtilsTest#_guessExtFromData jpeg` ✅ |
| TC-GW-195-a | R-GW-004 | `_parseQqTimestamp("bad")` | fallback | unit | `UtilsTest#parseQqTimestamp fallback` ✅ |
| TC-GW-196-a | R-GW-004 | AES-256-GCM encrypt/decrypt | 回环 | unit | `UtilsTest#aes256gcm roundtrip` ✅ |
| TC-GW-197-a | R-GW-004 | `createBindTask` retcode!=0 | 返回错误 | integration | `OnboardTest#createBindTask checks retcode` ✅ |
| TC-GW-198-a | R-GW-004 | `buildConnectUrl` 带特殊字符 | URL 编码 | unit | `OnboardTest#buildConnectUrl encodes` ✅ |
| TC-GW-199-a | R-GW-004 | `buildUserAgent(null)` | fallback 字符串 | unit | `OnboardTest#buildUserAgent fallback` ✅ |
| TC-GW-200-a | R-GW-004 | `coerceList(null)` | `[]` | unit | `OnboardTest#coerceList null` ✅ |
| TC-GW-200-b | R-GW-004 | `coerceList("s")` | `["s"]` | unit | `OnboardTest#coerceList scalar` ✅ |

---

## 域 STATE

测试类: `HermesStateTest.kt`（已覆盖核心 CRUD）；`SessionDB` / `ContextCompressor` / `MemoryManager` 需 Robolectric 补测。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-STATE-001-a | R-STATE-001 | `get("no", default=42)` | 42 | unit | `HermesStateTest#get missing returns default` ✅ |
| TC-STATE-002-a | R-STATE-001 | `set("k","v")` | dirty=true；autoSave 触发 | unit | `HermesStateTest#set marks dirty and autosaves` ✅ |
| TC-STATE-003-a | R-STATE-001 | `delete("k")` 存在 | 返 true | unit | `HermesStateTest#delete returns existence` ✅ |
| TC-STATE-003-b | R-STATE-001 | `delete("nope")` | 返 false | unit | `HermesStateTest#delete missing false` ✅ |
| TC-STATE-004-a | R-STATE-001 | `keys` / `size` / `contains` | 反映当前 map | unit | `HermesStateTest#view operations consistent` ✅ |
| TC-STATE-005-a | R-STATE-001 | 并发两进程 `save` | FileChannel.lock 串行 | integration | `HermesStateTest#save uses file lock` 🟢 |
| TC-STATE-006-a | R-STATE-001 | `merge` map→非 map | 替换 | unit | `HermesStateTest#merge replaces non-map` ✅ |
| TC-STATE-007-a | R-STATE-001 | `snapshot()` 修改 | 不影响源 | unit | `HermesStateTest#snapshot is copy` ✅ |
| TC-STATE-008-a | R-STATE-001 | `clear()` | size=0 | unit | `HermesStateTest#clear empties` ✅ |
| TC-STATE-009-a | R-STATE-001 | `getGlobalState()` 首次 + 二次 | 单例返回同实例 | unit | `HermesStateTest#global state singleton` ✅ |
| TC-STATE-015-a | R-STATE-003 | SessionDB FTS5 索引表存在 | schema 正确 | integration | `SessionDBTest#fts5 index present` 🟢 |
| TC-STATE-016-a | R-STATE-003 | 并发写 2 session | 串行成功、无 race | integration | `SessionDBTest#concurrent writes serialized` 🟢 |
| TC-STATE-017-a | R-STATE-003 | `getGlobalSessionDB()` 多次 | 同 instance | unit | `SessionDBTest#global singleton lazy` 🟢 |
| TC-STATE-025-a | R-STATE-002 | 长度触阈值 | 压缩启动 | integration | `ContextCompressorTest#needsCompression true when over threshold` 🟢 |
| TC-STATE-026-a | R-STATE-002 | 压缩后 | system + tail 完整保留 | integration | `ContextCompressorTest#keepRecent strategy returns at least minRecent when possible` 🟢 |
| TC-STATE-027-a | R-STATE-002 | tool 结果在中段 | 优先压缩 | integration | `ContextCompressorTest#drop tool results strategy removes a middle tool_result` 🟢 |
| TC-STATE-028-a | R-STATE-002 | 压缩 API 异常 | 回退不压 + 日志 | integration | `ContextCompressorTest#_generateSummary returns null on Android without LLM` 🟢 |
| TC-STATE-035-a | R-STATE-003 | memory 文件有内容 | loadIntoPrompt 拼到 system | integration | `MemoryManagerTest#buildSystemPrompt concatenates nonempty provider blocks` 🟢 |
| TC-STATE-036-a | R-STATE-003 | memory 更新 | 触发 persist | integration | `MemoryManagerTest#onMemoryWrite fans out to external providers` 🟢 |

---

## 域 SKILL

测试类: 新建 `DiskCleanupPluginTest`, `MemoryProviderHolographicTest`, `MemoryProviderHonchoTest`, `SkillCommandsTest`。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-SKILL-010-a | R-SKILL-001 | `quick()` 跑一遍含 category=test 的 tracked 条目 | test 文件立删；返回 map `deleted>=1` | integration | `DiskCleanupPluginTest#quick deletes test category immediately` ✅ |
| TC-SKILL-011-a | R-SKILL-001 | `isSafePath()` 传入 HERMES_HOME 外路径 | false；HERMES_HOME 内 / `/tmp/hermes-*` 下 true | unit | `DiskCleanupPluginTest#isSafePath scope bounds` ✅ |
| TC-SKILL-012-a | R-SKILL-001 | `dryRun()` 含 test + temp + research | 返 Pair(auto,prompt)；磁盘上文件仍在 | unit | `DiskCleanupPluginTest#dryRun is read-only` ✅ |
| TC-SKILL-020-a | R-SKILL-001 | MemoryProvider 接口 | 6 方法 `initialize/store/retrieve/delete/list/close` + `providerName` 属性（对齐 Python `hermes/plugins/memory/__init__.py`）| unit | `MemoryProviderTest#interface surface` ✅ |
| TC-SKILL-021-a | R-SKILL-001 | Holographic provider 写读 | 回环相等 | integration | `MemoryProviderHolographicTest#roundtrip` 🟡 |
| TC-SKILL-022-a | R-SKILL-001 | Honcho provider `save` | POST 请求有效签名 | integration | `MemoryProviderHonchoTest#save signed` 🟡 |
| TC-SKILL-030-a | R-SKILL-002 | `resolveSkillCommandKey("foo_bar")` 且 `/foo-bar` 已扫描 | 返回 `/foo-bar`（下划线归一 + 前缀加斜杠）| unit | `SkillCommandsTest#resolveSkillCommandKey underscore normalization` ✅ |
| TC-SKILL-031-a | R-SKILL-002 | `buildPlanPath(userInstruction="Fix the Android bug!")` | 返回 `.hermes/plans/<ts>-fix-the-android-bug.md`（slug: 小写/非 `a-z0-9` 转 `-`/最多 8 段/长度 ≤48；单参 string — Python 上游同样不拆 argv）| unit | `SkillCommandsTest#buildPlanPath slug rules` ✅ |
| TC-SKILL-040-a | R-SKILL-003 | skills 增量同步（`tools/skills_sync.py` 对应路径） | 参照 TOOL 段 TC-TOOL-185..191（已双指 R-SKILL-003） | unit/integration | `SkillsSyncTest` 全套（7 条） ✅ |
| TC-SKILL-041-a | R-SKILL-003 | 同步后 hub 索引刷新 | R-SKILL-001 的 SkillsHub 重新扫描 | integration | `SkillsHubTest#refresh after sync` 🟢 |
| TC-SKILL-042-a | R-SKILL-003 | 冲突 / 签名失败 | 拒绝覆盖 | unit | `SkillsSyncTest#signature mismatch rejects` 🟢 |

---

## 域 MCP

测试类: 新建 `McpToolTest`, `McpOAuthTest`, `ManagedToolGatewayTest`(已存在 — 补覆盖)。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-MCP-001-a | R-MCP-001 | MCP server 列表 2 tools | Registry 多 2 条 | integration | `McpToolTest#registerMcpServers returns empty when SDK unavailable`（Android stub 路径） ✅ |
| TC-MCP-002-a | R-MCP-001 | server A 连不上 server B OK | B 的工具仍注册 | integration | `McpToolTest#isolation on failure` 🟢 |
| TC-MCP-003-a | R-MCP-001 | 调用 MCP 工具 | 代理到远端 server | integration | `McpToolTest#makeToolHandler returns error json`（Android 降级路径） ✅ |
| TC-MCP-004-a | R-MCP-001 | MCP schema | 转 OpenAI schema 格式一致 | unit | `McpToolTest#normalizeMcpInputSchema returns input when present` ✅ |
| TC-MCP-005-a | R-MCP-001 | stdio 传输 | Android stub toolError | unit | `McpToolTest#mcp availability flags are all false on android` ✅ |
| TC-MCP-010-a | R-MCP-002 | `mcp_serve.py` 对应的 server 侧暴露工具 | Android 平台上为 stub（无 local server）；保留类/方法对齐 | unit | `McpToolTest#buildSafeEnv returns empty map` + `McpServerTask default state is disconnected` ✅ |
| TC-MCP-010-b | R-MCP-002 | 方法签名与 `mcp_serve.py` 1:1 | 由 verify_align 守卫 | alignment | §2 三件套 ✅ |
| TC-MCP-020-a | R-MCP-003 | OAuth token 已存在 | 从磁盘重放为 cached | integration | `McpOAuthTest#token storage persists and reloads` ✅ |
| TC-MCP-021-a | R-MCP-003 | `parseBaseUrl` 归一化 MCP server URL | 正确归一化 | unit | `McpOAuthTest#parseBaseUrl normalises mcp server url` + `parseBaseUrl falls back on malformed` ✅ |
| TC-MCP-022-a | R-MCP-003 | `buildClientMetadata` + overrides / defaults | 生成符合 OAuth 动态注册的 metadata | unit | `McpOAuthTest#buildClientMetadata applies overrides` + `buildClientMetadata default values` ✅ |
| TC-MCP-023-a | R-MCP-003 | `safeFilename` 清洗 host 为文件名 | 非法字符被替换 | unit | `McpOAuthTest#safeFilename strips unsafe chars` ✅ |
| TC-MCP-024-a | R-MCP-003 | Android 无 interactive browser | `interactive and browser checks return false` | unit | `McpOAuthTest#interactive and browser checks return false on android` ✅ |
| TC-MCP-025-a | R-MCP-003 | Android 无法交互完成 OAuth | `buildOauthAuth is null on android` + `waitForCallback returns null stub` | unit | `McpOAuthTest#buildOauthAuth is null on android` + `waitForCallback returns null stub` ✅ |
| TC-MCP-026-a | R-MCP-003 | 损坏的 token 文件 | 被忽略（而非抛异常） | unit | `McpOAuthTest#corrupt tokens file is ignored` ✅ |
| TC-MCP-027-a | R-MCP-003 | `OAuthNonInteractiveError` | 默认消息存在 | unit | `McpOAuthTest#OAuthNonInteractiveError has default message` ✅ |
| TC-MCP-030-a | R-MCP-001 | ManagedToolGateway 调 gateway tool | Android 降级到 local | unit | `ManagedToolGatewayTest#android downgrade` ✅ |
| TC-MCP-031-a | R-MCP-001 | 请求签名 | HMAC match | unit | `ManagedToolGatewayTest#request signature` ✅ |

---

## 域 CRON

测试类: `SchedulerTest`（已覆盖）、新建 `JobsTest`、`CronjobToolsTest`（已覆盖）。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CRON-001-a | R-CRON-001 | CronjobTools.create | toolError | unit | `CronjobToolsTest#create denied` ✅ |
| TC-CRON-001-b | R-CRON-001 | CronjobTools.delete | toolError | unit | `CronjobToolsTest#delete denied` ✅ |
| TC-CRON-002-a | R-CRON-001 | requirements() | `{skipped:"android"}` | unit | `CronjobToolsTest#requirements skipped` ✅ |
| TC-CRON-010-a | R-CRON-001 | `"*/5 * * * *"` 解析 | every 5 min | unit | `SchedulerTest#parses every-5-min` ✅ |
| TC-CRON-010-b | R-CRON-001 | `"0 9 * * 1-5"` | weekdays 9am | unit | `SchedulerTest#parses weekdays 9am` ✅ |
| TC-CRON-011-a | R-CRON-001 | `addJob` 再读回 | 持久化 | integration | `JobsTest#persistence roundtrip` ✅ |
| TC-CRON-012-a | R-CRON-001 | approve mode=`"no"` | 阻塞 | unit | `JobsTest#approval mode enforced` ✅ |
| TC-CRON-013-a | R-CRON-001 | Android 启动 | daemon 不起 | integration | `JobsTest#parseSchedule cron-expression is rejected on Android` 🟢 |

---

## 域 SAFETY

SAFETY 大多通过引用其它域的 TC 覆盖；此处列集成层 smoke。

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-SAFETY-020-a | R-SAFETY-001 | PathSecurity canonicalPath `/a/./b/../c` | `/a/c` | unit | 复用 `PathSecurityTest` ✅ |
| TC-SAFETY-021-a | R-SAFETY-001 | dangerous pattern 列表长度 | 与 Python 一致 | unit | 复用 `ApprovalTest#pattern count` ✅ |
| TC-SAFETY-022-a | R-SAFETY-001 | Tirith 降级 | 视作 "ask" | unit | 复用 `TirithSecurityTest` ✅ |
| TC-SAFETY-023-a | R-SAFETY-002 | OsvCheck 网络失败 | fail-open | unit | 复用 `OsvCheckTest#fail-open on network error` ✅ |
| TC-SAFETY-024-a | R-SAFETY-002 | `UrlSafety.isSafe("http://127.0.0.1")` | false | unit | 复用 `UrlSafetyTest#private IP rejected` ✅ |
| TC-SAFETY-025-a | R-SAFETY-002 | WebsitePolicy 默认 | disabled + 30s cache | unit | 复用 `WebsitePolicyTest#default disabled + cache` ✅ |
| TC-SAFETY-026-a | R-SAFETY-002 | readFile `.exe` | 拒绝 | unit | 复用 `BinaryExtensionsTest` ✅ |
| TC-SAFETY-027-a | R-SAFETY-001 | write `~/.ssh/id_rsa` | 拒绝 | unit | 复用 `FileSafetyTest#SSH denied` ✅ |
| TC-SAFETY-028-a | R-SAFETY-001 | `HERMES_WRITE_SAFE_ROOT` 设 | 约束所有 write | unit | 复用 `FileSafetyTest#safe root jail` ✅ |
| TC-SAFETY-029-a | R-SAFETY-001 | read `/dev/urandom` | 拒绝 | unit | `FileOperationsTest#blocks /dev character device` 🟢 |

---

## 域 CONFIG

测试类: 新建 `HermesGatewayPreferencesTest`、`HermesGatewayConfigBuilderTest`、`HermesGatewayControllerTest`（均在 `HermesApp/app/src/test/`，使用 Robolectric）。

### HermesGatewayPreferences.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-001-a | R-GW-001 | `HermesGatewayPreferences.getInstance(ctx)` 两次 | 同实例 | unit | `HermesGatewayPreferencesTest#singleton` 🟡 |
| TC-CONFIG-002-a | R-GW-001 | 写 `appId` | 进 EncryptedPrefs | integration | `HermesGatewayPreferencesTest#dual store writes` 🟡 |
| TC-CONFIG-002-b | R-GW-001 | 写 policy | 进 DataStore | integration | `HermesGatewayPreferencesTest#policy goes to datastore` 🟡 |
| TC-CONFIG-003-a | R-GW-001 | saveAgentMaxTurns(0) | clamp 到 1 | unit | `HermesGatewayPreferencesTest#maxTurns clamp low` 🟡 |
| TC-CONFIG-003-b | R-GW-001 | saveAgentMaxTurns(9999) | clamp 到 cap | unit | `HermesGatewayPreferencesTest#maxTurns clamp high` 🟡 |
| TC-CONFIG-004-a | R-GW-001 | `clearSecrets("feishu")` | 仅 `feishu_*` key 清 | integration | `HermesGatewayPreferencesTest#clearSecrets prefix only` 🟡 |
| TC-CONFIG-005-a | R-GW-001 | 首次读 Flow 无值 | emit 默认 | unit | `HermesGatewayPreferencesTest#default flow emit` 🟡 |
| TC-CONFIG-006-a | R-GW-001 | 写后 restart process 读 | 数据仍在 | integration | `HermesGatewayPreferencesTest#roundtrip persistence` 🟡 |
| TC-CONFIG-007-a | R-GW-001 | 常量 key 名 | 与 Python 对齐 | unit | `HermesGatewayPreferencesTest#constant names` 🟡 |

### HermesGatewayConfigBuilder.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-010-a | R-GW-001 | Feishu creds 缺 `appSecret` | `config.platforms` 不含 feishu | unit | `HermesGatewayConfigBuilderTest#incomplete creds skipped` 🟢 |
| TC-CONFIG-011-a | R-GW-001 | 两平台一有一无 | 过滤 null | unit | `HermesGatewayConfigBuilderTest#null platforms filtered` 🟢 |
| TC-CONFIG-012-a | R-GW-001 | extra 字段部分空 | 仅非空入 map | unit | `HermesGatewayConfigBuilderTest#extra non-null only` 🟢 |
| TC-CONFIG-013-a | R-GW-001 | `readCsv(" a, b ,c")` | `["a","b","c"]` | unit | `HermesGatewayConfigBuilderTest#readCsv normalizes` 🟢 |
| TC-CONFIG-014-a | R-GW-001 | 默认 maxConcurrentSessions | `5` | unit | `HermesGatewayConfigBuilderTest#maxConcurrent default 5` 🟢 |
| TC-CONFIG-015-a | R-GW-001 | 策略默认 | dm/group 等默认值与 Python 一致 | unit | `HermesGatewayConfigBuilderTest#policy defaults` 🟢 |

### HermesGatewayController.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-CONFIG-020-a | R-GW-006 | start()→RUNNING | Status 转换 STOPPED → STARTING → RUNNING | integration | `HermesGatewayControllerTest#status FSM happy path` 🟡 |
| TC-CONFIG-021-a | R-GW-006 | 空 platforms 下 start() | Status → FAILED + errorMessage | integration | `HermesGatewayControllerTest#empty platforms fails` 🟡 |
| TC-CONFIG-022-a | R-GW-006 | RUNNING 下 start() | no-op 返回 true | unit | `HermesGatewayControllerTest#start idempotent RUNNING` 🟢 |
| TC-CONFIG-023-a | R-GW-006 | stop() 过程抛 | Status 仍 STOPPED | integration | `HermesGatewayControllerTest#stop exception still STOPPED` 🟡 |
| TC-CONFIG-024-a | R-GW-006 | `stripInternalMarkup("<tool>...</tool>")` | 剥净 | unit | `HermesGatewayControllerTest#stripInternalMarkup removes xml` 🟢 |
| TC-CONFIG-025-a | R-GW-006 | `gatewayChatTitle` 长 100 字符 | 截断 24 | unit | `HermesGatewayControllerTest#chat title truncation` 🟢 |
| TC-CONFIG-026-a | R-GW-006 | agent 返空 reply | 使用 fallback 文本 | integration | `HermesGatewayControllerTest#empty reply fallback` 🟡 |
| TC-CONFIG-027-a | R-GW-006 | persist 抛 IOException | 吞 + logcat ERROR | integration | `HermesGatewayControllerTest#persist swallow` 🟡 |

---

## 域 UI

测试类: `HermesSettings*ScreenTest.kt` + ViewModel unit tests。使用 Compose 测试 rule + Robolectric。

### HermesSettingsScreen.kt（hub）

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-001-a | R-UI-001 | 屏幕渲染 | 5 个 tile 可见 | ui | `HermesSettingsScreenTest#five tiles visible` 🟡 |
| TC-UI-002-a | R-UI-001 | 每 tile 图标 | Icon 匹配资源 | ui | `HermesSettingsScreenTest#tile icons` 🟡 |
| TC-UI-003-a | R-UI-001 | 点击 tile | callback 触发 | ui | `HermesSettingsScreenTest#tile click triggers callback` 🟡 |
| TC-UI-004-a | R-UI-001 | 只打开不做任何 tap | 无 prefs / service 写 | ui | `HermesSettingsScreenTest#no side effect on open` 🟡 |
| TC-UI-005-a | R-UI-001 | tile 文本超宽 | 截断显示 | ui | `HermesSettingsScreenTest#tile text ellipsis` 🟡 |

### HermesGatewayCredentialsScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-010-a | R-UI-001 | Feishu 选 | 字段数 6 | ui | `HermesGatewayCredentialsScreenTest#feishu field count` 🟡 |
| TC-UI-010-b | R-UI-001 | Weixin 选 | 字段数 3 | ui | `HermesGatewayCredentialsScreenTest#weixin field count` 🟡 |
| TC-UI-011-a | R-UI-001 | 密码字段 | textVisible=false 默认 | ui | `HermesGatewayCredentialsScreenTest#password masked default` 🟡 |
| TC-UI-012-a | R-UI-001 | 切 enable toggle | 立刻 saveEnable 调用 | ui | `HermesGatewayCredentialsScreenTest#enable toggle immediate save` 🟡 |
| TC-UI-013-a | R-UI-001 | 按 Save | 每字段各一次 write | ui | `HermesGatewayCredentialsScreenTest#save per field write` 🟡 |
| TC-UI-014-a | R-UI-001 | Save 后 | savedFlash 显示 1500ms 后消失 | ui | `HermesGatewayCredentialsScreenTest#saved flash 1500ms` 🟡 |
| TC-UI-015-a | R-UI-001 | 打开屏幕 | 初始值等 prefs | ui | `HermesGatewayCredentialsScreenTest#initial values from prefs` 🟡 |

### HermesGatewayPoliciesScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-020-a | R-UI-001 | dm_policy chip | 选项 `{"allow","require_mention","deny"}` | ui | `HermesGatewayPoliciesScreenTest#dm policy chips` 🟡 |
| TC-UI-020-b | R-UI-001 | group_policy chip | 同上集合 | ui | `HermesGatewayPoliciesScreenTest#group policy chips` 🟡 |
| TC-UI-021-a | R-UI-001 | 改 chip 后不按 Save 退出 | prefs 未变 | ui | `HermesGatewayPoliciesScreenTest#only save persists` 🟡 |
| TC-UI-022-a | R-UI-001 | `require_mention` chip → save → 读 Flow | string 为 `"require_mention"` | integration | `HermesGatewayPoliciesScreenTest#string-boolean match` 🟡 |
| TC-UI-023-a | R-UI-001 | 未设定过 | 默认值 allow / require_mention | unit | `HermesGatewayPoliciesScreenTest#defaults` 🟡 |
| TC-UI-024-a | R-UI-001 | 后续 prefs 外部变更 | 仅首次 emit 初始化 state | ui | `HermesGatewayPoliciesScreenTest#first-emit only init` 🟡 |

### HermesAgentParamsScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-030-a | R-UI-001 | TextField 输入字母 | 被过滤 | ui | `HermesAgentParamsScreenTest#digits only filter` 🟡 |
| TC-UI-031-a | R-UI-001 | 输入 9999 → Save | clamp 后写入 prefs | ui | `HermesAgentParamsScreenTest#save clamps value` 🟡 |
| TC-UI-032-a | R-UI-001 | 输入空 → Save | no-op | ui | `HermesAgentParamsScreenTest#empty no-op save` 🟡 |
| TC-UI-033-a | R-UI-001 | prefs 外部写 | 屏幕 resync 显示 | ui | `HermesAgentParamsScreenTest#external change resync` 🟡 |

### HermesGatewayServiceScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-040-a | R-UI-001 | 打开 run switch | 同时 `saveRunSwitch(true)` + `startService` | ui | `HermesGatewayServiceScreenTest#run switch double write` 🟡 |
| TC-UI-040-b | R-UI-001 | 关 run switch | 同时 `saveRunSwitch(false)` + `stopService` | ui | `HermesGatewayServiceScreenTest#run switch off` 🟡 |
| TC-UI-041-a | R-UI-001 | autostart toggle | 仅写 prefs，不碰 service | ui | `HermesGatewayServiceScreenTest#autostart pref only` 🟡 |
| TC-UI-042-a | R-UI-001 | Status=RUNNING | 显示 "运行中" 文案 | ui | `HermesGatewayServiceScreenTest#status RUNNING mapped` 🟡 |
| TC-UI-042-b | R-UI-001 | Status=FAILED | 显示 "启动失败" | ui | `HermesGatewayServiceScreenTest#status FAILED mapped` 🟡 |
| TC-UI-043-a | R-UI-001 | errorMessage != null | 错误条渲染 | ui | `HermesGatewayServiceScreenTest#error bar visible` 🟡 |

### HermesGatewayQrBindScreen.kt

| TC | 验 R | 输入 / 操作 | 期望 | 类型 | 测试方法 / 状态 |
|---|---|---|---|---|---|
| TC-UI-050-a | R-UI-001 | Feishu QR 流程成功 | `saveFeishuCreds(...)` 写入 | integration | `HermesGatewayQrBindScreenTest#feishu success writes creds` 🟡 |
| TC-UI-051-a | R-UI-001 | Weixin QR 成功 | `saveWeixinCreds(...)` 写入 | integration | `HermesGatewayQrBindScreenTest#weixin success writes creds` 🟡 |
| TC-UI-052-a | R-UI-001 | QR 生成失败 | 显示错误 UI | ui | `HermesGatewayQrBindScreenTest#qr gen failure` 🟡 |
| TC-UI-053-a | R-UI-001 | 按取消 | state 清零 + job cancel | ui | `HermesGatewayQrBindScreenTest#cancel clears state` 🟡 |
| TC-UI-054-a | R-UI-001 | 按 "清除凭证" | clearSecrets 调用 | ui | `HermesGatewayQrBindScreenTest#clear credentials invokes clear` 🟡 |
| TC-UI-055-a | R-UI-001 | Weixin QR 失败 → fallback 文案 | 显示 "请改用扫码登录 APP" 之类兜底 | ui | `HermesGatewayQrBindScreenTest#weixin fallback message` 🟡 |

---

## 统计

| 域 | R 数 | TC 数 | 已落地 ✅ | 待写 🟡 |
|---|---|---|---|---|
| CORE | 2 | 5 | 5 (alignment) | 0 |
| PARSER | 10 | 36 | 36 | 0 |
| AGENT (ErrorClassifier) | 1 | 48 | 48 | 0 |
| AGENT (Helpers) | 1 | 22 | 22 | 0 |
| AGENT (FileSafety) | 1 | 10 | 10 | 0 |
| AGENT (TurnLoop) | 1 | 9 | 9 (3 E2E + 6 unit) | 0 |
| AGENT (CredentialPool) | 1 | 7 | 0 | 7 |
| ACP | 4 | 53 | 49 | 4 |
| TOOL | 3 | 186 | 52 | 134 |
| GATEWAY | 6 | 91 | 65 | 26 |
| STATE | 3 | 19 | 9 | 10 |
| SKILL | 3 | 11 | 0 | 11 |
| MCP | 3 | 17 | 16 | 1 |
| CRON | 1 | 8 | 5 | 3 |
| SAFETY | 2 | 10 | 9 | 1 |
| CONFIG | (删除) | 23 | 0 | 23 |
| UI | 1 | 30 | 0 | 30 |
| **合计** | **42** | **585** | **335** | **250** |

> CONFIG 域 23 条 TC 在 requirements.md 三轮 prune 后归并到 R-GW-001 / R-GW-006 / R-UI-001，保留 TC 行以便 Phase 3 落地（测试类本身不受域归并影响）。

跑已落地 TC：
```bash
./gradlew :hermes-android:testDebugUnitTest \
  --tests "com.xiaomo.hermes.hermes.MissingParsersTest" \
  --tests "com.xiaomo.hermes.hermes.agent.*" \
  --tests "com.xiaomo.hermes.hermes.acp.*" \
  --tests "com.xiaomo.hermes.hermes.tools.*" \
  --tests "com.xiaomo.hermes.hermes.gateway.*" \
  --tests "com.xiaomo.hermes.hermes.cron.*" \
  --tests "com.xiaomo.hermes.hermes.HermesStateTest"
```

— 250 条 🟡 是 Phase 3 的产品代码 + 新测试类；以下按 §9.1 Top→Mid→Low 在 Phase 3 迭代中逐批落地。
