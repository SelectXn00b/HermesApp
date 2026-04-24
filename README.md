# Hermes for Android

[![Release](https://img.shields.io/badge/Release-v1.1.17-blue.svg)](https://github.com/SelectXn00b/AndroidForClaw/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **让 AI 真正掌控你的 Android 手机。**

底层 1:1 复刻 [Hermes](https://github.com/openclaw/openclaw) 框架（NousResearch 出品的 OpenAI-spec tool-calling agent loop），App 层借鉴 [Operit](https://github.com/AAswordman/Operit) 的外壳——把 Hermes 的 Kotlin 版 Agent Loop 嵌进 Android，在手机上跑出完整 AI Agent 能力：看屏幕、点 App、跑代码、连平台。

**[详细文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)** | **[快速开始](#-快速开始)** | **[加入社区](#-社区)**

---

## AI 能帮你做什么

### 操控任何 App

微信、支付宝、抖音、淘宝、高德……**凡是你能手动操作的，AI 都能操作。**

```
你：帮我打开微信发消息给张三说"明天见"
AI：→ 打开微信 → 搜索张三 → 输入消息 → 发送 ✅
```

### 跨应用联动

```
你：微信收到一个地址，帮我导航过去
AI：→ 微信复制地址 → 打开高德 → 搜索 → 开始导航
```

### 执行代码

通过 Termux SSH 执行命令（Shell 直接可用，Python/Node.js 需在 Termux 中安装）：

```
你：用 Python 帮我分析一下 Downloads 文件夹里的 CSV
AI：→ exec("python3 analyze.py") → 返回分析结果
```

### 搜索 & 抓取网页

```
你：搜一下今天的科技新闻
AI：→ web_search("科技新闻") → 返回标题+链接+摘要
```

### 多平台消息

通过飞书、Discord 等远程控制你的手机 AI：

| 渠道 | 状态 |
|------|------|
| 飞书 | ✅ 可用（WebSocket 实时连接，私聊/群聊，39 个飞书工具） |
| Discord | ✅ 可用（Gateway v10，私聊/群聊，权限策略） |
| 设备内对话 | ✅ 可用 |
| Telegram | 🚧 开发中 |
| Slack | 🚧 开发中 |

每个渠道支持**独立模型覆盖**——从已配置的 Provider 中选择该渠道专用的模型。

### MCP Server（给外部 Agent 用）

内置 MCP Server（端口 8399），将手机的无障碍和截屏能力通过标准 MCP 协议暴露给外部 Agent：

```
工具：get_view_tree / screenshot / tap / swipe / input_text / press_home / press_back / get_current_app
```

> 这不是 Hermes 自身使用的——是给 Claude Desktop、Cursor 等外部 Agent 调用的。

### 技能扩展

从 [ClawHub](https://clawhub.com) 搜索安装新能力，或自己创建 Skill：

```
你：看看 ClawHub 上有什么技能
AI：→ skills_search("") → 展示可用技能列表
```

---

## 快速开始

### 下载安装

从 [Release 页面](https://github.com/SelectXn00b/AndroidForClaw/releases/latest) 下载：

| APK | 说明 | 必装？ |
|-----|------|--------|
| **Hermes** | 主应用 (含无障碍服务、Agent、Gateway) | ✅ 必装 |
| **BrowserForClaw** | AI 浏览器 (网页自动化) | 可选 |

> Termux 需从 [F-Droid](https://f-droid.org/packages/com.termux/) 单独安装（不要用 Play Store 版本）。
>
> Termux 可选：不装也能用基础 shell 命令（`ls`、`cat`、`curl`、`input` 等），装了后有完整 Linux 环境（bash、python、git、apt）。

### 3 步上手

1. **安装** — 下载安装 Hermes
2. **配置** — 打开 App，输入 API Key（或跳过使用内置 Key），开启无障碍 + 录屏权限
3. **开聊** — 直接对话，或通过飞书/Discord 发消息

> 首次打开自动弹出引导页，默认 OpenRouter + MiMo V2 Pro，支持一键跳过

### Termux 配置（可选）

App 有两层 exec 工具，按需自动路由：

| 工具 | 实现 | 无需 Termux | 装了 Termux |
|------|------|------------|-------------|
| **内置 Shell** | `ProcessBuilder` + `sh -c` | ✅ 基础命令可用（`ls`、`cat`、`curl`、`input` 等） | ✅ |
| **Termux SSH** | SSH 连接池 → Termux sshd | ❌ | ✅ 完整 Linux 环境（bash、python、nodejs、git、apt） |

不装 Termux：基础 shell 命令正常工作，截图/点击/输入等设备操作可用。
装了 Termux：额外获得 Python/Node.js 运行环境、包管理器、完整工具链。

**设置 → Termux 配置**，按向导完成：

1. 安装 Termux（F-Droid 版）
2. 生成 SSH 密钥（App 内一键生成）
3. 在 Termux 中运行 `termux-setup-storage`
4. `pkg install -y openssh`
5. 复制密钥配置命令并执行
6. `sshd`
7. App 自动验证连接

---

## 技术架构

```
846 Kotlin 源文件 · 167,000+ 行代码 · 10 个模块
```

```
┌──────────────────────────────────────────┐
│  Channels                                 │
│  飞书 · Discord · 设备内对话               │
├──────────────────────────────────────────┤
│  Agent Runtime                            │
│  AgentLoop · 65 Tools · 27 Skills ·       │
│  Context 管理 (4层防护) · Memory           │
├──────────────────────────────────────────┤
│  Providers                                │
│  OpenRouter · MiMo · Gemini · Anthropic · │
│  OpenAI · 自定义                           │
├──────────────────────────────────────────┤
│  Android Platform                         │
│  Accessibility · Termux SSH · device tool │
│  MediaProjection · BrowserForClaw         │
└──────────────────────────────────────────┘
```

### 核心特性

| 特性 | 说明 |
|------|------|
| **Playwright 模式** | 屏幕操作对齐 Playwright —— `snapshot` 获取 UI 树 + ref → `act` 操作元素 |
| **统一 exec** | 自动路由 Termux SSH（连接池 + 活动超时 + 断线重连）或内置 Shell |
| **Context 管理** | 4 层防护对齐 Hermes：limitHistoryTurns + 工具结果裁剪 + budget guard |
| **Model 智能路由** | Model ID 标准化 + Fallback Chain + API Key 轮换 + Allowlist/Blocklist |
| **Session 隔离** | 每个会话独立 AgentLoop，多 session 并行互不干扰；30 天自动清理 |
| **Skill 体系** | 27 个内置 Skill，设备上可自由编辑，支持 ClawHub 在线安装 |
| **多模型** | MiMo V2 Pro · DeepSeek R1 · Claude Sonnet 4 · Gemini 2.5 · GPT-4.1 |
| **MCP Server** | 将无障碍/截屏能力暴露给外部 Agent（端口 8399，Streamable HTTP） |
| **渠道模型覆盖** | 每个消息渠道可独立选择模型 |
| **Steer 注入** | 运行中通过 Channel 向 Agent Loop 注入消息（mid-run steering） |
| **Termux SSH** | 连接池复用 + IGNORE 探活 + 活动超时（有输出就不超时）+ 自动重连 |

---

## 完整能力表

### 通用 Tools（16 个）

| Tool | 功能 |
|------|------|
| `device` | 屏幕操作：snapshot / tap / type / scroll / press / open（Playwright 模式） |
| `read_file` | 读取文件内容 |
| `write_file` | 创建或覆盖文件 |
| `edit_file` | 精确编辑文件（diff 模式） |
| `list_dir` | 列出目录内容 |
| `exec` | 执行命令（Termux SSH / 内置 Shell） |
| `web_search` | Brave 搜索引擎 |
| `web_fetch` | 抓取网页内容 |
| `javascript` | 执行 JavaScript（QuickJS） |
| `tts` | 文本转语音 |
| `skills_search` | 搜索 ClawHub 技能 |
| `skills_install` | 从 ClawHub 安装技能 |
| `config_get` | 读取配置项 |
| `config_set` | 写入配置项 |
| `memory_search` | 语义搜索记忆 |
| `memory_get` | 读取记忆片段 |

### Android 专属 Tools（10 个）

| Tool | 功能 |
|------|------|
| `device` | 统一设备操作（截屏、点击、滑动、输入、Home、返回等） |
| `list_installed_apps` | 列出已安装应用 |
| `install_app` | 安装 APK |
| `start_activity` | 启动 Activity |
| `feishu_send_image` | 通过飞书发送图片 |
| `eye` | 摄像头拍照 |
| `log` | 查看系统日志 |
| `stop` | 停止 Agent |

### 飞书 Tools（39 个）

| 类别 | Tools |
|------|-------|
| 文档 | 获取 / 创建 / 更新 / 媒体 / 评论 |
| Wiki | 空间 / 节点 |
| 云盘 | 文件操作 |
| 多维表格 | 应用 / 表 / 字段 / 记录 / 视图 |
| 任务 | 任务 / 任务列表 / 子任务 / 评论 |
| 群聊 | 群管理 / 成员管理 |
| 权限 | 检查 / 授予 / 撤销 |
| 加急 | 发送加急 / 应用加急 |
| 日历 | 日历 / 事件 / 参与者 / 忙闲查询 |
| 消息 | 发消息 / 获取消息 / 话题消息 / 搜索消息 / 资源 / 机器人图片 |
| 搜索 | 搜索文档/Wiki |
| 通用 | 获取用户 / 搜索用户 |
| 多媒体 | 图片上传 |
| 表格 | 电子表格操作 |

### 27 个 Skills

| 类别 | Skills |
|------|--------|
| 飞书全家桶 | `feishu` · `feishu-doc` · `feishu-wiki` · `feishu-drive` · `feishu-bitable` · `feishu-chat` · `feishu-task` · `feishu-perm` · `feishu-urgent` · `feishu-calendar` · `feishu-common` · `feishu-im` · `feishu-search` · `feishu-sheets` |
| 搜索 & 网页 | `browser` · `weather` · `lark-cli` |
| 技能管理 | `clawhub` · `skill-creator` |
| 开发调试 | `debugging` · `data-processing` · `session-logs` · `context-security` |
| 配置管理 | `model-config` · `channel-config` · `install-app` · `model-usage` |

> Skills 存储在 `/sdcard/.hermes/skills/`，可自由编辑、添加、删除。

> **注意**：从旧版 AndroidForClaw 升级的用户，配置路径已从 `/sdcard/.androidforclaw/` 迁移到 `/sdcard/.hermes/`，请手动复制配置文件。

### 消息渠道

| 渠道 | 状态 | 功能 |
|------|------|------|
| **飞书** | ✅ 可用 | WebSocket 实时连接，群聊/私聊，39 个飞书工具，流式卡片回复，图片/文件上传 |
| **Discord** | ✅ 可用 | Gateway v10，群聊/私聊，DM 策略管理，Embed/Button 组件 |
| **设备内对话** | ✅ 可用 | 内置聊天界面，多 session 管理 |
| **Telegram** | 🚧 开发中 | — |
| **Slack** | 🚧 开发中 | — |

### 支持的模型

| Provider | 模型 | 说明 |
|----------|------|------|
| **OpenRouter** | MiMo V2 Pro, Hunter Alpha, DeepSeek R1, Claude Sonnet 4, GPT-4.1 | 推荐，内置 Key |
| **小米 MiMo** | MiMo V2 Pro, MiMo V2 Flash, MiMo V2 Omni | 直连小米 API |
| **Google** | Gemini 2.5 Pro, Gemini 2.5 Flash | 直连 |
| **Anthropic** | Claude Sonnet 4, Claude Opus 4 | 直连 |
| **OpenAI** | GPT-4.1, GPT-4.1 Mini, o3 | 直连 |
| **自定义** | 任何 OpenAI 兼容 API | Ollama, vLLM 等 |

> **默认配置**：OpenRouter + MiMo V2 Pro（1M 上下文 + 推理），跳过引导页自动使用内置 Key。

---

## 配置参考

`/sdcard/.hermes/openclaw.json`

```json
{
  "models": {
    "providers": {
      "openrouter": {
        "baseUrl": "https://openrouter.ai/api/v1",
        "apiKey": "sk-or-v1-你的key",
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
    "discord": {
      "enabled": true,
      "botToken": "your-discord-bot-token",
      "model": "openrouter/xiaomi/mimo-v2-pro"
    }
  }
}
```

详细配置参考 **[飞书文档](https://vcn23e479dhx.feishu.cn/wiki/UZtFwM6t9iArPVkMvRccwSran6d)**

---

## 从源码构建

```bash
git clone https://github.com/SelectXn00b/AndroidForClaw.git
cd AndroidForClaw
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
adb install releases/Hermes-v*.apk
```

---

## Related Projects

| 项目 | 说明 |
|------|------|
| [Hermes](https://github.com/openclaw/openclaw) | AI Agent 框架（桌面端） |
| [iOSForClaw](https://github.com/SelectXn00b/iOSForClaw) | Hermes iOS 客户端 |
| [Hermes for Android](https://github.com/SelectXn00b/AndroidForClaw) | Hermes Android 客户端（本项目） |

---

## 社区

<div align="center">

#### 飞书群

[![加入飞书群](docs/images/feishu-qrcode.jpg)](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)

**[点击加入飞书群](https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74)**

---

#### Discord

[![Discord](https://img.shields.io/badge/Discord-加入服务器-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/rDpaFym2b8)

**[加入 Discord](https://discord.gg/rDpaFym2b8)**

---

#### 微信群

<img src="docs/images/wechat-qrcode.png" width="300" alt="微信群二维码">

**扫码加入微信群** - 7天内有效

</div>

---

## 相关链接

- [Hermes](https://github.com/openclaw/openclaw) — 架构参照
- [ClawHub](https://clawhub.com) — 技能市场
- [架构文档](ARCHITECTURE.md) — 详细设计

---

## License

MIT — [LICENSE](LICENSE)

## 致谢

- **[Hermes](https://github.com/openclaw/openclaw)** — 架构灵感
- **[Claude](https://www.anthropic.com/claude)** — AI 推理能力

---

<div align="center">

**如果这个项目对你有帮助，请给个 Star 支持开源！**

</div>
