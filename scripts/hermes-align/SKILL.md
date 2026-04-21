---
name: hermes-align
description: Hermes Agent → hermes-android 模块 1:1 对齐移植工具。提供 gen_mapping.py（自动生成 Python→Kotlin 映射表）和 verify_align.py（检查对齐度 + 生成未对齐清单）。触发条件：提到"Hermes"、"对齐"、"移植"、"复刻"、"hermes-align"、"hermes-android"时使用。覆盖 287 个 Python 文件的完整命名映射（文件、类、方法、变量）。
---

# Hermes Agent → hermes-android 对齐 Skill

## 目标

将 Hermes Agent (Python) 的功能 1:1 复刻到 Android 独立模块 `hermes-android`。
**不改动 `openclaw-android` 模块**，所有 Hermes 代码在 `hermes-android` 中独立开发。

## 当前对齐度

> ⚠️ 以 `references/TODO.md` 为准（每次跑脚本自动更新）

最新数据（2026-04-19）：
- 文件 157/183 (85.8%) — 缺 26 个文件
- 方法 781/3059 (25.5%) — 缺 2278 个方法
- 类 120/197 (60.9%) — 缺 77 个类
- 加权综合: **51.9%**

**开发时务必先读 `references/TODO.md`**，里面按文件列出了所有缺失的方法/类/文件。

## 目录映射

```
hermes-agent/                           → hermes-android/src/main/java/.../hermes/
├── agent/ (25 文件)                    → agent/
├── tools/ (70 文件)                    → tools/
├── gateway/ (12 核心 + 20 平台)        → gateway/
│   └── platforms/                      →   gateway/platforms/
├── plugins/memory/ (8 后端)            → plugins/memory/
├── acp_adapter/ (9 文件)               → acp/
├── environments/ (6 + 12 parsers)      → environments/
├── cron/ (2 文件)                      → cron/
├── run_agent.py                        → 不移植（逻辑拆到 agent/）
├── cli.py                              → 不移植（终端 UI 不适用）
└── hermes_cli/                         → 不移植（终端 UI 不适用）
```

## 命名转换规则（严格执行）

| Python | Kotlin | 示例 |
|--------|--------|------|
| 文件: `context_compressor.py` | 文件: `ContextCompressor.kt` | snake_case → PascalCase.kt |
| 类: `CredentialPool` | 类: `CredentialPool` | 保持原名 |
| 方法: `_save_trajectory()` | 方法: `_saveTrajectory()` | 保留前导 `_` + snake_case → camelCase |
| 变量: `save_trajectories` | 变量: `saveTrajectories` | snake_case → camelCase |
| 常量: `LOCKED_FIELD_NAMES` | 常量: `LOCKED_FIELD_NAMES` | 全大写保持不变 |

> ⚠️ **前导 `_` 保留**：Python 的 `_private_method` → Kotlin 的 `_privateMethod`，不是 `privateMethod`

### ⛔ 文件名严格 1:1 映射规则

**文件名只做 snake_case → PascalCase 转换，不允许添加任何后缀或前缀。**

| ❌ 错误 | ✅ 正确 | 规则 |
|---------|---------|------|
| `DiscordAdapter.kt` | `Discord.kt` | 不加 `Adapter` 后缀 |
| `HonchoClient.kt` | `Client.kt` | 不加模块名前缀 |
| `FeishuCommentRules.kt` | `FeishuComment.kt` | 不改原始名称 |
| `TelegramBot.kt` | `Telegram.kt` | 不加功能后缀 |

**判断方法**：Python 文件名 `xxx_yyy.py` → Kotlin 文件名 `XxxYyy.kt`，仅此而已。
- `discord.py` → `Discord.kt`（不是 `DiscordAdapter.kt`）
- `client.py` → `Client.kt`（不是 `HonchoClient.kt`）
- `feishu_comment.py` → `FeishuComment.kt`（不是 `FeishuCommentRules.kt`）

**目录名映射**仅有一个例外：`acp_adapter/` → `acp/`（去掉 `_adapter` 后缀），其余目录名保持 1:1。

## 对齐流程（3 步）

### Step 1: 生成映射表

```bash
python3 skills/hermes-align/scripts/gen_mapping.py \
  --hermes hermes-agent/ \
  --output skills/hermes-align/references/mapping.json
```

### Step 2: 验证对齐度 + 生成未对齐清单

```bash
python3 skills/hermes-align/scripts/verify_align.py \
  --hermes hermes-agent/ \
  --android AndroidForClaw/hermes-android/src/main/java/com/xiaomo/hermes/hermes/ \
  --mapping skills/hermes-align/references/mapping.json \
  --output-todo skills/hermes-align/references/TODO.md
```

输出：
- 终端打印对齐度报告
- `references/TODO.md` — 完整的未对齐清单（缺失文件 + 缺失方法 + 缺失类）

### Step 3: 按 TODO.md 逐文件补齐

优先级：
1. `agent/` — 核心（CredentialPool、AuxiliaryClient、ContextCompressor 等）
2. `tools/` — 工具系统（SkillManagerTool、TerminalTool、BrowserTool 等）
3. `gateway/` — 平台适配器（Feishu、Telegram、Discord 等）
4. `plugins/` — 记忆后端（MemoryProvider 实现）
5. `environments/` — ToolCallParser（模型格式解析）
6. `acp/` — ACP 协议适配
7. `cron/` — 定时任务

## 参考文件

| 文件 | 说明 |
|------|------|
| `references/TODO.md` | **未对齐清单**（脚本自动生成，开发时首先查阅） |
| `references/mapping.json` | 自动生成的映射表（188 文件、234 类、3541 方法、912 常量） |
| `references/exceptions.json` | 排除不需要移植的目录/文件 |
| `references/type_mapping.md` | Python → Kotlin 类型速查 |
| `references/exceptions.md` | 允许的命名偏差 + Android 扩展标记规则 |

## 开发规则

- **不"优化"**：Python 叫什么，Kotlin 就叫什么（只做 case 转换）
- **不跳过模块**：即使 Android 上少用也要保持结构完整
- **不合并文件**：Python 一个文件 = Kotlin 一个类/文件
- **不合并类**：Python 一个类 = Kotlin 一个类，上游有几个就写几个
- **Android 扩展**（如 TaskerTool、KeepAliveWorker）单独标记 `// ANDROID_EXTENSION`，不计入对齐度
- **不碰 openclaw-android**：所有 Hermes 代码在 hermes-android 模块内
- **开发前先读 TODO.md**：知道哪些文件/方法缺失，避免重复工作
- **开发后跑 verify**：确认对齐度提升，更新 TODO.md
