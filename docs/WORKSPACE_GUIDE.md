# AndroidForClaw Workspace 使用指南

## 概述

AndroidForClaw workspace 对齐 Hermes 架构，为用户提供可直接访问和编辑的工作区。

## 🎯 Workspace 设计理念

### Hermes vs AndroidForClaw

| 特性 | Hermes | AndroidForClaw |
|------|----------|----------------|
| **Workspace 路径** | `~/.hermes/workspace/` | `/sdcard/.hermes/workspace/` |
| **Skills 路径** | `~/.hermes/workspace/skills/` | `/sdcard/.hermes/workspace/skills/` |
| **访问方式** | 文件系统直接访问 | 文件管理器直接访问 |
| **权限要求** | 文件系统权限 | MANAGE_EXTERNAL_STORAGE |

### 为什么选择 `/sdcard/.hermes/workspace/`？

1. **用户可访问** - 位于外部存储根目录，任何文件管理器都能访问
2. **独立目录** - 与 Hermes 类似，独立于应用数据目录
3. **易于备份** - 用户可以轻松备份整个 workspace
4. **跨设备共享** - 可以在多个设备间同步 skills
5. **版本控制** - 可以使用 Git 等工具管理 skills

## 📁 目录结构

### 完整结构

```
/sdcard/
├── androidforclaw-workspace/          ← 用户工作区（对标 ~/.hermes/workspace/）
│   ├── skills/                        ← 用户自定义 Skills
│   │   ├── my-custom-skill/
│   │   │   └── SKILL.md
│   │   ├── wechat-automation/
│   │   │   ├── SKILL.md
│   │   │   └── scripts/
│   │   │       └── login.js
│   │   └── game-testing/
│   │       └── SKILL.md
│   ├── sessions/                      ← 会话数据（未来功能）
│   └── cache/                         ← 缓存目录（未来功能）
│
└── AndroidForClaw/                    ← 应用数据目录
    ├── config/                        ← 配置文件
    │   ├── hermes.json              ← 主配置文件
    │   └── models.json                ← 模型提供商配置
    ├── .skills/                       ← 托管 Skills（未来功能）
    └── logs/                          ← 日志文件
```

### Skills 加载优先级

```
高 → 低

1. Workspace Skills     /sdcard/.hermes/workspace/skills/     ← 用户自定义（最高优先级）
2. Managed Skills       /sdcard/.hermes/.skills/              ← 包管理器安装
3. Bundled Skills       app/src/main/assets/skills/                  ← 内置（只读）
```

**覆盖规则**: 高优先级的 skill 会完全覆盖低优先级的同名 skill。

## 🛠️ 如何使用 Workspace

### 1. 创建自定义 Skill

**步骤**:

1. 打开文件管理器（推荐使用 Solid Explorer、FX File Explorer 等）
2. 导航到 `/sdcard/.hermes/workspace/skills/`
3. 创建新目录，如 `my-skill/`
4. 在目录内创建 `SKILL.md` 文件
5. 编写 skill 内容（参考 Hermes 格式）

**示例**: 创建微信自动化 skill

```bash
# 目录结构
/sdcard/.hermes/workspace/skills/wechat-automation/
└── SKILL.md
```

**SKILL.md 内容**:

```markdown
---
name: wechat-automation
description: 微信自动化操作 - WeChat automation operations
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "💬",
      "version": "1.0.0",
      "category": "automation"
    }
  }
---

# WeChat Automation Skill

微信自动化操作技能

## 🎯 When to Use

Use this skill when you need to:

✅ **自动发送消息** - Send messages automatically
✅ **读取聊天记录** - Read chat history
✅ **群组管理** - Manage WeChat groups

## 📚 Available Tools

**tap(x, y)** - 点击屏幕坐标
**type(text)** - 输入文字
**screenshot()** - 截图观察
**open_app("com.tencent.mm")** - 打开微信

## 🔄 Workflow Pattern

```
1. 打开微信
   open_app("com.tencent.mm")
   wait(2)
   screenshot()

2. 导航到联系人
   tap(x, y)  # 点击联系人按钮
   wait(1)
   screenshot()

3. 选择聊天对象
   tap(x, y)  # 点击目标联系人
   wait(1)
   screenshot()

4. 发送消息
   tap(x, y)  # 点击输入框
   type("Hello!")
   tap(x, y)  # 点击发送
```

## 💡 Examples

### Example 1: 发送消息

```
# 1. 打开微信
open_app("com.tencent.mm")
wait(2)
screenshot()

# 2. 搜索联系人
tap(540, 200)  # 搜索框
type("张三")
wait(1)
screenshot()

# 3. 点击联系人
tap(540, 400)  # 第一个搜索结果
wait(1)
screenshot()

# 4. 发送消息
tap(540, 1800)  # 输入框
type("你好，这是自动消息")
tap(960, 1800)  # 发送按钮
wait(0.5)
screenshot()

stop("消息发送成功")
```

## 📋 Best Practices

- 使用 screenshot() 确认每一步状态
- 根据实际屏幕分辨率调整坐标
- 处理网络延迟和加载时间

---

**WeChat Automation** - AndroidForClaw Custom Skill 💬
```

### 2. 覆盖内置 Skill

如果你想修改内置 skill 的行为：

1. 在 workspace 中创建同名 skill
2. 完全自定义内容
3. AndroidForClaw 会优先加载 workspace 版本

**示例**: 覆盖 `app-testing` skill

```bash
# 创建目录
/sdcard/.hermes/workspace/skills/app-testing/
└── SKILL.md  # 你的自定义版本
```

### 3. 热重载（Hot Reload）

AndroidForClaw 支持 workspace skills 的热重载：

- **自动检测**: 当你修改 `SKILL.md` 文件时，app 会自动重新加载
- **无需重启**: 修改立即生效
- **开发友好**: 快速迭代和测试

**启用方式**: 自动启用（如果 workspace 目录存在）

### 4. 备份和恢复

**备份 workspace**:

```bash
# 使用 adb 备份
adb pull /sdcard/.hermes/workspace/ ./backup/

# 或使用文件管理器
# 复制整个 androidforclaw-workspace 目录到电脑
```

**恢复 workspace**:

```bash
# 使用 adb 恢复
adb push ./backup/androidforclaw-workspace/ /sdcard/

# 或使用文件管理器
# 复制到 /sdcard/ 目录下
```

### 5. 版本控制（Git）

你可以使用 Git 管理 workspace skills：

```bash
# 在 workspace 目录初始化 Git
cd /sdcard/.hermes/workspace/
git init

# 添加 .gitignore
echo "cache/" > .gitignore
echo "sessions/" >> .gitignore

# 提交 skills
git add skills/
git commit -m "Initial commit: custom skills"

# 推送到远程仓库
git remote add origin https://github.com/yourusername/my-skills.git
git push -u origin main
```

## 📝 Skill 开发指南

### Hermes 格式规范

所有 skills 必须遵循 Hermes AgentSkills.io 格式：

**必需部分**:

1. ✅ **YAML Frontmatter** - 元数据（name, description, metadata）
2. ✅ **When to Use** - 使用场景（带 ✅ checkmarks）
3. ✅ **Available Tools** - 可用工具列表
4. ✅ **Workflow Pattern** - 工作流程
5. ✅ **Examples** - 具体示例
6. ✅ **Best Practices** - 最佳实践（带 ✅ ❌ 标记）
7. ✅ **Troubleshooting** - 故障排查（可选但推荐）

**参考模板**: `app/src/main/assets/skills/create-skill/SKILL.md`

### 测试 Skill

1. **在 workspace 创建 skill**
2. **启动 AndroidForClaw**
3. **在对话中测试**: "使用 my-skill 执行任务"
4. **查看日志**: 检查 skill 是否被加载和使用
5. **迭代优化**: 根据效果调整 skill 内容

### 调试技巧

**查看 skill 加载日志**:

```bash
adb logcat | grep SkillsLoader
```

**示例输出**:

```
SkillsLoader: 开始加载 Skills...
SkillsLoader: 扫描 Bundled Skills: 12 个目录
SkillsLoader: ✅ Bundled: mobile-operations (2500 tokens)
SkillsLoader: 扫描 Workspace Skills: 2 个目录
SkillsLoader: ✅ Workspace (新增): wechat-automation (1200 tokens)
SkillsLoader: Skills 加载完成: 总计 13 个
```

## 🔧 高级用法

### 1. Skill 依赖（requires）

你可以声明 skill 的依赖：

```yaml
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "🔧",
      "requires": {
        "bins": ["adb"],
        "env": ["OPENAI_API_KEY"],
        "config": ["wechat_token"]
      }
    }
  }
```

### 2. Always Skills

设置 `always: true` 让 skill 始终加载：

```yaml
metadata:
  {
    "hermes": {
      "always": true,  # 始终加载到系统提示词
      "emoji": "🤖"
    }
  }
```

### 3. 多文件 Skill

复杂 skill 可以包含多个文件：

```
/sdcard/.hermes/workspace/skills/complex-skill/
├── SKILL.md          ← 主文件（必需）
├── scripts/
│   ├── helper.js     ← JavaScript 脚本
│   └── data.json     ← 数据文件
└── README.md         ← 说明文档
```

**在 SKILL.md 中引用**:

```markdown
## Script Files

This skill uses helper scripts:
- `scripts/helper.js` - Data processing
- `scripts/data.json` - Configuration data
```

## 🚀 最佳实践

### DO ✅

- ✅ 使用清晰的 skill 名称（kebab-case）
- ✅ 提供详细的使用示例
- ✅ 包含故障排查指南
- ✅ 定期备份 workspace
- ✅ 使用版本控制管理 skills
- ✅ 遵循 Hermes 格式规范

### DON'T ❌

- ❌ 在 skill 名称中使用空格或特殊字符
- ❌ 硬编码敏感信息（API keys, 密码）
- ❌ 创建过于庞大的 skill（拆分为多个）
- ❌ 忽略错误处理和边界情况
- ❌ 修改 bundled skills（使用覆盖机制）

## 📚 资源

- **Hermes Skills 文档**: https://docs.hermes.ai/tools/skills
- **AgentSkills.io**: https://agentskills.io
- **内置 Skill 参考**: `app/src/main/assets/skills/`
- **Skill 创建模板**: `app/src/main/assets/skills/create-skill/SKILL.md`

## ❓ 常见问题

### Q: Workspace 目录不存在怎么办？

**A**: AndroidForClaw 不会自动创建 workspace 目录。你需要手动创建：

```bash
# 使用 adb
adb shell mkdir -p /sdcard/.hermes/workspace/skills/

# 或使用文件管理器
# 导航到 /sdcard/
# 创建目录 androidforclaw-workspace/skills/
```

### Q: 我的 skill 没有被加载？

**A**: 检查以下几点：

1. 目录结构正确？`/sdcard/.hermes/workspace/skills/my-skill/SKILL.md`
2. SKILL.md 文件名大小写正确？（必须是 `SKILL.md`）
3. YAML frontmatter 格式正确？
4. 查看日志：`adb logcat | grep SkillsLoader`

### Q: 如何禁用某个 bundled skill？

**A**: 在 workspace 创建同名 skill，内容为空或禁用说明：

```markdown
---
name: unwanted-skill
description: 此 skill 已禁用
metadata:
  {
    "hermes": {
      "always": false,
      "emoji": "🚫"
    }
  }
---

# Disabled Skill

此 skill 已被用户禁用。
```

### Q: 可以在 workspace 中使用子目录吗？

**A**: 目前不支持嵌套子目录。所有 skills 必须在 `/sdcard/.hermes/workspace/skills/` 的第一级。

```
✅ 正确:
/sdcard/.hermes/workspace/skills/my-skill/SKILL.md

❌ 错误:
/sdcard/.hermes/workspace/skills/category/my-skill/SKILL.md
```

### Q: Workspace 占用多少空间？

**A**: 非常小。单个 skill 通常 5-50KB。即使有 100 个自定义 skills，也只占用几 MB。

### Q: 如何分享我的 skills？

**A**:

1. **Git 仓库**: 推送到 GitHub/GitLab
2. **ZIP 文件**: 压缩 skills 目录分享
3. **AgentSkills.io**: 发布到 skills 市场（未来功能）

## 🎉 总结

AndroidForClaw workspace 完全对齐 Hermes 架构，为用户提供：

- 🎯 **独立工作区** - 与 Hermes 一致的目录结构
- 📝 **用户可编辑** - 通过文件管理器直接访问
- ⚡ **热重载** - 修改立即生效
- 🔄 **版本控制** - 可使用 Git 管理
- 📦 **易于备份** - 完整备份恢复支持

开始创建你的第一个自定义 skill 吧！🚀

---

**AndroidForClaw Workspace** - Aligned with Hermes Architecture 🤖📱
