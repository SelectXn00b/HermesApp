# 用户自定义 Skills 指南

## 📖 什么是 Skills？

Skills 是教 AI Agent 如何完成特定任务的知识文档（Markdown 格式）。

**核心理念**：
- **Tools（工具）**：基础能力（tap, screenshot等）
- **Skills（技能）**：教 Agent 如何使用工具完成复杂任务

## 📂 Skills 存放位置

```
Workspace Skills (用户自定义，最高优先级)
📁 /sdcard/AndroidForClaw/workspace/skills/my-skill/SKILL.md
```

## 📝 Skill 文件格式

```markdown
---
name: my-custom-skill
description: 这是我的自定义技能
metadata: { "hermes": { "always": false, "emoji": "🔧" } }
---

# My Custom Skill

这里写技能的具体内容...

## 使用的工具

- `tap(x, y)` - 点击
- `screenshot()` - 截图

## 操作步骤

1. 截图观察
2. 点击按钮
3. 验证结果
```

### Metadata 字段

```json
{
  "hermes": {
    "always": false,     // 是否始终加载
    "emoji": "🔧"        // 图标
  }
}
```

- `always: true` - 始终加载（消耗tokens，慎用）
- `always: false` - 按需加载（推荐）

## 🎓 编写 Skills 最佳实践

### 1. 具体的指令

❌ 差：
```markdown
打开应用，输入用户名和密码，点击登录。
```

✅ 好：
```markdown
1. 截图观察初始状态
   screenshot()

2. 点击用户名输入框（屏幕中部）
   tap(540, 800)
   wait(0.5)

3. 输入用户名
   type("testuser")

4. 验证登录成功
   wait(2)
   screenshot()
```

### 2. 包含错误处理

```markdown
## 处理登录失败

如果看到"用户名或密码错误"：
1. 截图确认错误信息
2. 清空输入框重试或返回
```

### 3. 使用占位符

```markdown
## 搜索商品

1. 输入商品名称（用户提供）
   type("{{user_search_term}}")
```

## 📱 创建第一个自定义 Skill

### 示例：淘宝商品搜索

**1. 创建目录**

```bash
adb shell mkdir -p /sdcard/AndroidForClaw/workspace/skills/taobao-search
```

**2. 编写 SKILL.md**

```markdown
---
name: taobao-search
description: 淘宝商品搜索流程
metadata: { "hermes": { "always": false, "emoji": "🛒" } }
---

# 淘宝商品搜索

## 搜索流程

### 1. 打开淘宝
open_app("com.taobao.taobao")
wait(3)
screenshot()

### 2. 点击搜索框
tap(540, 200)
wait(1)

### 3. 输入搜索词
type("{{商品名称}}")
wait(0.5)

### 4. 提交搜索
tap(960, 200)  // 搜索按钮
wait(2)
screenshot()

## 注意事项

- 坐标可能变化，需根据截图调整
- 网络加载可能需要更长时间
```

**3. 推送到手机**

```bash
adb push SKILL.md /sdcard/AndroidForClaw/workspace/skills/taobao-search/
```

**4. 测试**

重启 App 或等待热重载，然后测试：
```
"打开淘宝搜索耳机"
```

## 📚 更多示例

### 微信发消息

```markdown
---
name: wechat-send-message
description: 微信发送消息流程
metadata: { "hermes": { "always": false, "emoji": "💬" } }
---

# 微信发送消息

1. 打开微信
   open_app("com.tencent.mm")
   wait(2)

2. 搜索联系人
   tap(960, 150)
   type("{{联系人名称}}")
   wait(1)

3. 点击第一个结果
   tap(540, 400)

4. 输入并发送消息
   tap(540, 2200)
   type("{{消息内容}}")
   tap(960, 2200)
   screenshot()
```

## 🔄 热重载

- Workspace Skills 自动热重载
- 修改文件后无需重启应用

## ❓ 常见问题

**Q: Skill 太长会影响性能吗？**
A: 会消耗 tokens。建议 < 2000 tokens，使用 `always: false`

**Q: 可以用中文吗？**
A: 可以

**Q: 如何调试？**
A: 
- 使用 `log()` 输出调试信息
- 每步用 `screenshot()` 确认状态
- 查看日志：`adb logcat | grep SkillsLoader`

**Q: Skill 更新不生效？**
A: 检查文件路径，或重启应用

## 📖 参考

- 查看内置 Skills: `app/src/main/assets/skills/`
- 可用工具列表: 查看 `mobile-operations` skill
- 格式兼容: [AgentSkills.io](https://agentskills.io/)

---

**开始创建你的第一个 Skill 吧！** 🚀
