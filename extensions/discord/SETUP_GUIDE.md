# Discord Extension 配置指南

## 📋 前置准备

### 1. 创建 Discord Bot

1. 访问 [Discord Developer Portal](https://discord.com/developers/applications)
2. 点击 "New Application" 创建新应用
3. 在左侧菜单选择 "Bot"
4. 点击 "Add Bot" 创建 Bot
5. 点击 "Reset Token" 并复制 Bot Token（⚠️ 保密！）

### 2. 配置 Bot Intents

在 Bot 页面，启用以下 **Privileged Gateway Intents**:

- ✅ **PRESENCE INTENT**
- ✅ **SERVER MEMBERS INTENT**
- ✅ **MESSAGE CONTENT INTENT** (特权 Intent，必须启用)

### 3. 配置 Bot Permissions

在 "Bot" 页面设置权限：

**最小权限**:
- ✅ Read Messages/View Channels
- ✅ Send Messages
- ✅ Send Messages in Threads
- ✅ Add Reactions
- ✅ Read Message History

**推荐权限**（完整功能）:
- ✅ 上述所有权限
- ✅ Embed Links
- ✅ Attach Files
- ✅ Use External Emojis

### 4. 邀请 Bot 到服务器

1. 在 "OAuth2" → "URL Generator" 页面
2. 选择 **Scopes**: `bot`
3. 选择 **Bot Permissions**: 选择上面配置的权限
4. 复制生成的 URL 并在浏览器中打开
5. 选择要添加 Bot 的服务器并授权

---

## ⚙️ 配置文件

### 配置文件位置

```
/sdcard/AndroidForClaw/config/openclaw.json
```

### 最小配置（测试用）

```json
{
  "gateway": {
    "discord": {
      "enabled": true,
      "token": "YOUR_BOT_TOKEN_HERE"
    }
  }
}
```

### 完整配置示例

```json
{
  "gateway": {
    "discord": {
      "enabled": true,
      "token": "YOUR_BOT_TOKEN_HERE",
      "name": "AndroidForClaw Bot",

      "dm": {
        "policy": "pairing",
        "allowFrom": [
          "USER_ID_1",
          "USER_ID_2"
        ]
      },

      "groupPolicy": "allowlist",

      "guilds": {
        "YOUR_GUILD_ID": {
          "channels": [
            "CHANNEL_ID_1",
            "CHANNEL_ID_2"
          ],
          "requireMention": true,
          "toolPolicy": "default"
        }
      },

      "replyToMode": "off"
    }
  }
}
```

---

## 🔧 配置说明

### DM (私聊) 策略 (`dm.policy`)

| 策略 | 说明 |
|------|------|
| `open` | 接受所有用户的 DM（⚠️ 不推荐） |
| `pairing` | 需要管理员审批配对（**推荐**） |
| `allowlist` | 仅允许白名单用户 |
| `denylist` | 拒绝黑名单用户 |

### Guild (服务器) 策略 (`groupPolicy`)

| 策略 | 说明 |
|------|------|
| `open` | 接受所有频道消息（需 @提及） |
| `allowlist` | 仅允许配置的 Guild/Channel（**推荐**） |
| `denylist` | 拒绝配置的 Guild/Channel |

### Guild 配置 (`guilds.<GUILD_ID>`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `channels` | `string[]` | 允许的 Channel ID 列表 |
| `requireMention` | `boolean` | 是否需要 @Bot 才响应（默认 `true`） |
| `toolPolicy` | `string` | 工具权限策略：`default`、`restricted`、`full` |

### 回复模式 (`replyToMode`)

| 模式 | 说明 |
|------|------|
| `off` | 不使用回复（**默认**） |
| `always` | 总是使用回复 |
| `threads` | 在线程中使用回复 |

---

## 🆔 获取 ID

### 获取用户 ID

1. 开启 Discord 开发者模式: 设置 → 高级 → 开发者模式
2. 右键点击用户 → 复制 ID

### 获取 Guild ID (服务器 ID)

1. 右键点击服务器图标 → 复制 ID

### 获取 Channel ID (频道 ID)

1. 右键点击频道名称 → 复制 ID

---

## 🚀 启动测试

### 1. 推送配置文件

```bash
adb push openclaw.json /sdcard/AndroidForClaw/config/openclaw.json
```

### 2. 启动应用

打开 AndroidForClaw 应用，查看 Logcat：

```bash
adb logcat | grep -E "Discord|MyApplication"
```

### 3. 成功日志

```
========================================
🤖 检查 Discord Channel 配置...
========================================
✅ Discord Channel 已启用，准备启动...
   Name: AndroidForClaw Bot
   DM Policy: pairing
   Group Policy: allowlist
   Reply Mode: off
========================================
✅ Discord Channel 启动成功!
   Bot: YourBotName (123456789)
   现在可以接收 Discord 消息了
========================================
```

### 4. 测试消息

- **DM 测试**: 直接给 Bot 发私信
- **Guild 测试**: 在配置的频道中 @Bot 并发送消息

---

## 🐛 故障排除

### Bot Token 无效

```
❌ Discord Channel 启动失败
   错误: 401 Unauthorized
```

**解决**:
- 检查 Token 是否正确
- 确保没有多余的空格或换行
- 重新生成 Token

### MESSAGE_CONTENT Intent 未启用

Bot 可以连接但无法接收消息内容。

**解决**:
1. 访问 Discord Developer Portal
2. 进入 Bot 页面
3. 启用 "MESSAGE CONTENT INTENT"
4. 重启应用

### Bot 不响应 @提及

**检查**:
- `requireMention` 是否设置为 `true`
- 确保正确 @Bot（带紫色高亮）
- 检查 Bot 是否有权限读取消息

### 无法接收 DM

**检查**:
- `dm.policy` 设置
- 如果是 `pairing` 或 `allowlist`，确保用户 ID 在 `allowFrom` 中

---

## 📚 配置示例

### 示例 1: 开放测试配置

```json
{
  "gateway": {
    "discord": {
      "enabled": true,
      "token": "YOUR_BOT_TOKEN",
      "dm": {
        "policy": "open"
      },
      "groupPolicy": "open"
    }
  }
}
```

### 示例 2: 安全生产配置

```json
{
  "gateway": {
    "discord": {
      "enabled": true,
      "token": "YOUR_BOT_TOKEN",
      "name": "Production Bot",

      "dm": {
        "policy": "allowlist",
        "allowFrom": [
          "123456789012345678",
          "987654321098765432"
        ]
      },

      "groupPolicy": "allowlist",

      "guilds": {
        "111111111111111111": {
          "channels": ["222222222222222222"],
          "requireMention": true,
          "toolPolicy": "default"
        }
      }
    }
  }
}
```

### 示例 3: 多账户配置

```json
{
  "gateway": {
    "discord": {
      "enabled": true,
      "token": "PRIMARY_BOT_TOKEN",
      "name": "Primary Bot",

      "accounts": {
        "test": {
          "enabled": true,
          "token": "TEST_BOT_TOKEN",
          "name": "Test Bot",
          "dm": {
            "policy": "open"
          }
        }
      }
    }
  }
}
```

---

## 🔒 安全建议

1. **不要提交 Token 到代码仓库**
   - 使用环境变量或配置文件
   - 添加 `openclaw.json` 到 `.gitignore`

2. **使用最小权限原则**
   - 只授予必要的权限
   - 定期审查权限

3. **启用 DM 白名单**
   - 生产环境使用 `allowlist` 或 `pairing`
   - 避免使用 `open` 策略

4. **限制 Guild 访问**
   - 使用 `allowlist` 模式
   - 指定具体的 Channel ID

5. **定期更新 Token**
   - 如果怀疑泄露，立即重置
   - 使用 Discord Portal 的 Token Reset 功能

---

## 📞 支持

如有问题，请查看：
- [Discord API 文档](https://discord.com/developers/docs)
- [项目 Issues](https://github.com/SelectXn00b/AndroidForClaw/issues)
- [COMPLETED.md](./COMPLETED.md) - 实现文档

---

**AndroidForClaw Discord Extension** 🤖✅

配置完成后即可开始使用！
