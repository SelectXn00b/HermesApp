# AndroidForClaw - 让 AI 使用 Android 手机 📱🤖

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org/)

> **让 AI 自主控制 Android 手机** - 无需预设脚本，基于视觉观察和自然语言理解

**[English](README.md)** | **[📖 文档](docs/README.md)** | **[🚀 快速开始](#-快速开始)** | **[🤝 参与贡献](CONTRIBUTING.md)**

---

## 🎯 项目简介

**AndroidForClaw** 是 [OpenClaw](https://github.com/openclaw/openclaw) 的 Android 版本，一个 Android AI Agent Runtime，赋予 AI 使用 Android 手机的能力。

### 与 OpenClaw 的关系

- **架构对齐度**: ~85% (Agent Loop, Skills System, Gateway Pattern)
- **差异**: 适配 Android 平台 (Accessibility, MediaProjection, APK 打包)
- **定位**: OpenClaw 是大脑，AndroidForClaw 是手机执行器

### 核心能力

- 🔍 **视觉观察** - 通过截图和无障碍树理解界面
- 🖱️ **设备交互** - 点击、滑动、输入、导航
- 🤖 **智能决策** - 用户自定义 LLM 配置 (支持 OpenAI 兼容 API)
- 📝 **知识系统** - Skills 教学，知识与代码分离 (AgentSkills.io 兼容)
- ⚡ **代码执行** - Shell、JavaScript (QuickJS)、文件操作
- 🌐 **多渠道接入** - 飞书、Discord、HTTP API (规划中: Gateway)

**应用场景**: 移动自动化、应用测试、数据采集、任务执行、设备控制

---

## ⚡ 快速开始

### 方法 1: 下载预编译 APK（推荐）

**下载地址**: [releases/](releases/)

1. **下载 APK**
   ```
   androidforclaw-v2.4.3-debug.apk          (主应用, 39MB)
   androidforclaw-observer-v2.4.3.apk       (观察者: UI树+截图, 4.3MB)
   BClaw-universal-release.apk              (AI 浏览器, 可选)
   ```

2. **安装**
   ```bash
   adb install releases/androidforclaw-v2.4.3-debug.apk
   adb install releases/androidforclaw-observer-v2.4.3.apk
   adb install releases/BClaw-universal-release.apk  # 可选
   ```

3. **配置 API**
   - 推送配置到设备:
     ```bash
     adb push config/openclaw.json /sdcard/AndroidForClaw/config/openclaw.json
     ```
   - 或在手机上直接编辑: `/sdcard/AndroidForClaw/config/openclaw.json`

4. **授予权限**
   - 打开应用并授予:
     - ✅ 无障碍服务
     - ✅ 悬浮窗权限
     - ✅ 录屏权限

**开始使用**: 在飞书/Discord 发送消息即可控制手机！

---

### 方法 2: 从源码构建

1. **克隆仓库**
   ```bash
   git clone https://github.com/xiaomochn/AndroidForClaw.git
   cd AndroidForClaw
   ```

2. **配置**
   ```bash
   cp config/openclaw.json.example config/openclaw.json
   # 编辑 config/openclaw.json，填入你的 API Keys
   ```

3. **构建安装**
   ```bash
   ./gradlew :app:assembleDebug :extensions:observer:assembleRelease
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb install extensions/observer/build/outputs/apk/release/observer-release-unsigned.apk

   # 可选: 构建 BClaw 浏览器
   cd extensions/BrowserForClaw/android-project
   ./gradlew assembleRelease
   adb install app/build/outputs/apk/release/app-universal-release-unsigned.apk
   ```

---

## 🏗️ 架构设计

```
┌─────────────────────────────────────┐
│      Gateway (规划中)                │  多渠道、会话、安全
├─────────────────────────────────────┤
│      Agent Runtime (核心)            │  AgentLoop、Skills、Tools
├─────────────────────────────────────┤
│      Android Platform               │  Accessibility、ADB、UI
└─────────────────────────────────────┘
```

### 核心组件

- **Agent Loop**: 核心执行循环 (LLM → 工具 → 观察)
- **Skills System**: 基于 Markdown 的知识系统 (类似 OpenClaw)
- **Tool Registry**: Android 专用工具 (截图、点击、滑动等)
- **Gateway**: 多渠道接入 (飞书、Discord、HTTP API)
- **Session Manager**: 会话历史和上下文管理

---

## 📦 技术栈

- **语言**: Kotlin + Java
- **架构**: MVVM + Repository 模式
- **LLM API**: OpenAI 兼容 (Claude Opus 4.6)
- **Android 服务**: Accessibility Service, MediaProjection
- **存储**: MMKV (配置和状态)
- **UI**: Jetpack Compose
- **JavaScript 运行时**: QuickJS

---

## 🛠️ 配置说明

**配置文件**: `/sdcard/AndroidForClaw/config/openclaw.json` (单一配置文件,与 OpenClaw 对齐)

**配置包含**:
- Agent 设置 (maxIterations, defaultModel, timeout, mode)
- Thinking 配置 (enabled, budgetTokens, showInUI)
- Skills 配置 (paths, autoLoad, disabled)
- Tools 配置 (screenshot, accessibility, exec, browser)
- Gateway 配置 (port, security, channels)
- Models 配置 (LLM providers 和模型定义)
- 飞书/Discord 配置
- UI 配置 (theme, language, floatingWindow)
- 日志配置

**配置示例**:

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

完整配置选项参考 [config/openclaw.json.example](config/openclaw.json.example)。

---

## 📱 使用方式

### 通过飞书

1. 在 `/sdcard/AndroidForClaw/config/openclaw.json` 中配置飞书机器人:
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
2. 将机器人添加到群聊
3. 发送消息: `@Bot 帮我打开微信`

### 通过 Discord

1. 在 `/sdcard/AndroidForClaw/config/openclaw.json` 中配置 Discord 机器人
2. 邀请机器人到服务器
3. 发送消息: `@Bot 打开微信`

### 通过 HTTP API

```bash
curl -X POST http://手机IP:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"message": "截个图"}'
```

### 通过 ADB (测试)

```bash
adb shell am broadcast \
  -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT \
  --es message "打开微信"
```

---

## 🔧 开发指南

### 项目结构

```
AndroidForClaw/
├── app/                          # 主应用
│   ├── src/main/java/
│   │   ├── agent/               # Agent 运行时
│   │   │   ├── loop/            # AgentLoop
│   │   │   ├── tools/           # 工具注册
│   │   │   └── skills/          # Skills 加载器
│   │   ├── gateway/             # Gateway 服务器
│   │   ├── providers/           # LLM 提供商
│   │   └── ui/                  # 用户界面
│   └── src/main/assets/skills/  # 内置 Skills
├── accessibility-service/        # 无障碍服务 APK
├── extensions/
│   ├── feishu/                  # 飞书渠道
│   └── discord/                 # Discord 渠道
└── config/                       # 配置示例
```

### 构建命令

```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建无障碍服务
./gradlew :accessibility-service:assembleRelease

# 运行测试
./gradlew test

# 清理
./gradlew clean
```

---

## 🤝 参与贡献

欢迎贡献！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

### 报告问题

- **Bug 报告**: 包含设备型号、Android 版本和日志
- **功能请求**: 描述使用场景和预期行为
- **问题咨询**: 先查看 [docs/](docs/)，再提交 issue

---

## 📄 开源协议

本项目采用 MIT 协议 - 详见 [LICENSE](LICENSE) 文件。

---

## 🙏 致谢

- **[OpenClaw](https://github.com/openclaw/openclaw)** - 架构和设计灵感
- **[Claude](https://www.anthropic.com/claude)** - AI 推理和工具使用能力
- **[AgentSkills.io](https://agentskills.io)** - Skills 格式标准

---

## 📞 联系方式 & 社区

### 加入社区 - 体验 AI 控制手机！🚀

<div align="center">

#### 飞书群

[![加入飞书群](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[点击加入飞书群](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord 服务器

[![Discord](https://img.shields.io/badge/Discord-加入服务器-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/k9NKrXUN)

**[加入 Discord 服务器](https://discord.gg/k9NKrXUN)** - 在社区里体验 AI 手机控制！

</div>

*在群里交流使用经验、分享技巧、获取帮助、体验 AI 控制手机*

### 其他渠道

- **GitHub Issues**: [报告 Bug 或请求功能](https://github.com/xiaomochn/AndroidForClaw/issues)
- **Discussions**: [加入讨论](https://github.com/xiaomochn/AndroidForClaw/discussions)

---

**AndroidForClaw** - 赋予 AI 使用手机的能力 🦞📱
