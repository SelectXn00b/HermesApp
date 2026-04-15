# AndroidForClaw - User Intents

项目意图和需求记录，按时间顺序整理用户的核心意图和需求。

---

## 2026-03-07 - 飞书 Channel 完整实现

**用户原话关键词**: "不要简化版本 完要整版本"、"所有的都实现了"

**核心意图**:
- 完整实现飞书 Channel 模块，不要简化版本
- 实现所有飞书工具集（8 个类别）
- 完全对齐 Hermes 的 clawdbot-feishu 插件

**技术要点**:
- 8 个工具类别：Doc/Wiki/Drive/Bitable/Task/Chat/Perm/Urgent
- 30+ 工具完整实现
- WebSocket 连接、消息处理、会话管理
- Policy 控制、历史记录、去重机制
- FeishuToolRegistry 统一管理

**实现状态**: ✅ 已完成

---

## 2026-03-07 - Channel 配置界面

**用户原话关键词**: "在设置里加一个channel"、"点击进去显示各种channel"、"让用户配置"

**核心意图**:
- 在应用设置页面添加 Channels 入口
- 创建 Channel 列表页面展示所有可用 Channel
- 为 Feishu Channel 提供完整的配置界面
- 配置项完全对齐 clawdbot-feishu（App ID/Secret、连接模式、策略等）

**技术要点**:
- ChannelListActivity - Channel 列表
- FeishuChannelActivity - 飞书配置页面
- 配置保存到 `/sdcard/AndroidForClaw/config/channels/feishu.json`
- Material 3 + Jetpack Compose 实现
- MMKV 保存启用状态

**实现状态**: ✅ 已完成

**遇到的问题**:
- LocalContext.current 使用位置错误导致崩溃
- 已修复：将 context 提取到 Composable 函数顶层

---

## 2026-03-07 10:00 - User Intent Collector Skill

**用户原话关键词**: "把我说的话收集起来整理成一个文档"、"记录我的意图"、"累积起来就是项目的意思"

**核心意图**:
- 创建自动收集用户意图的 Skill
- 每次对话自动提取和记录用户的核心意图
- 不记录完整对话，只记录意图摘要
- 形成项目需求和演进的历史记录

**技术要点**:
- Skill 位置: `/sdcard/AndroidForClaw/workspace/skills/user-intent-collector.md` (全局)
- 备份位置: `app/src/main/assets/skills/user-intent-collector.md` (内置)
- 文档位置: `/sdcard/AndroidForClaw/docs/USER_INTENTS.md`
- 格式: 时间戳 + 意图标题 + 核心要点 + 技术细节 + 状态
- 支持本地文件和飞书文档同步

**实现状态**: ✅ 已完成

**重要说明**:
- Workspace Skills 优先级最高，全局生效
- 三个 Skills 目录：Workspace (用户) > Managed (系统) > Bundled (内置)
- 当前 Skill 设置为 `always: true`，每次对话自动执行

---

## 2026-03-07 10:20 - Skills 目录结构理解

**用户原话关键词**: "这像是这个项目的目录 不像是全局目录"、"怎么会有三个"

**核心意图**:
- 理解 AndroidForClaw 的 Skills 目录层级
- 区分项目内置 Skill 和全局 Skill
- 确保 user-intent-collector 是全局生效的

**技术要点**:
- Workspace Skills: `/sdcard/AndroidForClaw/workspace/skills/` (用户自定义，优先级最高)
- Managed Skills: `/sdcard/AndroidForClaw/.skills/` (系统管理)
- Bundled Skills: `app/src/main/assets/skills/` (应用内置，优先级最低)
- 对齐 Hermes 的 Skills 加载机制

**实现状态**: ✅ 已完成

**明确的理解**:
- 不是有三个 Skill，是有三个可以放 Skill 的目录
- user-intent-collector.md 只是一个文件，放在多个位置：
  - Workspace: 运行时实际使用
  - Bundled: 源码备份，用于版本管理
- 系统会优先加载 Workspace 目录的 Skill

---

## 🎯 当前项目核心需求总结

### 1. 架构对齐 Hermes
**目标**: AndroidForClaw 完全对齐 Hermes 架构标准
- Gateway + Runtime + Platform 三层架构
- Skills 和 Tools 分层设计
- Bootstrap 文件系统 (SOUL.md, USER.md)
- 配置管理系统 (models.json, openclaw.json)

**状态**: 🔄 持续对齐中

### 2. Feishu Channel 完整实现
**目标**: 提供完整的飞书接入能力
- 8 个工具类别，30+ 工具
- WebSocket/Webhook 双模式支持
- 完整的配置界面
- Policy 访问控制

**状态**: ✅ 已完成

### 3. 用户意图自动记录
**目标**: 每次对话自动记录用户意图
- 形成项目需求演进文档
- 只记录意图摘要，不记录完整对话
- 累积项目方向和决策历史

**状态**: ✅ 已完成

### 4. Skills 系统完善
**目标**: 建立完整的 Skills 生态
- 支持 Workspace/Managed/Bundled 三层加载
- On-demand 按需加载
- AgentSkills.io 兼容格式
- 用户自定义 Skills 支持

**状态**: 🔄 部分完成，需要完善加载机制

### 5. Gateway 架构 (规划中)
**目标**: 多渠道接入支持
- WebSocket 控制平面
- 多 Channel 支持 (Feishu/WhatsApp/Telegram/Web)
- Session 管理和安全控制
- 远程控制和监控

**状态**: 📅 规划中

---

## 历史意图概览

### 架构对齐
- 项目架构对齐 Hermes 标准
- 实现 Gateway + Runtime + Platform 三层架构
- Bootstrap 文件系统（SOUL.md、USER.md）
- Skills 和 Tools 分层设计

### 配置管理
- models.json 配置文件支持
- openclaw.json 配置迁移
- 环境变量支持
- 配置热加载

### UI 改进
- 移除旧的悬浮窗功能
- 集成 EasyFloat 用于会话信息显示
- 修复两个启动图标问题
- Material 3 设计系统

### 功能增强
- Agent Loop 核心执行引擎
- SkillRegistry 和 ToolRegistry
- Session 管理和历史记录
- 测试用例支持

---

**文档说明**:
- 本文档由 user-intent-collector skill 自动维护
- 每次对话后自动更新
- 保留项目演进的完整脉络
- 便于后续回顾和理解项目方向
