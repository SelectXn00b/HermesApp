# API 文档：`ui.d.ts`

`ui.d.ts` 描述了两层 UI 能力：

- `Tools.UI`：直接执行点击、输入、滑动等动作。
- `UINode`：把页面结构包装成可搜索、可遍历、可交互的对象模型。

## 运行时入口

```ts
Tools.UI
UINode
```

`UINode` 也是全局类，可以直接使用。

## `Tools.UI` 命名空间

### 页面读取

#### `getPageInfo()`

获取当前页面信息，返回 `UIPageResultData`。

### 直接动作

#### `tap(x, y)`

按坐标点击。

#### `longPress(x, y)`

按坐标长按。

#### `setText(text, resourceId?)`

向输入框设置文本；若给出 `resourceId`，会尝试定位对应输入框。

#### `pressKey(keyCode)`

按键事件。

#### `swipe(startX, startY, endX, endY, duration?)`

执行滑动。

### 元素点击：`clickElement(...)`

该方法有多种重载形式，是 `ui.d.ts` 中最复杂的一组定义。

支持的调用方式包括：

```ts
clickElement(resourceId)
clickElement(bounds)
clickElement(resourceId, index)
clickElement(type, value)
clickElement(type, value, index)
clickElement({ resourceId?, className?, text?, contentDesc?, bounds?, index?, partialMatch?, isClickable? })
```

其中：

- `bounds` 格式为 `"[x1,y1][x2,y2]"`
- `type` 可为 `resourceId | className | bounds`
- 对象参数支持按多种属性组合查找

### 子代理

#### `runSubAgent(intent, maxSteps?, agentId?, targetApp?)`

```ts
runSubAgent(intent: string, maxSteps?: number, agentId?: string, targetApp?: string): Promise<AutomationExecutionResultData>
```

适合用高层目标描述驱动 UI 自动化。

## `UINode` 类

### 创建方式

通常有两种：

```ts
const root = await UINode.getCurrentPage();
```

或者：

```ts
const page = await Tools.UI.getPageInfo();
const root = UINode.fromPageInfo(page);
```

### 常用只读属性

- `className`
- `text`
- `contentDesc`
- `resourceId`
- `bounds`
- `isClickable`
- `rawNode`
- `parent`
- `path`
- `centerPoint`
- `children`
- `childCount`

### 文本提取

- `allTexts(trim?, skipEmpty?)`
- `textContent(separator?)`
- `hasText(text, caseSensitive?)`

### 搜索方法

- `find(criteria, deep?)`
- `findAll(criteria, deep?)`
- `findByText(text, options?)`
- `findAllByText(text, options?)`
- `findById(id, options?)`
- `findAllById(id, options?)`
- `findByClass(className, options?)`
- `findAllByClass(className, options?)`
- `findByContentDesc(description, options?)`
- `findAllByContentDesc(description, options?)`
- `findClickable()`
- `closest(criteria)`

### 动作方法

- `click()`
- `longPress()`
- `setText(text)`
- `wait(ms?)`
- `clickAndWait(ms?)`
- `longPressAndWait(ms?)`

其中 `wait()` / `clickAndWait()` / `longPressAndWait()` 返回的是更新后的 `UINode` 页面状态。

### 工具方法

- `toString()`
- `toTree(indent?)`
- `toTreeString(indent?)`
- `toFormattedString?()`
- `equals(other)`

### 静态方法

- `fromPageInfo(pageInfo)`
- `getCurrentPage()`
- `findAndWait(query, delayMs?)`
- `clickAndWait(query, delayMs?)`
- `longPressAndWait(query, delayMs?)`

## 示例

### 读取当前页面并查找文本

```ts
const root = await UINode.getCurrentPage();
const button = root.findByText('确定');
if (button) {
  await button.click();
}
```

### 使用 `clickElement` 的对象模式

```ts
await Tools.UI.clickElement({
  text: '登录',
  partialMatch: false,
  isClickable: true
});
```

### 设置输入框文本

```ts
await Tools.UI.setText('hello world', 'com.example:id/input');
```

### 调用 UI 子代理

```ts
const result = await Tools.UI.runSubAgent(
  '打开系统设置并进入 WLAN 页面',
  20,
  undefined,
  'com.android.settings'
);
complete(result);
```

## 相关文件

- `examples/types/ui.d.ts`
- `examples/types/results.d.ts`
- `docs/package_dev/results.md`
