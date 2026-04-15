# AndroidForClaw 目录结构设计

## 对齐 Hermes 架构

参考 Hermes 的 `~/.hermes/` 目录结构，AndroidForClaw 采用类似的组织方式。

## 📁 完整目录结构

### Android 路径映射

| Hermes | AndroidForClaw | 说明 |
|----------|----------------|------|
| `~/.hermes/` | `/sdcard/.androidforclaw/` | 主目录（隐藏目录） |
| `~/.hermes/workspace/` | `/sdcard/.androidforclaw/workspace/` | 用户工作区（可见） |
| `~/.hermes/skills/` | `/sdcard/.androidforclaw/skills/` | 托管 Skills |
| `~/.hermes/agents/` | `/sdcard/.androidforclaw/agents/` | Agent 数据 |
| `~/.hermes/openclaw.json` | `/sdcard/.androidforclaw/openclaw.json` | 主配置 |

### 详细结构

```
/sdcard/
│
├── .androidforclaw/                      ← 主目录（隐藏，类似 ~/.hermes/）
│   ├── openclaw.json                     ← 主配置文件
│   ├── hermes.last-known-good.json     ← 配置备份
│   ├── .device-id                        ← 设备唯一 ID
│   ├── .builtin-mimo-provider            ← 内置提供商标记
│   ├── app.log                           ← 应用日志
│   ├── gateway.log                       ← Gateway 日志（未来）
│   ├── update-check.json                 ← 更新检查
│   │
│   ├── config/                           ← 配置目录
│   │   ├── models.json                   ← 模型提供商配置
│   │   └── channels.json                 ← 渠道配置
│   │
│   ├── config-backups/                   ← 配置备份
│   │   └── hermes-YYYYMMDD-HHMMSS.json
│   │
│   ├── agents/                           ← Agent 数据（多 agent 支持）
│   │   └── main/                         ← 默认 agent
│   │       ├── agent/                    ← Agent 配置
│   │       │   └── config.json
│   │       └── sessions/                 ← 会话数据
│   │           ├── session-001.json
│   │           └── session-002.json
│   │
│   ├── skills/                           ← 托管 Skills（包管理器安装）
│   │   └── wechat-official/              ← 示例：官方 WeChat skill
│   │       ├── SKILL.md
│   │       ├── skill.json                ← Skill 元数据
│   │       └── scripts/
│   │
│   ├── devices/                          ← 设备配对（多设备支持）
│   │   ├── paired.json                   ← 已配对设备
│   │   └── pending.json                  ← 待配对设备
│   │
│   ├── cron/                             ← 定时任务（未来功能）
│   │   └── jobs.json
│   │
│   └── canvas/                           ← Canvas 数据（未来功能）
│       └── index.html
│
└── androidforclaw-workspace/             ← 用户工作区（可见目录）
    ├── skills/                           ← 用户自定义 Skills
    │   ├── my-custom-skill/
    │   │   └── SKILL.md
    │   └── wechat-automation/
    │       ├── SKILL.md
    │       └── scripts/
    │           └── login.js
    │
    ├── .hermes/                        ← Workspace 元数据
    │   └── .gitignore
    │
    ├── IDENTITY.md                       ← Agent 身份配置
    ├── BOOTSTRAP.md                      ← 启动指令
    ├── SOUL.md                           ← Agent 个性
    ├── AGENTS.md                         ← Agent 配置
    ├── TOOLS.md                          ← 工具配置
    ├── USER.md                           ← 用户信息
    ├── HEARTBEAT.md                      ← 心跳配置
    │
    └── sessions/                         ← 会话工作目录（未来）
        └── 2026-03-07/
            ├── notes.md
            └── artifacts/
```

## 🎯 设计原则

### 1. 隐藏 vs 可见

**隐藏目录** (`.androidforclaw/`):
- 系统配置和数据
- 日志文件
- 自动生成的内容
- 用户通常不需要直接编辑

**可见目录** (`androidforclaw-workspace/`):
- 用户自定义 skills
- 个性化配置（IDENTITY.md 等）
- 会话工作目录
- 用户经常访问和编辑

### 2. 配置文件分层

```
优先级（高 → 低）:

1. openclaw.json              ← 主配置（所有设置）
2. config/models.json         ← 模型提供商（可选，被 openclaw.json 覆盖）
3. config/channels.json       ← 渠道配置（可选）
```

### 3. Skills 加载顺序

```
优先级（高 → 低）:

1. androidforclaw-workspace/skills/     ← 用户自定义（最高）
2. .androidforclaw/skills/              ← 托管 Skills
3. app/assets/skills/                   ← 内置 Skills
```

## 📝 核心文件说明

### Workspace 文件

#### IDENTITY.md - Agent 身份

```markdown
# IDENTITY.md - Who Am I?

_Fill this in during your first conversation. Make it yours._

- **Name:** AndroidForClaw Agent
- **Creature:** AI-powered mobile automation assistant
- **Vibe:** Helpful, precise, reliable
- **Emoji:** 🤖
- **Avatar:** avatars/android-claw.png

---

This isn't just metadata. It's the start of figuring out who you are.
```

#### BOOTSTRAP.md - 启动指令

```markdown
# BOOTSTRAP.md - Startup Instructions

Instructions loaded every time I start:

## Core Principles

1. Always observe before acting (screenshot first)
2. Verify every step (screenshot after actions)
3. Be systematic and methodical
4. Document your reasoning

## Available Tools

- screenshot() - Observe screen state
- tap(x, y) - Interact with UI
- swipe() - Navigate and scroll
- type() - Input text
- open_app() - Launch applications
```

#### SOUL.md - Agent 个性

```markdown
# SOUL.md - My Character

## How I Approach Tasks

- I'm thorough but efficient
- I explain my actions clearly
- I handle errors gracefully
- I learn from mistakes

## My Values

- Reliability > Speed
- Clarity > Complexity
- User trust is paramount
```

#### TOOLS.md - 工具配置

```markdown
# TOOLS.md - Tool Preferences

## Screenshot Settings

- Always capture before/after actions
- Include UI tree for element analysis

## Timing Preferences

- Default wait after tap: 1s
- Default wait for loading: 2s
- Page transition: 1.5s
```

#### USER.md - 用户信息

```markdown
# USER.md - About My User

## User Preferences

- Preferred language: Chinese
- Notification style: Minimal
- Logging level: Info

## Common Tasks

- WeChat automation
- App testing
- UI validation
```

#### AGENTS.md - Multi-Agent 配置

```markdown
# AGENTS.md - Agent Configuration

## Default Agent

- Name: main
- Type: mobile-automation
- Skills: All bundled + workspace
```

#### HEARTBEAT.md - 心跳配置

```markdown
# HEARTBEAT.md - Keep Me Alive

Check-in frequency: 5 minutes
Health check: Active sessions, system resources
```

### 配置文件

#### openclaw.json - 主配置

```json
{
  "thinking": {
    "enabled": true
  },
  "gateway": {
    "enabled": false,
    "port": 8080
  },
  "agents": {
    "defaults": {
      "workspace": "/sdcard/androidforclaw-workspace",
      "model": {
        "provider": "anthropic",
        "id": "claude-opus-4-6"
      }
    }
  },
  "skills": {
    "bundled": true,
    "managed": true,
    "workspace": true,
    "hotReload": true
  },
  "providers": {
    "anthropic": {
      "baseUrl": "https://api.anthropic.com",
      "apiKey": "${ANTHROPIC_API_KEY}",
      "api": "anthropic-messages"
    }
  }
}
```

#### .device-id

```
android-xxxxxxxxxxxxx
```

## 🔄 迁移计划

### 从当前结构迁移

**当前**:
```
/sdcard/AndroidForClaw/
├── config/
│   ├── openclaw.json
│   └── models.json
└── workspace/
    └── skills/
```

**新结构**:
```
/sdcard/.androidforclaw/
├── openclaw.json              ← 移动自 AndroidForClaw/config/
├── config/
│   └── models.json            ← 保留作为可选配置
└── ...

/sdcard/.androidforclaw/workspace/
├── skills/                    ← 移动自 AndroidForClaw/workspace/skills/
├── IDENTITY.md                ← 新增
├── BOOTSTRAP.md               ← 新增
└── ...
```

### 兼容性处理

1. **向后兼容**: 同时支持旧路径和新路径
2. **自动迁移**: 首次启动时自动迁移数据
3. **配置合并**: 合并旧配置到新格式

## 🛠️ 实现优先级

### Phase 1 - 基础结构（当前）
- [x] Workspace skills 路径更新
- [x] 首次启动引导（Hermes 风格）
- [x] 支持 IDENTITY.md, USER.md, SOUL.md 检测
- [ ] 创建完整基础目录结构
- [ ] Workspace 文件热重载

### Phase 2 - 完整对齐
- [ ] 移动配置到 `.androidforclaw/`
- [ ] 实现 agents/ 目录结构
- [ ] 添加配置备份机制
- [ ] 完整 Bootstrap 文件支持

### Phase 3 - 高级功能
- [ ] Multi-agent 支持
- [ ] 设备配对功能
- [ ] Cron 定时任务
- [ ] Canvas 集成

## 📚 参考

- **Hermes 目录**: `~/.hermes/`
- **文档**: https://docs.hermes.ai
- **当前实现**: `app/src/main/java/com/xiaomo/androidforclaw/agent/skills/SkillsLoader.kt`

---

**Directory Structure** - Aligned with Hermes Architecture 📁
