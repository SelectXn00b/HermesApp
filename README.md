# 📱 AndroidForClaw — OpenClaw for Android, Now Available

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org/)

**[中文文档](README_CN.md)** | **[📖 Documentation](docs/README.md)** | **[🚀 Quick Start](#-quick-start)** | **[🤝 Contributing](CONTRIBUTING.md)**

---

## 🌟 From OpenClaw to AndroidForClaw

You may already know **[OpenClaw](https://github.com/openclaw/openclaw)** — the open-source AI assistant framework with 280k+ GitHub stars. It connects AI to 20+ messaging channels, controls browsers, executes code, and manages schedules. It's the hottest personal AI Agent project right now.

**But OpenClaw has a natural limitation:**

It's designed for desktops. Android is just a "remote node" in its architecture.
Your phone needs to connect to a Gateway on your computer to work. The AI can't see your phone screen, can't touch WeChat, TikTok, or Taobao.

**So we built AndroidForClaw.**

## 🎯 What is AndroidForClaw?

**In one sentence:** OpenClaw's native Android version.

**98% identical architecture** — Same Tool Calling protocol, same Agent Loop, same Bootstrap config system, same Skill extension mechanism.

**The difference:** The AI lives directly on your phone, with full device control through Accessibility Service.

No computer needed. No Gateway needed. No pairing needed. Install the APK, grant permissions, start using.

---

## ✨ What We Support

### I. Core Capabilities Aligned with OpenClaw

#### 🧠 Agent Core
- ✅ **Tool Calling Protocol** — Standard function calling, fully compatible with OpenClaw
- ✅ **Agent Loop** — Observe → Think → Act → Verify cycle
- ✅ **Bootstrap Config** — Complete IDENTITY / AGENTS / SOUL / TOOLS configuration system
- ✅ **Memory Persistence** — Long-term memory, retains important information across sessions
- ✅ **Skill Extensions** — Custom skill directory, flexible Agent capability expansion
- ✅ **Multi-Model Support** — Claude / GPT / Gemini and other mainstream LLMs
- ✅ **Exploration Mode** — Dynamic decision-making, AI autonomously explores
- ✅ **Planning Mode** — Plan first, execute later, suitable for fixed workflows

#### 🔧 Universal Tool Chain
- ✅ **File Operations** — read_file / write_file / edit_file
- ✅ **Directory Browsing** — list_dir
- ✅ **Shell Commands** — ls / cat / grep / find / sed / awk, etc.
- ✅ **Web Scraping** — web_fetch
- ✅ **JavaScript Engine** — Built-in QuickJS, ES6+ support

#### 🌐 Browser Automation
- ✅ **Web Navigation** — Open any URL
- ✅ **Element Clicking** — Precise CSS selector targeting
- ✅ **Form Filling** — Auto text input
- ✅ **Page Scrolling** — Up / Down / Top / Bottom
- ✅ **JS Execution** — Execute JavaScript in web pages
- ✅ **Content Extraction** — text / HTML / Markdown
- ✅ **Cookie Management** — Read and set cookies
- ✅ **Screenshot** — Full page screenshot support

#### 💬 Messaging Channels
- ✅ **Feishu (Lark)** — Native Feishu message channel
- ✅ **Discord** — Discord Bot integration with WebSocket Gateway
- ✅ **On-Device Chat** — Built-in chat interface
- 🔜 **More channels** continuously being integrated

---

### II. Android's Superpowers 🔥 (What OpenClaw Can't Do)

#### 👁️ Screen Awareness
- ✅ **Real-time Screenshot** — Capture screen anytime, AI "sees" through vision models
- ✅ **UI Tree Parsing** — Complete View hierarchy, precisely locate every element
- ✅ **State Detection** — AI knows which app, which page you're on

#### ✋ Touch Operations
- ✅ **Tap** — tap(x, y), precisely tap any screen location
- ✅ **Swipe** — Up/down/left/right, customizable duration
- ✅ **Long Press** — Trigger long-press menus
- ✅ **Text Input** — Type in any input field

#### 📱 App Control
- ✅ **Launch Apps** — Open any installed app by package name
- ✅ **Activity Navigation** — Jump directly to specific app pages
- ✅ **App List** — Get all installed applications
- ✅ **System Navigation** — Home / Back / Recent Tasks

#### 🔥 Any App Control (Core Difference!)

Through Accessibility Service, any app you can manually operate, AI can operate:

| App | What AI Can Do |
|-----|----------------|
| 📱 WeChat | Send messages, browse Moments, transfer money |
| 📱 Alipay | Check bills, scan to pay |
| 📱 TikTok | Search videos, browse content |
| 📱 Taobao | Search products, compare prices |
| 📱 Amap | Search routes, start navigation |

#### 🔗 Cross-App Workflows

Chain multiple apps for complex tasks:

**"Got an address in WeChat, navigate me there"**
→ Open WeChat → Recognize address → Copy → Open Amap → Paste & search → Start navigation

#### 🧪 App Testing
- ✅ Functional testing / UI testing / Regression testing / Exploratory testing
- ✅ Auto screenshot for every operation

---

## 💡 One-Sentence Summary

**OpenClaw** lets AI chat, browse web, and write code.
**AndroidForClaw** on the same foundation, lets AI use your phone like you do.

🤖 **98% OpenClaw underneath, covering your entire phone.**

---

## ⚡ Quick Start

### Method 1: Download Pre-built APK (Recommended)

**Download**: [releases/](releases/)

1. **Download APK**
   ```
   app-release.apk                          (Main app, ~31MB)
   observer-release.apk                     (S4Claw: Accessibility & Screenshot, ~4.3MB)
   B4Claw-v1.0.0.apk                        (Browser4Claw: Browser for AI, Optional)
   ```

2. **Install**
   ```bash
   adb install app-release.apk
   adb install observer-release.apk
   adb install B4Claw-v1.0.0.apk  # Optional
   ```

3. **Configure API**
   - Push config to device:
     ```bash
     adb push config/openclaw.json /sdcard/.androidforclaw/config/openclaw.json
     ```
   - Or edit directly on phone: `/sdcard/.androidforclaw/config/openclaw.json`

4. **Grant Permissions**
   - Open **S4Claw** app and enable:
     - ✅ Accessibility Service (Required for device control)
     - ✅ Media Projection (Required for screenshots)
   - Open **Main app** and grant:
     - ✅ Display Over Apps (Required for floating window)

**Get Started**: Send messages in Feishu/Discord to control your phone!

---

### Method 2: Build from Source

1. **Clone**
   ```bash
   git clone https://github.com/xiaomochn/AndroidForClaw.git
   cd AndroidForClaw
   ```

2. **Configure**
   ```bash
   cp config/openclaw.json.example config/openclaw.json
   # Edit config/openclaw.json, fill in your API Keys
   ```

3. **Build & Install**
   ```bash
   # Build main app and S4Claw
   ./gradlew :app:assembleDebug :extensions:observer:assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb install extensions/observer/build/outputs/apk/debug/observer-debug.apk

   # Optional: Build B4Claw browser
   cd extensions/BrowserForClaw/android-project
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-universal-debug.apk
   ```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│      Gateway (Planned)              │  Multi-channel, Sessions, Security
├─────────────────────────────────────┤
│      Agent Runtime (Core)           │  AgentLoop, Skills, Tools
├─────────────────────────────────────┤
│      Android Platform               │  Accessibility, ADB, UI
└─────────────────────────────────────┘
```

### Key Components

- **Agent Loop**: Core execution loop (LLM → Tools → Observation)
- **Skills System**: Markdown-based knowledge (like OpenClaw)
- **Tool Registry**: Android-specific tools (screenshot, tap, swipe, etc.)
- **Gateway**: Multi-channel access (Feishu, Discord, HTTP API)
- **Session Manager**: Conversation history and context management

---

## 📦 Tech Stack

- **Language**: Kotlin + Java
- **Architecture**: MVVM + Repository Pattern
- **LLM API**: OpenAI-compatible (Claude Opus 4.6)
- **Android Services**: Accessibility Service, MediaProjection
- **Storage**: MMKV (configuration and state)
- **UI**: Jetpack Compose
- **JavaScript Runtime**: QuickJS

---

## 🛠️ Configuration

**Config File**: `/sdcard/.androidforclaw/config/openclaw.json` (single config file, aligned with OpenClaw)

**Configuration includes**:
- Agent settings (maxIterations, defaultModel, timeout, mode)
- Thinking configuration (enabled, budgetTokens, showInUI)
- Skills configuration (paths, autoLoad, disabled)
- Tools configuration (screenshot, accessibility, exec, browser)
- Gateway configuration (port, security, channels)
- Models configuration (LLM providers and model definitions)
- Feishu/Discord configuration
- UI configuration (theme, language, floatingWindow)
- Logging configuration

**Example configuration**:

```json
{
  "version": "1.0.0",
  "agent": {
    "name": "androidforclaw",
    "defaultModel": "claude-opus-4-6",
    "maxIterations": 50
  },
  "thinking": {
    "enabled": true,
    "budgetTokens": 10000
  },
  "skills": {
    "bundledPath": "assets://skills/",
    "workspacePath": "/sdcard/.androidforclaw/workspace/skills/",
    "autoLoad": ["mobile-operations"]
  },
  "models": {
    "mode": "merge",
    "providers": {
      "anthropic": {
        "baseUrl": "https://api.anthropic.com/v1",
        "apiKey": "${ANTHROPIC_API_KEY}",
        "api": "anthropic",
        "models": [
          {
            "id": "claude-opus-4-6",
            "name": "Claude Opus 4.6",
            "reasoning": true,
            "contextWindow": 200000
          }
        ]
      }
    }
  },
  "gateway": {
    "enabled": true,
    "port": 8080,
    "feishu": {
      "enabled": true,
      "appId": "${FEISHU_APP_ID}",
      "appSecret": "${FEISHU_APP_SECRET}"
    }
  }
}
```

See [config/openclaw.json.example](config/openclaw.json.example) for full options.

---

## 📱 Usage

### Via Feishu (Lark)

1. Configure Feishu bot in `/sdcard/.androidforclaw/config/openclaw.json`:
   ```json
   {
     "gateway": {
       "feishu": {
         "enabled": true,
         "appId": "your_app_id",
         "appSecret": "your_app_secret"
       }
     }
   }
   ```
2. Add bot to group chat
3. Send message: `@Bot 帮我打开微信`

### Via Discord

1. Configure Discord bot in `/sdcard/.androidforclaw/config/openclaw.json`
2. Invite bot to server
3. Send message: `@Bot open WeChat`

### Via HTTP API

```bash
curl -X POST http://phone-ip:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"message": "Take a screenshot"}'
```

### Via ADB (Testing)

```bash
adb shell am broadcast \
  -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT \
  --es message "打开微信"
```

---

## 🔧 Development

### Project Structure

```
AndroidForClaw/
├── app/                          # Main application
│   ├── src/main/java/
│   │   ├── agent/               # Agent runtime
│   │   │   ├── loop/            # AgentLoop
│   │   │   ├── tools/           # Tool registry
│   │   │   └── skills/          # Skills loader
│   │   ├── gateway/             # Gateway server
│   │   ├── providers/           # LLM providers
│   │   └── ui/                  # User interface
│   └── src/main/assets/skills/  # Bundled skills
├── accessibility-service/        # Accessibility service APK
├── extensions/
│   ├── feishu/                  # Feishu channel
│   └── discord/                 # Discord channel
└── config/                       # Configuration examples
```

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build accessibility service
./gradlew :accessibility-service:assembleRelease

# Run tests
./gradlew test

# Clean
./gradlew clean
```

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details.

### Reporting Issues

- **Bug reports**: Include device model, Android version, and logs
- **Feature requests**: Describe use case and expected behavior
- **Questions**: Check [docs/](docs/) first, then open an issue

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **[OpenClaw](https://github.com/openclaw/openclaw)** - Architecture and design inspiration
- **[Claude](https://www.anthropic.com/claude)** - AI reasoning and tool use capabilities
- **[AgentSkills.io](https://agentskills.io)** - Skills format standard

---

## 📞 Contact & Community

### Join Our Community - Try AI Phone Control! 🚀

<div align="center">

#### Feishu Group (飞书群)

[![Join Feishu Group](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[Click to Join Feishu Group](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord Server

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/k9NKrXUN)

**[Join Discord Server](https://discord.gg/k9NKrXUN)** - Experience AI phone control in the community!

---

#### WeChat Group (微信群)

<img src="docs/images/wechat-qrcode.png" width="300" alt="WeChat Group QR Code">

**扫码加入微信群** - 该二维码7天内有效，重新进入将更新

</div>

### Other Channels

- **GitHub Issues**: [Report bugs or request features](https://github.com/xiaomochn/AndroidForClaw/issues)
- **Discussions**: [Join the conversation](https://github.com/xiaomochn/AndroidForClaw/discussions)

---

**AndroidForClaw** - Give AI the power to use phones 🦞📱
