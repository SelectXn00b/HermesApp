# Hermes Requirements (R-Doc)

> **状态**: 2026-04-26 初版（§9.0 三阶段文档的第 ① 阶段）
> **下游**: `docs/hermes-test-cases.md`（每条需求至少一条 TC 覆盖）→ `hermes-android/src/test/**` 的 JUnit
> **来源**: Python 上游时引用 `reference/hermes-agent/.../xxx.py:line`；纯 Android 侧时引用 Settings UI spec / CLAUDE.md §6
>
> **ID 规则**: `R-<DOMAIN>-<NNN>`，`NNN` 全局递增不回收；删掉的需求标 `[DELETED]` 保留占位。
>
> **DOMAIN**: `AGENT` / `TOOL` / `PARSER` / `ACP` / `MCP` / `GATEWAY` / `STATE` / `SKILL` / `CRON` / `SAFETY` / `CONFIG` / `UI`

---

## 索引

| 域 | 编号范围 | 覆盖状态 |
|---|---|---|
| PARSER | R-PARSER-001 .. 050 | 🟢 核心 6 parser 全覆盖 |
| AGENT | R-AGENT-001 .. 200 | 🟡 ErrorClassifier + RetryUtils + SmartModelRouting + TitleGenerator + FileSafety + Helpers 已覆盖；其余 adapter / ContextCompressor / MemoryManager 待补 |
| ACP | R-ACP-001 .. 050 | 🟡 Tools + Auth 已覆盖；Permissions / Events 待补 |
| TOOL | R-TOOL-001 .. 300 | 🔴 待补（Registry / Approval / FileTools / TerminalTool / ... 共 61 个文件） |
| GATEWAY | R-GW-001 .. 200 | 🟡 Feishu/Weixin/Telegram/qqbot/Base/Helpers 部分覆盖；其余 10+ 平台待补 |
| STATE | R-STATE-001 .. 100 | 🔴 待补（HermesState / TrajectoryCompressor） |
| SKILL | R-SKILL-001 .. 100 | 🔴 待补 |
| MCP | R-MCP-001 .. 050 | 🔴 待补 |
| CRON | R-CRON-001 .. 050 | 🔴 待补 |
| SAFETY | R-SAFETY-001 .. 050 | 🟡 FileSafety 覆盖；PathSecurity / Approval 待补 |
| CONFIG | R-CONFIG-001 .. 050 | 🔴 待补（HermesGatewayPreferences） |
| UI | R-UI-001 .. 050 | 🔴 待补（Settings 5 sub-screens） |

---

## 域 PARSER — Tool-call parsers

所有 parser 在 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/*Parser.kt`，parse 模型响应文本为 `ToolCalls` 列表。

### R-PARSER-001: LongcatToolCallParser 识别 `<longcat_tool_call>` 标签
**来源**: `reference/hermes-agent/hermes/longcat_parser.py`
**行为**: 解析形如 `<longcat_tool_call>{"name":..,"arguments":{..}}</longcat_tool_call>` 的内联 tag，返回 `ToolCall(name, arguments)`。
**验收**: 单个 tag → 1 条 toolCall；多个 tag → 按出现顺序；参数通过 JSON 解析。

### R-PARSER-002: LongcatToolCallParser 无 tag 时返回 null
**来源**: 同上
**行为**: 响应中无 `<longcat_tool_call>` 片段时 `parse(resp).toolCalls == null`（非空列表）。

### R-PARSER-003: LongcatToolCallParser 跳过空 name 条目
**来源**: 同上
**行为**: JSON 里缺 `name` 字段或 name 为空时整条丢弃。若全部丢弃则 `toolCalls == null`。

### R-PARSER-004: LongcatToolCallParser.supportedModels 包含 `longcat`
**来源**: 同上
**行为**: `supportedModels` 列表里必含 `"longcat"` 字符串。

### R-PARSER-010: Qwen3CoderToolCallParser 识别 `<tool_call><function=name><parameter=k>v</parameter>` 语法
**来源**: `reference/hermes-agent/hermes/qwen3_coder_parser.py`
**行为**: `<tool_call><function=name><parameter=key>value</parameter>...</function></tool_call>` 结构中每个 function 块转为一条 ToolCall；parameter 子标签的 key/value 拼 arguments map。

### R-PARSER-011: Qwen3CoderToolCallParser 类型转换布尔
**来源**: 同上
**行为**: `<parameter=flag>true</parameter>` → `arguments["flag"] == true`（Boolean，不是字符串）。false/True/False 同理。

### R-PARSER-012: Qwen3CoderToolCallParser 无 tag 返回 null
**行为**: 响应中无 `<tool_call>` 标签时 `toolCalls == null`。

### R-PARSER-013: Qwen3CoderToolCallParser._parseFunctionCall 防御畸形
**行为**: 未找到 `>` 分隔符时 `_parseFunctionCall(raw)` 返回 null。

### R-PARSER-014: Qwen3CoderToolCallParser.supportedModels 包含 `qwen3_coder`
**行为**: 列表里必含 `"qwen3_coder"`。

### R-PARSER-020: LlamaToolCallParser 解析 `<|python_tag|>` + JSON
**来源**: `reference/hermes-agent/hermes/llama_parser.py`
**行为**: 响应格式 `<|python_tag|>{"name":..,"arguments":{..}}` 或纯 JSON，提取 name/arguments。

### R-PARSER-021: LlamaToolCallParser 接受 parameters 作为 arguments 同义词
**来源**: 同上
**行为**: JSON 里使用 `parameters` 字段时等同 `arguments` 读取。

### R-PARSER-022: LlamaToolCallParser 无 JSON 且无 tag 返回 null
**行为**: 纯文本响应 `toolCalls == null`。

### R-PARSER-023: LlamaToolCallParser 畸形 JSON 返回 null
**行为**: `{not valid json` 解析失败时 `toolCalls == null`（不抛异常）。

### R-PARSER-024: LlamaToolCallParser.supportedModels 包含 llama3/llama4 json 变体
**行为**: 列表里必含 `"llama3_json"` 和 `"llama4_json"`。

### R-PARSER-030: Glm47ToolCallParser.supportedModels == ["glm47"]
**来源**: `reference/hermes-agent/hermes/glm_4_7_parser.py`
**行为**: 精确为 `["glm47"]`。

### R-PARSER-031: Glm47ToolCallParser 解析 `<arg_key>`/`<arg_value>` 语法
**来源**: 同上
**行为**: `<tool_call>name\n<arg_key>k</arg_key>\n<arg_value>v</arg_value></tool_call>` 格式转为 ToolCall。

### R-PARSER-032: Glm47ToolCallParser 无 tool_call 返回 null
**行为**: `toolCalls == null`。

### R-PARSER-040: QwenToolCallParser 继承 Hermes 格式
**来源**: `reference/hermes-agent/hermes/qwen_parser.py`
**行为**: `supportedModels == ["qwen"]`；解析形如 `<tool_call>{"name":..,"arguments":{..}}</tool_call>`（同 HermesToolCallParser）。

### R-PARSER-050: DeepSeekV31ToolCallParser.supportedModels 含 `deepseek_v3_1` 和 `deepseek_v31`
**来源**: `reference/hermes-agent/hermes/deepseek_v31_parser.py`
**行为**: 两个名字都接受。

### R-PARSER-051: DeepSeekV31ToolCallParser 解析心形 emoji 分隔符
**来源**: 同上
**行为**: `❤️<name>❤️` 格式（Unicode `\u2764\ufe0f`）识别为 ToolCall(name, arguments={})。

### R-PARSER-052: DeepSeekV31ToolCallParser 无分隔符返回空列表（不是 null）
**行为**: `toolCalls` 是非 null 空列表。

### R-PARSER-053: DeepSeekV31ToolCallParser 解析多次调用
**行为**: `❤️fn1❤️ ... ❤️fn2❤️` 产生两条按顺序的 ToolCall。

---

## 域 AGENT — Agent loop helpers

### R-AGENT-001: classifyApiError 状态码 401 → auth
**来源**: `reference/hermes-agent/hermes/agent/error_classifier.py:classify_by_status`
**行为**: HTTP 401 → `FailoverReason.auth`，`retryable=false`, `shouldRotateCredential=true`, `shouldFallback=true`。

### R-AGENT-002: classifyApiError 状态码 403 无 key-limit 语义 → auth
**行为**: 403 且消息不含 "key limit exceeded" / "spending limit" → `auth`，不 rotate 但 fallback。

### R-AGENT-003: classifyApiError 状态码 403 含 key-limit → billing
**行为**: 403 + "key limit exceeded" → `billing`，rotate+fallback。

### R-AGENT-004: classifyApiError 状态码 402 默认 billing
**行为**: 402 默认 → `billing`，non-retryable。

### R-AGENT-005: classifyApiError 402 含 usage-limit+transient 信号 → rate_limit
**行为**: 402 + `_USAGE_LIMIT_PATTERNS` 任一 + `_USAGE_LIMIT_TRANSIENT_SIGNALS` 任一 → `rate_limit`，retryable。

### R-AGENT-006: classifyApiError 404 → model_not_found
**行为**: 404 不论文本 → `model_not_found`，non-retryable, fallback。

### R-AGENT-007: classifyApiError 413 → payload_too_large
**行为**: 413 → `payload_too_large`，retryable + shouldCompress。

### R-AGENT-008: classifyApiError 429 → rate_limit
**行为**: 429 → `rate_limit`，retryable + rotate + fallback。

### R-AGENT-009: classifyApiError 429 含 "extra usage" + "long context" → long_context_tier
**行为**: 高优先级早期拦截。retryable + shouldCompress。

### R-AGENT-010: classifyApiError 400 含 "signature" + "thinking" → thinking_signature
**行为**: Anthropic 特殊错误，retryable, 不 compress。

### R-AGENT-011: classifyApiError 400 含 context_length patterns → context_overflow
**行为**: retryable + shouldCompress。

### R-AGENT-012: classifyApiError 400 含 model_not_found patterns → model_not_found
**行为**: non-retryable, fallback。

### R-AGENT-013: classifyApiError 400 含 rate_limit patterns → rate_limit
**行为**: 优先于 billing 匹配。

### R-AGENT-014: classifyApiError 400 含 billing patterns → billing
**行为**: non-retryable, rotate+fallback。

### R-AGENT-015: classifyApiError 400 泛消息 + large session → context_overflow
**行为**: 消息 <30 字符 / 为 "error" / 为空 + approxTokens > 80000 推断为 context_overflow。

### R-AGENT-016: classifyApiError 400 其他 → format_error
**行为**: 默认 → `format_error`，non-retryable。

### R-AGENT-017: classifyApiError 500/502 → server_error retryable
### R-AGENT-018: classifyApiError 503/529 → overloaded retryable
### R-AGENT-019: classifyApiError 其他 4xx → format_error non-retryable
### R-AGENT-020: classifyApiError 其他 5xx → server_error retryable

### R-AGENT-021: classifyApiError error_code resource_exhausted → rate_limit
### R-AGENT-022: classifyApiError error_code insufficient_quota → billing
### R-AGENT-023: classifyApiError error_code context_length_exceeded → context_overflow
### R-AGENT-024: classifyApiError error_code model_not_found → model_not_found

### R-AGENT-030: classifyApiError msg-only payload patterns → payload_too_large
### R-AGENT-031: classifyApiError msg-only billing patterns → billing
### R-AGENT-032: classifyApiError msg-only rate_limit patterns → rate_limit
### R-AGENT-033: classifyApiError msg-only context_overflow patterns → context_overflow
### R-AGENT-034: classifyApiError msg-only auth patterns → auth
### R-AGENT-035: classifyApiError msg-only model_not_found patterns → model_not_found

### R-AGENT-040: classifyApiError IOException / SocketTimeoutException → timeout
### R-AGENT-041: classifyApiError server_disconnect + large session → context_overflow
### R-AGENT-042: classifyApiError server_disconnect 小会话 → timeout
### R-AGENT-043: classifyApiError 未匹配 → unknown retryable

### R-AGENT-050: _extractErrorCode 读取 body.error.code
### R-AGENT-051: _extractErrorCode 回退到顶层 code / error_code
### R-AGENT-052: _extractErrorCode 整数 code 转字符串
### R-AGENT-053: _extractErrorCode 空 body 返回空字符串
### R-AGENT-054: _extractMessage 优先 body.error.message，上限 500 字符
### R-AGENT-055: _extractMessage 无 body 回退到 throwable.message
### R-AGENT-056: _extractStatusCode 反射读 getStatusCode()
### R-AGENT-057: _extractErrorBody 反射读 getBody()

### R-AGENT-060: ClassifiedError.isAuth 仅 auth / auth_permanent 为真
### R-AGENT-061: FailoverReason.value 字符串与 Python 线协议对齐

---

### R-AGENT-100: SmartModelRouting 短简单消息路由 cheap
**来源**: `reference/hermes-agent/hermes/agent/smart_model_routing.py`
**行为**: 输入 "hi" → `gpt-4o-mini`，estimatedCost < 0.01。

### R-AGENT-101: SmartModelRouting 三引号代码围栏提升 complexity 至 mid
**行为**: length<50 (0.1) + ``` (0.2) = 0.3（FP drift 使其轻微超过），落入 mid 分支，返回 currentModel。
**Why**: 浮点 0.1+0.2 = 0.30000000000000004，刚好跨过 "<= 0.3" 的 cheap 边界。

### R-AGENT-102: SmartModelRouting 长 + 复杂关键字 → expensive
**行为**: 长消息 + "analyze"/"architecture" 等关键词 → `claude-opus-4-6`。

### R-AGENT-103: SmartModelRouting mid-length + 复杂关键字 → currentModel
**行为**: 50-199 字符 + "analyze" → 返回 `currentModel`。

---

### R-AGENT-110: calculateRetryDelayMs 指数退避且 cap 在 maxMs
**来源**: `reference/hermes-agent/hermes/agent/retry_utils.py`
**行为**: attempt 很大时（如 30）结果等于 `maxMs`。

### R-AGENT-111: calculateRetryDelayMs 无 jitter 是 `baseMs * 2^attempt`
**行为**: (0, 1000) → 1000；(1, 1000) → 2000；(2, 1000) → 4000。

### R-AGENT-112: calculateRetryDelayMs jitter=true 区间 [base·2^n, base·2^n + maxJitter)
**行为**: attempt=3 baseMs=1000 jitter=true → 结果 ∈ [8000, 12000)（20 次采样）。

### R-AGENT-113: shouldRetry 达到 maxRetries 返回 false
**行为**: `shouldRetry(IOException, maxRetries=3, attempt=3)` = false；attempt=0 = true。

### R-AGENT-114: shouldRetry 接受 IO/网络异常
**行为**: IOException / SocketTimeoutException / ConnectException → true。

### R-AGENT-115: shouldRetry 拒绝无关异常
**行为**: IllegalArgumentException → false。

### R-AGENT-116: jitteredBackoff 结果在 cap 以内
**行为**: attempt=10 baseDelay=5 maxDelay=60 jitterRatio=0.5 → 结果 ∈ [30, 60]。

### R-AGENT-117: CountIterator 永远 hasNext 且递增
**行为**: 顺序产生 start, start+1, ...。

### R-AGENT-118: withRetry 首次成功立即返回结果
### R-AGENT-119: withRetry 非可重试异常立刻 rethrow，不计数

---

### R-AGENT-130: TitleGenerator.generate 空/纯空白 → "New Chat"
**来源**: `reference/hermes-agent/hermes/agent/title_generator.py`

### R-AGENT-131: TitleGenerator.generate 归一化 whitespace 且短文案原样
**行为**: `"Hello  world\n\ntest"` → `"Hello world test"`。

### R-AGENT-132: TitleGenerator.generate 长输入去停用词 + 截断 60 字符
**行为**: 长 input 剥掉 "the" 等停用词，总长 ≤ 60。

### R-AGENT-133: TitleGenerator.generate 全停用词回退到截断
**行为**: 全是停用词的长输入 → 长度恰好 60。

### R-AGENT-134: TitleGenerator.generateFromMessages 取第一条 user 消息
### R-AGENT-135: TitleGenerator.generateFromMessages 无 user → "New Chat"
### R-AGENT-136: _TITLE_PROMPT 包含指令关键字（"3-7 words" / "Return ONLY"）
### R-AGENT-137: generateTitle stub 可调用且返回 null 或 String

---

### R-AGENT-160: FileSafety.buildWriteDeniedPaths 含 SSH 私钥路径
**来源**: `reference/hermes-agent/hermes/agent/file_safety.py`
**行为**: 生成列表含 `.ssh/id_rsa`, `.ssh/id_ed25519`, `.ssh/authorized_keys`, `/etc/sudoers`, `/etc/passwd`, `/etc/shadow`。

### R-AGENT-161: FileSafety.buildWriteDeniedPaths 含 shell rc 文件
**行为**: `.bashrc`, `.zshrc`, `.profile`, `.netrc`。

### R-AGENT-162: FileSafety.buildWriteDeniedPrefixes 所有项以分隔符结尾
**行为**: 每个 prefix 末尾是 `File.separator`；含 `.ssh/`, `.aws/`, `.gnupg/`。

### R-AGENT-163: FileSafety.isWriteDenied 绝对路径阻塞
**行为**: `$HOME/.ssh/id_rsa` / `/etc/passwd` / `/etc/sudoers` → true。

### R-AGENT-164: FileSafety.isWriteDenied prefix 子路径阻塞
**行为**: `$HOME/.ssh/any_file` / `$HOME/.aws/credentials` / `$HOME/.gnupg/anything` → true。

### R-AGENT-165: FileSafety.isWriteDenied 普通用户路径不阻塞
**行为**: 在无 `HERMES_WRITE_SAFE_ROOT` 环境下 `$HOME/projects/x.txt` 和 `/tmp/nope.log` → false。

### R-AGENT-166: FileSafety.getSafeWriteRoot 环境变量缺失时为 null 或绝对路径
**行为**: env 未设 → null；已设则必须绝对路径。

### R-AGENT-167: FileSafety.getReadBlockError 普通路径返回 null
### R-AGENT-168: FileSafety.getReadBlockError Hermes cache 路径返回 "internal Hermes cache" 错误
### R-AGENT-169: FileSafety.getReadBlockError 展开波浪号

---

## 域 ACP — ACP adapter

### R-ACP-001: Tools.TOOL_KIND_MAP 文件操作 kind
**来源**: `reference/hermes-agent/hermes/acp_adapter/tools.py:TOOL_KIND_MAP`
**行为**: `read_file→read`, `write_file→edit`, `patch→edit`, `search_files→search`。

### R-ACP-002: Tools.TOOL_KIND_MAP execute kind
**行为**: `terminal`, `process`, `execute_code` → `execute`。

### R-ACP-003: Tools.TOOL_KIND_MAP fetch kind
**行为**: `web_search`, `web_extract`, `browser_navigate` → `fetch`。

### R-ACP-004: Tools.TOOL_KIND_MAP think kind
**行为**: `_thinking` → `think`。

### R-ACP-005: Tools.getToolKind 未知工具默认 `other`
**行为**: 无映射时返回 `"other"`，不是 null / 不抛异常。

### R-ACP-006: Tools.makeToolCallId 前缀 `tc-` 长度 15
**行为**: `tc-` + 12 字符（UUID 去连字符后 take(12)）。

### R-ACP-007: Tools.makeToolCallId 每次返回新值
**行为**: 两次调用不等（碰撞概率可忽略）。

### R-ACP-010: Tools.buildToolTitle terminal 短命令
**行为**: `"ls"` → `"terminal: ls"`。

### R-ACP-011: Tools.buildToolTitle terminal 长命令截断 80 字符
**行为**: 命令长度 > 80 → 取 77 字符 + `"..."`，整体 `"terminal: " + 77 + "..."` = 90 字符。

### R-ACP-012: Tools.buildToolTitle terminal 缺参
**行为**: `{}` → `"terminal: "`。

### R-ACP-013: Tools.buildToolTitle read_file / write_file 使用 path
**行为**: `read: /tmp/a`, `write: /tmp/b`；缺 path → `?`。

### R-ACP-014: Tools.buildToolTitle patch 含 mode
**行为**: `patch (append): /tmp/c`；缺 mode 默认 `replace`。

### R-ACP-015: Tools.buildToolTitle search_files / web_search 使用 pattern / query
### R-ACP-016: Tools.buildToolTitle web_extract 单/多 URL
**行为**: 单 URL → `extract: http://a`；多 URL → `extract: http://a (+N-1)`；空列表 → `web extract`。

### R-ACP-017: Tools.buildToolTitle delegate_task 短/长 goal
**行为**: 短 goal 直接拼 `delegate: X`；长 goal 取 57 字符 + `...`；缺 goal → `delegate task`。

### R-ACP-018: Tools.buildToolTitle execute_code / vision_analyze
**行为**: `execute_code` → `execute code`；`vision_analyze` → `analyze image: <question[:50]>`；缺 question → `?`。

### R-ACP-019: Tools.buildToolTitle 未处理 tool → 回退到 toolName
**行为**: `random_tool` → `random_tool`。

### R-ACP-020: Tools.extractLocations 无 path 返回空
### R-ACP-021: Tools.extractLocations 只有 path → 单元素无 line
### R-ACP-022: Tools.extractLocations offset 作为 line
### R-ACP-023: Tools.extractLocations offset 和 line 同时存在优先 offset
### R-ACP-024: Tools.extractLocations 仅 line 时用 line

### R-ACP-030: Tools.buildToolStart 填充 id/title/kind/locations/rawInput
### R-ACP-031: Tools.buildToolStart write_file 产生 DiffContent
### R-ACP-032: Tools.buildToolStart patch replace 产生 DiffContent(old+new)
### R-ACP-033: Tools.buildToolStart terminal 产生 `$ cmd` 文本块
### R-ACP-034: Tools.buildToolStart read_file 产生 "Reading X" 文本块
### R-ACP-035: Tools.buildToolStart search_files 产生 "Searching for 'p' (target)" 文本块（默认 target=content）
### R-ACP-036: Tools.buildToolStart 未知 tool → JSON dump 文本块

### R-ACP-040: Tools.buildToolComplete 填充 id/kind/status=completed/rawOutput
### R-ACP-041: Tools.buildToolComplete null result → 空文本块
### R-ACP-042: Tools.buildToolComplete 超长 result (>5000) → 截 4900 + "... (N chars total, truncated)"
### R-ACP-043: Tools.buildToolComplete 短 result 原样透传

### R-ACP-050: detectProvider 无凭证时返回 null
**来源**: `reference/hermes-agent/hermes/acp_adapter/auth.py`
**行为**: CredentialPool 里无任何 provider 可用时 `detectProvider()` 返回 null。

### R-ACP-051: hasProvider 与 detectProvider 一致
**行为**: `hasProvider() == (detectProvider() != null)`。

### R-ACP-052: detectProvider 多次调用等价
**行为**: 无 side-effect，多次调用返回相同值。

---

## 域 TOOL — Tool implementations

Kotlin 入口 `hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/*.kt`；Python 上游 `reference/hermes-agent/tools/*.py`。Android 端部分工具（Terminal/CodeExecution/Cron/Skill 写入类/各远端 AI 服务）按 §6.1 平台差异显式存根 — 这些存根本身也是可测合约（"在 Android 上必定返回 toolError"）。

### Registry.kt — 工具注册表

#### R-TOOL-001: `ToolRegistry.register` 同名不同 toolset 拒收
**来源**: `reference/hermes-agent/tools/registry.py:registry.register` + `Registry.kt`
**行为**: 已注册 `name` 再次以不同 `toolset` 注册时记录 ERROR 但不抛异常；调用 `get(name)` 仍返回首次注册的 entry。
**验收**: 先 `register("foo", toolset="a", ...)` 后 `register("foo", toolset="b", ...)` → logcat WARN/ERROR + `get("foo").toolset == "a"`。

#### R-TOOL-002: MCP 工具允许 re-register
**行为**: 当新旧 name 都以 `mcp-` 开头时允许后写覆盖（MCP 动态 reconnect 场景）。
**验收**: 两次 `register("mcp-x", ...)` 不产生 ERROR 日志。

#### R-TOOL-003: `register` 自动装配 checkFn 仅首写生效
**行为**: 同一 toolset 首次注册时其 `checkFn` 写入 `_toolsetChecks`；后续注册即便带 checkFn 也被忽略。

#### R-TOOL-004: `dispatch` 未注册工具返回结构化 error JSON
**行为**: `dispatch("nonexistent", {})` 返回 `{"error":"Unknown tool: nonexistent"}` 的 Gson 字符串，不抛异常。

#### R-TOOL-005: `dispatch` handler==null 返回特定 error
**行为**: 注册了 entry 但 handler 为 null → `{"error":"Tool '<name>' has no handler"}`。

#### R-TOOL-006: `dispatch` handler 抛异常被包装
**行为**: handler 抛 `RuntimeException("boom")` → `{"error":"Tool execution failed: RuntimeException: boom"}` 字符串返回，绝不向上抛。

#### R-TOOL-007: `deregister` 最后一个条目时清 toolset 全部状态
**行为**: toolset 下最后一个 tool 被移除时同步清 `_toolsetChecks[toolset]` 与所有指向它的 alias。

#### R-TOOL-008: `getDefinitions` 失败的 checkFn 静默剔除
**行为**: `checkFn` 抛异常 → 该 tool 从结果中移除；不抛异常、不记 ERROR。

#### R-TOOL-009: `getDefinitions` 单次调用内 checkFn 去重
**行为**: 每次 `getDefinitions()` 内部 per-call memo map，同 toolset 的 checkFn 只调用一次。

#### R-TOOL-010: `getMaxResultSize` 优先级
**行为**: 优先 entry pinned size → 调用方 default → 硬编码 `50000`；其中 `read_file` 固定为 `POSITIVE_INFINITY`（R-TOOL-045）。

#### R-TOOL-011: `toolError(msg, extra)` 返回合法 JSON
**行为**: 结果是 Gson 序列化的 JSON 字符串，且解析后含 `error` 字段等于 msg；`extra` map 的 key 合并到顶层。

#### R-TOOL-012: `toolResult(data, kwargs)` 保留类型
**行为**: data 为 Map 时字段进入顶层；kwargs 合并后若与 data key 冲突，kwargs 胜出。

#### R-TOOL-013: `registerToolsetAlias` 覆盖警告
**行为**: 指向不同 toolset 的 alias 覆盖时 logcat WARN；仍然完成覆盖。

#### R-TOOL-014: `isToolsetAvailable` 异常视为不可用
**行为**: toolset 的 checkFn 抛异常 → `isToolsetAvailable(name) == false`。

#### R-TOOL-015: `getDefinitions` 输出 schema 兼容 OpenAI
**行为**: 每条输出形状为 `{"type":"function","function":{"name":..,"description":..,"parameters":..}}`；`name` 字段被显式设为 tool name（而非 schema 里重复）。

### Approval.kt — 危险命令审批

#### R-TOOL-020: `_normalizeCommandForDetection` 规范化输入
**来源**: `reference/hermes-agent/tools/approval.py`
**行为**: 剥离 ANSI escape、null byte，NFKC 归一化；防止全宽半宽绕过。
**验收**: `ｒｍ　－ｒｆ　／` 经规范化后被 `detectDangerousCommand` 命中。

#### R-TOOL-021: `detectDangerousCommand` 返回三元组
**行为**: 命中时返回 `Triple(true, description, description)`；未命中全部为 null。

#### R-TOOL-022: `promptDangerousApproval` 无 callback 默认拒绝
**行为**: Android 无注册审批 callback 时直接返回 `"deny"`，永不静默执行。

#### R-TOOL-023: 沙箱环境跳过审批
**行为**: `HERMES_ENV in {docker, singularity, modal, daytona}` 或 `HERMES_YOLO_MODE` 或 session-yolo 开启时 `checkDangerousCommand(...).approved=true` 无需用户操作。

#### R-TOOL-024: cron 会话非 approve 模式阻塞
**行为**: `HERMES_CRON_SESSION` 环境变量存在且 `cron_mode != "approve"` 时 → `approved=false` + 消息包含 `approvals.cron_mode: approve`。

#### R-TOOL-025: gateway 审批 5 分钟超时
**行为**: `HERMES_GATEWAY_SESSION` / `HERMES_EXEC_ASK` 下提交 pending，最长阻塞 `gateway_timeout` 秒（默认 300）；超时返回 `"timed out"`。

#### R-TOOL-026: "always" 选项持久化到 YAML
**行为**: 选 "always" 时同步调用 `approveSession`+`approvePermanent`，并 `savePermanentAllowlist` 到 `~/.hermes/config.yaml` 的 `command_allowlist:` 块。

#### R-TOOL-027: `_smartApprove` 回退 escalate
**行为**: Android 无辅助 LLM → 返回 `"escalate"`，上层回退到手动审批。

#### R-TOOL-028: `isApproved` 双键 alias 查
**行为**: 检查 description key 与 legacy first-word key 两种命中方式。

#### R-TOOL-029: Tirith "always" 降级
**行为**: Tirith 安全模块标记的危险命令即便选 "always" 也仅加入 session 白名单，不落永久 YAML（防止被第三方规则污染）。

### BudgetConfig.kt — 结果大小预算

#### R-TOOL-045: `PINNED_THRESHOLDS` 强制 read_file 无上限
**来源**: `reference/hermes-agent/tools/budget_config.py`
**行为**: `PINNED_THRESHOLDS["read_file"] == POSITIVE_INFINITY`；`resolveThreshold("read_file")` 必返回 Double 无穷。
**验收**: 任何 toolOverrides / registry pinned 都不覆盖该值。

#### R-TOOL-046: 优先级链
**行为**: pinned → toolOverrides → `registry.getMaxResultSize` → `defaultResultSize`。

#### R-TOOL-047: 返回 Double 类型
**行为**: 返回值是 `Double`（非 Int），让 `POSITIVE_INFINITY` 能原样流通。

#### R-TOOL-048: 默认常量
**行为**: `DEFAULT_RESULT_SIZE_CHARS=100_000`、`DEFAULT_TURN_BUDGET_CHARS=200_000`、`DEFAULT_PREVIEW_SIZE_CHARS=1_500`。

### TodoTool.kt — Todo 持久化

#### R-TOOL-055: `VALID_STATUSES` 四值集合
**行为**: 精确等于 `{pending, in_progress, completed, cancelled}`。

#### R-TOOL-056: `merge=false` 整表替换
**行为**: 覆盖现有列表，旧条目全丢。

#### R-TOOL-057: `merge=true` 按 id 更新
**行为**: 存在的 id 只更新 content/status，不改插入顺序；新 id 追加末尾。

#### R-TOOL-058: `_validate` 永不抛异常
**行为**: 缺 `id` → `"?"`；content 为空 → `"(no description)"`；status 非法 → `"pending"`。

#### R-TOOL-059: `_dedupeById` 保留最后一次
**行为**: 同一 call 内同 id 多条时保留最后一条。

#### R-TOOL-060: merge 模式下非法 status 静默忽略
**行为**: 更新里 status 不在 `VALID_STATUSES` → 不改现有 status，不报错。

#### R-TOOL-061: `formatForInjection` 空列表返回 null
**行为**: 列表空或无 pending/in_progress 条目时返回 null（调用方据此决定是否注入）。

#### R-TOOL-062: 显示 marker 映射
**行为**: `[x]/[>]/[ ]/[~]/[?]` 分别对应 completed/in_progress/pending/cancelled/unknown。

#### R-TOOL-063: store==null 返回结构化错误
**行为**: `todoTool(..., store=null)` → `toolError("TodoStore not initialized")`。

#### R-TOOL-064: 响应形状固定
**行为**: 读写都返回 `{"todos":[..],"summary":{total, pending, in_progress, completed, cancelled}}`。

### FileOperations.kt + FileTools.kt — 文件读写

#### R-TOOL-070: `_isWriteDenied` 覆盖敏感文件精确匹配
**行为**: 精确匹配 SSH 私钥、`/etc/sudoers/passwd/shadow`、shell rc 文件时返回 true。

#### R-TOOL-071: `_isWriteDenied` 前缀匹配
**行为**: `~/.ssh/`、`~/.aws/`、`~/.gnupg/`、`~/.kube/`、`/etc/sudoers.d/`、`/etc/systemd/`、`~/.docker/`、`~/.azure/`、`~/.config/gh/` 下的任何写入被拒。

#### R-TOOL-072: `HERMES_WRITE_SAFE_ROOT` 约束一切写
**行为**: 环境变量存在时，所有落在 root 外的写入被拒（即便在 allowlist）。

#### R-TOOL-073: `readFile` 行数上限
**行为**: `MAX_LINES=2000`、`MAX_LINE_LENGTH=2000`；超出截断并附 `... [truncated]` 后缀。

#### R-TOOL-074: 二进制扩展名拒读
**行为**: 后缀在 `BINARY_EXTENSIONS` → 返回 `isBinary=true` + error，不读内容。

#### R-TOOL-075: 缺失文件 suggestions
**行为**: 同目录下扫 50 个候选，按名/basename/前缀/子串/扩展名评分，返回 top 5。

#### R-TOOL-076: `writeFile` 自动创建父目录
**行为**: `parentFile.mkdirs()` 并在响应里 `dirsCreated=true`（即便父目录已存在）。

#### R-TOOL-077: `moveFile` 跨分区 fallback
**行为**: `renameTo` 失败时退化为 copy+delete。

#### R-TOOL-078: `search` 结果截断 truncated 标记
**行为**: `totalCount > limit + offset` 时结果里 `truncated=true`。

#### R-TOOL-079: `_exec` 超时 destroyForcibly
**行为**: 超时 → 强杀进程 + `exitCode=-1` + 消息 `"Command timed out after N seconds"`。

#### R-TOOL-080: `_checkLint` 缺 linter 跳过
**行为**: 扩展名不在 `LINTERS` map 或二进制不在 PATH → 返回 `skipped=true` + 原因字符串，不视为失败。

#### R-TOOL-095: `_isBlockedDevice` 阻断 /dev 字符设备
**行为**: `/dev/zero/random/urandom/full/tty/stdin/stdout/stderr` + `/proc/*/fd/{0,1,2}` 读被拒（防挂死 / 无限输出）。

#### R-TOOL-096: `_checkSensitivePath` 写入黑名单
**行为**: `/etc/`、`/boot/`、`/usr/lib/systemd/`、`/private/etc/`、`/private/var/` 下写入拒绝；精确拒 `/var/run/docker.sock`、`/run/docker.sock`。

#### R-TOOL-097: `notifyOtherToolCall` 重置读 dedup 计数
**行为**: 非 read 工具运行后 dedup 计数清零（避免跨工具"你刚读过"误报）。

#### R-TOOL-098: `_checkFileStaleness` 外部修改检测
**行为**: 记录 `lastModified`，下次读时若 mtime 变大则返回 warning 字符串（不阻塞读）。

#### R-TOOL-099: per-task `_fileOpsCache` 缓存
**行为**: 每个 taskId 对应一个 `ShellFileOperations` 实例；`clearFileOpsCache(null)` 清所有，`clearFileOpsCache(tid)` 清一个。

#### R-TOOL-100: dedup/timestamps/history LRU 上限
**行为**: 超过 `_READ_DEDUP_CAP=1000` / `_READ_TIMESTAMPS_CAP=1000` / `_READ_HISTORY_CAP=500` 按插入顺序驱逐最老条目。

#### R-TOOL-101: handler 默认值吞容错
**行为**: `path=""`、`offset=1`、`limit=500`、`target="content"` 等默认值被 missing arg 沉默回落，不抛异常。

### MemoryTool.kt — 记忆读写

#### R-TOOL-110: `ENTRY_DELIMITER = "\n§\n"` 分隔符恒定
**行为**: 同步到磁盘的记忆用 `\n§\n` 分隔条目；变更破坏解析。

#### R-TOOL-111: `_scanMemoryContent` 拦截不可见 unicode
**行为**: `U+200B..U+FEFF` 零宽、`U+202A..U+202E` BiDi → 返回 `"Blocked: content contains invisible unicode character U+XXXX"`。

#### R-TOOL-112: `_scanMemoryContent` 拦截注入模式
**行为**: 至少 8 条正则（ignore previous instructions / role_hijack / deception / sys_prompt_override / disregard_rules / curl+env 外泄 / cat .env 等）命中 → 阻塞。

#### R-TOOL-113: 两 target 独立存储
**行为**: `target in {memory, user}`；`memory` 上限 2200 字符、`user` 上限 1375；互不干扰。

#### R-TOOL-114: `add` 幂等
**行为**: 重复添加相同 trim 后内容不追加，返回 `message="Entry already exists (no duplicate added)"`。

#### R-TOOL-115: `add` 超额拒绝
**行为**: 新条目会使总长度超限 → `success=false` + 包含 `current_entries` 与 `usage` 字段的错误。

#### R-TOOL-116: `replace` 多重不同匹配需更具体
**行为**: 多处匹配且文本不同 → `{success=false, matches=[previews]}`；若所有匹配同文本取第一处。

#### R-TOOL-117: `replace` 禁止空 new_content
**行为**: `new_content==""` → `"new_content cannot be empty. Use 'remove' to delete entries."`。

#### R-TOOL-118: `_fileLock` 原子写
**行为**: 每次变更先 FileLock → 读盘 → dedupe → 写临时文件 `.mem_XXXXXXXX.tmp` → rename 原子替换。

#### R-TOOL-119: system prompt snapshot 冻结
**行为**: `_systemPromptSnapshot` 在 `loadFromDisk` 时快照，session 内不变（保持 prefix-cache 有效）；live mutations 只影响 tool 响应。

### PathSecurity.kt — 路径安全

#### R-TOOL-130: `validateWithinDir` 穿越 canonicalPath
**行为**: 同时对 `path` 和 `root` 取 `canonicalPath`（跟随 symlink、归一化 `..`），符号链接逃逸被抓。

#### R-TOOL-131: 失败返回字符串，不抛
**行为**: 逃逸 → 返回 `"Path escapes allowed directory: $path not under $root"` 字符串；canonicalization 异常被当作"逃逸"（fail-closed）。

#### R-TOOL-132: `hasTraversalComponent` 不触盘
**行为**: 纯字符串检测，包含字面 `..` 段返回 true（仅快速预筛）；不做 canonicalization。

### SkillsHub.kt — 技能源聚合

#### R-TOOL-140: `normalizeBundlePath` 拒不安全路径
**行为**: 抛 IAE 当：empty path / absolute / contains `..` / Windows 驱动器盘符 `[A-Za-z]:` / 非 `allowNested` 下多段路径。

#### R-TOOL-141: `_resolveToken` 优先级链
**行为**: `GITHUB_TOKEN`/`GH_TOKEN` env → `gh auth token`（5s 超时）→ GitHub App JWT（需要 3 个 env + PyJWT 等价；Android 无此能力时返回 null）→ anonymous。

#### R-TOOL-142: `_rateLimited` 设置条件
**行为**: HTTP 403 + `X-RateLimit-Remaining=0` 时置 `_rateLimited=true` 并 WARN。

#### R-TOOL-143: `GitHubSource.search` dedupe by trust
**行为**: 结果按名去重，保留 trust-rank 更高者（builtin=2 > trusted=1 > community=0）；limit 在 dedupe 之后裁剪。

#### R-TOOL-144: `GitHubSource.fetch` 要求 SKILL.md 存在
**行为**: owner/repo/path 三段 + 必有 SKILL.md；否则 fetch 返回 null。

#### R-TOOL-145: `_downloadDirectoryViaTree` truncated 树拒绝
**行为**: GitHub tree API 响应 `truncated=true` → 返回 null（不可信赖）；404 回退 recursive `/contents/`。

#### R-TOOL-146: `parallelSearchSources` Android 顺序执行
**行为**: 声称 parallel 但 Android 上顺序执行；per-source 异常 catch 并加入 `failed` 列表；总 timeout 参数接受但忽略。

#### R-TOOL-147: `installFromQuarantine` Android stub
**行为**: 返回 `Pair(false, "not wired on Android")`；`uninstallSkill` 同。

#### R-TOOL-148: `bundleContentHash` 确定性
**行为**: 文件按绝对路径排序，逐字节 SHA-256，截断前 16 hex，前缀 `sha256:`。

### SkillManagerTool.kt — 技能管理（Android stub）

#### R-TOOL-160: 所有 CRUD 返回 Android 拒绝
**行为**: `_createSkill`、`_editSkill`、`_patchSkill`、`_deleteSkill` 全部返回 `{"error":"skill_manage is not available on Android"}`。

#### R-TOOL-161: `_validateName` 正则 + 长度
**行为**: 拒绝不匹配 `^[a-z0-9][a-z0-9._-]*$` 或长度 > 64 的名字。

#### R-TOOL-162: `_validateContentSize` 单文件上限
**行为**: 每文件 ≤ `MAX_SKILL_CONTENT_CHARS=100_000`；错误信息带 label。

#### R-TOOL-163: `_atomicWriteText` 原子替换
**行为**: `createTempFile` + `Files.move(…, ATOMIC_MOVE)`；失败删临时文件并重抛。

### SkillsGuard.kt — 技能安全扫描

#### R-TOOL-170: `scanFile` 限定可扫描扩展
**行为**: 扩展名不在 `SCANNABLE_EXTENSIONS` 且文件名 ≠ `SKILL.md` → 跳过。

#### R-TOOL-171: findings per-line dedup
**行为**: 同一 `(pattern_id, line_number)` 只保留一条，避免多 pass 重复。

#### R-TOOL-172: 长匹配截断
**行为**: 匹配字符串长于 120 → 截断 + `...` 后缀。

#### R-TOOL-173: 不可见字符 per-line 一条
**行为**: 每行只报一条 invisible-unicode finding，severity=high、category=injection。

#### R-TOOL-174: symlink 逃逸检测
**行为**: 链接目标 canonicalFile 不在 root 内 → 触发 `symlink_escape` (critical)；canonicalization 异常 → `broken_symlink` (medium)。

#### R-TOOL-175: 可执行位仅容白名单扩展
**行为**: `.sh/.bash/.py/.rb/.pl` 以外的可执行文件被标记。

#### R-TOOL-176: `_determineVerdict` 严格阶梯
**行为**: 无 finding → safe；任一 critical → dangerous；任一 high → caution；仅 medium/low → caution（medium 不升级为 dangerous）。

#### R-TOOL-177: `shouldAllowInstall` 策略矩阵
**行为**: 查 `INSTALL_POLICY[trustLevel][verdict]`：allow→`(true, ...)`；ask→`(null, ...)`（仅 agent-created + dangerous）；block→`(false, ...)`；`force=true` 覆盖 block 返回 `(true, "Force-installed ...")`。

#### R-TOOL-178: `_resolveTrustLevel` 前缀归一化
**行为**: 去除 `skills-sh/`、`skils-sh/` 等拼写 alias；`agent-created` → `agent-created`；`official/*` → `builtin`；`openai/skills`、`anthropics/skills` → `trusted`；其余 → `community`。

### SkillsSync.kt — 技能同步

#### R-TOOL-185: 遵守 `HERMES_BUNDLED_SKILLS` env
**行为**: 环境变量存在时用它；否则 `<hermes_home>/bundled_skills`。

#### R-TOOL-186: manifest 格式
**行为**: v2 每行 `name:hash`，按 name 升序；缺文件返回空 map；格式错误行 hash 取空字符串。

#### R-TOOL-187: `_readSkillName` 只扫前 4000 字节 frontmatter
**行为**: 首 `---` 块取 `name:` 首值；未找到返回 null。

#### R-TOOL-188: 用户修改保护
**行为**: `userHash != originHash` → 标记 user_modified 并跳过，不覆盖。

#### R-TOOL-189: 目的地存在时首次 skip 不覆盖
**行为**: manifest 里没记录该 skill 而目的地已存在 → 记 hash 但不覆盖 + `skipped` 计数+1。

#### R-TOOL-190: 复制失败回滚 .bak
**行为**: 更新时若 copy 失败，`.bak` rename 回原名，保留旧版本。

#### R-TOOL-191: `resetBundledSkill` stub
**行为**: 返回 false，Android 未布线。

### SkillsTool.kt — 技能发现 + 元数据

#### R-TOOL-195: `loadEnv` 手写解析
**行为**: 跳空行和 `#`；剥离两端成对单/双引号；首个 `=` 切分 key/value。

#### R-TOOL-196: `_parseFrontmatter` 前导 `---` 要求
**行为**: 不是 `---` 开头 → 返回 (空 frontmatter, 原 content)；SnakeYAML 解析失败也返回空 map。

#### R-TOOL-197: env var 名正则过滤
**行为**: `^[A-Za-z_][A-Za-z0-9_]*$`，其他 silently drop。

#### R-TOOL-198: env 文件或进程 env 满足一条即可
**行为**: `_isEnvVarPersisted` 先检供给的 env map，再查 `System.getenv()`。

#### R-TOOL-199: `_findAllSkills` 排除目录
**行为**: `.git/.github/.hub` 段被跳过；`skipDisabled=true` 时按 `HERMES_DISABLED_SKILLS` 逗号列过滤。

#### R-TOOL-200: `_getSessionPlatform` 默认 android
**行为**: 未识别的 `os.name` → `"android"`。

#### R-TOOL-201: 远端 env 信息被中性化
**行为**: `_isGatewaySurface()==false`、`_getTerminalBackendName()=="android"`、`_gatewaySetupHint()==""`。

### TerminalTool.kt — 终端执行

#### R-TOOL-210: `background=true` Android 不支持
**行为**: 立即返回 `{exit_code:-1, error:"background execution is not supported on Android"}`。

#### R-TOOL-211: timeout 双重 clamp
**行为**: 实际超时 = `min(timeout ?: 30, FOREGROUND_MAX_TIMEOUT)`（默认 600，env 可调）。

#### R-TOOL-212: Android shell 固定路径
**行为**: 显式 `/system/bin/sh -c <command>`，SELinux 安全。

#### R-TOOL-213: 超时退出码 124
**行为**: 超时后 `destroyForcibly` + `exit_code=124`（GNU timeout 约定）+ 部分 stdout + 标记信息。

#### R-TOOL-214: `_validateWorkdir` 白名单字符
**行为**: 正则 `^[A-Za-z0-9/\\:_\-.~ +@=,]+$`，其他拒绝。

#### R-TOOL-215: `_parseEnvVar` bad input 回默认
**行为**: `TERMINAL_TIMEOUT=5m` 这种解析失败 → 转换器应用到默认字符串，不崩。

#### R-TOOL-216: sudo/approval/Modal stubs
**行为**: `setSudoPasswordCallback`、`setApprovalCallback`、`_transformSudoCommand`、`_createEnvironment` 在 Android 上为空实现。

### CodeExecutionTool.kt — 代码执行（Android stub）

#### R-TOOL-225: `executeCode` 永远 toolError
**行为**: 返回 `toolError("code_execution tool is not available on Android")`。

#### R-TOOL-226: `_rpcServerLoop`/`_rpcPollLoop` 抛异常
**行为**: Android 上 throw `UnsupportedOperationException`（防被误启动）。

#### R-TOOL-227: `buildExecuteCodeSchema` 按 mode 变描述
**行为**: `strict` vs `project` 的 description 文本不同；enabled-tool 列表反映在 doc lines 中。

#### R-TOOL-228: `_resolveChildCwd` 环境变量优先
**行为**: `TERMINAL_CWD` 环境变量存在且是目录 → 用它；否则 `user.dir`；`strict` 模式返 staging dir。

### ClarifyTool.kt — 澄清问答

#### R-TOOL-235: 空问题拒绝
**行为**: `question.trim().isEmpty()` → `toolError("Question text is required.")`。

#### R-TOOL-236: choices 全空 → null
**行为**: 所有候选 trim 后都是空 → 作 open-ended（不呈现选项）。

#### R-TOOL-237: `choices.size > MAX_CHOICES` 截断
**行为**: `MAX_CHOICES=4`，超过部分被截断（不报错）；UI 单独追加 "Other"。

#### R-TOOL-238: callback==null 返回错误
**行为**: `{"error":"Clarify tool is not available in this execution context."}`。

#### R-TOOL-239: callback 异常包装
**行为**: 捕获 → `{"error":"Failed to get user input: <msg>"}`。

### Interrupt.kt — 中断标志

#### R-TOOL-245: per-thread 状态
**行为**: `Set<Long>` 按 threadId 存储；不同线程之间互不影响。

#### R-TOOL-246: `isInterrupted()` 只查当前线程
**行为**: 无 API 查询其他线程；强制 `Thread.currentThread().id`。

#### R-TOOL-247: `_ThreadAwareEventProxy.wait` 非阻塞
**行为**: Android 上只是 `isSet()` 返回值，忽略 timeout（不同于 Python `threading.Event`）。

### CheckpointManager.kt — Git-backed 检查点

#### R-TOOL-255: `enabled=false` no-op 默认关
**行为**: 总开关默认 false；所有操作直接 short-circuit。

#### R-TOOL-256: `_gitAvailable` 进程级缓存
**行为**: 扫一次 PATH，结果整个进程寿命不变；无 git → `ensureCheckpoint` 返回 false + 日志 `"Checkpoints disabled: git not found"`。

#### R-TOOL-257: 拒绝 `/` 或 home 目录
**行为**: `ensureCheckpoint(root)` 或 home → 返回 false + "directory too broad"。

#### R-TOOL-258: per-turn dedup
**行为**: `_checkpointedDirs` 每 turn 重置；同一目录一 turn 内只 snapshot 一次。

#### R-TOOL-259: 文件数上限
**行为**: `_MAX_FILES=50_000`，超出 → abort + 日志。

#### R-TOOL-260: 空 diff 不 commit
**行为**: `git diff --cached --quiet` exit 0 → 跳过。

#### R-TOOL-261: git 环境隔离
**行为**: `--allow-empty-message --no-gpg-sign`，env 设 `GIT_CONFIG_GLOBAL=/dev/null` 等，清 `GIT_INDEX_FILE`/`GIT_NAMESPACE`/`GIT_ALTERNATE_OBJECT_DIRECTORIES`，防用户 hook 泄漏。

#### R-TOOL-262: `_validateCommitHash` 严格
**行为**: 拒空、拒 `-` 开头（防 arg injection）、仅接受 `^[0-9a-fA-F]{4,64}$`。

#### R-TOOL-263: `_validateFilePath` 绝对路径拒绝
**行为**: 绝对路径、canonical 不在 workdir 下 → 拒绝。

#### R-TOOL-264: shadow repo 路径 deterministic
**行为**: `CHECKPOINT_BASE / sha256(abs).hex[:16]`。

#### R-TOOL-265: `restore` 先回滚 snapshot
**行为**: checkout 前先 `_take`，保证回滚本身可被再回滚；`--` 分隔防二义。

#### R-TOOL-266: `listCheckpoints` --shortstat
**行为**: 每 commit 解 `files_changed/insertions/deletions`；cap 由 `maxSnapshots`（默认 50）。

### 其他 TOOL 子域（Mid/Low tier）

#### R-TOOL-280: BrowserTool `_mergeBrowserPath` 补 SANE_PATH
**行为**: 空输入返 7 项 SANE path；非空时追加（Termux bin 目录去重）。

#### R-TOOL-281: BrowserCdpTool 依赖 env
**行为**: `BROWSER_CDP_URL` 未设 → `_resolveCdpEndpoint()` 返回空字符串。

#### R-TOOL-282: BrowserCamofox 全员 toolError
**行为**: `camofoxNavigate/Snapshot/Click/Type/…` 全部 toolError("Camofox is not available on Android")。

#### R-TOOL-283: BrowserCamofoxState 确定性 identity
**行为**: `getCamofoxIdentity(taskId)` 给定同 hermes home 得到相同 user_id/session_key；`user_id` 前缀 `hermes_` + 10 hex；`session_key` 前缀 `task_` + 16 hex。

#### R-TOOL-290: DelegateTool 阻塞工具集
**行为**: `DELEGATE_BLOCKED_TOOLS = {delegate_task, status, cancel}`；递归深度上限 2。

#### R-TOOL-291: CronjobTools 全动作 Android 拒绝
**行为**: `cronjob` 所有 `action` 返回 `toolError("cronjob tool is not available on Android")`。

#### R-TOOL-292: HomeassistantTool entity id 正则
**行为**: `^[a-z_][a-z0-9_]*\.[a-z0-9_]+$`；大写不匹配。

#### R-TOOL-293: HomeassistantTool `_BLOCKED_DOMAINS`
**行为**: `shell_command, command_line, python_script, pyscript, hassio, rest_command` 六域调用被拒。

#### R-TOOL-300: DiscordTool 4 intent flag 位
**行为**: `GATEWAY_GUILD_MEMBERS=1<<14, GUILD_MEMBERS_LIMITED=1<<15, MESSAGE_CONTENT=1<<18, MESSAGE_CONTENT_LIMITED=1<<19`。

#### R-TOOL-301: SendMessageTool Telegram topic 正则
**行为**: `^(-?\d+)(?::(\d+))?$` 捕获 chatId + 可选 topicId。

#### R-TOOL-302: SendMessageTool Feishu target 正则
**行为**: `^((?:oc|ou|on|chat|open)_[-A-Za-z0-9]+)(?::([-A-Za-z0-9_]+))?$`。

#### R-TOOL-303: SendMessageTool Weixin target 宽松
**行为**: 接受 wxid_xxx、`@chatroom` 后缀、`filehelper`、`gh_xxx`、`v/wm/wb` 等前缀。

#### R-TOOL-304: `_PHONE_PLATFORMS` = {signal, sms, whatsapp}
**行为**: 这三种按 E.164 电话号码路由；其他按平台 id。

#### R-TOOL-310: ImageGenerationTool 常量
**行为**: 默认 model `fal-ai/flux-2/klein/9b`，`aspect_ratio` 有效值 `{landscape, square, portrait}` 三项。

#### R-TOOL-311: TtsTool 提供商默认
**行为**: Gemini 24kHz mono 16bit PCM；xAI 24kHz 128kbps；Edge 默认 voice `en-US-AriaNeural`。

#### R-TOOL-312: TranscriptionTools 文件尺寸上限
**行为**: `MAX_FILE_SIZE=26_214_400` bytes（25 MiB）；支持扩展 10 种，`LOCAL_NATIVE_AUDIO_FORMATS={.wav,.aiff,.aif}`。

#### R-TOOL-313: NeuTtsSynth WAV 写出
**行为**: 16000-24000 Hz PCM16 mono；样本 clamp 到 `[-1,1]`；标准 44-byte 头；空 samples → data chunk=0。

#### R-TOOL-320: SessionSearchTool 并发限
**行为**: 默认 `Semaphore(3)`；per-session 异常被吞，不影响其他 session。

#### R-TOOL-321: AnsiStrip 快速路径
**行为**: 纯 ASCII 且不含 `\x1b/\x9b` → 原样返回，不走正则。

#### R-TOOL-322: BinaryExtensions `.pdf` 不在集合
**行为**: PDF 故意保留为文本 (PDF.js 场景)；`.jar/.wasm/.class` 全在。

#### R-TOOL-323: FuzzyMatch unicode 归一化
**行为**: smart quotes → 直引号；em-dash → `--`；NBSP → 空格；ellipsis → `...`。

#### R-TOOL-324: PatchParser V4A 结构
**行为**: 识别 `*** Begin Patch` / `*** End Patch` 标记；marker 缺失时返回 warning；hunk 行前缀必须是 ` - +` 之一。

#### R-TOOL-325: OpenrouterClient 单例 + 120s 超时
**行为**: lazy singleton OkHttp；缺 `OPENROUTER_API_KEY` 抛 IAE；connect/read 均 120s。

#### R-TOOL-326: OsvCheck fail-open
**行为**: 网络异常返 null；识别 `MAL-*` 包 id 但忽略 CVE。

#### R-TOOL-327: ProcessRegistry 容量限
**行为**: `MAX_OUTPUT_CHARS=200_000`、`FINISHED_TTL_SECONDS=1800`、`MAX_PROCESSES=64`、watch 8/10s overload kill after 45s。

#### R-TOOL-328: ToolResultStorage 三层防线
**行为**: per-tool → per-result persistence → per-turn budget `MAX_TURN_BUDGET_CHARS=200_000`；persisted 输出用 `<persisted-output>` 标签包。

#### R-TOOL-329: MixtureOfAgentsTool 4 reference model + 1 aggregator
**行为**: 4 个 reference (temperature 0.6)、1 个 Claude Opus aggregator (0.4)；`MIN_SUCCESSFUL_REFERENCES=1`。

#### R-TOOL-330: WebTools schema
**行为**: `web_search` 要求 `query`；`web_extract` 最多 5 URLs；`DEFAULT_MIN_LENGTH_FOR_SUMMARIZATION=5000`。

#### R-TOOL-331: UrlSafety 私有 IP 黑名单
**行为**: 127/169.254/100.64-100.127/site-local/multicast 全被拒；仅白名单 `multimedia.nt.qq.com.cn` + HTTPS 放行。

#### R-TOOL-332: UrlSafety 云元数据 host 拒
**行为**: `metadata.google.internal`、`metadata.goog` 明确阻断。

#### R-TOOL-333: WebsitePolicy 默认 disabled + 30s cache
**行为**: 默认 `{enabled:false, domains:[]}`；缓存 TTL 30s；host 规范化 trim+lower+去尾点。

#### R-TOOL-334: XaiHttp UA 字符串
**行为**: `"Hermes-Agent/<version>"`；`BuildConfig.VERSION_NAME` 缺失 → `"unknown"`。

#### R-TOOL-335: EnvPassthrough 空白名称跳过
**行为**: `registerEnvPassthrough` 对 blank/empty names 跳过；ConcurrentHashMap 线程安全。

#### R-TOOL-336: CredentialFiles Android no-op
**行为**: `registerCredentialFile` 总是 false；`getCredentialFileMounts()` 返空 map；`iterSkillsFiles` 返空迭代器。

---

## 域 GATEWAY — Platform adapters

Kotlin `hermes-android/.../gateway/platforms/*.kt`；Python `reference/hermes-agent/gateway/platforms/*.py`。§6.1 平台差异允许（Android 无 servlet/Web 服务器/某些原生 SDK）。

### Base.kt — 公共基类

#### R-GW-001: `handleMessage` 无 handler 丢弃 + WARN
**行为**: `messageHandler` 未设时 incoming event 丢弃并 logcat WARN。

#### R-GW-002: fatal error 状态机
**行为**: `setFatalError(code, msg, retryable)` → `isConnected=false`、`_fatalErrorHandler` 触发、`hasFatalError` 等 getter 反映。

#### R-GW-003: `acquirePlatformLock` 互斥
**行为**: 同一 adapter 二次获锁前需 release；阻止并发 connect。

#### R-GW-004: post-delivery callback generation 校验
**行为**: `popPostDeliveryCallback(sessionKey, gen)` generation 不一致 → 不 pop，返回 null。

#### R-GW-005: `isCommand`/`getCommand`/`getCommandArgs`
**行为**: `/foo bar baz` → cmd=`foo`, args=`[bar, baz]`；空文本 isCommand=false。

#### R-GW-006: `interruptSessionActivity` 双动作
**行为**: AtomicBoolean 设 true + `stopTyping(chatId)` 调用异常被吞。

#### R-GW-007: `utf16Len`/`prefixWithinUtf16Limit` 代理对安全
**行为**: 字符串截断不破坏 surrogate pair。

#### R-GW-008: `safeUrlForLog` 掩码凭证
**行为**: `http://user:pass@host/x` → `http://***:***@host/x`。

#### R-GW-009: `looksLikeImage` magic bytes
**行为**: JPEG `FF D8`、PNG `89 50 4E 47`、GIF `47 49 46 38` → true。

#### R-GW-010: `cacheImageFromUrl`/`cacheDocumentFromBytes` 清理
**行为**: mtime 超过 TTL 的缓存文件 `cleanupImageCache` 被清；新文件落到 `context.cacheDir` 下。

### Helpers.kt — 共享工具

#### R-GW-015: MessageDeduplicator 空 id 不 dedup
**行为**: `isDuplicate("")` → false；非空 id TTL 内返 true，TTL 外 false。

#### R-GW-016: MessageDeduplicator LRU 驱逐
**行为**: 超 `maxSize` 按插入顺序淘汰最早条目。

#### R-GW-017: TextBatchAggregator 聚合延迟
**行为**: `enqueue(key, "a")` 后 `enqueue(key, "b")` → 最终 handler 收到 `"a\nb"` 一次；每次 enqueue 重置定时器。

#### R-GW-018: TextBatchAggregator split threshold
**行为**: 最新 chunk 长度 ≥ `splitThreshold` 时使用 `splitDelay` 而非 `batchDelay`。

#### R-GW-019: TextBatchAggregator cancelAll
**行为**: 丢所有待发送 + 取消所有 job；handler 不再被调用。

#### R-GW-020: ThreadParticipationTracker 持久化
**行为**: `mark` 幂等；写 `~/.hermes/<platform>_threads.json`；malformed JSON → 空 map；`maxTracked` 修剪最旧。

#### R-GW-021: stripMarkdown 覆盖
**行为**: `**bold**`、`*italic*`、`__bold__`、`_italic_`、fenced code、inline backtick、`#` heading、`[t](u)` → `t`；`\n{3,}` 收成 `\n\n`；两端 trim。

#### R-GW-022: redactPhone 梯度
**行为**: 空 → `"<none>"`；≤4 → `"****"`；5-8 → `"XX****YY"`；否则 `"XXXX****YYYY"`。

### Feishu.kt — 飞书适配器

#### R-GW-030: 凭证空 connect 失败
**行为**: `app_id==""` 或 `app_secret==""` → `connect()==false` + 日志 reason。

#### R-GW-031: token 刷新 5 分钟提前
**行为**: `expires_in - 300s` skew；非 2xx 或 `code != 0` → token=null。

#### R-GW-032: URL 模板常量
**行为**: 
- Token: `$_domain/open-apis/auth/v3/tenant_access_token/internal`
- Reply: `/open-apis/im/v1/messages/$replyTo/reply`
- New msg: `/open-apis/im/v1/messages?receive_id_type=chat_id`
- Image upload: `/open-apis/im/v1/images` (`image_type=message`)
- File upload: `/open-apis/im/v1/files` (`file_type=stream`)
- WS: `wss://open.feishu.cn/open-apis/ws/v2`

#### R-GW-033: 入站 dedup + allowlist
**行为**: `_handleMessageEventData` 先查 `MessageDeduplicator`（cap=5000, TTL=5min），再查 sender 是否在 `_allowedUsers`（除非 `_allowAllUsers=true`）。

#### R-GW-034: `_parseMessageContent` 映射
**行为**: `text/post/image/file/audio/media/sticker/interactive` 每类返回 `(String, MessageType)`；图片等返回 placeholder `"[Image: $key]"`；interactive → `MessageType.SYSTEM`。

#### R-GW-035: `_splitMessage` 30000 字符
**行为**: 按优先级 newline at > maxLength/2 → space → hard-split；`MAX_MESSAGE_LENGTH=30000`。

#### R-GW-036: reaction 生命周期
**行为**: 入站触发 ack reaction（若 `_sendAckReaction=true`）；`onProcessingComplete` 移除 ack；发送失败加 failure reaction。

#### R-GW-037: image MIME 嗅探
**行为**: `_uploadImage` 读 magic bytes：FFD8→jpeg、8950→png、4749→gif、5249→webp。

#### R-GW-038: per-chat serial 处理
**行为**: 每 chatId 一个 `Channel<MessageEvent>` + `Job`；严格顺序消费同一会话消息。

#### R-GW-039: markdown post 渲染
**行为**: `_buildMarkdownPostPayload` 把 markdown 转飞书 post 结构（rows/elements）；代码块单独 row；table 被 flatten；mention 占位符被替换为 `@user_name`。

#### R-GW-040: QR onboarding 流程
**行为**: `_initRegistration`→`_beginRegistration`→`_pollRegistration` 三阶段；`_renderQr` 产生 QR；`probeBot`/`qrRegister` 暴露到 UI。

### FeishuComment.kt — 文档评论回复

#### R-GW-065: URL 模板替换
**行为**: `_buildRequest("GET", "/open-apis/drive/v1/files/:file_token/comments", paths={"file_token":"X"})` → `/files/X/comments`。

#### R-GW-066: `_FEISHU_DOC_URL_RE` 识别链接
**行为**: 识别 docx/wiki/sheet/sheets/slides/mindnote/bitable/base/file on `feishu.cn/larkoffice.com/larksuite.com/lark.suite.com`，捕获 docType + token (10-40 chars)。

#### R-GW-067: chunk size
**行为**: `_chunkText` 按 `_REPLY_CHUNK_SIZE=4000` 切段。

#### R-GW-068: prompt 截断
**行为**: `_truncate` 使用 `_PROMPT_TEXT_LIMIT=220`；`_selectLocalTimeline` 最多 20 条，`_selectWholeTimeline` 最多 12。

#### R-GW-069: NO_REPLY sentinel
**行为**: 回复文本等于 `_NO_REPLY_SENTINEL="NO_REPLY"` 时不发送。

#### R-GW-070: session store TTL
**行为**: `_sessionKey("docx", "TOKEN")` → `"docx:TOKEN"`；TTL `_SESSION_TTL_S=3600`；max `_SESSION_MAX_MESSAGES=50`。

#### R-GW-071: notice_type 过滤
**行为**: `_ALLOWED_NOTICE_TYPES = {"add_comment", "add_reply"}`；其他 notice 跳过。

### FeishuCommentRules.kt — 评论规则

#### R-GW-085: MtimeCache 热重载
**行为**: 文件不存在 → 空 map；`lastModified()` 变化才重读；异常 → 空 map。

#### R-GW-086: 3-tier 规则解析
**行为**: `resolveRule(cfg, docType, docToken, userOpenId)` 查 `"docType:token"` → `"*"` → top → default；`enabled/policy/allowFrom` 独立回落；`matchSource` 标识命中层。

#### R-GW-087: policy 合法值集合
**行为**: `_VALID_POLICIES={"allowlist","pairing"}`；其他值被丢。

#### R-GW-088: `isUserAllowed`
**行为**: allowlist 模式要求 openId ∈ allowFrom；pairing 模式要求 openId 在 `~/.hermes/feishu_comment_pairing.json`。

### Weixin.kt — 微信个人号（iLink）适配器

#### R-GW-095: 凭证空 connect 失败
**行为**: `account_id==""` 或 `login_token==""` → `connect()==false` + 日志提示 QR 登录。

#### R-GW-096: header 组装
**行为**: `AuthorizationType: ilink_bot_token`、`Authorization: Bearer $login_token`、`X-WECHAT-UIN` = base64 随机 UIN；`SKRouteTag` 仅在 `_routeTag` 非空时加。

#### R-GW-097: LRU dedup
**行为**: `_seenMessageIds` cap=500；`_sentClientIds` 回声丢弃。

#### R-GW-098: 长轮询容错
**行为**: `SocketTimeoutException` 视为正常继续；累计失败 ≥ `MAX_CONSECUTIVE_FAILURES` 后退避 `RETRY_DELAY_SECONDS`/`BACKOFF_DELAY_SECONDS`；`SESSION_EXPIRED_ERRCODE` 退避 5 min。

#### R-GW-099: typing ticket 缓存
**行为**: `_fetchTypingTicket` 结果按 chatId 缓存；`_sendTypingSignal` 无 ticket 时 silent no-op。

#### R-GW-100: AES-128-ECB 辅助
**行为**: `_aes128EcbEncrypt`/`_aes128EcbDecrypt` 可回环；`_pkcs7Pad` 块大小 16；`_parseAesKey` base64 解码并修正 padding。

#### R-GW-101: CDN URL 校验
**行为**: `_assertWeixinCdnUrl` 拒非腾讯域；`_cdnDownloadUrl`/`_cdnUploadUrl` 按格式构造。

#### R-GW-102: markdown 切分
**行为**: `_splitTextForWeixinDelivery`/`_packMarkdownBlocksForWeixin` 按 `_CHUNK_SIZE` 约束每段；表格块优先整块发。

#### R-GW-103: QR login + 账号持久化
**行为**: `qrLogin(hermesHome, onQrCodeReady, onStatusUpdate)` 完整流程；成功后 `saveWeixinAccount` 写 `~/.hermes/accounts/<accountId>.json`。

### WeCom.kt / WeComCallback.kt / WeComCrypto.kt

#### R-GW-120: WeCom 凭证空失败
**行为**: `corp_id` 或 `corp_secret` 为空 → connect=false。

#### R-GW-121: WeCom token URL
**行为**: `https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=..&corpsecret=..`；`errcode != 0` 返 null token。

#### R-GW-122: WeCom send 截断
**行为**: content 超 2048 字符截断；POST `/cgi-bin/message/send` JSON 格式固定。

#### R-GW-123: WeCom `agent_id` 非数字 → 0
**行为**: `_agentId.toIntOrNull() ?: 0`。

#### R-GW-124: WeComCallback Android 拒
**行为**: `connect()==false`；`send()` 返 `"WeCom callback not supported on Android — use webhook mode"`。

#### R-GW-125: WeComCrypto 构造器校验
**行为**: `encodingAesKey.length==43` 必须；`token`/`receiveId` 非空；否则抛。

#### R-GW-126: WeComCrypto 签名
**行为**: `sha1Signature(token, ts, nonce, encrypt)` 四段字典序排序，拼接后 SHA-1 hex；不匹配 → `SignatureError`。

#### R-GW-127: WeComCrypto 解密布局
**行为**: AES-CBC/PKCS5 → PKCS7 剥 → 跳 16-byte 随机前缀 → 读 4-byte BE 长度 → XML → 检查尾部 `receive_id` 匹配；不符 → `DecryptError("receive_id mismatch")`。

#### R-GW-128: PKCS7 块大小 32
**行为**: `PKCS7Encoder` block=32；pad byte < 1 或 > 32 → 拒。

### Telegram.kt / TelegramNetwork.kt

#### R-GW-135: Telegram token 空拒
**行为**: `connect()==false` + `"TELEGRAM_BOT_TOKEN not set"`。

#### R-GW-136: Telegram URL 常量
**行为**: `API_BASE="https://api.telegram.org"`；endpoint `/getMe`、`/sendMessage`、`/sendPhoto`、`/sendDocument`、`/sendChatAction`（`action=typing`）、`/getUpdates?timeout=30&offset=N`、`/setWebhook`、`/deleteWebhook`。

#### R-GW-137: update offset 单调递增
**行为**: 每条 update 处理后 offset = `update_id + 1`；无重复、无空洞。

#### R-GW-138: caption 上限
**行为**: `MAX_CAPTION_UTF16=1024`；text `MAX_MESSAGE_LENGTH=4096`。

#### R-GW-139: group 门禁
**行为**: `_allowGroups=false` 或 chat_id 不在 `_allowedGroups` → 群消息被拒。

#### R-GW-140: drop_pending_updates
**行为**: connect 时 true → `getUpdates?offset=-1` 然后再起 poll。

#### R-GW-141: TelegramNetworkClient 重试分类
**行为**: 
- 2xx → 成功
- 400 → `NetworkError("Bad request...")` 无重试
- 401/403 → Forbidden 无重试
- 404 → NotFound 无重试
- 429 → 读 `retry_after`，延迟后重试，超 `maxRetries-1` 抛 TooManyRequests
- 5xx → 指数退避 `baseDelayMs << attempt`
- 网络异常 → 指数退避

#### R-GW-142: TelegramRateLimiter
**行为**: `recordRateLimit(endpoint, retryAfter)` 记时间戳 + min-delay；`checkRateLimit` 返回剩余延迟或 0；`clearAll` 清全部。

### Discord.kt

#### R-GW-150: Discord bot id 解析
**行为**: `connect()` GET `/users/@me` 带 `Authorization: Bot <token>`；401/403 失败。

#### R-GW-151: Discord send reply_to
**行为**: `replyTo != null` 时 body 带 `message_reference: {message_id: replyTo}`；content 截 2000 字符。

#### R-GW-152: Discord typing no body
**行为**: POST `/channels/$chatId/typing` 空 body。

### Signal / Slack / WhatsApp / Matrix / Mattermost / SMS / Email / Webhook / ApiServer / Bluebubbles

#### R-GW-160: stub adapters connect=false
**行为**: `connect()` 均返 false + 特定 reason 日志（Signal/Slack/WhatsApp/Mattermost/Matrix: "not implemented on Android"；SMS: "iOS-specific"；Email: "requires IMAP/SMTP libraries"；Webhook/ApiServer: "requires HTTP server"；Bluebubbles: "iOS/macOS-specific"）。

#### R-GW-161: stub adapters send error msg 确定
**行为**: `SendResult(success=false, error="<Platform> not supported on Android ...")`；error 字符串供遥测断言。

#### R-GW-162: stub `disconnect` 幂等
**行为**: `markDisconnected()` 可反复调用。

#### R-GW-163: `adapter.platform` 枚举一致
**行为**: `Platform.SIGNAL/SLACK/WHATSAPP/MATRIX/MATTERMOST/SMS/EMAIL/WEBHOOK/API_SERVER/BLUEBUBBLES` 各自正确。

### Dingtalk.kt / Homeassistant.kt

#### R-GW-180: Dingtalk token URL
**行为**: `https://oapi.dingtalk.com/gettoken?appkey=..&appsecret=..`；`errcode != 0` → null；cache `expires_in - 300s`。

#### R-GW-181: Dingtalk send endpoint
**行为**: POST `/topapi/message/corpconversation/asyncsend_v2?access_token=..` + `{msgtype:"text"}` body；**不截断** content 长度。

#### R-GW-182: HomeAssistant backoff 阶梯
**行为**: `BACKOFF_STEPS=[5,10,30,60,120,300]`；`cooldown_seconds` 默认 10。

#### R-GW-183: HomeAssistant auth
**行为**: GET `/api/` 带 `Authorization: Bearer <hass_token>`；非 2xx 失败。

#### R-GW-184: HomeAssistant notify
**行为**: POST `/api/services/persistent_notification/create` body `{title:"Hermes Agent", message: content}`。

### qqbot/ — QQ 官方机器人

#### R-GW-190: QQAdapter Android stub
**行为**: `connect()` log `"stub — not implemented on Android"` + 返 false；transport methods 全无副作用。

#### R-GW-191: `_guessChatType` 前缀
**行为**: `c2c_` → c2c；`group_` → group；`guild_` → guild；`dm_` → dm；其他 → c2c。

#### R-GW-192: `_stripAtMention` 正则
**行为**: 去除 `<@[!]?\d+>` 形式的 mention。

#### R-GW-193: `_isVoiceContentType` 扩展
**行为**: 内容类型音频 MIME 或文件名 `.silk/.amr/.wav/.mp3/.opus` → true。

#### R-GW-194: `_guessExtFromData` magic bytes
**行为**: SILK (`02 23 21 53 49 4c 4b`) / ID3 (`49 44 33`) / RIFF (`52 49 46 46`) 各自对应扩展。

#### R-GW-195: `_parseQqTimestamp` 解析失败 fallback
**行为**: 非法时间戳 → `now()`（不抛）。

#### R-GW-196: Crypto AES-256-GCM 回环
**行为**: `decryptSecret(encBase64, keyBase64)` 可与 `generateBindKey` + 对应加密回环；IV=12 字节，tag=128 位。

#### R-GW-197: Onboard createBindTask retcode 检查
**行为**: POST `https://$PORTAL_HOST/lite/create_bind_task`；`retcode != 0` 抛异常，携带 `msg`。

#### R-GW-198: buildConnectUrl URL 编码
**行为**: taskId 中的空格等字符被 `%20` 编码。

#### R-GW-199: buildUserAgent 兜底
**行为**: `BuildConfig.VERSION_NAME` 缺失 → `"dev"`；形如 `QQBotAdapter/1.0 (Android/<sdk>; android; Hermes/<ver>)`。

#### R-GW-200: coerceList 宽容
**行为**: null → `[]`；String 按逗号切；Collection 直接转；其他 toString。

---

## 域 STATE — Session + Context compression

Kotlin `hermes-android/.../hermes/HermesState.kt` + `agent/ContextCompressor.kt` + `agent/MemoryManager.kt`；Python `hermes_state.py`、`trajectory_compressor.py`、`agent/context_compressor.py`、`agent/memory_manager.py`。

### HermesState.kt — 单文件 JSON 状态

#### R-STATE-001: `get` missing key 返 default
**来源**: `reference/hermes-agent/hermes_state.py`
**行为**: 不存在的 key 返提供的 default（可为 null）。

#### R-STATE-002: `set` 标脏 + autoSave
**行为**: `set(k, v)` → `isDirty()==true`；`autoSave=true` 触发异步 save。

#### R-STATE-003: `delete` 返是否存在
**行为**: key 存在返 true 删除，不存在返 false。

#### R-STATE-004: `contains`/`keys`/`size` 反映当前 map
**行为**: `set` 后立即可见，无需先 save。

#### R-STATE-005: `save` FileChannel.lock 跨进程
**行为**: 写前获取 `.state.json.lock` sidecar FileLock；并发写不丢失数据。

#### R-STATE-006: `merge` 浅合并非 Map 时替换
**行为**: 当前值是 Map → `putAll`；其他类型 → 整体替换为新 map。

#### R-STATE-007: `snapshot` 返回副本
**行为**: 返回值独立，修改它不影响 state。

#### R-STATE-008: `clear` 空 map
**行为**: 所有 key 删除；`size==0`。

#### R-STATE-009: `getGlobalState` 懒加载单例
**行为**: 首次调用加载；`resetGlobalState()` 后可再次初始化（测试 hook）。

### SessionDB — SQLite 会话历史

#### R-STATE-015: FTS5 索引
**行为**: 表结构含 `sessions` + `messages_fts` (FTS5)；插入 message 时触发器同步。

#### R-STATE-016: 并发写串行
**行为**: 单 writer 模式；`WAL` journal mode；读不阻塞写。

#### R-STATE-017: `getGlobalSessionDB` 懒初始化
**行为**: 首次调用建表；数据库路径 `<hermes_home>/sessions.db`。

### ContextCompressor.kt / TrajectoryCompressor.kt — 会话压缩

#### R-STATE-025: 压缩触发阈值
**来源**: Python `trajectory_compressor.py`
**行为**: 当 messages 累计 token 超 `COMPRESSION_THRESHOLD` 触发压缩；阈值从 `HermesConstants` 读。

#### R-STATE-026: 保留 system + tail
**行为**: 压缩时保留 system prompt 与最近 N 条完整消息；中间段被摘要替换。

#### R-STATE-027: 工具结果压缩规则
**行为**: 大 tool_result 被 `persist` 到 ToolResultStorage 并用 `<persisted-output>` 占位替换原内容。

#### R-STATE-028: 压缩失败 graceful
**行为**: 底层 LLM 调用失败 → 保留原 messages，不崩。

### MemoryManager.kt

#### R-STATE-035: memory 加载到 prompt
**行为**: system prompt 构建时注入 memory store；`_systemPromptSnapshot` 冻结保持 prefix cache。

#### R-STATE-036: memory 更新触发 persist
**行为**: `MemoryTool.add/replace/remove` 成功后磁盘同步（R-TOOL-118）；sys prompt snapshot 不随 mid-session 变更而变。

---

## 域 SKILL — Skills & Plugins

Kotlin `hermes-android/.../hermes/plugins/{memory,diskcleanup}/` + SkillsHub/SkillManager/SkillsGuard/SkillsSync/SkillsTool（已在 TOOL 域覆盖）；Python `hermes/plugins/`、`hermes/hooks.py` 的 skill hook。

### DiskCleanup.kt — 磁盘清理插件

#### R-SKILL-010: 触发条件
**行为**: 定时轮询 free space < 阈值时触发；`HERMES_DISK_CLEANUP_ENABLED=false` 时完全停用。

#### R-SKILL-011: 清理范围
**行为**: 仅清 `<hermes_home>/cache/` 与过期 trajectory 文件；业务数据（state.json/sessions.db/memories/）永不删。

#### R-SKILL-012: dry-run 支持
**行为**: dry-run 模式只列出目标，不 rm。

### MemoryProvider.kt (plugins/memory)

#### R-SKILL-020: MemoryProvider 接口三方法
**行为**: `loadMemory()`、`saveMemory(entries)`、`searchMemory(query)`；holographic / honcho 两个实现。

#### R-SKILL-021: Holographic provider
**行为**: 本地 SQLite 向量存储；嵌入模型 endpoint 从 HERMES config 读。

#### R-SKILL-022: Honcho provider
**行为**: 云端 API；`HONCHO_API_KEY` 缺失时构造器抛或 fallback 本地。

### SkillCommands + SkillUtils（若存在）

#### R-SKILL-030: skill command dispatch
**行为**: `/skill <name> [args]` 解析为 SkillInvocation 并 dispatch 到 skill handler；未知 skill → error。

#### R-SKILL-031: argument parsing
**行为**: shell-style 分词，引号内视为一段；`\` 转义。

---

## 域 MCP — MCP client + server

Kotlin `hermes-android/.../tools/McpTool.kt`、`McpOAuth.kt`、`McpOauthManager.kt`、`ManagedToolGateway.kt`；Python 对应同名 `.py`。

### McpTool.kt

#### R-MCP-001: MCP 工具动态注册
**行为**: 运行时发现的 MCP tool 以 `mcp-<server>-<tool>` 命名注册到 ToolRegistry；R-TOOL-002 规则允许 re-register。

#### R-MCP-002: connect server 失败不影响其他
**行为**: 一个 MCP server 连接失败仅该 server 下工具不可用；其他 server 正常。

#### R-MCP-003: 工具调用代理
**行为**: `dispatch("mcp-foo-bar", args)` 转发到 foo server 的 `bar` 工具；返回值按 MCP 规范解析。

#### R-MCP-004: schema 转 OpenAI
**行为**: MCP JSON schema → OpenAI function spec（R-TOOL-015）。

#### R-MCP-005: Android 传输 stub
**行为**: stdio 传输在 Android 上返 toolError；仅 HTTP/WebSocket 传输启用。

### McpOAuth.kt / McpOauthManager.kt

#### R-MCP-020: OAuth token 缓存
**行为**: per-server token 缓存在内存 + 持久化到 `~/.hermes/mcp_oauth.json`；刷新 token 到期前 5 min。

#### R-MCP-021: refresh_token 流
**行为**: access_token 过期时用 refresh_token 自动刷新；两者都过期 → 重新授权。

#### R-MCP-022: 授权 URL 构造
**行为**: 构造时包含 PKCE challenge + state；回调校验 state 一致。

### ManagedToolGateway.kt

#### R-MCP-030: gateway Android 降级
**行为**: `managedNousToolsEnabled()` (R-TOOL-324) 控制；Android 默认关闭。

#### R-MCP-031: 请求签名
**行为**: 每个代理请求附带 HMAC 签名头；gateway 端验证。

---

## 域 CRON — Scheduler + Jobs

Kotlin `hermes-android/.../hermes/cron/{Scheduler,Jobs}.kt` + `tools/CronjobTools.kt`；Python `cron/scheduler.py`、`cron/jobs.py`、`cron/store.py`。

### CronjobTools.kt

#### R-CRON-001: Android 拒所有 action
**行为**: `cronjob(action=...)` 一律 `toolError("cronjob tool is not available on Android")`；R-TOOL-291。

#### R-CRON-002: requirements check
**行为**: `checkCronjobRequirements()` 固定 false。

### Scheduler.kt / Jobs.kt

#### R-CRON-010: schedule 解析
**行为**: crontab 格式 `m h dom mon dow` 或 `@daily/@hourly` 等 shortcut；格式错 → 抛 IllegalArgumentException。

#### R-CRON-011: job 持久化
**行为**: 存 `~/.hermes/cron_jobs.json`；进程重启后自动恢复。

#### R-CRON-012: approval 模式
**行为**: job 运行前若 `cron_mode="approve"` → 走 Approval（R-TOOL-024）；其他模式直接运行。

#### R-CRON-013: Android 不起 daemon
**行为**: Scheduler Android 上仅元数据管理；实际触发由外部进程或不运行。

---

## 域 SAFETY — 额外安全层

（FileSafety 已在 AGENT 域覆盖。）本节补 PathSecurity、Approval、TirithSecurity、OsvCheck、UrlSafety、WebsitePolicy、BinaryExtensions。

#### R-SAFETY-020: PathSecurity `canonicalPath` 归一化（复指）
**关联**: R-TOOL-130/131/132。

#### R-SAFETY-021: Approval dangerous pattern 数量
**行为**: `DANGEROUS_PATTERNS` 至少 47 条；增加时保持 IGNORE_CASE + DOT_MATCHES_ALL 语义。

#### R-SAFETY-022: TirithSecurity 降级允许
**行为**: Tirith 模块 "always" 审批降级为 session-only（R-TOOL-029）。

#### R-SAFETY-023: OsvCheck 网络 fail-open
**关联**: R-TOOL-326。失败不阻塞工具调用。

#### R-SAFETY-024: UrlSafety 私有 IP 全拒
**关联**: R-TOOL-331/332。

#### R-SAFETY-025: WebsitePolicy 默认关 + 30s cache
**关联**: R-TOOL-333。

#### R-SAFETY-026: BinaryExtensions 拒读扩展
**关联**: R-TOOL-074/322。`.pdf` 不在 set 中（PDF 可读为文本）。

#### R-SAFETY-027: SSH 路径写拒
**关联**: R-TOOL-070/071。

#### R-SAFETY-028: HERMES_WRITE_SAFE_ROOT 约束一切
**关联**: R-TOOL-072。

#### R-SAFETY-029: `/dev` 字符设备读拒
**关联**: R-TOOL-095。

---

## 域 CONFIG — Settings preferences

Kotlin `app/src/main/java/com/ai/assistance/operit/hermes/gateway/{HermesGatewayPreferences,HermesGatewayConfigBuilder,HermesGatewayController}.kt`；无 Python 上游（Android 独有）。

### HermesGatewayPreferences.kt

#### R-CONFIG-001: 单例
**行为**: `getInstance(context)` 跨线程返回同一实例。

#### R-CONFIG-002: 双 store
**行为**: 非凭证配置走 `DataStore("hermes_gateway_preferences")`；凭证走 `EncryptedSharedPreferences("hermes_gateway_secrets")`（AES256_SIV + AES256_GCM）；KeyStore 失败 fallback `hermes_gateway_secrets_plain`。

#### R-CONFIG-003: `saveAgentMaxTurns` clamp
**行为**: 参数 clamp 到 `[1, 200]`；默认 30；`saveAgentMaxTurns(300)` 存 200。

#### R-CONFIG-004: `clearSecrets(platform)` 仅清 prefix
**行为**: 删除所有以 `${platformKey}__` 开头的 key；其他平台凭证不受影响。

#### R-CONFIG-005: Flow 默认值
**行为**: 未设置过的 `platformEnabledFlow("feishu")` 首发 false；`platformPolicyFieldFlow(..., default)` 首发 default。

#### R-CONFIG-006: 凭证 round-trip
**行为**: `writeSecret` 后 `readSecret` 返回同值；明文 fallback 模式下行为一致。

#### R-CONFIG-007: 常量
**行为**: `PLATFORM_FEISHU="feishu"`、`PLATFORM_WEIXIN="weixin"`；policy fields 列表 = `{dm_policy, dm_allow_from, group_policy, group_allow_from, reply_to_mode, require_mention}`。

### HermesGatewayConfigBuilder.kt

#### R-CONFIG-010: 凭证不全不建 platform
**行为**: Feishu 缺 `app_id` 或 `app_secret` → `buildFeishu` 返 null；Weixin 缺 `account_id` 或 `login_token` 同。

#### R-CONFIG-011: `config.platforms` 过滤 null
**行为**: 两平台都未配置 → `config.platforms.isEmpty()`。

#### R-CONFIG-012: extra 仅非空字段
**行为**: `verification_token`/`encrypt_key` 空时不进 `extra` map。

#### R-CONFIG-013: `readCsv` 规范化
**行为**: `" u1 , u2\nu3 , "` → `["u1", "u2", "u3"]`。

#### R-CONFIG-014: `maxConcurrentSessions=5` 硬编码
**行为**: 默认并发 session 数固定。

#### R-CONFIG-015: policy 默认值
**行为**: Feishu 默认 `dm_policy="open"`、`group_policy="allowlist"`、`reply_to_mode="first"`；Weixin 同。

### HermesGatewayController.kt

#### R-CONFIG-020: Status FSM
**行为**: `Status ∈ {STOPPED, STARTING, RUNNING, STOPPING, FAILED}`；初始 STOPPED。

#### R-CONFIG-021: `start()` 空 platforms 失败
**行为**: 无可用 platform → 状态进入 FAILED + `error="no enabled platforms with credentials"`。

#### R-CONFIG-022: `start()` 幂等 RUNNING
**行为**: 已 RUNNING 时重复 start → short-circuit 返 true，不重建 config。

#### R-CONFIG-023: `stop()` 异常进入 STOPPED
**行为**: runner 抛异常也最终进入 STOPPED 状态。

#### R-CONFIG-024: `stripInternalMarkup` 剥内部标签
**行为**: 剥 `<think>`、`<tool_result>`、`<tool>`、`<status>` 块（大小写无关）。

#### R-CONFIG-025: `gatewayChatTitle` 截断 24 字符
**行为**: `feishu:xxx`, `oc_abc@` → `"feishu: oc_abc"`，限长 24。

#### R-CONFIG-026: 空 agent 回复 fallback
**行为**: agent stream 空 → 回复 `"(empty response)"`。

#### R-CONFIG-027: 持久化失败吞异常
**行为**: `ChatHistoryManager` 写失败只记日志，不阻塞 gateway 继续回复。

---

## 域 UI — Settings screens

Kotlin `app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/Hermes*.kt`；无 Python 上游。

### HermesSettingsScreen.kt — Hub

#### R-UI-001: 五个 tile
**行为**: 渲染恰好 5 个 `HermesSubScreenCard`（credentials/policies/params/service/qr）；顺序固定。

#### R-UI-002: 图标
**行为**: 对应图标 `VpnKey/Rule/Tune/PowerSettingsNew/QrCode`。

#### R-UI-003: 点击触发 callback
**行为**: 每个 tile 点击调对应 `navigateTo*` lambda。

#### R-UI-004: 无副作用
**行为**: 组合期不读 prefs、不起 coroutine。

#### R-UI-005: 文本截断
**行为**: title 1 行、subtitle 2 行，超出 `TextOverflow.Ellipsis`。

### HermesGatewayCredentialsScreen.kt

#### R-UI-010: 字段数固定
**行为**: Feishu 4 字段（app_id + 3 secret）；Weixin 2 字段。

#### R-UI-011: 密码字段 masking
**行为**: secret 字段用 `PasswordVisualTransformation` + `KeyboardType.Password`。

#### R-UI-012: enable 开关即时 save
**行为**: 切换立即 `savePlatformEnabled`。

#### R-UI-013: Save 逐字段写
**行为**: 点保存后每字段一次 `writeSecret`。

#### R-UI-014: savedFlash 1500ms
**行为**: 保存后 UI 显 `"已保存"` 1.5s 自动清除。

#### R-UI-015: 初始值来自 prefs
**行为**: compose 首次 remember 读 `readSecret`。

### HermesGatewayPoliciesScreen.kt

#### R-UI-020: policy chip 集合
**行为**: Feishu DM `{open, pairing, allowlist}`；Weixin DM `{open, allowlist, disabled}`（不含 pairing）；group 三者都是 `{open, allowlist, disabled}`。

#### R-UI-021: 仅 Save 持久化
**行为**: chip 切换进入 `mutableStateMapOf`；Save 按钮才一次性 flush 到 prefs。

#### R-UI-022: `require_mention` 字符串布尔
**行为**: 存为字面 `"true"` / `"false"`。

#### R-UI-023: 默认值
**行为**: Feishu 默认 `dm=pairing, group=allowlist, requireMention=true`；Weixin 默认 `dm=open, group=disabled, requireMention=false`。

#### R-UI-024: state 仅 first-emit 初始化
**行为**: `if (state.isEmpty())` 守卫；后续 flow emit 不覆盖用户编辑。

### HermesAgentParamsScreen.kt

#### R-UI-030: 数字-only 输入
**行为**: 非数字被过滤；`take(3)` 3 位数上限。

#### R-UI-031: Save 走 clamp
**行为**: `saveAgentMaxTurns(300)` 存 200（R-CONFIG-003）。

#### R-UI-032: 空输入 no-op
**行为**: `toIntOrNull()==null` → `return@Button`。

#### R-UI-033: 外部变更 resync
**行为**: `LaunchedEffect(currentMaxTurns)` 重新同步 TextField。

### HermesGatewayServiceScreen.kt

#### R-UI-040: run_switch 双写
**行为**: 切 on → `saveServiceEnabled(true)` + `GatewayForegroundService.start`；切 off → `stop`。

#### R-UI-041: autostart 仅 pref
**行为**: 切 autostart 开关不起服务，仅写 pref。

#### R-UI-042: status 映射
**行为**: `Status` 每值对应一个 `hermes_service_status_*` 资源。

#### R-UI-043: error 条件渲染
**行为**: `error != null && error.isNotBlank()` 才显示。

### HermesGatewayQrBindScreen.kt

#### R-UI-050: Feishu 成功写凭证
**行为**: `qrRegister` 成功 → 写 `app_id/app_secret/domain/bot_name/bot_open_id` + `savePlatformEnabled(FEISHU, true)` + 自启动 foreground service。

#### R-UI-051: Weixin 成功写凭证
**行为**: `qrLogin` 成功 → 写 `account_id/login_token`。

#### R-UI-052: QR 生成失败 UI
**行为**: `generateQrBitmap` 返回 null → 显 `"⚠️ 二维码生成失败"`。

#### R-UI-053: cancel 清状态
**行为**: cancel 按钮 → `loginJob` 取消 + `statusText="已取消"` + `qrBitmap=null`。

#### R-UI-054: clear credentials
**行为**: 清凭证按钮调 `clearSecrets(platformKey)` + 重置 in-UI 本地状态。

#### R-UI-055: Weixin qr fallback
**行为**: `qrcodeImgContent` 为空 → 用 text `qrcode` 字段生成 QR bitmap。


