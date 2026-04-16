# 📱 Hermes for Android

[![Release](https://img.shields.io/badge/Release-v1.1.17-blue.svg)](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **Let AI truly control your Android phone.**

Architecture aligned with [Hermes](https://github.com/openclaw/openclaw) framework, bringing full AI Agent capabilities to your phone — see the screen, tap apps, run code, connect platforms.

**[📖 Docs (Chinese)](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** · **[🚀 Quick Start](#-quick-start)** · **[💬 Community](#-community)** · **[中文文档](README.md)**

---

## 🔥 What Can AI Do for You

### 📱 Control Any App

WeChat, Alipay, TikTok, Taobao, Maps… **Anything you can do manually, AI can do too.**

```
You: Open WeChat and send "See you tomorrow" to John
AI:  → Open WeChat → Search John → Type message → Send ✅
```

### 🔗 Cross-App Workflows

```
You: Got an address in WeChat, navigate me there
AI:  → Copy address from WeChat → Open Maps → Search → Start navigation
```

### 🐧 Run Code

Python, Node.js, Shell — run directly on your phone:

```
You: Use Python to analyze the CSV in my Downloads folder
AI:  → exec("python3 analyze.py") → Return analysis results
```

### 🌐 Web Search & Fetch

```
You: Search for today's tech news
AI:  → web_search("tech news") → Return titles + links + summaries
```

### 💬 Multi-Platform Messaging

Control your phone AI remotely via Feishu, Discord, Telegram, Slack and more:

| Channel | Status |
|---------|--------|
| Feishu | ✅ Available |
| Discord | ✅ Available |
| Telegram | 🔧 Ready (config aligned with Hermes) |
| Slack | 🔧 Ready (Socket / HTTP dual mode) |
| Signal | 🔧 Ready (signal-cli integration) |
| WhatsApp | 🔧 Ready |

Each channel supports **per-channel model override** — pick a dedicated model from your configured providers.

### 🤖 MCP Server (For External Agents)

Built-in MCP Server (port 8399) exposes the phone's accessibility and screenshot capabilities to external agents via the standard MCP protocol:

```
Tools: get_view_tree / screenshot / tap / swipe / input_text / press_home / press_back / get_current_app
```

> This is NOT used by Hermes itself — it's for external agents like Claude Desktop, Cursor, etc.

### 🧩 Skill Extensions

Search and install new capabilities from [ClawHub](https://clawhub.com), or create your own Skills:

```
You: What skills are available on ClawHub?
AI:  → skills_search("") → Show available skill list
```

---

## ⚡ Quick Start

### Download & Install

Download from the [Release page](https://github.com/SelectXn00b/AndroidForClaw/releases/latest):

| APK | Description | Required? |
|-----|-------------|-----------|
| **Hermes** | Main app (Accessibility Service, Agent, Gateway) | ✅ Required |
| **BrowserForClaw** | AI Browser (web automation) | Optional |
| **termux-app + termux-api** | Terminal (run Python/Node.js) | Optional |

### 3 Steps to Get Started

1. **Install** — Download and install Hermes
2. **Configure** — Open the app, enter an API Key (or skip to use built-in Key), enable Accessibility + Screen Capture permissions
3. **Chat** — Talk directly in the app, or send messages via Feishu/Discord

> 💡 First launch opens a setup wizard automatically. Default: OpenRouter + MiMo V2 Pro. One-click skip supported.

### Termux Setup (Optional)

With Termux installed, AI can run Python/Node.js/Shell. Built-in one-click setup wizard:

**Settings → Termux Config → Copy command → Paste into Termux → Done**

---

## 🏗️ Architecture

```
324 source files · 62,000+ lines of code · 10 modules
```

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  Feishu · Discord · Telegram · Slack ·    │
│  Signal · WhatsApp · In-app chat          │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  AgentLoop · 20 Tools · 20 Skills ·       │
│  Context Management (4-layer) · Memory    │
├──────────────────────────────────────────┤
│  Providers                                │
│  OpenRouter · MiMo · Gemini · Anthropic · │
│  OpenAI · Custom                          │
├──────────────────────────────────────────┤
│  Android Platform                         │
│  Accessibility · Termux SSH · device tool │
│  MediaProjection · BrowserForClaw         │
└──────────────────────────────────────────┘
```

### Core Features

| Feature | Description |
|---------|-------------|
| **Playwright Mode** | Screen ops aligned with Playwright — `snapshot` gets UI tree + ref → `act` operates elements |
| **Unified exec** | Auto-routes to Termux (SSH) or built-in Shell, transparent to the model |
| **Context Management** | 4-layer protection aligned with Hermes: limitHistoryTurns + tool result trimming + budget guard |
| **Model Smart Routing** | Model ID normalization + Fallback Chain (multi-candidate degradation) + API Key rotation + Allowlist/Blocklist |
| **Session Maintenance** | 30-day auto-cleanup, 500 entry cap, 10MB rotation, disk budget auto-reclaim |
| **Skill System** | 20 built-in Skills editable on device, ClawHub online installation |
| **Multi-model** | MiMo V2 Pro · DeepSeek R1 · Claude Sonnet 4 · Gemini 2.5 · GPT-4.1 |
| **MCP Server** | Expose accessibility/screenshot to external agents (port 8399, Streamable HTTP) |
| **Per-channel Model** | Each messaging channel can independently select a model, fields aligned with Hermes types |
| **Steer Injection** | Inject messages into a running Agent Loop mid-run via Channel (mid-run steering) |

---

## 📋 Full Capability Table

### 🔧 20 Tools

| Tool | Function | Alignment |
|------|----------|-----------|
| `device` | Screen ops: snapshot/tap/type/scroll/press/open | Playwright |
| `read_file` | Read file contents | Hermes |
| `write_file` | Create or overwrite files | Hermes |
| `edit_file` | Precise file editing | Hermes |
| `list_dir` | List directory contents | Hermes |
| `exec` | Execute commands (Termux SSH / built-in Shell) | Hermes |
| `web_search` | Brave search engine | Hermes |
| `web_fetch` | Fetch web page content | Hermes |
| `javascript` | Execute JavaScript (QuickJS) | Hermes |
| `tts` | Text-to-speech (device TTS engine) | Hermes |
| `skills_search` | Search ClawHub skills | Hermes |
| `skills_install` | Install skills from ClawHub | Hermes |
| `memory_search` | Semantic memory search | Hermes |
| `memory_get` | Read memory snippets | Hermes |
| `config_get` | Read config entries | Hermes |
| `config_set` | Write config entries | Hermes |
| `list_installed_apps` | List installed apps | Android-specific |
| `install_app` | Install APK | Android-specific |
| `start_activity` | Launch Activity | Android-specific |
| `stop` | Stop the Agent | Android-specific |

### 🧩 20 Skills

| Category | Skills |
|----------|--------|
| Feishu Suite | `feishu` · `feishu-doc` · `feishu-wiki` · `feishu-drive` · `feishu-bitable` · `feishu-chat` · `feishu-task` · `feishu-perm` · `feishu-urgent` |
| Search & Web | `browser` · `weather` |
| Skill Management | `clawhub` · `skill-creator` |
| Dev & Debug | `debugging` · `data-processing` · `session-logs` |
| Config Management | `model-config` · `channel-config` · `install-app` · `model-usage` |

> Skills are stored at `/sdcard/.hermes/skills/` — freely editable, addable, and removable.

### 💬 Messaging Channels

| Channel | Status | Features |
|---------|--------|----------|
| **Feishu** | ✅ Available | WebSocket real-time, group/DM, 32 Feishu tools |
| **Discord** | ✅ Available | Gateway connection, group/DM |
| **Telegram** | 🔧 Ready | Bot API polling/webhook, model override, streaming |
| **Slack** | 🔧 Ready | Socket Mode / HTTP Mode, model override, streaming |
| **Signal** | 🔧 Ready | signal-cli daemon integration, model override |
| **WhatsApp** | 🔧 Ready | WhatsApp Business API, model override |
| **In-app Chat** | ✅ Available | Built-in chat UI |

> All channel config fields are aligned with Hermes TypeScript type definitions (`types.slack.ts`, `types.telegram.ts`, etc.).

### 🤖 Supported Models

| Provider | Models | Notes |
|----------|--------|-------|
| **OpenRouter** | MiMo V2 Pro, Hunter Alpha, DeepSeek R1, Claude Sonnet 4, GPT-4.1 | Recommended, built-in Key |
| **Xiaomi MiMo** | MiMo V2 Pro, MiMo V2 Flash, MiMo V2 Omni | Direct Xiaomi API |
| **Google** | Gemini 2.5 Pro, Gemini 2.5 Flash | Direct |
| **Anthropic** | Claude Sonnet 4, Claude Opus 4 | Direct |
| **OpenAI** | GPT-4.1, GPT-4.1 Mini, o3 | Direct |
| **Custom** | Any OpenAI-compatible API | Ollama, vLLM, etc. |

> **Default**: OpenRouter + MiMo V2 Pro (1M context + reasoning). Skip the wizard to auto-use built-in Key.

---

## 🛠️ Configuration

`/sdcard/.hermes/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-your-key",
        "models": [{"id": "xiaomi/mimo-v2-pro", "reasoning": true, "contextWindow": 1048576}]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": { "primary": "openrouter/xiaomi/mimo-v2-pro" }
    }
  },
  "channels": {
    "feishu": { "enabled": true, "appId": "cli_xxx", "appSecret": "xxx" },
    "slack": {
      "enabled": true,
      "botToken": "xoxb-...",
      "appToken": "xapp-...",
      "mode": "socket",
      "streaming": "partial",
      "model": "openrouter/xiaomi/mimo-v2-pro"
    },
    "telegram": {
      "enabled": true,
      "botToken": "123456:ABC-...",
      "streaming": "partial"
    }
  }
}
```

Each channel supports **per-channel model override** — pick a specific model from your configured providers, or leave empty to use the global default.

See **[📖 Feishu Docs](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** for detailed configuration reference.

---

## 🔨 Build from Source

```bash
git clone https://github.com/SelectXn00b/AndroidForClaw.git
cd Hermes
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
adb install releases/Hermes-v1.3.0-release.apk
```

---

## 🔗 Related Projects

| Project | Description |
|---------|-------------|
| [Hermes](https://github.com/openclaw/openclaw) | AI Agent framework (Desktop) |
| [iOSForClaw](https://github.com/SelectXn00b/iOSForClaw) | Hermes iOS client |
| [Hermes](https://github.com/SelectXn00b/AndroidForClaw) | Hermes Android client (this project) |

---

## 📞 Community

<div align="center">

#### Feishu Group

[![Join Feishu Group](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[Click to join Feishu Group](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/k9NKrXUN)

**[Join Discord](https://discord.gg/k9NKrXUN)**

---

#### WeChat Group

<img src="docs/images/wechat-qrcode.png" width="300" alt="WeChat Group QR Code">

**Scan to join WeChat group** — Valid for 7 days

</div>

---

## 🔗 Links

- [Hermes](https://github.com/openclaw/openclaw) — Architecture reference
- [ClawHub](https://clawhub.com) — Skill marketplace
- [Architecture Doc](ARCHITECTURE.md) — Detailed design

---

## 📄 License

MIT — [LICENSE](LICENSE)

## 🙏 Acknowledgments

- **[Hermes](https://github.com/openclaw/openclaw)** — Architecture inspiration
- **[Claude](https://www.anthropic.com/claude)** — AI reasoning capabilities

---

<div align="center">

⭐ **If this project helps you, please give it a Star!** ⭐

</div>
