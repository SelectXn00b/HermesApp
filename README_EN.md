# 📱 AndroidForClaw — OpenClaw for Android, Now Available

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com/)

**[中文文档](README.md)** | **[📖 Feishu Docs](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[🚀 Quick Start](#-quick-start)** | **[🤝 Contributing](#-contributing)**

---

## 📥 Download

### Latest Release: [v1.0.0](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)

| APK | Description | Size |
|-----|-------------|------|
| **[AndroidForClaw](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)** | Main app (required) | ~22MB |
| **[S4Claw](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)** | Accessibility + Screenshot (required) | ~4.4MB |
| **[BrowserForClaw](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)** | AI Browser (optional) | ~8.4MB |
| **[Termux](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)** | Terminal environment (optional) | ~32MB |
| **[Termux-API](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)** | Terminal API (optional) | ~6.8MB |

> 💡 First launch shows a setup wizard — just enter an [OpenRouter API Key](https://openrouter.ai/keys) (free to register) and you're good to go!

---

## 📖 Documentation

**Detailed feature demos and tutorials (Chinese):**

👉 **[https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)**

Includes:
- 🎬 **Feature demo videos** — Watch AI control a real phone
- 📋 **Setup tutorials** — Feishu / Discord / model configuration
- 💡 **Tips & tricks** — Best practices and FAQ

---

## 🌟 Why AndroidForClaw?

**[OpenClaw](https://github.com/openclaw/openclaw)** (280k+ GitHub stars) is an amazing AI assistant framework — but it's designed for desktops. Your phone is just a "remote node."

**AndroidForClaw** brings OpenClaw natively to Android. Same architecture (98% compatible), but the AI lives directly on your phone with full device control via Accessibility Service.

No computer needed. No Gateway. No pairing. Install, authorize, go.

---

## ✨ Core Capabilities

### OpenClaw-Compatible Foundation

| Capability | Description |
|-----------|-------------|
| 🧠 Agent Loop | Observe → Think → Act → Verify cycle |
| 🔧 Tool Calling | Standard function calls, fully compatible |
| 📝 Bootstrap | IDENTITY / AGENTS / SOUL / TOOLS configuration |
| 🧩 Skills | Five-tier loading + ClawHub skill marketplace |
| 💬 Channels | Feishu / Discord / In-app chat |
| 🤖 Multi-model | Claude / GPT / Gemini / DeepSeek |
| 🌐 Browser | Navigation, clicks, forms, screenshots, JS |
| 📁 File System | Read/write files, directory browsing, shell |

### Android-Exclusive Superpowers 🔥

| Capability | Description |
|-----------|-------------|
| 👁️ Screen Vision | Real-time screenshots + UI tree parsing |
| ✋ Touch Control | Tap, swipe, long-press, text input |
| 📱 App Control | Launch any app, Activity navigation |
| 🔗 Cross-app | Chain multiple apps for complex workflows |

#### Control Any App

Via Accessibility Service — anything you can do manually, AI can do too:

| App | AI Can... |
|-----|-----------|
| WeChat | Send messages, read Moments |
| Alipay | Check bills, scan to pay |
| TikTok | Search videos, browse content |
| Taobao | Search products, compare prices |
| Maps | Search routes, start navigation |

**Cross-app example:** "Got an address in WeChat, navigate me there"
→ Open WeChat → Extract address → Copy → Open Maps → Paste → Navigate

---

## ⚡ Quick Start

### Option 1: Download APK (Recommended)

1. **Download** from [Release page](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)
2. **Install** AndroidForClaw + S4Claw (required), BrowserForClaw (optional)
3. **Configure** — First launch shows setup wizard, enter [OpenRouter API Key](https://openrouter.ai/keys)
4. **Authorize** — Open S4Claw, enable Accessibility Service + Screen Capture
5. **Go** — Chat via Feishu/Discord, or directly in the app

### Option 2: Build from Source

```bash
git clone https://github.com/SelectXn00b/AndroidForClaw.git
cd AndroidForClaw

./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk

./gradlew :extensions:observer:assembleRelease
adb install extensions/observer/build/outputs/apk/release/observer-release.apk
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│      Gateway                         │  Feishu / Discord / HTTP
├─────────────────────────────────────┤
│      Agent Runtime                   │  AgentLoop / Skills / Tools
├─────────────────────────────────────┤
│      Android Platform                │  Accessibility / ADB / UI
└─────────────────────────────────────┘
```

### Data Directory

```
/sdcard/.androidforclaw/              ← Mirrors OpenClaw's ~/.openclaw/
├── openclaw.json                     ← Main config
├── workspace/                        ← User workspace
│   ├── skills/                       ← Custom skills
│   ├── memory/                       ← Persistent memory
│   └── sessions/                     ← Session history
├── skills/                           ← Managed skills
└── logs/                             ← Logs
```

---

## 🛠️ Configuration

Config file: `/sdcard/.androidforclaw/openclaw.json`

Auto-generated on first launch. Only an API key is required:

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-your-key"
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
      "appId": "your-feishu-app-id",
      "appSecret": "your-feishu-app-secret"
    }
  }
}
```

---

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📞 Community

- **[Feishu Group](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**
- **[Discord](https://discord.gg/k9NKrXUN)**
- **[GitHub Issues](https://github.com/SelectXn00b/AndroidForClaw/issues)**

---

## 📄 License

MIT License - See [LICENSE](LICENSE)

## 🙏 Acknowledgments

- **[OpenClaw](https://github.com/openclaw/openclaw)** - Architecture inspiration
- **[Claude](https://www.anthropic.com/claude)** - AI reasoning

---

⭐ **Like it? Give us a Star!** ⭐

**AndroidForClaw** - Giving AI the ability to use your phone 🦞📱
