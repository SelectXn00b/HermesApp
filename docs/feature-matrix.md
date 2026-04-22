# Operit-Hermes 集成 — 功能矩阵

> Stop hook 的硬性检查对象。⬜/🟨/✅ 三态。矩阵有任何 ⬜/🟨 → 禁止结束会话。

## 图例
- ⬜ 未开始
- 🟨 进行中
- ✅ 完成（附证据链接或一句证明）

---

## 1. 构建

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | `:hermes-android:compileDebugKotlin` 通过 | JDK 21 构建日志 |
| ✅ | `:app:compileDebugKotlin` 通过 | 同上 |
| ✅ | `:app:assembleDebug` APK 成功 | app-debug.apk 已生成 |
| ✅ | applicationId 避开 `com.xiaomi.mimo` provider 冲突 | `.hermes` 后缀，已安装成功 |

## 2. 内核替换（Plan Option B）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | `OperitChatCompletionServer` 把 `<tool>` XML → `toolCalls` 合成 | 正则基于 ChatMarkupRegex.toolCallPattern |
| ✅ | `HermesAgentLoop.beforeNextTurn` hook 新增 | AgentLoop.kt |
| ✅ | `EnhancedAIService.sendMessage` L917-1104 替换为 `runViaHermes(...)` | |
| ✅ | `processStreamCompletion` / `handleToolInvocation` / `processToolResults` 废弃（或仅保留定义） | |
| ⬜ | 轮间 token 阈值在 `beforeNextTurn` 里等价原 L2089-2110 | 需人工验证 onTokenLimitExceeded 触发 |
| ⬜ | accumulated*TokenCount 会计与原实现一致 | 需对比原 L1020、L2218 |

## 3. 真机冒烟（S7）

| 状态 | 项目 | 证据 |
|---|---|---|
| ✅ | 设备已连上 adb | `adb devices -l` → `8370d265	device` |
| ⬜ | MiMo v2 Pro 公网 config 已注入 datastore | `run-as tee files/datastore/model_configs.preferences_pb` |
| ✅ | 启动 app 不 crash | 安装 4/22 17:35 APK (sha=sessione) 后 `adb shell am start` 成功，pid=22832，activity in stack (`dumpsys activity activities` 显示 Task #5464)，logcat 无 FATAL/AndroidRuntime |
| ⬜ | 发一条无工具消息：assistant 文本正常流出 | logcat 截图 / 人肉确认 |
| ⬜ | 发一条触发 `<tool>` 的消息：tool dispatch + result 正确渲染 | |
| ⬜ | 多轮工具调用：第 2 轮 tool result 正确反馈给模型 | |
| ⬜ | `<status type="complete">` 触发 `handleTaskCompletion` | |
| ⬜ | `<status type="wait_for_user_need">` 触发 `handleWaitForUserNeed` | |
| ⬜ | 超长消息触发 `onTokenLimitExceeded` | |

## 4. 回归检查

| 状态 | 项目 | 证据 |
|---|---|---|
| ⬜ | Memory query hook 仍工作（execContext 构造不变） | |
| ⬜ | Role card 注入仍生效 | |
| ⬜ | Group orchestration 不受影响 | |
| ⬜ | `onNonFatalError` 回调正常触发 | |
| ⬜ | `onToolInvocation` 回调每次工具调用触发一次 | |

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
