# gateway/ 模块移植 Checklist

## 文件清单（8 个核心 + 20+ 平台）

### 核心模块（8）
- [ ] `run.py` → `Run.kt`
- [ ] `session.py` → `Session.kt`
- [ ] `session_context.py` → `SessionContext.kt`
- [ ] `config.py` → `Config.kt`
- [ ] `delivery.py` → `Delivery.kt`
- [ ] `stream_consumer.py` → `StreamConsumer.kt`
- [ ] `hooks.py` → `Hooks.kt`
- [ ] `channel_directory.py` → `ChannelDirectory.kt`
- [ ] `pairing.py` → `Pairing.kt`
- [ ] `mirror.py` → `Mirror.kt`
- [ ] `restart.py` → `Restart.kt`
- [ ] `status.py` → `Status.kt`

### 平台适配器（platforms/）
- [x] `PlatformAdapter.kt` — 基类 ✅
- [x] `FeishuAdapter.kt` — 飞书 ⚠️ 骨架
- [ ] `TelegramAdapter.kt`
- [ ] `DiscordAdapter.kt`
- [ ] `SlackAdapter.kt`
- [ ] `WhatsAppAdapter.kt`
- [ ] `SignalAdapter.kt`
- [ ] `MatrixAdapter.kt`
- [ ] `WeComAdapter.kt`
- [ ] `WeixinAdapter.kt`
- [ ] `QQBotAdapter.kt`
- [ ] `DingTalkAdapter.kt`
- [ ] `EmailAdapter.kt`
- [ ] `SMSAdapter.kt`
- [ ] `MattermostAdapter.kt`
- [ ] `HomeAssistantAdapter.kt`
- [ ] `WebhookAdapter.kt`
- [ ] `BlueBubblesAdapter.kt`
- [ ] `APIServerAdapter.kt`
