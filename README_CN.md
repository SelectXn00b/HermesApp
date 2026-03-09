# 📱 AndroidForClaw — OpenClaw 的手机版，来了

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org/)

**[English](README.md)** | **[📖 文档](docs/README.md)** | **[🚀 快速开始](#-快速开始)** | **[🤝 参与贡献](CONTRIBUTING.md)**

---

## 🌟 从 OpenClaw 说起

你可能已经知道 **[OpenClaw](https://github.com/openclaw/openclaw)** — GitHub 280k+ Star 的开源 AI 助手框架。它让 AI 连接 20+ 消息渠道、控制浏览器、执行代码、管理日程，是目前最热门的个人 AI Agent 项目。

**但 OpenClaw 有一个天然的局限：**

它是为桌面设计的。Android 在它的架构里只是一个「远程节点」。
手机需要连电脑上的 Gateway 才能工作，AI 看不到手机屏幕，碰不到微信、抖音、淘宝。

**所以我们做了 AndroidForClaw。**

## 🎯 AndroidForClaw 是什么？

**一句话：** OpenClaw 的 Android 原生版本。

**底层架构与 OpenClaw 98% 一致** — 同样的 Tool Calling 协议、同样的 Agent Loop、同样的 Bootstrap 配置体系、同样的 Skill 扩展机制。

**不同的是：** AI 直接住在你的手机里，通过 Accessibility Service 获得了完整的设备控制能力。

不需要电脑，不需要 Gateway，不需要配对。装上 APK，授权，开始用。

---

## ✨ 我们支持什么？

### 一、与 OpenClaw 一致的底层能力

#### 🧠 Agent 核心
- ✅ **Tool Calling 协议** — 标准函数调用，与 OpenClaw 完全兼容
- ✅ **Agent Loop** — 观察 → 思考 → 行动 → 验证 循环
- ✅ **Bootstrap 配置** — 完整的 IDENTITY / AGENTS / SOUL / TOOLS 配置体系
- ✅ **Memory 持久化** — 长期记忆，跨会话保留重要信息
- ✅ **Skill 扩展** — 自定义技能目录，灵活扩展 Agent 能力
- ✅ **ClawHub 集成** — 原生支持从 ClawHub 发现和安装技能 (npm 待实现)
- ✅ **多模型支持** — Claude / GPT / Gemini 等主流 LLM
- ✅ **Exploration 模式** — 动态决策，AI 自主探索
- ✅ **Planning 模式** — 先规划后执行，适合固定流程

#### 🔧 通用工具链
- ✅ **文件读写** — read_file / write_file / edit_file
- ✅ **目录浏览** — list_dir
- ✅ **Shell 命令** — 基础命令 (ls / cat / grep / find / sed / awk 等)
- 🚧 **满血版终端支持** — Termux 集成开发中，提供完整 Shell 环境
- ✅ **网页抓取** — web_fetch
- ✅ **JavaScript 引擎** — 内置 QuickJS，支持 ES6+

#### 🌐 浏览器自动化
- ✅ **网页导航** — 打开任意 URL
- ✅ **元素点击** — CSS 选择器精确定位
- ✅ **表单填写** — 自动输入文本
- ✅ **页面滚动** — 上下 / 顶部 / 底部
- ✅ **JS 执行** — 在网页中执行 JavaScript
- ✅ **内容提取** — text / HTML / Markdown
- ✅ **Cookie 管理** — 读取和设置
- ✅ **网页截图** — 支持全页截图

#### 💬 消息渠道
- ✅ **飞书** — 原生飞书消息通道
- ✅ **Discord** — Discord Bot 集成，支持 WebSocket Gateway
- ✅ **设备内对话** — 内置聊天界面
- 🔜 **更多渠道** 持续接入中

---

### 二、Android 独有的超能力 🔥（OpenClaw 做不到的）

#### 👁️ 屏幕感知
- ✅ **实时截图** — 随时截取当前屏幕，AI 通过视觉模型「看懂」画面
- ✅ **UI 树解析** — 完整 View 层级结构，精确定位每个元素
- ✅ **状态判断** — AI 知道当前在哪个应用、哪个页面

#### ✋ 触控操作
- ✅ **点击** — tap(x, y)，精确点击屏幕任意位置
- ✅ **滑动** — swipe，上下左右，支持自定义时长
- ✅ **长按** — long_press，触发长按菜单
- ✅ **文本输入** — 在任意输入框中打字

#### 📱 应用控制
- ✅ **启动应用** — 通过包名打开任意已安装应用
- ✅ **Activity 跳转** — 直接跳转到应用的指定页面
- ✅ **应用列表** — 获取所有已安装应用
- ✅ **系统导航** — Home / 返回 / 最近任务

#### 🔥 任意 App 操控（核心差异！）

通过 Accessibility Service，任何你能手动操作的应用，AI 都能操作：

| App | AI 能做什么 |
|-----|-------------|
| 📱 微信 | 发消息、看朋友圈、转账 |
| 📱 支付宝 | 查账单、扫码付款 |
| 📱 抖音 | 搜索视频、浏览内容 |
| 📱 淘宝 | 搜商品、比价格 |
| 📱 高德 | 搜路线、开导航 |

#### 🔗 跨应用工作流

串联多个 App 完成复杂任务：

**「微信收到一个地址，帮我导航过去」**
→ 打开微信 → 识别地址 → 复制 → 打开高德 → 粘贴搜索 → 开始导航

#### 🧪 应用测试
- ✅ 功能测试 / UI 测试 / 回归测试 / 探索测试
- ✅ 每步操作自动截图留证

#### 🎁 ClawHub 集成（独有特性！）

原生支持 ClawHub 技能市场：

- **🔍 搜索技能**: `skills.search` - 从 ClawHub 发现技能
- **📥 安装技能**: `skills.install` - 一键从 ClawHub 或 URL 安装
- **🔄 更新技能**: `skills.update` - 保持技能最新
- **📋 技能状态**: 完整查看 已安装/内置/可用 技能
- **🔒 锁文件**: `skill.lock.json` 追踪已安装版本

**示例**:
```bash
# 搜索 Twitter 技能
curl -X POST http://手机IP:8080/gateway \
  -d '{"method":"skills.search","params":{"query":"twitter"}}'

# 从 ClawHub 安装技能
curl -X POST http://手机IP:8080/gateway \
  -d '{"method":"skills.install","params":{"source":"clawhub://twitter"}}'
```

*📦 npm 仓库支持即将到来！*

---

## 💡 一句话总结

**OpenClaw** 让 AI 能对话、能上网、能写代码。
**AndroidForClaw** 在同样的底层上，让 AI 还能像你一样用手机。

🤖 **底层 98% OpenClaw，能力覆盖整部手机。**

---

## ⚡ 快速开始

### 方法 1: 下载预编译 APK（推荐）

**下载地址**: [releases/](releases/)

1. **下载 APK**
   ```
   app-release.apk                          (主应用, ~31MB)
   observer-release.apk                     (S4Claw: 无障碍服务+截图, ~4.3MB)
   B4Claw-v1.0.0.apk                        (Browser4Claw: AI 浏览器, 可选)
   ```

2. **安装**
   ```bash
   adb install app-release.apk
   adb install observer-release.apk
   adb install B4Claw-v1.0.0.apk  # 可选
   ```

3. **配置 API**
   - 推送配置到设备:
     ```bash
     adb push config/openclaw.json /sdcard/.androidforclaw/config/openclaw.json
     ```
   - 或在手机上直接编辑: `/sdcard/.androidforclaw/config/openclaw.json`

4. **授予权限**
   - 打开 **S4Claw** 应用并启用:
     - ✅ 无障碍服务 (设备控制必需)
     - ✅ 录屏权限 (截图功能必需)
   - 打开**主应用**并授予:
     - ✅ 悬浮窗权限 (悬浮窗显示必需)

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
   # 构建主应用和 S4Claw
   ./gradlew :app:assembleDebug :extensions:observer:assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb install extensions/observer/build/outputs/apk/debug/observer-debug.apk

   # 可选: 构建 B4Claw 浏览器
   cd extensions/BrowserForClaw/android-project
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-universal-debug.apk
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

## 📁 项目目录结构

AndroidForClaw 使用 `/sdcard/.androidforclaw/` 作为项目数据根目录，与 OpenClaw Desktop 的 `~/.openclaw/` 完全对齐：

```
/sdcard/.androidforclaw/              ← 项目数据根目录
├── config/                           ← 配置文件
│   └── openclaw.json                 ← 主配置文件
├── workspace/                        ← 用户工作区 (可通过文件管理器访问)
│   ├── skills/                       ← 用户自定义 Skills
│   ├── sessions/                     ← 会话历史 (JSONL 格式)
│   └── memory/                       ← 持久化记忆
├── skills/                           ← 托管 Skills (包管理器安装)
└── logs/                             ← 日志文件
```

所有文件都可以通过手机文件管理器访问和编辑！

---

## 🛠️ 配置说明

**配置文件**: `/sdcard/.androidforclaw/config/openclaw.json` (单一配置文件,与 OpenClaw 对齐)

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

完整配置选项参考 [config/openclaw.json.example](config/openclaw.json.example)。

---

## 📱 使用方式

### 通过飞书

1. 在 `/sdcard/.androidforclaw/config/openclaw.json` 中配置飞书机器人:
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

1. 在 `/sdcard/.androidforclaw/config/openclaw.json` 中配置 Discord 机器人
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

---

#### 微信群

<img src="docs/images/wechat-qrcode.png" width="300" alt="微信群二维码">

**扫码加入微信群** - 该二维码7天内（3月16日前）有效，重新进入将更新

</div>

*在群里交流使用经验、分享技巧、获取帮助、体验 AI 控制手机*

### 其他渠道

- **GitHub Issues**: [报告 Bug 或请求功能](https://github.com/xiaomochn/AndroidForClaw/issues)
- **Discussions**: [加入讨论](https://github.com/xiaomochn/AndroidForClaw/discussions)

---

**AndroidForClaw** - 赋予 AI 使用手机的能力 🦞📱
