# Channels Overview

AndroidForClaw 的多渠道接入架构。

---

## 🎯 什么是 Channel？

**Channel** 是用户与 Agent 交互的入口。不同的 Channel 提供不同的访问方式。

**设计理念** (来自 Hermes):
```
User → Channel → Gateway → Agent Runtime → Tools
```

---

## 📱 当前支持的 Channels

### 1. App UI (Android Native)

**入口**: MainActivityCompose (Jetpack Compose 聊天界面)

**特点**:
- ✅ 原生 Android 体验
- ✅ 本地执行，无需网络
- ✅ 会话持久化
- ✅ 实时响应

**使用方式**:
1. 打开 AndroidForClaw App
2. 在聊天框输入消息
3. 等待 AI 回复

**技术实现**:
```
MainActivityCompose → ChatViewModel → MainEntryNew.runWithSession()
```

**代码**: `app/src/main/java/com/agent/mobile/ui/activity/MainActivityCompose.kt`

---

### 2. WebUI (Web Interface)

**入口**: Web 聊天界面 (http://localhost:5174)

**特点**:
- ✅ 跨平台访问 (PC/平板/手机浏览器)
- ✅ 实时同步 (WebSocket)
- ✅ 现代 UI (Lit Web Components)
- ✅ 轻量级 (5KB 框架)

**使用方式**:
1. 启动 WebUI: `cd ui && npm run dev`
2. 配置端口转发: `adb forward tcp:8080 tcp:8080`
3. 打开浏览器: `http://localhost:5174/`
4. 输入消息

**技术实现**:
```
WebUI → WebSocket → GatewayServer → MainEntryNew.runWithSession()
```

**代码**: `ui/src/ui/app.ts`

---

### 3. ADB Broadcast (Debug)

**入口**: ADB 命令行

**特点**:
- ✅ 无需 UI
- ✅ 脚本化
- ✅ 调试友好
- ✅ CI/CD 集成

**使用方式**:
```bash
adb shell am broadcast \
  -a com.xiaomo.hermes.ACTION_EXECUTE_AGENT \
  -p com.xiaomo.hermes.debug \
  --es message "你的消息"
```

**技术实现**:
```
ADB → AgentMessageReceiver → MainEntryNew.runWithSession()
```

**代码**: `app/src/main/java/com/xiaomo/hermes/core/AgentMessageReceiver.kt`

**测试**:
```bash
adb shell am broadcast -a PHONE_FORCLAW_SEND_MESSAGE --es message "测试" com.xiaomo.hermes
```

---

## 🚀 规划中的 Channels

### 4. WhatsApp (未来)

**目标**: 通过 WhatsApp 消息控制手机

**技术方案**:
- WhatsApp Business API
- Gateway 适配器
- 消息路由到 Session

**预期用法**:
```
用户发 WhatsApp 消息: "打开微信"
  ↓ WhatsApp Business API
Gateway receives message
  ↓ Route to session
Agent executes task
  ↓ Broadcast result
Gateway replies via WhatsApp
```

---

### 5. Telegram (未来)

**目标**: 通过 Telegram Bot 控制

**技术方案**:
- Telegram Bot API
- 命令解析 (/start, /help, etc.)
- Session 绑定

**预期用法**:
```
/start - 开始对话
/session new - 创建新会话
打开微信 - 执行任务
/stop - 停止执行
```

---

### 6. HTTP API (部分实现)

**当前状态**: 基础 API (health, device status)

**目标**: 完整的 REST API

**规划**:
```
POST /api/agent/execute
POST /api/sessions/create
GET  /api/sessions/{id}/history
DELETE /api/sessions/{id}
GET  /api/tools/list
POST /api/tools/{name}/execute
```

**适用场景**:
- 第三方集成
- 自动化脚本
- CI/CD pipeline

---

## 🔄 Channel 通信协议

### 统一的消息格式

所有 Channel 最终都转换为统一格式:

```kotlin
data class ChannelMessage(
    val channelId: String,      // 渠道标识 (app, webui, adb, ...)
    val sessionId: String,      // 会话 ID
    val userId: String?,        // 用户 ID (可选)
    val message: String,        // 消息内容
    val timestamp: Long         // 时间戳
)
```

### Channel Adapter 接口

```kotlin
interface ChannelAdapter {
    val channelId: String

    // 接收消息
    suspend fun onMessage(message: ChannelMessage): ChannelResponse

    // 发送响应
    suspend fun sendResponse(response: ChannelResponse)

    // 发送事件 (可选)
    suspend fun sendEvent(event: ChannelEvent)
}
```

---

## 📊 Channel 对比

| Channel | 实时性 | 易用性 | 远程访问 | 状态 |
|---------|--------|--------|----------|------|
| **App UI** | ⭐⭐⭐ | ⭐⭐⭐ | ❌ | ✅ 已实现 |
| **WebUI** | ⭐⭐⭐ | ⭐⭐ | ⚠️ 需端口转发 | ✅ 已实现 |
| **ADB** | ⭐⭐ | ⭐ | ❌ | ✅ 已实现 |
| **WhatsApp** | ⭐⭐ | ⭐⭐⭐ | ✅ | 📅 规划中 |
| **Telegram** | ⭐⭐ | ⭐⭐⭐ | ✅ | 📅 规划中 |
| **HTTP API** | ⭐ | ⭐ | ✅ | 🚧 部分实现 |

---

## 🔧 配置 Channel

### ChannelManager

**文件**: `app/src/main/java/com/agent/mobile/channel/ChannelManager.kt`

**功能**:
- 管理所有 Channel 连接
- 提供设备状态信息
- 记录 inbound/outbound 统计

**使用**:
```kotlin
val channelManager = ChannelManager(context)

// 获取设备状态
val status = channelManager.getCurrentAccount()

// 记录消息
channelManager.recordInbound()  // 接收消息
channelManager.recordOutbound() // 发送消息
```

---

## 🌐 Gateway 集成

所有 Channel 通过 Gateway 统一管理:

```
┌──────────────────────────────────────┐
│         Gateway Server               │
├──────────────────────────────────────┤
│                                      │
│  Channel Adapters:                   │
│  ├── AppUIAdapter                    │
│  ├── WebUIAdapter (WebSocket)        │
│  ├── ADBAdapter (BroadcastReceiver)  │
│  ├── WhatsAppAdapter (future)        │
│  └── TelegramAdapter (future)        │
│                                      │
│  ↓ 统一路由到                         │
│                                      │
│  Session Manager                     │
│  └── Agent Runtime                   │
└──────────────────────────────────────┘
```

---

## 📚 相关文档

- [Gateway Overview](../gateway/overview.md)
- [WebSocket API](../gateway/websocket.md)
- [Testing Guide](../debug/testing.md)

---

**Last Updated**: 2026-03-06
**Active Channels**: 3
**Planned Channels**: 3
