# 📱 AndroidForClaw — OpenClaw 的手机版，来了

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](https://github.com/xiaomochn/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com/)

**[English](README_EN.md)** | **[📖 飞书文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[🚀 快速开始](#-快速开始)** | **[🤝 参与贡献](#-参与贡献)**

---

## 📥 下载安装

### 最新版本: [v1.0.0](https://github.com/xiaomochn/AndroidForClaw/releases/latest)

| APK | 说明 | 大小 |
|-----|------|------|
| **[AndroidForClaw](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | 主应用 (必装) | ~22MB |
| **[S4Claw](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | 无障碍服务+截图 (必装) | ~4.4MB |
| **[BrowserForClaw](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | AI 浏览器 (可选) | ~8.4MB |
| **[Termux](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | 终端环境 (可选) | ~32MB |
| **[Termux-API](https://github.com/xiaomochn/AndroidForClaw/releases/latest)** | 终端 API (可选) | ~6.8MB |

> 💡 首次启动会弹出引导页，只需输入一个 [OpenRouter API Key](https://openrouter.ai/keys)（注册免费）即可开始使用！

---

## 📖 飞书文档

**详细的功能演示和使用教程：**

👉 **[https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)**

文档包含：
- 🎬 **功能演示视频** — 看 AI 实际控制手机
- 📋 **详细配置教程** — 飞书/Discord/模型配置步骤
- 💡 **使用技巧** — 最佳实践和常见问题

---

## 🌟 从 OpenClaw 说起

你可能已经知道 **[OpenClaw](https://github.com/openclaw/openclaw)** — GitHub 280k+ Star 的开源 AI 助手框架。它让 AI 连接 20+ 消息渠道、控制浏览器、执行代码、管理日程。

**但 OpenClaw 有一个天然的局限：它是为桌面设计的。**

手机需要连电脑上的 Gateway 才能工作，AI 看不到手机屏幕，碰不到微信、抖音、淘宝。

**所以我们做了 AndroidForClaw。**

---

## 🎯 AndroidForClaw 是什么？

**一句话：** OpenClaw 的 Android 原生版本。

底层架构与 OpenClaw 98% 一致 — 同样的 Tool Calling 协议、Agent Loop、Bootstrap 配置、Skill 扩展。

**不同的是：** AI 直接住在你的手机里，通过 Accessibility Service 获得完整的设备控制能力。

不需要电脑，不需要 Gateway，不需要配对。装上 APK，授权，开始用。

---

## ✨ 核心能力

### 与 OpenClaw 一致的底层

| 能力 | 说明 |
|------|------|
| 🧠 Agent Loop | 观察 → 思考 → 行动 → 验证 循环 |
| 🔧 Tool Calling | 标准函数调用，与 OpenClaw 完全兼容 |
| 📝 Bootstrap | IDENTITY / AGENTS / SOUL / TOOLS 配置体系 |
| 🧩 Skill 扩展 | 五层优先级加载 + ClawHub 技能市场 |
| 💬 多渠道 | 飞书 / Discord / 设备内对话 |
| 🤖 多模型 | Claude / GPT / Gemini / DeepSeek 等 |
| 🌐 浏览器 | 导航、点击、填表、截图、JS 执行 |
| 📁 文件系统 | 读写文件、目录浏览、Shell 命令 |

### Android 独有的超能力 🔥

| 能力 | 说明 |
|------|------|
| 👁️ 屏幕感知 | 实时截图 + UI 树解析，AI 能「看懂」屏幕 |
| ✋ 触控操作 | 点击、滑动、长按、文本输入 |
| 📱 应用控制 | 启动任意应用、Activity 跳转、系统导航 |
| 🔗 跨应用 | 串联多个 App 完成复杂任务 |

#### 🔥 任意 App 操控

通过 Accessibility Service，任何你能手动操作的应用，AI 都能操作：

| App | AI 能做什么 |
|-----|-------------|
| 📱 微信 | 发消息、看朋友圈、转账 |
| 📱 支付宝 | 查账单、扫码付款 |
| 📱 抖音 | 搜索视频、浏览内容 |
| 📱 淘宝 | 搜商品、比价格 |
| 📱 高德 | 搜路线、开导航 |

**跨应用示例：**「微信收到一个地址，帮我导航过去」
→ 打开微信 → 识别地址 → 复制 → 打开高德 → 粘贴搜索 → 开始导航

---

## ⚡ 快速开始

### 方法 1: 下载 APK（推荐）

1. **下载** — 从 [Release 页面](https://github.com/xiaomochn/AndroidForClaw/releases/latest) 下载 APK
2. **安装** — 安装 AndroidForClaw + S4Claw（必装），BrowserForClaw（可选）
3. **配置** — 首次启动自动弹出引导页，输入 [OpenRouter API Key](https://openrouter.ai/keys) 即可
4. **授权** — 打开 S4Claw，启用无障碍服务 + 录屏权限
5. **开始** — 在飞书/Discord 发消息，或直接在 app 里对话

### 方法 2: 从源码构建

```bash
git clone https://github.com/xiaomochn/AndroidForClaw.git
cd AndroidForClaw

# 构建主应用
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk

# 构建 S4Claw
./gradlew :extensions:observer:assembleRelease
adb install extensions/observer/build/outputs/apk/release/observer-release.apk
```

---

## 🏗️ 架构设计

```
┌─────────────────────────────────────┐
│      Gateway                         │  飞书 / Discord / HTTP
├─────────────────────────────────────┤
│      Agent Runtime                   │  AgentLoop / Skills / Tools
├─────────────────────────────────────┤
│      Android Platform                │  Accessibility / ADB / UI
└─────────────────────────────────────┘
```

### 项目结构

```
AndroidForClaw/
├── app/                          # 主应用
│   ├── src/main/java/
│   │   ├── agent/               # Agent 运行时 (AgentLoop, Tools, Skills)
│   │   ├── gateway/             # Gateway 服务器
│   │   ├── providers/           # LLM 提供商
│   │   └── ui/                  # 用户界面
│   └── src/main/assets/skills/  # 内置 Skills
├── extensions/
│   ├── observer/                # S4Claw (无障碍服务)
│   ├── feishu/                  # 飞书渠道
│   ├── discord/                 # Discord 渠道
│   └── BrowserForClaw/          # AI 浏览器
└── releases/                    # 预编译 APK
```

### 数据目录

```
/sdcard/.androidforclaw/              ← 对齐 OpenClaw 的 ~/.openclaw/
├── openclaw.json                     ← 主配置文件
├── workspace/                        ← 用户工作区
│   ├── skills/                       ← 用户自定义 Skills
│   ├── memory/                       ← 持久化记忆
│   └── sessions/                     ← 会话历史
├── skills/                           ← 托管 Skills
└── logs/                             ← 日志
```

---

## 🛠️ 配置

配置文件：`/sdcard/.androidforclaw/openclaw.json`

首次启动自动生成最小配置，只需填入 API Key。高级配置参考飞书文档。

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-你的key"
      }
    }
  },
  "agents": {
    "defaults": {
      "model": { "primary": "openrouter/hunter-alpha" }
    }
  },
  "channels": {
    "feishu": {
      "enabled": true,
      "appId": "你的飞书appId",
      "appSecret": "你的飞书appSecret"
    }
  }
}
```

---

## 🤝 参与贡献

欢迎贡献！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

- **Bug 报告**: 包含设备型号、Android 版本和日志
- **功能请求**: 描述使用场景和预期行为

---

## 📞 社区

<div align="center">

#### 飞书群

[![加入飞书群](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[点击加入飞书群](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-加入服务器-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/k9NKrXUN)

**[加入 Discord](https://discord.gg/k9NKrXUN)**

---

#### 微信群

<img src="docs/images/wechat-qrcode.png" width="300" alt="微信群二维码">

**扫码加入微信群** - 7天内有效

</div>

---

## 📄 开源协议

MIT License - 详见 [LICENSE](LICENSE)

## 🙏 致谢

- **[OpenClaw](https://github.com/openclaw/openclaw)** - 架构灵感
- **[Claude](https://www.anthropic.com/claude)** - AI 推理能力

---

⭐ **觉得有用？给个 Star 支持一下！** ⭐

**AndroidForClaw** - 赋予 AI 使用手机的能力 🦞📱
