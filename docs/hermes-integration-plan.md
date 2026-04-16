# Hermes-Android 混搭增强：详细集成方案

## 现状分析

### App 现有架构（`app/agent/`）

```
ChatController → GatewayController → AgentLoop → UnifiedLLMProvider
                                         ↓
                                    ToolRegistry / AndroidToolRegistry
                                         ↓
                                    ContextManager → MessageCompactor
```

**已有组件：**
- `AgentLoop (loop/Run.kt)` — 流式 LLM 调用 + 工具循环（OpenClaw 对齐）
- `ContextCompressor (agent/memory/)` — 对话压缩（对齐 OpenClaw compaction.ts）
- `MemoryManager (agent/memory/)` — 记忆管理（SQLite 后端 + 向量搜索）
- `SessionStore (agent/session/)` — 会话持久化
- `ToolRegistry` — 40+ Android 工具（tap, screenshot, exec, web_search 等）

### Hermes 独有组件（`hermes-android/`，app 中没有）

| 组件 | 文件 | 行数 | 价值 |
|------|------|------|------|
| **Trajectory** | `agent/Trajectory.kt` | ~200 | 逐轮记录 JSONL 日志 |
| **TrajectoryCompressor** | `TrajectoryCompressor.kt` | ~1070 | 压缩轨迹 → 摘要 |
| **Holographic** | `plugins/memory/holographic/` | ~600 | 全息记忆存储 |
| **Honcho** | `plugins/memory/honcho/` | ~500 | 会话记忆 API |
| **HermesAgentLoop** | `AgentLoop.kt` | ~450 | OpenAI 标准 tool-calling（非流式） |

### 不需要接入的（app 已有等价实现）

- `hermes/agent/ContextCompressor.kt` — app 有自己的 ContextCompressor（对齐 OpenClaw）
- `hermes/agent/MemoryManager.kt` — app 有自己的 MemoryManager + SQLite
- `hermes/gateway/Run.kt` — app 有 GatewayController
- `hermes/Session.kt` — app 有 SessionStore
- `hermes/Config.kt` — app 有 ConfigLoader

---

## Phase 1 — 接 Trajectory 追踪系统

### 目标
在 app 的 AgentLoop 中注入 hermes 的 Trajectory 记录系统，每次对话生成 JSONL 轨迹日志。

### 步骤

#### 1.1 创建适配器 `HermesBridge.kt`

```
app/src/main/java/com/xiaomo/androidforclaw/hermes/HermesBridge.kt
```

职责：
- 持有 hermes-android 的 Trajectory 实例
- 持有 hermes-android 的 TrajectoryCompressor 实例
- 提供 `recordTurn(role, content, toolCalls, toolResult)` 方法
- 提供 `compress(sessionKey)` 方法

#### 1.2 在 AgentLoop (Run.kt) 中注入 Trajectory 钩子

**注入点 1：LLM 回调后**
```kotlin
// 在 AgentLoop.runInternal() 的 LLM 响应处理中
hermesBridge?.recordTurn(
    sessionId = sessionId,
    role = "assistant",
    content = response.content,
    toolCalls = response.toolCalls
)
```

**注入点 2：工具执行后**
```kotlin
// 在 ToolCallDispatcher.dispatch() 之后
hermesBridge?.recordTurn(
    sessionId = sessionId,
    role = "tool",
    content = toolResult,
    toolCallId = tc.id
)
```

**注入点 3：用户消息**
```kotlin
// 在 AgentLoop 入口
hermesBridge?.recordTurn(
    sessionId = sessionId,
    role = "user",
    content = userMessage
)
```

#### 1.3 JSONL 存储路径

```
/sdcard/.androidforclaw/trajectories/{sessionKey}/
    ├── 2026-04-15T20:31:00.jsonl    ← 单次对话的轨迹
    ├── 2026-04-15T21:00:00.jsonl
    └── compressed/
        └── 2026-04-15.jsonl.gz      ← 压缩后的摘要
```

#### 1.4 压缩触发

- 手动：`/compress` 命令
- 自动：会话过期时（参考 hermes 的 `_session_expiry_watcher`）
- 自动：轨迹文件超过阈值（如 100KB）

---

## Phase 2 — 接 Memory 插件

### 2.1 Holographic Memory（可选）

hermes 的 `HolographicStore` 提供多维度记忆存储。需要适配到 app 的 `MemoryManager` 接口：

```kotlin
// app 的 MemoryManager 接口
interface MemoryProvider {
    fun search(query: String, limit: Int): List<MemoryHit>
    fun store(content: String, metadata: Map<String, Any>)
    fun delete(id: String)
}

// hermes 的 HolographicStore → 实现 MemoryProvider 接口
class HolographicMemoryProvider(
    private val store: HolographicStore
) : MemoryProvider { ... }
```

**前提**：HolographicStore 的 stub 方法需要先实现。

### 2.2 Honcho Session Memory（可选）

Honcho 是外部 API 服务，需要网络连接。在 Android 上作为可选插件：

```kotlin
// 检查 Honcho API key
val honchoApiKey = config.get("honcho_api_key")
if (honchoApiKey != null) {
    val honchoProvider = HonchoMemoryProvider(honchoApiKey)
    memoryManager.addProvider(honchoProvider)
}
```

---

## Phase 3 — 配置统一

### 3.1 HermesBridge 配置

在 `openclaw.json` 中添加 hermes 段：

```json
{
  "hermes": {
    "trajectory": {
      "enabled": true,
      "dir": "/sdcard/.androidforclaw/trajectories",
      "auto_compress": true,
      "compress_threshold_kb": 100
    },
    "memory": {
      "holographic": false,
      "honcho": false
    }
  }
}
```

### 3.2 条件编译（Flavor）

不通过 Flavor 区分（standard/hermes 现在功能一样）。改为运行时配置：
- `hermes.trajectory.enabled = true` → 开启轨迹记录
- `hermes.memory.honcho = true` → 注入 Honcho provider

---

## 接入点对照表

| App 现有代码 | 注入方式 | 新增依赖 |
|-------------|---------|---------|
| `AgentLoop.runInternal()` | 添加 Trajectory 钩子 | `HermesBridge.recordTurn()` |
| `AgentLoop.runInternal()` | 添加压缩触发 | `HermesBridge.maybeCompress()` |
| `MemoryManager` | 注册新 Provider | `HolographicMemoryProvider` |
| `GatewayController` | 注册 `/compress` 命令 | `HermesBridge.compress()` |
| `ConfigLoader` | 读取 `hermes` 段 | 无（已有 JSON 解析） |

---

## 文件清单

### 需要创建的文件

1. `app/src/main/java/.../hermes/HermesBridge.kt` — 桥接层（~150行）
2. `app/src/main/java/.../hermes/HolographicMemoryProvider.kt` — 记忆适配器（~80行）
3. `app/src/main/java/.../hermes/HonchoMemoryProvider.kt` — 记忆适配器（~80行）

### 需要修改的文件

1. `app/src/main/java/.../agent/loop/Run.kt` — 添加 Trajectory 钩子（~20行改动）
2. `app/src/main/java/.../gateway/GatewayController.kt` — 注册 `/compress` 命令（~10行改动）
3. `app/src/main/java/.../agent/tools/ToolRegistry.kt` — 可选注册 Trajectory 工具（~10行改动）

### 不需要修改的文件

- `hermes-android/` 所有文件 — 作为库被 app 引用，不改
- `ChatController.kt` — UI 层不动
- `IGatewayChannel.kt` — 桥接层不动

---

## 验证标准

1. **编译**：`./gradlew :app:compileDebugKotlin` 通过
2. **功能**：对话后检查 `/sdcard/.androidforclaw/trajectories/` 有 JSONL 文件
3. **压缩**：执行 `/compress` 后轨迹被压缩
4. **不影响现有功能**：对话、工具执行、流式输出正常

---

## 优先级

| 阶段 | 内容 | 预计工时 | 阻塞项 |
|------|------|---------|--------|
| Phase 1.1 | HermesBridge + Trajectory 钩子 | 2-3h | 无 |
| Phase 1.2 | TrajectoryCompressor 集成 | 1-2h | Phase 1.1 |
| Phase 2.1 | Holographic Memory | 1-2h | hermes stub 实现 |
| Phase 2.2 | Honcho Memory | 1h | API key 配置 |
| Phase 3 | 配置统一 | 1h | Phase 1 + 2 |

**最小可用版本**：Phase 1.1（Trajectory 记录），2-3 小时。
