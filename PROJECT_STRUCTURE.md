# forClaw 项目结构总览

**创建日期**: 2026-03-06

---

## 📂 目录结构

```
forclaw/
├── README.md                    # 总项目说明
├── PROJECT_STRUCTURE.md         # 本文档 - 项目结构说明
│
├── phoneforclaw/               # ✅ 手机控制 (已完成核心)
│   ├── app/                    # Android 应用
│   │   ├── src/main/
│   │   │   ├── assets/
│   │   │   │   ├── bootstrap/  # Bootstrap 文件 (IDENTITY, AGENTS)
│   │   │   │   └── skills/     # 内置 Skills (7 个)
│   │   │   └── java/com/agent/mobile/
│   │   │       ├── agent/
│   │   │       │   ├── loop/
│   │   │       │   │   └── AgentLoop.kt
│   │   │       │   ├── context/
│   │   │       │   │   └── ContextBuilder.kt
│   │   │       │   ├── skills/
│   │   │       │   │   ├── SkillParser.kt
│   │   │       │   │   └── SkillsLoader.kt
│   │   │       │   └── tools/
│   │   │       │       ├── ToolRegistry.kt
│   │   │       │       └── SkillRegistry.kt
│   │   │       ├── providers/
│   │   │       │   └── MifyRepository.kt
│   │   │       ├── service/
│   │   │       │   ├── PhoneAccessibilityService.kt
│   │   │       │   └── FloatingWindowService.kt
│   │   │       └── ui/
│   │   ├── build.gradle
│   │   └── ...
│   ├── README.md               # 项目说明
│   ├── ARCHITECTURE.md         # 架构设计
│   ├── CLAUDE.md              # Claude Code 开发指南
│   ├── QUICKSTART.md          # 快速开始
│   └── doc/                    # 详细文档
│       ├── Block1-6_完成报告.md
│       ├── OpenClaw架构对齐指南.md
│       ├── OpenClaw对齐详细检查报告.md
│       ├── 能力对齐清单.md
│       └── ...
│
├── browserforclaw/             # 🚧 浏览器控制 (规划中)
│   ├── extension/              # 浏览器插件
│   │   ├── manifest.json       # Manifest V3
│   │   ├── src/
│   │   │   ├── background/     # Background Service Worker
│   │   │   ├── content/        # Content Script
│   │   │   ├── popup/          # Popup UI
│   │   │   └── tools/          # Tools 实现
│   │   └── package.json
│   ├── server/                 # WebSocket Server
│   │   ├── src/
│   │   │   ├── index.ts
│   │   │   ├── websocket.ts
│   │   │   ├── rpc.ts
│   │   │   └── native.ts
│   │   └── package.json
│   ├── README.md              # 项目规划
│   └── ARCHITECTURE.md        # 架构设计 (待创建)
│
├── tvforclaw/                  # 📅 智能电视控制 (未来)
│   └── README.md              # 待创建
│
└── homeforclaw/                # 📅 智能家居控制 (未来)
    └── README.md              # 待创建
```

---

## 🎯 项目状态

| 项目 | 状态 | 进度 | 说明 |
|------|------|------|------|
| **phoneforclaw** | ✅ 已完成核心 | 90% | 独立运行，待 Gateway 集成 |
| **browserforclaw** | 🚧 规划中 | 0% | 设计完成，待实施 |
| **tvforclaw** | 📅 未来规划 | 0% | 需求调研中 |
| **homeforclaw** | 📅 未来规划 | 0% | 需求调研中 |

---

## 📱 phoneforclaw 详细状态

### 已完成 (Block 1-6)

| 模块 | 状态 | 对齐度 |
|------|------|--------|
| Skills Parser | ✅ | 100% |
| Skills Loader | ✅ | 100% |
| Context Builder | ✅ | 95% |
| Bootstrap Files | ✅ | 100% |
| On-Demand Loading | ✅ | 100% |
| User Extension | ✅ | 100% |
| AgentLoop | ✅ | 100% |
| Tool Registry | ✅ | 100% |
| Android Tools | ✅ | 100% |

**整体对齐度**: Skills 系统 100%，全功能 57%

### 待完成

| 功能 | 优先级 | 工作量 |
|------|--------|--------|
| 记忆压缩 | P0 | 3-5 天 |
| 循环检测 | P1 | 2-3 天 |
| 记忆系统 | P2 | 2-3 周 |
| Gateway 集成 | P2 | 1-2 周 |

---

## 🌐 browserforclaw 规划

### Phase 1: 基础框架 (1-2 周)
- Extension 基础结构
- WebSocket Server
- Native Messaging 桥接
- 基础 Tools (screenshot, click, type, navigate)

### Phase 2: 核心能力 (2-3 周)
- 观察类 Tools (元素查找、内容提取)
- 操作类 Tools (滚动、表单填写)
- 导航类 Tools (多标签管理)

### Phase 3: 高级能力 (2-3 周)
- 会话管理 (Cookie, Storage)
- 智能等待
- 错误处理

### Phase 4: OpenClaw 集成 (1-2 周)
- WebSocket 协议对齐
- Skills 系统集成
- Standalone Mode 支持

---

## 🔗 项目间关系

### 架构层级

```
┌────────────────────────────────────────────┐
│         OpenClaw Gateway (可选)            │
│  Session Management │ Multi-channel Access │
└────────────────────────────────────────────┘
                    ↓ WebSocket
        ┌───────────┼───────────┐
        │           │           │
┌───────┴──┐  ┌────┴────┐  ┌──┴─────┐
│  phone   │  │ browser │  │   tv   │
│ forclaw  │  │ forclaw │  │forclaw │
└──────────┘  └─────────┘  └────────┘
     ↓            ↓             ↓
  Android      Chrome       AndroidTV
```

### 独立性

每个 forClaw 项目可以：
- **独立运行** (Standalone Mode) - 内置 AgentLoop
- **连接 Gateway** (Gateway Mode) - 作为 OpenClaw 的能力提供者

### 共同特性

所有项目共享：
- 相同的 RPC 接口
- AgentSkills.io 格式
- 相似的 Tools 架构
- 统一的开发规范

---

## 📊 能力对比

| 能力 | phoneforclaw | browserforclaw | tvforclaw | homeforclaw |
|------|--------------|----------------|-----------|-------------|
| 截图 | ✅ | ✅ | 🚧 | ❌ |
| 点击 | ✅ tap | ✅ click | 🚧 remote | ❌ |
| 输入 | ✅ type | ✅ type | 🚧 voice | ❌ |
| 导航 | ✅ apps | ✅ urls | 🚧 channels | ❌ |
| 内容提取 | ✅ UI tree | ✅ DOM | ❌ | ❌ |
| 会话管理 | ⚠️ | ✅ Cookie | ❌ | ❌ |
| 多任务 | ✅ apps | ✅ tabs | 🚧 | ✅ devices |

---

## 🎯 开发优先级

### Q1 2026
1. ✅ **phoneforclaw 核心完成** (Block 1-6)
2. ⚠️ **phoneforclaw 记忆压缩** (P0)
3. ⚠️ **phoneforclaw 循环检测** (P1)

### Q2 2026
4. 🚧 **browserforclaw Phase 1** (基础框架)
5. 🚧 **browserforclaw Phase 2** (核心能力)
6. 🚧 **phoneforclaw Gateway 集成**

### Q3 2026
7. 🚧 **browserforclaw Phase 3** (高级能力)
8. 🚧 **browserforclaw Phase 4** (OpenClaw 集成)
9. 📅 **跨项目任务编排**

### Q4 2026
10. 📅 **tvforclaw 立项**
11. 📅 **homeforclaw 调研**

---

## 📝 命名规范

### 项目命名
- 格式: `[platform]forclaw`
- 示例: phoneforclaw, browserforclaw, tvforclaw

### 包名/模块名
- **Android**: `com.agent.mobile` (保持向后兼容)
- **Browser**: `@forclaw/browser`
- **TV**: `com.forclaw.tv`
- **Home**: `@forclaw/home`

### Git 仓库
- 主仓库: `forclaw/forclaw`
- 子项目: 在主仓库内的子目录

---

## 🤝 贡献指南

### 新项目贡献

如果要创建新的 forClaw 项目：

1. **在 forclaw/ 下创建目录**
   ```bash
   mkdir [platform]forclaw
   ```

2. **遵循统一结构**
   - README.md (项目说明)
   - ARCHITECTURE.md (架构设计)
   - 实现至少 5 个基础 Tools
   - 支持 Standalone + Gateway 两种模式

3. **更新主 README**
   - 添加到项目列表
   - 更新架构图

### 现有项目贡献

- **phoneforclaw**: Kotlin/Android
- **browserforclaw**: TypeScript/Extension
- **其他**: 待定

---

## 📚 相关文档

### 主项目文档
- [forclaw/README.md](README.md) - 总项目说明

### phoneforclaw 文档
- [phoneforclaw/README.md](phoneforclaw/README.md)
- [phoneforclaw/ARCHITECTURE.md](phoneforclaw/ARCHITECTURE.md)
- [phoneforclaw/CLAUDE.md](phoneforclaw/CLAUDE.md)
- [phoneforclaw/doc/OpenClaw对齐详细检查报告.md](phoneforclaw/doc/OpenClaw对齐详细检查报告.md)

### browserforclaw 文档
- [browserforclaw/README.md](browserforclaw/README.md)
- [browserforclaw/ARCHITECTURE.md](browserforclaw/ARCHITECTURE.md) (待创建)

---

## 🎉 里程碑

- ✅ **2026-03-05**: phoneforclaw Block 1-6 完成，Skills 系统 100% 对齐
- ✅ **2026-03-06**: forclaw 项目结构创建，项目重定位
- 🚧 **2026-03-13**: phoneforclaw 记忆压缩完成 (预计)
- 🚧 **2026-04-01**: browserforclaw Phase 1 完成 (预计)
- 📅 **2026-06-01**: phoneforclaw + browserforclaw Gateway 集成 (目标)

---

**forclaw** - Extend OpenClaw's reach to every platform 🦞

*从手机开始，逐步覆盖所有平台！*
