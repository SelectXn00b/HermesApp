# Device Tool 设计方案 — 对齐 Playwright/Hermes Browser Tool

## 背景

Hermes 的 `browser` tool 用 Playwright 模式：
- `snapshot` 获取 accessibility tree（带 ref ID）
- `act(kind, ref)` 通过 ref 操作元素
- 模型不需要坐标，只需要 ref

AndroidForClaw 要用同样的模式操控 Android 屏幕。

## 对照关系

| Hermes browser | AndroidForClaw device | 说明 |
|-----------------|----------------------|------|
| `browser(action="snapshot")` | `device(action="snapshot")` | 获取 UI 树 + ref |
| `browser(action="act", kind="click", ref="e5")` | `device(action="act", kind="tap", ref="e5")` | 点击元素 |
| `browser(action="act", kind="type", ref="e5", text="hello")` | `device(action="act", kind="type", ref="e5", text="hello")` | 在元素中输入 |
| `browser(action="act", kind="press", key="Enter")` | `device(action="act", kind="press", key="BACK")` | 按键 |
| `browser(action="act", kind="scroll")` | `device(action="act", kind="scroll", direction="down")` | 滚动 |
| `browser(action="act", kind="hover", ref="e5")` | `device(action="act", kind="long_press", ref="e5")` | 长按 |
| `browser(action="screenshot")` | `device(action="screenshot")` | 截图 |
| `browser(action="navigate", url="...")` | `device(action="open", package="com.tencent.mm")` | 打开 App |
| `browser(action="act", kind="wait", timeMs=2000)` | `device(action="act", kind="wait", timeMs=2000)` | 等待 |
| `browser(action="act", kind="close")` | `device(action="act", kind="home")` | 回到桌面 |

## Function Call Schema

### 统一入口：`device`

```json
{
  "type": "function",
  "function": {
    "name": "device",
    "description": "Control the Android device screen. Use snapshot to get UI elements with refs, then act on them.",
    "parameters": {
      "type": "object",
      "properties": {
        "action": {
          "type": "string",
          "enum": ["snapshot", "screenshot", "act", "open", "status"],
          "description": "Action to perform"
        },
        "kind": {
          "type": "string",
          "enum": ["tap", "type", "press", "long_press", "scroll", "swipe", "wait", "home", "back"],
          "description": "For action=act: the kind of interaction"
        },
        "ref": {
          "type": "string",
          "description": "Element ref from snapshot (e.g. 'e5')"
        },
        "text": {
          "type": "string",
          "description": "Text to type (for kind=type) or key name (for kind=press)"
        },
        "key": {
          "type": "string",
          "description": "Key to press: BACK, HOME, ENTER, TAB, etc."
        },
        "coordinate": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "Fallback [x, y] coordinate (when ref not available)"
        },
        "direction": {
          "type": "string",
          "enum": ["up", "down", "left", "right"],
          "description": "Scroll direction"
        },
        "amount": {
          "type": "integer",
          "description": "Scroll amount (default: 3)"
        },
        "timeMs": {
          "type": "integer",
          "description": "Wait time in milliseconds"
        },
        "package_name": {
          "type": "string",
          "description": "App package name for action=open"
        },
        "format": {
          "type": "string",
          "enum": ["tree", "compact", "interactive"],
          "description": "Snapshot format (default: compact)"
        }
      },
      "required": ["action"]
    }
  }
}
```

## 使用示例

### 1. 获取屏幕 UI 树

```json
device(action="snapshot")
```

返回：
```
[Screen: 1156x2510 AndroidForClaw]

navigation 'Main Menu' [ref=e1]
  button '设置' [ref=e2] (clickable)
  button '聊天' [ref=e3] (clickable)
  text '欢迎使用' [ref=e4]
input '搜索...' [ref=e5] (editable, focusable)
list 'Messages' [ref=e6] (scrollable)
  item '张三: 你好' [ref=e7] (clickable)
  item '李四: 明天见' [ref=e8] (clickable)
button '发送' [ref=e9] (clickable)
```

### 2. 通过 ref 点击元素

```json
device(action="act", kind="tap", ref="e7")
```

返回：
```
Tapped '张三: 你好' at (578, 800)
```

### 3. 在输入框中输入

```json
device(action="act", kind="type", ref="e5", text="hello world")
```

返回：
```
Typed 'hello world' into '搜索...' [ref=e5]
```

### 4. 按键

```json
device(action="act", kind="press", key="BACK")
```

### 5. 滚动

```json
device(action="act", kind="scroll", direction="down", amount=3)
```

或者在指定元素上滚动：
```json
device(action="act", kind="scroll", ref="e6", direction="down")
```

### 6. 长按

```json
device(action="act", kind="long_press", ref="e7")
```

### 7. 截图

```json
device(action="screenshot")
```

返回截图 base64 + 基本信息。

### 8. 打开 App

```json
device(action="open", package_name="com.tencent.mm")
```

### 9. 等待

```json
device(action="act", kind="wait", timeMs=2000)
```

### 10. 回到桌面

```json
device(action="act", kind="home")
```

### 11. 坐标 fallback

当无障碍不可用，或 ref 定位不到时，可以用坐标：
```json
device(action="act", kind="tap", coordinate=[500, 300])
```

### 12. 组合操作：典型流程

```
→ device(action="open", package_name="com.tencent.mm")
← App opened: 微信

→ device(action="snapshot")
← [Screen: 微信]
  tab '微信' [ref=e1] (selected)
  tab '通讯录' [ref=e2]
  tab '发现' [ref=e3]
  tab '我' [ref=e4]
  search '搜索' [ref=e5]
  list 'Conversations' [ref=e6] (scrollable)
    item '张三' [ref=e7] (clickable)
    item '工作群' [ref=e8] (clickable)

→ device(action="act", kind="tap", ref="e7")
← Tapped '张三' at (578, 400)

→ device(action="snapshot")
← [Screen: 微信 > 张三]
  list 'Messages' [ref=e10] (scrollable)
    message '你好' [ref=e11]
    message '在吗？' [ref=e12]
  input '输入消息...' [ref=e13] (editable)
  button '发送' [ref=e14]

→ device(action="act", kind="type", ref="e13", text="我在的")
← Typed '我在的' into '输入消息...'

→ device(action="act", kind="tap", ref="e14")
← Tapped '发送'
```

## Ref 生成规则

从 Android Accessibility Tree 生成 ref：

```kotlin
// 遍历 AccessibilityNodeInfo 树
fun buildRefTree(rootNode: AccessibilityNodeInfo): List<RefNode> {
    val nodes = mutableListOf<RefNode>()
    var refCounter = 1
    
    fun traverse(node: AccessibilityNodeInfo, depth: Int) {
        // 跳过不可见、无意义的节点
        if (!node.isVisibleToUser) return
        
        val text = node.text?.toString() 
            ?: node.contentDescription?.toString()
        val className = node.className?.toString()?.substringAfterLast('.')
        
        // 只给有意义的节点分配 ref
        val isInteractive = node.isClickable || node.isEditable || node.isScrollable
        val hasText = !text.isNullOrBlank()
        
        if (isInteractive || hasText) {
            val ref = "e${refCounter++}"
            nodes.add(RefNode(
                ref = ref,
                role = mapClassName(className),  // Button, Input, Text, List, etc.
                text = text,
                bounds = node.boundsInScreen,
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focusable = node.isFocusable,
                depth = depth
            ))
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverse(it, depth + 1) }
        }
    }
    
    traverse(rootNode, 0)
    return nodes
}
```

## 输出格式（对齐 Playwright snapshot）

### compact 格式（默认）
```
[Screen: 1156x2510 微信]
button '设置' [ref=e1] (clickable)
input '搜索' [ref=e2] (editable)
list 'Messages' [ref=e3] (scrollable)
  item '张三: 你好' [ref=e4] (clickable)
```

### tree 格式（详细）
```
[Screen: 1156x2510 微信]
├─ FrameLayout
│  ├─ button '设置' [ref=e1] bounds=(0,100,200,200) clickable
│  ├─ EditText '搜索' [ref=e2] bounds=(200,100,1000,200) editable focusable
│  └─ RecyclerView [ref=e3] scrollable
│     ├─ LinearLayout '张三: 你好' [ref=e4] bounds=(0,300,1156,500) clickable
│     └─ LinearLayout '李四: 明天见' [ref=e5] bounds=(0,500,1156,700) clickable
```

### interactive 格式（只显示可交互元素）
```
[Screen: 微信] Interactive elements:
[e1] button '设置' (100, 150)
[e2] input '搜索' (600, 150)
[e4] item '张三: 你好' (578, 400)
[e5] item '李四: 明天见' (578, 600)
```

## 代码结构

```
agent/tools/device/
├── DeviceTool.kt              — 统一入口 tool (action dispatch)
├── actions/
│   ├── SnapshotAction.kt      — snapshot: 获取 UI 树 + ref
│   ├── ScreenshotAction.kt    — screenshot: 截图
│   ├── TapAction.kt           — tap: 通过 ref 或坐标点击
│   ├── TypeAction.kt          — type: 在 ref 元素中输入
│   ├── PressAction.kt         — press: 按键 (BACK/HOME/ENTER)
│   ├── ScrollAction.kt        — scroll: 滚动
│   ├── LongPressAction.kt     — long_press: 长按
│   ├── SwipeAction.kt         — swipe: 滑动
│   ├── WaitAction.kt          — wait: 等待
│   └── OpenAction.kt          — open: 打开 App
├── RefManager.kt              — ref ID 管理和坐标映射
├── SnapshotFormatter.kt       — UI 树格式化 (compact/tree/interactive)
└── DeviceToolResult.kt        — 返回值封装
```

## Ref 到坐标的映射

```kotlin
class RefManager {
    // 每次 snapshot 后缓存 ref → bounds 映射
    private val refMap = mutableMapOf<String, Rect>()
    
    fun resolveRef(ref: String): Pair<Int, Int>? {
        val bounds = refMap[ref] ?: return null
        return Pair(bounds.centerX(), bounds.centerY())
    }
    
    fun updateRefs(nodes: List<RefNode>) {
        refMap.clear()
        nodes.forEach { refMap[it.ref] = it.bounds }
    }
}
```

模型调用 `device(action="act", kind="tap", ref="e5")` 时：
1. `RefManager.resolveRef("e5")` → 得到坐标 `(578, 400)`
2. 执行 `AccessibilityService.performAction(ACTION_CLICK)` 或 `input tap 578 400`

## 兼容性

旧的 tool 名称继续保留，重定向到 `device`：
```kotlin
// AndroidToolRegistry
"tap" → device(action="act", kind="tap", ...)
"screenshot" → device(action="screenshot")
"get_view_tree" → device(action="snapshot")
"swipe" → device(action="act", kind="swipe", ...)
// etc.
```

## 迁移路径

1. **Phase 1**: 实现 `DeviceTool` + `SnapshotAction` + `RefManager`，旧 tool 保留
2. **Phase 2**: 在 system prompt 中推荐使用 `device` tool，旧 tool 标记 deprecated
3. **Phase 3**: 模型完全切到 `device`，移除旧 tool
