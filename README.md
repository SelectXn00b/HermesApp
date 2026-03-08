# AndroidForClaw - Give AI an Android Phone 📱🤖

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org/)

> **Let AI autonomously control Android phones** - No pre-scripted workflows, based on visual observation and natural language understanding

**[中文文档](README_CN.md)** | **[📖 Documentation](docs/README.md)** | **[🚀 Quick Start](#-quick-start)** | **[🤝 Contributing](CONTRIBUTING.md)**

---

## 🎯 About

**AndroidForClaw** is the Android version of [OpenClaw](https://github.com/openclaw/openclaw), an Android AI Agent Runtime that gives AI the ability to use Android phones.

### Relationship with OpenClaw

- **Architecture Alignment**: ~85% (Agent Loop, Skills System, Gateway Pattern)
- **Differences**: Adapted for Android platform (Accessibility, MediaProjection, APK packaging)
- **Positioning**: OpenClaw is the brain, AndroidForClaw is the phone executor

### Core Capabilities

- 🔍 **Visual Observation** - Understand UI through screenshots and accessibility tree
- 🖱️ **Device Interaction** - Tap, swipe, type, navigate
- 🤖 **Intelligent Decision** - User-configured LLM (supports OpenAI-compatible APIs)
- 📝 **Knowledge System** - Skills teach how to use tools (AgentSkills.io compatible)
- ⚡ **Code Execution** - Shell, JavaScript (QuickJS), File operations
- 🌐 **Multi-Channel** - Feishu, Discord, HTTP API (Planned: Gateway)

**Use Cases**: Mobile automation, app testing, data collection, task execution, device control

---

## ⚡ Quick Start

### Method 1: Download Pre-built APK (Recommended)

**Download**: [releases/](releases/)

1. **Download APK**
   ```
   androidforclaw-v2.4.3-debug.apk          (Main app, 39MB)
   androidforclaw-observer-v2.4.3.apk       (Observer: UI Tree + Screenshot, 4.3MB)
   BClaw-universal-release.apk              (Browser for AI, Optional)
   ```

2. **Install**
   ```bash
   adb install releases/androidforclaw-v2.4.3-debug.apk
   adb install releases/androidforclaw-observer-v2.4.3.apk
   adb install releases/BClaw-universal-release.apk  # Optional
   ```

3. **Configure API**
   - Push config to device:
     ```bash
     adb push config/openclaw.json /sdcard/AndroidForClaw/config/openclaw.json
     ```
   - Or edit directly on phone: `/sdcard/AndroidForClaw/config/openclaw.json`

4. **Grant Permissions**
   - Open app and grant:
     - ✅ Accessibility Service
     - ✅ Display Over Apps
     - ✅ Media Projection (Screenshot)

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
   ./gradlew :app:assembleDebug :extensions:observer:assembleRelease
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb install extensions/observer/build/outputs/apk/release/observer-release-unsigned.apk

   # Optional: Build BClaw browser
   cd extensions/BrowserForClaw/android-project
   ./gradlew assembleRelease
   adb install app/build/outputs/apk/release/app-universal-release-unsigned.apk
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

**Config File**: `/sdcard/AndroidForClaw/config/openclaw.json` (single config file, aligned with OpenClaw)

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
    "workspacePath": "/sdcard/AndroidForClaw/workspace/skills/",
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

1. Configure Feishu bot in `/sdcard/AndroidForClaw/config/openclaw.json`:
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

1. Configure Discord bot in `/sdcard/AndroidForClaw/config/openclaw.json`
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

</div>

### Other Channels

- **GitHub Issues**: [Report bugs or request features](https://github.com/xiaomochn/AndroidForClaw/issues)
- **Discussions**: [Join the conversation](https://github.com/xiaomochn/AndroidForClaw/discussions)

---

**AndroidForClaw** - Give AI the power to use phones 🦞📱
