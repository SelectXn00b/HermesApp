# AndroidForClaw 测试指南

## ADB 消息测试

通过 ADB 广播发送消息给 Agent：

```bash
adb shell am broadcast -a PHONE_FORCLAW_SEND_MESSAGE --es message "打开微信" com.xiaomo.androidforclaw
```

查看日志：
```bash
adb logcat -s MainEntryNew AgentLoop ToolRegistry GatewayServer
```

---

## 自动化测试

运行全部单元测试：
```bash
./gradlew test
```

运行设备测试（需连接设备）：
```bash
./gradlew connectedAndroidTest
```

### 测试分类

| 类型 | 路径 | 说明 |
|------|------|------|
| 单元测试 | `app/src/test/` | ConfigLoader, SkillParser, AgentLoop 等 |
| 设备测试 | `app/src/androidTest/` | UI 测试、E2E 测试、权限流程 |

---

## 日志过滤

```bash
# Agent 执行
adb logcat -s MainEntryNew AgentLoop

# Gateway
adb logcat -s GatewayServer GatewayController

# 工具调用
adb logcat -s ToolRegistry

# 会话管理
adb logcat -s SessionManager

# 渠道消息
adb logcat -s FeishuChannel DiscordChannel
```

---

## 常见问题

### Agent 不回复
```bash
# 检查 MainEntryNew 是否初始化
adb logcat -d | grep MainEntryNew

# 检查模型配置
adb shell cat /sdcard/.androidforclaw/config/hermes.json | head -20
```

### 工具调用失败
```bash
# 检查权限
adb logcat -d | grep "Permission\|Accessibility"

# 检查工具执行
adb logcat -d | grep "ToolRegistry.*execute"
```

### Gateway 连接问题
```bash
# 检查 WebSocket 状态
adb logcat -d | grep "GatewayWebSocket\|operator.*connect"
```

---

**更新日期**: 2026-03-22
