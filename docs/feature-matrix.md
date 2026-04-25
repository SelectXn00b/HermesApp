# Hermes-Hermes 集成 — 功能矩阵

> Stop hook 的硬性检查对象。三态：未开始 / 进行中 / 已完成。矩阵有任何未完成项即禁止结束会话。

## 图例
- 白色方块标记 = 未开始
- 黄色方块标记 = 进行中
- 绿色对号标记 = 完成（附证据链接或一句证明）

---

## 1. 构建

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | `:hermes-android:compileDebugKotlin` 通过 | JDK 21 构建日志 |
| ✅ | `:app:compileDebugKotlin` 通过 | 同上 |
| ✅ | `:app:assembleDebug` APK 成功 | app-debug.apk 已生成 |
| ✅ | applicationId 避开 `com.xiaomi.mimo` provider 冲突 | 现为 `com.xiaomo.androidforclaw`（commit 61b0102），与 `com.xiaomi.mimo` 无前缀重合 |

## 2. 内核替换（Plan Option B）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | `OperitChatCompletionServer` 把 `<tool>` XML → `toolCalls` 合成 | 正则基于 ChatMarkupRegex.toolCallPattern |
| ✅ | `HermesAgentLoop.beforeNextTurn` hook 新增 | AgentLoop.kt |
| ✅ | `EnhancedAIService.sendMessage` L917-1104 替换为 `runViaHermes(...)` | |
| ✅ | `processStreamCompletion` / `handleToolInvocation` / `processToolResults` 废弃（或仅保留定义） | |
| ✅ | 轮间 token 阈值在 `beforeNextTurn` 里等价原 L2089-2110 | `git show origin/main:…/EnhancedAIService.kt` L2089-2110 vs 当前 L1138-1165: 同 `estimatePreparedRequestWindow` 调用、同 `usageRatio >= tokenUsageThreshold` 判据、同 `onTokenLimitExceeded` + `isConversationActive.set(false)` + `stopAiService` 退出链。新增 `turn > 0` 守卫匹配原 "after tool call" 语义，增加的 `isExecutionContextActive` 属额外防御 |
| ✅ | accumulated*TokenCount 会计与原实现一致 | `git show origin/main:…/EnhancedAIService.kt` L1020 / L2218（两处重复）vs 当前 `onTurnComplete` 回调 L1020-1034：同样做 `accumulatedInputTokenCount += input` 等三字段累加 + 同 `updateTokensForProviderModel` + 同 `incrementRequestCountForProviderModel`，原两处整合为单一每轮回调，由 `OperitChatCompletionServer` L71-75 传入 `service.{input,cachedInput,output}TokenCount` |

## 3. 真机冒烟（S7）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | 设备已连上 adb | `adb devices -l` → `8370d265	device` |
| ✅ | MiMo v2 Pro 公网 config 已注入 datastore | `adb shell run-as … cat files/datastore/model_configs.preferences_pb` 显示 `{"id":"default","name":"MiMo v2.1 Pro Core","apiEndpoint":"http://mimorouter.llmcore.ai.srv/","modelName":"mimo-v2.1-pro-core-15000step","apiProviderType":"OPENAI_GENERIC","enableToolCall":true}` |
| ✅ | 启动 app 不 crash | 安装 4/22 17:35 APK (sha=sessione) 后 `adb shell am start` 成功，pid=22832，activity in stack (`dumpsys activity activities` 显示 Task #5464)，logcat 无 FATAL/AndroidRuntime |
| ✅ | 发一条无工具消息：assistant 文本正常流出 | 模拟器 emulator-5554 18:19:42 向 mimorouter.llmcore.ai.srv 发 "你好，请用一句话回复我，不要调用任何工具"；200 OK；33 chunks；响应 "你好，我在这里，随时准备为您提供帮助。" + `<status type="complete">`；input=5390 / output=64 tokens；流经 runViaHermes → OperitChatCompletionServer → AIService |
| ✅ | 发一条触发 `<tool>` 的消息：tool dispatch + result 正确渲染 | 模拟器 18:22:22 响应含 `<tool_dTJD name="query_memory">`；18:22:22.205 `FloatingChatService: InputProcessingState$ExecutingTool(toolName=query_memory)` 渲染；18:22:22.243 `HermesAgentLoop: [0b4a718c…] turn 1: 1 tools, total=3.4s` |
| ✅ | 多轮工具调用：第 2 轮 tool result 正确反馈给模型 | 同一会话 turn 1 完成后 turn 2 再次 query_memory：18:22:24.199 `<tool_xKyG name="query_memory">` + 18:22:24.233 `HermesAgentLoop: turn 2: 1 tools, total=2.0s`；turn 2 请求体 messages 含前一轮 `"name": "query_memory"` 的 tool_result — 证实 tool result round-trip 回模型 |
| ✅ | `<status type="complete">` 触发 `handleTaskCompletion` | 同 §3.1 的同一次调用响应里带 `<status type="complete"></status>`；18:19:45.404 `FloatingChatService: 输入处理状态已更新: InputProcessingState$Completed@97ee7d` — 证实 `handleTaskCompletion` L1289 的 `_inputProcessingState.value = InputProcessingState.Completed` 已执行 |
| ✅ | `<status type="wait_for_user_need">` 触发 `handleWaitForUserNeed` | "帮我做那个事情" 模糊 prompt 经 2 轮 query_memory 后，turn 3 响应含 `<status type="wait_for_user_need"></status>`；18:22:30.186 `EnhancedAIService: Wait for user need - skipping problem library analysis`（对应 L1344 `handleWaitForUserNeed` 内唯一日志标记） |
| ✅ | 超长消息触发 `onTokenLimitExceeded` | Hermes 侧契约由 `hermes-android/src/test/.../HermesAgentLoopBeforeNextTurnTest.kt` 6 测试覆盖：`beforeNextTurn` 返回 false → 循环在该 turn 前终止，`finishedNaturally=false`、`turnsUsed==turn`、无更多 LLM/工具调用；抛异常不中断；返 true 正常推进。`./gradlew :hermes-android:testDebugUnitTest` 全绿。Hermes 侧触发代码 `EnhancedAIService.kt` L1141-1168：`turn > 0 && usageRatio >= tokenUsageThreshold` 时 L1161 调 `onTokenLimitExceeded?.invoke()` → L1162 `isConversationActive.set(false)` → L1163 `stopAiService` → 返回 false → 触发上述契约 |

## 4. 回归检查

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | Memory query hook 仍工作（execContext 构造不变） | `execContext` 在 L775 构造后经 L926 传入 `runViaHermes`；`enableMemoryQuery` 继续传到 L1108 的 `handleTaskCompletion` 与 L1293 的 memory-query 路径 |
| ✅ | Role card 注入仍生效 | `roleCardId` 在 L942 传入 `runViaHermes`、L859 传入 tool-hook payload、L1111 / L1123 用于 `handleTaskCompletion` / `handleWaitForUserNeed` 的 avatarUri 解析 |
| ✅ | Group orchestration 不受影响 | `enableGroupOrchestrationHint` 仍在 L625 入口保留、L813 传入 prompt hook bridge；`isSubTask` 在 L939 传入 Hermes 路径并在 L972/L1160 用于 stopAiService 判定 |
| ✅ | `onNonFatalError` 回调正常触发 | 通过 L935/L1037 传入 `OperitChatCompletionServer`；新 sink 中 `AgentEvent.Error` 在 L1095 直接调用；memory-query 路径在 L1303 调用 |
| ✅ | `onToolInvocation` 回调每次工具调用触发一次 | L944 传入 `runViaHermes`；sink 的 `ToolCallStart` 事件在 L1076 每次触发一次 `onToolInvocation?.invoke(event.name)` — 每个工具调用恰好一次 |

## 5. 清理（可选，不影响核心功能）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | 删除 `processStreamCompletion` / `handleToolInvocation` / `processToolResults` 死代码 | `rg` 全仓库仅矩阵自身提及 |
| ✅ | 删除 `enhanceToolDetection` / `detectAndRepairTruncatedToolRound` | `rg` 全仓库仅矩阵自身提及 |
| ✅ | EnhancedAIService.kt 行数 ≤ 2100（从 ~2883 降下来） | `wc -l` = 1940 |

## 6. 工具 schema（延后，但进矩阵）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | AIToolHandler 注册的工具全部导出为 OpenAI JSON schema | `app/src/main/java/com/ai/assistance/operit/hermes/OpenAiToolSchema.kt` — `toolPromptsToOpenAiSchemas()`；在 `EnhancedAIService.kt` L1132 注入 `toolSchemas`；`:app:compileDebugKotlin` 通过 |
| ✅ | `validToolNames` 从 schema 自动派生 | `EnhancedAIService.kt` L1134 改为 `extractToolNames(openAiToolSchemas)`，schema 是唯一真值源 |

---

## 更新规则
- 改状态同时在"证据"列补一句话或链接（commit hash / logcat line / 文件路径）
- 发现遗漏功能 → 补进来（宁可多列，不能漏）
- 退出条件：全表 ✅
