# Workflow Intent 触发（自定义 Action）

本文档说明如何通过 **Intent 广播**触发 Operit 的工作流（Workflow）。

当前实现支持：

- **每个工作流的 Intent Trigger 可以配置自己的 `action`**
- 外部 App 通过发送广播（推荐使用“显式广播”）即可触发匹配的工作流

对应代码：

- 接收入口：`app/src/main/java/com/ai/assistance/operit/integrations/tasker/WorkflowTaskerReceiver.kt`
- 匹配与触发：`WorkflowRepository.triggerWorkflowsByIntentEvent(intent)`

---

## 1. 核心概念

### 1.1 TriggerNode(intent)

当某个工作流的触发器节点满足：

- `triggerType == "intent"`
- `triggerConfig["action"] == intent.action`

则该工作流会被触发执行。

### 1.2 为什么推荐“显式广播”

Android 对隐式广播有各种限制（尤其是后台、Android 8+ 等）。

为了确保广播能稳定投递给 Operit，推荐使用：

- **显式广播**：指定 `component`（包名 + Receiver 类名）

这样即使 action 是自定义的，也能确保发给 Operit 的 `WorkflowTaskerReceiver`。

---

## 2. Receiver / Component 信息

- **Receiver 类**：`com.ai.assistance.operit.integrations.tasker.WorkflowTaskerReceiver`
- **包名**：`com.ai.assistance.operit`
- **Component**：`com.ai.assistance.operit/.integrations.tasker.WorkflowTaskerReceiver`

---

## 3. 配置工作流的 action

在工作流编辑器中，将触发器设置为：

- **类型**：Intent
- **action**：填写你希望外部触发的 action，例如：
  - `com.example.myapp.TRIGGER_OPERIT_WORKFLOW_A`

注意：

- action 只是一个字符串，用于匹配。
- 触发时 intent 的 extras 会作为 TriggerNode 的输出 JSON（字符串）提供给下游节点（可用 ExtractNode(JSON) 提取字段）。

---

## 4. adb 触发示例

### 4.1 方式 A：显式广播（推荐，支持任意自定义 action）

```bash
adb shell am broadcast \
  -n com.ai.assistance.operit/.integrations.tasker.WorkflowTaskerReceiver \
  -a com.example.myapp.TRIGGER_OPERIT_WORKFLOW_A \
  --es message "hello from adb" \
  --es request_id "req-1001"
```

- `-n` 指定 component，确保发给 Operit。
- `-a` 为你在工作流 Trigger 里配置的 action。
- `--es/--ez/--ei/...` 为 extras，会被工作流 TriggerNode 收集并输出。

### 4.2 方式 B：隐式广播（仅当系统允许投递时）

如果你不想指定 component，可以试：

```bash
adb shell am broadcast \
  -a com.example.myapp.TRIGGER_OPERIT_WORKFLOW_A \
  --es message "hello"
```

但这取决于系统版本、ROM 策略以及 Operit 是否能在后台接收该隐式广播。

### 4.3 方式 C：使用内置默认 action（兼容用法）

如果你的工作流 Trigger 里 `action` 配置的是默认值：

- `com.ai.assistance.operit.TRIGGER_WORKFLOW`

那么可以使用：

```bash
adb shell am broadcast \
  -a com.ai.assistance.operit.TRIGGER_WORKFLOW \
  --es message "hello" \
  --es request_id "req-1002"
```

---

## 5. WORKFLOW_RESULT：工作流回传广播（示范模板默认值）

在内置的“Intent 触发 + 发送消息 + 回传广播”示范模板中，会使用工具节点 `send_broadcast` 回传结果：

- **action**：`com.ai.assistance.operit.WORKFLOW_RESULT`
- **extra_key**：`result`
- **extra_value**：来自 `send_message_to_ai` 节点的输出（字符串）

你也可以在工作流里自定义：

- 回传 action（例如回传给你自己的 App）
- extra 的 key/value（例如同时回传 `request_id`、`chat_id` 等）

---

## 6. 如何接收 WORKFLOW_RESULT

`adb` 本身无法直接作为“广播接收端”来打印收到的广播内容（它只能发送广播）。要接收回传广播，推荐两种方式：

### 6.1 用 Tasker 接收（最方便）

- 在 Tasker 创建 Profile：Event -> System -> Intent Received
- Action 填：`com.ai.assistance.operit.WORKFLOW_RESULT`
- 在 Task 中读取变量（通常可直接用 `%result` 或从 extras 映射中取）

### 6.2 写一个最小接收 App / Receiver（用于调试）

在你的测试 App 中注册一个 `BroadcastReceiver` 监听 `com.ai.assistance.operit.WORKFLOW_RESULT`，在 `onReceive()` 里读取：

- `intent.getStringExtra("result")`

然后你可以用 `adb logcat` 看接收端打印的内容。

---

## 7. 工作流内如何读取 extras（Trigger JSON + Extract(JSON)）

TriggerNode 会把收到的 extras 转为 JSON 字符串作为输出，例如：

- 收到 extras：
  - `message=hello`
  - `request_id=req-1001`

TriggerNode 输出（示意）：

```json
{"message":"hello","request_id":"req-1001"}
```

下游可以使用 `ExtractNode(mode=JSON)`：

- `source = NodeReference(triggerNodeId)`
- `expression = "message"`

从而得到 `hello`。

---

## 8. 注意事项

- 该 Receiver 为 `exported=true`：任何 App 都可以发送广播到该入口。
- 如果你担心滥用，建议后续增加 permission 或者在 Receiver 里做校验/白名单。
