# 📱 AndroidForClaw

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/badge/Release-v1.0.2-blue.svg)](https://github.com/xiaomochn/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com/)

**让 AI 真正掌控 Android 手机的智能体运行时。**

底层架构对齐 [OpenClaw](https://github.com/openclaw/openclaw)，在手机上实现完整的 AI Agent 能力。

**[📖 文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[🚀 快速开始](#-快速开始)** | **[💬 社区](#-社区)**

---

## 📥 下载安装

| APK | 说明 |
|-----|------|
| **[AndroidForClaw](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | 主应用（必装，含无障碍服务） |
| **[Termux](https://f-droid.org/packages/com.termux/)** | 终端环境（可选，用于执行代码） |

> 首次启动弹出引导页，输入 [OpenRouter API Key](https://openrouter.ai/keys)（免费）即可使用。

---

## ⚡ 快速开始

### 3 分钟上手

1. **安装** — 下载 APK，安装
2. **配置模型** — 首次启动输入 API Key（或跳过使用内置 Key）
3. **授权** — 开启无障碍服务 + 录屏权限
4. **对话** — 在 App 里直接对话，或通过飞书/Discord 发消息

### Termux 配置（可选）

装了 Termux 后，AI 可以执行 Python、Node.js、Shell 命令。

1. 安装 [Termux](https://f-droid.org/packages/com.termux/)
2. 打开 App → 设置 → **Termux 配置**
3. 复制一行命令，粘贴到 Termux 执行
4. 自动完成配置 ✅

---

## ✨ 核心能力

### 📱 屏幕操作（对齐 Playwright）

```
device(action="snapshot")                    → 获取 UI 树 + 元素 ref
device(action="act", kind="tap", ref="e5")   → 点击元素
device(action="act", kind="type", ref="e5", text="hello") → 输入文字
device(action="screenshot")                  → 截图
device(action="open", package_name="...")     → 打开 App
```

任何 App 都能操控：微信、支付宝、抖音、淘宝、高德……

### 🔧 19 个内置 Tools

| 类别 | Tools |
|------|-------|
| 文件 | `read_file`, `write_file`, `edit_file`, `list_dir` |
| 网络 | `web_search` (Brave), `web_fetch` |
| 执行 | `exec` (Termux SSH), `javascript` |
| 设备 | `device` (统一屏幕操作) |
| 技能 | `skills_search`, `skills_install` (ClawHub) |
| 记忆 | `memory_search`, `memory_get` |
| 配置 | `config_get`, `config_set` |

### 🧩 20 个 Skills

可在 `/sdcard/.androidforclaw/skills/` 自由编辑：

飞书全家桶（doc/wiki/drive/bitable/chat/task/perm/urgent）、browser、weather、clawhub、skill-creator、model-config、session-logs、debugging、data-processing、channel-config、install-app

### 💬 6 个消息渠道

飞书 ✅ | Discord ✅ | Telegram | Slack | Signal | WhatsApp

### 🤖 多模型支持

OpenRouter、Azure OpenAI、Anthropic、OpenAI、自定义兼容 API

---

## 🏗️ 架构

```
┌─────────────────────────────────────┐
│  Channels (飞书/Discord/Telegram...)  │
├─────────────────────────────────────┤
│  Agent Runtime (Loop/Tools/Skills)   │
├─────────────────────────────────────┤
│  Android (Accessibility/Termux/UI)   │
└─────────────────────────────────────┘
```

### 目录结构

```
/sdcard/.androidforclaw/           ← 对齐 ~/.openclaw/
├── openclaw.json                  ← 配置文件
├── skills/                        ← 可编辑 Skills
├── workspace/                     ← 工作区 (SOUL.md, memory/, sessions/)
└── .ssh/                          ← SSH 密钥 (Termux 通信)
```

---

## 🛠️ 配置

`/sdcard/.androidforclaw/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-你的key",
        "models": [{"id": "openrouter/hunter-alpha", "reasoning": true}]
      }
    }
  },
  "channels": {
    "feishu": {
      "enabled": true,
      "appId": "cli_xxx",
      "appSecret": "xxx"
    }
  }
}
```

---

## 🔨 从源码构建

```bash
git clone https://github.com/xiaomochn/AndroidForClaw.git
cd AndroidForClaw
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
adb install releases/AndroidForClaw-v1.0.2-release.apk
```

### 运行测试

```bash
# 单元测试
./gradlew :app:testDebugUnitTest

# 设备端 UI 测试
./gradlew :app:assembleDebugAndroidTest
adb shell am instrument -w com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner

# E2E 测试
node tests/e2e/chat-e2e.mjs
node tests/e2e/skill-hub-e2e.mjs
```

---

## 📞 社区

- **飞书群**: [点击加入](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)
- **Discord**: [加入服务器](https://discord.gg/k9NKrXUN)

---

## 📄 License

MIT — 详见 [LICENSE](LICENSE)

## 🙏 致谢

- **[OpenClaw](https://github.com/openclaw/openclaw)** — 架构参照
- 源码文件映射：[MAPPING.md](MAPPING.md)

---

⭐ **觉得有用？给个 Star 支持一下开源！**
