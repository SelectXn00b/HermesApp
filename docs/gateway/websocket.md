# Gateway 架构设计 - 长期规划

> **注意**: Gateway 是 AndroidForClaw 的长期架构规划，不是立即实施的内容。当前专注于 Agent Runtime 和 Skills 系统。

---

## 🎯 设计目标

Gateway 是单一控制平面，负责：

1. **多渠道接入** - 支持多种消息渠道（Web UI、WhatsApp、Telegram、HTTP API）
2. **会话管理** - 管理多用户、多会话并发
3. **安全控制** - Pairing 配对、Allowlist 白名单、访问策略
4. **远程控制** - WebSocket 实时控制和监控
5. **分布式能力** - Gateway 和 Runtime 可分离部署（可选）

---

## 📐 架构设计

### 总体架构

```
┌────────────────────────────────────────────────────┐
│                  Gateway Server                     │
│            (Kotlin / Ktor Web Server)              │
│                                                     │
│  ┌──────────────┐  ┌──────────────┐               │
│  │   Channels   │  │   Sessions   │               │
│  │   (渠道层)   │  │   (会话层)   │               │
│  └──────────────┘  └──────────────┘               │
│  ┌──────────────┐  ┌──────────────┐               │
│  │   Security   │  │   Routing    │               │
│  │   (安全层)   │  │   (路由层)   │               │
│  └──────────────┘  └──────────────┘               │
└────────────────────────────────────────────────────┘
                      ↓ WebSocket RPC
┌────────────────────────────────────────────────────┐
│              Agent Runtime (Android)                │
│                                                     │
│  ┌──────────────────────────────────────────────┐ │
│  │  AgentLoop + Skills + Android Tools          │ │
│  └──────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

---

## 🔌 RPC 接口设计

### 核心 API

```kotlin
interface GatewayRPC {
    /**
     * 执行 Agent 任务
     * @param sessionKey 会话标识
     * @param message 用户消息
     * @param thinking 推理模式 ("low" | "medium" | "high")
     * @return runId 和接收时间
     */
    suspend fun agent(params: AgentParams): AgentRunResponse

    /**
     * 等待 Agent 执行完成
     * @param runId 运行 ID
     * @param timeoutMs 超时时间（ms）
     * @return 执行状态
     */
    suspend fun agentWait(params: AgentWaitParams): AgentWaitResponse

    /**
     * 获取会话列表
     */
    suspend fun sessionList(): List<Session>

    /**
     * 获取会话详情
     */
    suspend fun sessionGet(sessionId: String): Session

    /**
     * 重置会话
     */
    suspend fun sessionReset(sessionId: String)

    /**
     * 健康检查
     */
    suspend fun health(): HealthResponse
}

data class AgentParams(
    val sessionKey: String,
    val message: String,
    val thinking: String? = "medium"  // "low" | "medium" | "high"
)

data class AgentRunResponse(
    val runId: String,
    val acceptedAt: Long
)

data class AgentWaitParams(
    val runId: String,
    val timeoutMs: Long? = 30000
)

data class AgentWaitResponse(
    val status: String,  // "ok" | "error" | "timeout"
    val startedAt: Long,
    val endedAt: Long,
    val error: String? = null
)

data class HealthResponse(
    val status: String,  // "ok"
    val version: String,
    val uptime: Long
)
```

---

## 🌐 多渠道接入

### 渠道抽象

```kotlin
interface Channel {
    val name: String
    val type: ChannelType

    /**
     * 启动渠道
     */
    suspend fun start()

    /**
     * 停止渠道
     */
    suspend fun stop()

    /**
     * 发送消息到用户
     */
    suspend fun sendMessage(userId: String, content: String)

    /**
     * 接收消息事件
     */
    fun onMessage(handler: suspend (ChannelMessage) -> Unit)
}

enum class ChannelType {
    WEB_UI,
    HTTP_API,
    WHATSAPP,
    TELEGRAM,
    WEBHOOK
}

data class ChannelMessage(
    val channelId: String,
    val userId: String,
    val content: String,
    val timestamp: Long
)
```

### 内置渠道实现

#### 1. Web UI Channel

```kotlin
class WebUIChannel(
    private val sessionManager: SessionManager
) : Channel {
    override val name = "web-ui"
    override val type = ChannelType.WEB_UI

    private val wsConnections = mutableMapOf<String, WebSocketSession>()

    override suspend fun start() {
        // 启动 WebSocket 服务器
        embeddedServer(Netty, port = 8080) {
            install(WebSockets)

            routing {
                webSocket("/ws") {
                    val userId = call.parameters["userId"] ?: return@webSocket
                    wsConnections[userId] = this

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val message = frame.readText()
                            handleMessage(userId, message)
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    override suspend fun sendMessage(userId: String, content: String) {
        val ws = wsConnections[userId] ?: return
        ws.send(Frame.Text(content))
    }
}
```

#### 2. HTTP API Channel

```kotlin
class HttpApiChannel(
    private val sessionManager: SessionManager
) : Channel {
    override val name = "http-api"
    override val type = ChannelType.HTTP_API

    override suspend fun start() {
        embeddedServer(Netty, port = 8080) {
            routing {
                post("/api/agent") {
                    val params = call.receive<AgentParams>()
                    val result = sessionManager.executeAgent(params)
                    call.respond(result)
                }

                get("/api/sessions") {
                    val sessions = sessionManager.listSessions()
                    call.respond(sessions)
                }
            }
        }.start(wait = false)
    }

    override suspend fun sendMessage(userId: String, content: String) {
        // HTTP API 是请求-响应模式，不需要主动推送
    }
}
```

---

## 🔐 安全控制

### 1. Pairing (配对机制)

```kotlin
class PairingManager {
    private val pairedDevices = mutableSetOf<String>()

    /**
     * 生成配对码
     */
    fun generatePairingCode(): String {
        return UUID.randomUUID().toString().take(8)
    }

    /**
     * 验证配对码
     */
    fun verifyPairingCode(code: String, deviceId: String): Boolean {
        // 验证逻辑
        if (isValidCode(code)) {
            pairedDevices.add(deviceId)
            return true
        }
        return false
    }

    /**
     * 检查设备是否已配对
     */
    fun isPaired(deviceId: String): Boolean {
        return pairedDevices.contains(deviceId)
    }
}
```

### 2. Allowlist (白名单)

```kotlin
class AllowlistManager {
    private val allowedUsers = mutableSetOf<String>()

    /**
     * 添加到白名单
     */
    fun allow(userId: String) {
        allowedUsers.add(userId)
    }

    /**
     * 从白名单移除
     */
    fun disallow(userId: String) {
        allowedUsers.remove(userId)
    }

    /**
     * 检查是否在白名单中
     */
    fun isAllowed(userId: String): Boolean {
        return allowedUsers.contains(userId)
    }
}
```

### 3. Access Policy

```kotlin
data class AccessPolicy(
    val requirePairing: Boolean = true,
    val requireAllowlist: Boolean = false,
    val allowDMs: Boolean = true,
    val allowChannels: Boolean = true,
    val rateLimit: RateLimit? = null
)

data class RateLimit(
    val maxRequestsPerMinute: Int = 10,
    val maxRequestsPerHour: Int = 100
)

class SecurityManager(
    private val pairingManager: PairingManager,
    private val allowlistManager: AllowlistManager,
    private val policy: AccessPolicy
) {
    /**
     * 检查请求是否被允许
     */
    fun checkAccess(request: ChannelMessage): AccessResult {
        // 1. 配对检查
        if (policy.requirePairing && !pairingManager.isPaired(request.userId)) {
            return AccessResult.Denied("Device not paired")
        }

        // 2. 白名单检查
        if (policy.requireAllowlist && !allowlistManager.isAllowed(request.userId)) {
            return AccessResult.Denied("User not in allowlist")
        }

        // 3. 频率限制
        if (policy.rateLimit != null && isRateLimited(request.userId)) {
            return AccessResult.Denied("Rate limit exceeded")
        }

        return AccessResult.Allowed
    }
}

sealed class AccessResult {
    object Allowed : AccessResult()
    data class Denied(val reason: String) : AccessResult()
}
```

---

## 📡 WebSocket 协议

### 消息格式

```typescript
// Client → Server
{
  "type": "agent.run",
  "id": "msg-123",
  "params": {
    "sessionKey": "user-abc-session-1",
    "message": "打开微信",
    "thinking": "medium"
  }
}

// Server → Client (Progress Events)
{
  "type": "agent.progress",
  "runId": "run-456",
  "event": "iteration",
  "data": {
    "iteration": 1,
    "timestamp": 1234567890
  }
}

{
  "type": "agent.progress",
  "runId": "run-456",
  "event": "tool_call",
  "data": {
    "tool": "screenshot",
    "args": {}
  }
}

{
  "type": "agent.progress",
  "runId": "run-456",
  "event": "tool_result",
  "data": {
    "tool": "screenshot",
    "result": "截图成功",
    "image": "base64..."
  }
}

// Server → Client (Completion)
{
  "type": "agent.complete",
  "runId": "run-456",
  "result": {
    "status": "ok",
    "content": "任务完成",
    "iterations": 5,
    "toolsUsed": ["screenshot", "tap", "stop"]
  }
}
```

---

## 🗄️ 会话管理

### Session 数据模型

```kotlin
data class Session(
    val id: String,
    val key: String,  // user-channel-sessionId
    val userId: String,
    val channelId: String,
    val createdAt: Long,
    val lastActiveAt: Long,
    val messages: List<Message>,
    val metadata: Map<String, Any>
)

data class Message(
    val role: String,  // "user" | "assistant" | "tool"
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val timestamp: Long
)
```

### SessionManager 增强

```kotlin
class SessionManager {
    private val sessions = mutableMapOf<String, Session>()
    private val runningTasks = mutableMapOf<String, Job>()

    /**
     * 获取或创建会话
     */
    suspend fun getOrCreate(sessionKey: String): Session {
        return sessions.getOrPut(sessionKey) {
            Session(
                id = UUID.randomUUID().toString(),
                key = sessionKey,
                userId = extractUserId(sessionKey),
                channelId = extractChannelId(sessionKey),
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis(),
                messages = emptyList(),
                metadata = emptyMap()
            )
        }
    }

    /**
     * 运行 Agent 任务
     */
    suspend fun runAgent(
        sessionKey: String,
        message: String,
        thinking: String = "medium"
    ): String {
        val session = getOrCreate(sessionKey)
        val runId = UUID.randomUUID().toString()

        // 启动异步任务
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = agentLoop.run(
                    systemPrompt = buildSystemPrompt(session),
                    userMessage = message,
                    reasoningEnabled = thinking != "low"
                )

                // 保存结果
                saveRunResult(runId, result)
            } catch (e: Exception) {
                saveRunError(runId, e)
            }
        }

        runningTasks[runId] = job
        return runId
    }

    /**
     * 等待任务完成
     */
    suspend fun waitForRun(runId: String, timeoutMs: Long): AgentWaitResponse {
        val job = runningTasks[runId] ?: return AgentWaitResponse(
            status = "error",
            startedAt = 0,
            endedAt = 0,
            error = "Run not found"
        )

        return try {
            withTimeout(timeoutMs) {
                job.join()
                getRunResult(runId)
            }
        } catch (e: TimeoutCancellationException) {
            AgentWaitResponse(
                status = "timeout",
                startedAt = 0,
                endedAt = System.currentTimeMillis(),
                error = "Timeout after ${timeoutMs}ms"
            )
        }
    }

    /**
     * 列出所有会话
     */
    fun listSessions(): List<Session> {
        return sessions.values.toList()
    }

    /**
     * 重置会话
     */
    fun resetSession(sessionId: String) {
        sessions.remove(sessionId)
    }
}
```

---

## 🌐 渠道实现示例

### Web UI Channel

```html
<!-- web/index.html -->
<!DOCTYPE html>
<html>
<head>
    <title>AndroidForClaw Control</title>
</head>
<body>
    <div id="chat-container">
        <div id="messages"></div>
        <input id="input" type="text" placeholder="输入指令..."/>
        <button onclick="send()">发送</button>
    </div>

    <script>
        const ws = new WebSocket('ws://localhost:8080/ws?userId=user123');

        ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            if (msg.type === 'agent.progress') {
                updateProgress(msg);
            } else if (msg.type === 'agent.complete') {
                showResult(msg.result);
            }
        };

        function send() {
            const input = document.getElementById('input');
            ws.send(JSON.stringify({
                type: 'agent.run',
                params: {
                    sessionKey: 'user123-web-default',
                    message: input.value,
                    thinking: 'medium'
                }
            }));
            input.value = '';
        }
    </script>
</body>
</html>
```

### HTTP API Channel

```bash
# 执行任务
curl -X POST http://localhost:8080/api/agent \
  -H "Content-Type: application/json" \
  -d '{
    "sessionKey": "user123-api-default",
    "message": "打开微信",
    "thinking": "medium"
  }'

# 响应
{
  "runId": "run-abc123",
  "acceptedAt": 1234567890
}

# 等待完成
curl -X GET "http://localhost:8080/api/agent/run-abc123/wait?timeout=60000"

# 响应
{
  "status": "ok",
  "startedAt": 1234567890,
  "endedAt": 1234567895,
  "result": {
    "content": "已打开微信",
    "iterations": 3
  }
}
```

---

## 📱 Android Runtime 通信

### 双向通信设计

```kotlin
class AndroidGatewayClient(
    private val gatewayUrl: String
) {
    private var webSocket: WebSocket? = null

    /**
     * 连接到 Gateway
     */
    fun connect() {
        val request = Request.Builder()
            .url("$gatewayUrl/runtime/connect")
            .build()

        webSocket = OkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleGatewayMessage(text)
            }
        })
    }

    /**
     * 处理来自 Gateway 的消息
     */
    private fun handleGatewayMessage(json: String) {
        val message = Gson().fromJson(json, GatewayMessage::class.java)

        when (message.type) {
            "agent.run" -> {
                // Gateway 请求执行任务
                val params = message.params as AgentParams
                executeAgentTask(params)
            }
            "session.reset" -> {
                // Gateway 请求重置会话
                val sessionId = message.params as String
                sessionManager.resetSession(sessionId)
            }
        }
    }

    /**
     * 发送进度到 Gateway
     */
    fun sendProgress(runId: String, event: ProgressEvent) {
        val message = mapOf(
            "type" to "agent.progress",
            "runId" to runId,
            "event" to event.type,
            "data" to event.data
        )
        webSocket?.send(Gson().toJson(message))
    }

    /**
     * 发送完成消息到 Gateway
     */
    fun sendComplete(runId: String, result: AgentResult) {
        val message = mapOf(
            "type" to "agent.complete",
            "runId" to runId,
            "result" to result
        )
        webSocket?.send(Gson().toJson(message))
    }
}
```

---

## 🏗️ 实施阶段

### Phase 1: 本地 Web UI (3周)

**目标**: 在设备上运行简单的 Web 服务器，提供本地控制页面

- [ ] 使用 NanoHTTPD 实现 HTTP 服务器
- [ ] 创建简单的 Web UI (HTML + JS)
- [ ] WebSocket 支持（实时通信）
- [ ] 基础 RPC 接口（agent, agentWait, sessionList）

**验收标准**:
- ✅ 可通过浏览器访问 `http://localhost:8080`
- ✅ 可输入指令并看到实时执行进度
- ✅ 显示截图和日志

---

### Phase 2: 远程控制 (4周)

**目标**: 支持局域网内远程访问

- [ ] HTTPS 支持（自签名证书）
- [ ] 身份认证（Pairing + Token）
- [ ] 远程日志查看
- [ ] 远程截图查看

**验收标准**:
- ✅ 可从电脑浏览器控制手机
- ✅ 需要配对码才能访问
- ✅ 实时查看执行日志和截图

---

### Phase 3: 多渠道支持 (6周)

**目标**: 支持多种消息渠道

- [ ] HTTP API Channel (REST API)
- [ ] Webhook Channel (接收第三方回调)
- [ ] (可选) Telegram Bot Channel

**验收标准**:
- ✅ 可通过 HTTP API 调用
- ✅ 可接入第三方系统（如企业内部平台）

---

### Phase 4: 分布式部署 (长期)

**目标**: Gateway 和 Runtime 分离部署

- [ ] Gateway 作为独立服务器部署
- [ ] Android Runtime 通过 WebSocket 连接 Gateway
- [ ] 多设备支持（一个 Gateway 管理多台手机）

**验收标准**:
- ✅ Gateway 部署在服务器上
- ✅ 多台 Android 设备连接到同一 Gateway
- ✅ 负载均衡和任务分发

---

## 🎯 技术选型

### Gateway 服务器

**选项 1: Ktor (推荐)**
- Kotlin 原生 Web 框架
- 协程支持
- WebSocket 原生支持
- 轻量级

**选项 2: Spring Boot**
- 成熟的 Java 生态
- 更多第三方库
- 相对较重

**决策**: 使用 Ktor，保持 Kotlin 统一

---

### Android 端 Web 服务器

**当前**: NanoHTTPD (已集成)
- 轻量级 (单文件)
- 支持 WebSocket
- 适合本地 Web UI

**未来**: Ktor Android
- 与 Gateway 技术栈统一
- 更强大的功能
- 更好的性能

---

## 📊 渐进式迁移

### 阶段 1: 当前架构（无 Gateway）

```
┌────────────────────────────┐
│   悬浮窗 UI (唯一入口)      │
└────────────────────────────┘
             ↓
┌────────────────────────────┐
│      AgentLoop             │
│  (直接调用)                 │
└────────────────────────────┘
```

---

### 阶段 2: 本地 Gateway（Web UI）

```
┌────────────────────────────┐
│  悬浮窗 UI  │  Web UI       │
└────────────────────────────┘
             ↓
┌────────────────────────────┐
│  Local Gateway (NanoHTTPD) │
└────────────────────────────┘
             ↓
┌────────────────────────────┐
│      AgentLoop             │
└────────────────────────────┘
```

**优点**:
- 保持向后兼容（悬浮窗仍可用）
- 增加 Web UI 控制方式
- Gateway 和 Runtime 在同一进程

---

### 阶段 3: 远程 Gateway（分离部署）

```
┌────────────────────────────┐
│  Web Browser               │
└────────────────────────────┘
             ↓ HTTP/WebSocket
┌────────────────────────────┐
│  Gateway Server (Ktor)     │
│  (部署在服务器)             │
└────────────────────────────┘
             ↓ WebSocket
┌────────────────────────────┐
│  Android Runtime           │
│  AgentLoop + Tools         │
└────────────────────────────┘
```

**优点**:
- 远程控制（不在局域网内也可控制）
- 多设备管理（一个 Gateway 控制多台手机）
- 集中式日志和监控

---

## 🔍 核心问题与答案

### Q: 为什么要 Gateway？

**A**: Gateway 提供统一的控制平面：
1. **多渠道支持** - 一个后端支持多种前端（Web、API、聊天 App）
2. **会话管理** - 统一管理用户会话和上下文
3. **安全控制** - 集中的身份认证和权限管理
4. **扩展性** - 轻松添加新渠道，不影响 Runtime

### Q: Gateway 是必需的吗？

**A**: 不是。当前的悬浮窗方案完全可用。

Gateway 的价值在于：
- 🌐 远程访问（在电脑上控制手机）
- 📊 集中监控（查看日志、截图）
- 🔌 API 接入（集成到其他系统）

如果只需要本地控制，现有架构已足够。

### Q: 何时实施 Gateway？

**A**: 在完成 Skills 系统后考虑。

**优先级**:
1. 🔴 P0: Skills 系统（知识外置，Token 优化）
2. 🟡 P1: 本地 Web UI（便捷控制）
3. 🟢 P2: 远程 Gateway（高级功能）

---

## 🎓 学习 Hermes 的方法

### 1. 读文档，不读全部代码

Hermes 的 `docs/` 目录非常完善：
- `docs/concepts/agent.md` - Agent 核心概念
- `docs/concepts/agent-loop.md` - Agent Loop 细节
- `docs/tools/skills.md` - Skills 系统

**建议**: 先读文档理解理念，再看代码实现。

---

### 2. 提取理念，不复制代码

Hermes 是 TypeScript + Node.js，我们是 Kotlin + Android。

**应该学习**:
- ✅ 架构分层思想
- ✅ Skills 系统设计
- ✅ 按需加载策略

**不要复制**:
- ❌ 具体代码实现
- ❌ 20+ 渠道的复杂性
- ❌ 分布式架构细节

---

### 3. 移动端优化

Hermes 是通用平台，我们是移动端专用。

**移动端特色**:
- 📱 Android 专属工具（Accessibility、MediaProjection）
- 🔋 电池和性能优化
- 📶 离线能力（本地执行）
- 🎨 移动端 UI/UX

---

## 📚 参考资源

### Hermes 文档
- [Hermes GitHub](https://github.com/openclaw/openclaw)
- [概念: Agent Runtime](https://docs.hermes.ai/concepts/agent)
- [概念: Agent Loop](https://docs.hermes.ai/concepts/agent-loop)
- [工具: Skills](https://docs.hermes.ai/tools/skills)

### 技术标准
- [AgentSkills.io](https://agentskills.io) - Skills 标准格式
- [Claude API Docs](https://www.anthropic.com/api) - AI 能力

### 相关项目
- [pi-mono](https://github.com/anthropics/pi-mono) - Hermes 使用的 Agent 引擎
- [nanobot](https://github.com/anthropics/nanobot) - CLI Agent 工具

---

## ✅ 对齐检查清单

### Agent Runtime (核心)
- [x] AgentLoop 核心循环
- [x] ToolRegistry / SkillRegistry
- [x] Extended Thinking 支持
- [ ] Skills 系统完整实现
- [ ] ContextBuilder 优化

### Platform Layer (Android)
- [x] Accessibility Service
- [x] MediaProjection (截图)
- [x] 悬浮窗 UI
- [x] Android 工具集

### Gateway Layer (规划)
- [ ] WebSocket RPC 接口
- [ ] 会话管理增强
- [ ] 多渠道抽象
- [ ] 安全控制
- [ ] Web UI 控制面板

---

**对齐是手段，不是目的** - 我们学习 Hermes 的优秀设计，但保持 AndroidForClaw 的移动端特色。
