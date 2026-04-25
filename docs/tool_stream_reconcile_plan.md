# Tool Stream Reconcile 方案说明

## 计划进度

- [x] 定义侧事件通道最小协议：`SAVEPOINT / ROLLBACK + id`
- [x] 设计并开始接入可修订文本流包装
- [x] 让 `EnhancedAIService` 和中间两次 `share()` 继续透传侧通道
- [x] 在 OpenAI 兼容链路里把“异常补闭合”切到“异常回滚到 savepoint”
- [x] 让主聊天 UI 在回滚时重建当前显示流
- [x] 再做一轮代码自查和边界梳理
- [x] 视需要补充剩余消费端的一致性处理
- [ ] 最终整理文档结论

本文档只讨论一个最小可落地方案。

前提：

- 当前版本已发布。
- 现有 `tool_xxxx` 随机 XML 标签方案必须继续保留并向前兼容。
- 不做“把整个 `Stream<String>` 改造成统一事件流”这种大改。
- 目标是修复 OpenAI 兼容链路里，tool 流式输出中途断网重试后产生的“半截旧 tool + 新 tool”污染。

典型坏形态：

```xml
<tool_ABC name="read_file">
<param name="path">/a/b

<tool_bcd name="read_file">
<param name="path">/a/b/c.txt</param>
</tool_bcd>
```

这里的问题不是“tool id 不一致”，而是：

- 半截旧 tool 已经进入当前显示。
- 半截旧 tool 也可能进入后续重试上下文。
- 重试后模型重新生成一个新的随机 `tool_xxxx`。
- 于是旧残片和新完整包同时存在。

## 1. 目标

目标只有一个：

- 允许流式显示继续存在。
- 但一旦发现本轮 assistant 输出属于“未提交内容”，就可以把它撤回到某个安全点。

这里要的不是“忽略后续文本”，而是“真正撤回已经显示出来的脏半截”。
现在的提交边界不再放在 `tool` 级别，而是放在“本轮 assistant attempt”级别。

## 2. 最小协议

主文本流继续保持不变：

- 还是 `Stream<String>`
- 还是正常 `emit(chunk)`
- 还是保留 `tool_xxxx` 现有协议

额外增加一个侧事件通道：

```kotlin
interface RevisableTextStream : Stream<String> {
    val eventChannel: Stream<TextStreamEvent>
}

data class TextStreamEvent(
    val eventType: TextStreamEventType,
    val id: String
)

enum class TextStreamEventType {
    SAVEPOINT,
    ROLLBACK
}
```

只保留两个事件：

- `SAVEPOINT(id)`：记录当前全文快照
- `ROLLBACK(id)`：回到这个快照

事件里只带两项：

- `eventType`
- `id`

不传文本，不传 offset，不传字符数。

## 3. 为什么只做这两个事件

不建议做下面这些：

- `RollbackChars(n)`
- `DeleteLastChunk`
- 文本终止符
- 复杂状态机事件

原因很简单：

- 字符级回退太脆弱，分块一变就错。
- 终止符会污染正文和历史。
- 复杂事件会把方案做重。
- 现在真正缺的是“回到安全快照”的能力，不是别的。

所以第一版就只保留：

- `SAVEPOINT`
- `ROLLBACK`

## 4. Provider 侧行为

provider 侧只做最小动作。

本轮 assistant request 开始前：

- 发一个 `SAVEPOINT(id)`

如果本轮正常完成：

- 不需要额外事件

如果本轮中途断网、中止、重试：

- 发一个 `ROLLBACK(id)`

也就是说：

- 本轮正常完成，不需要“提交事件”
- 本轮非正常结束，才需要回退

这样做的含义是：

- 旧 attempt 的所有已显示文本整体作废
- 重试时重新发送原始 `message + history`
- 不再依赖 LLM 的“continue from interruption”续写提示

## 5. 实际链路

当前链路不是 provider 直接到 UI，而是：

1. `provider`
2. `EnhancedAIService`
3. `AIMessageManager`
4. `MessageProcessingDelegate`
5. `ChatMessage.contentStream`
6. UI 渲染器

这里有一个关键现实：

- 这条链路里文本流会被 `share()` 至少两次。

也就是说，如果只是让 `EnhancedAIService` 返回一个“带事件通道的自定义流”，但后面的 `share()` 不认识这个能力，那么侧事件通道会在中途丢失。

所以方案不能只写成：

- “EnhancedAIService 包一层就好了”

而必须写成：

- “EnhancedAIService 发出侧通道能力，后续链路继续透传这个能力，一直到 UI”

## 6. 最小落点

### 6.1 EnhancedAIService

`EnhancedAIService` 可以继续故技重施：

- 包装 provider 返回的主文本流
- 同时把侧事件通道也一起挂在返回对象上

它本身也可以订阅该事件通道，用现有全文覆盖能力维护自己的运行时状态：

- `SAVEPOINT(id)` 时，本地保存 `streamBuffer.toString()`
- `ROLLBACK(id)` 时，恢复该快照
- 用恢复后的全文重置当前轮内容

这一步是为了让：

- 当前轮累积文本
- 后续重试上下文

都能回到安全点。

### 6.2 AIMessageManager / MessageProcessingDelegate

这两个位置不能只透传主文本流，还必须继续透传事件侧通道。

原因：

- 这里会再次 `share()` 文本流
- 如果只 share 主流，不 share 侧通道，事件能力就断了

因此这里的要求很简单：

- 主文本流怎么 share，侧事件流也要跟着 share
- 往下分发的仍然是“同一个文本流对象 + 同一个事件流对象”

另外 `MessageProcessingDelegate` 自己有 `contentBuilder` 一类的本地累积状态。

所以它也需要响应事件：

- `SAVEPOINT(id)` 时存一份当前全文
- `ROLLBACK(id)` 时把本地累积内容回退到该快照

否则就会出现：

- UI 看起来回退了
- 但 delegate 最后落库的内容还是脏的

### 6.3 UI

UI 不能只依赖 `content` 字段修复。

原因：

- 流式阶段 UI 直接消费 `contentStream`
- 已经进了流式渲染器的半截文本，不会因为上层 `content` 改了就自动消失

所以 UI 侧也必须理解这两个事件：

- `SAVEPOINT(id)`：存当前显示快照
- `ROLLBACK(id)`：恢复到该快照

UI 的本质动作只有两个：

1. 清掉当前流式渲染器里已经积累的脏状态
2. 用快照重新建立当前显示

这样用户看到的才是真撤回，而不是“逻辑上回退了，画面上没回退”。

## 7. 为什么这仍然算最小改动

这个方案没有动下面这些根本前提：

- 不改现有 `tool_xxxx` 兼容协议
- 不改历史存储格式
- 不把主文本流改成统一事件协议
- 不把 tool 输出改成一次性整体返回

新增的只有：

1. 一个侧事件通道
2. 两个事件类型
3. 每个消费端自己在 `SAVEPOINT` 时存快照，在 `ROLLBACK` 时恢复

这是最接近“依旧是撤回”，同时又不至于把整条流式架构打烂的做法。

## 8. 关键注意事项

### 8.1 `id` 不是 `tool_xxxx`

这里的 `id` 建议只作为 savepoint id 使用。

不要把它和 `tool_xxxx` 强行绑定。

原因：

- `tool_xxxx` 是现有已发布协议的一部分
- savepoint id 是运行时撤回控制的一部分
- 两者职责不同

兼容上更稳妥的做法是：

- `tool_xxxx` 继续按现在的随机策略生成
- savepoint id 单独生成

### 8.2 侧事件流也要可重放

如果事件流不支持 replay，就会出现一种坏情况：

- 下游后订阅者收到了 `ROLLBACK(id)`
- 但没收到早先的 `SAVEPOINT(id)`
- 于是找不到快照

所以侧事件流也必须具备和主流一致的可分发、可重放能力。

### 8.3 不能只修上游

如果只在 `EnhancedAIService` 做回退，而下游不跟：

- delegate 的累积文本可能还是脏的
- UI 渲染状态可能还是脏的

如果只在 UI 修，而上游不跟：

- 重试上下文和最终落库内容还是脏的

所以这不是“一个点修完”的问题，而是“同一个 side channel 一路分发到底”的问题。

## 9. 最终建议

建议按下面的方式理解和落地：

- 主通道继续是 `Stream<String>`
- 旁路增加一个事件通道
- 事件只保留 `SAVEPOINT / ROLLBACK`
- 事件只带 `eventType + id`
- `EnhancedAIService` 返回流时，同时把该事件通道继续往下分发
- 这条侧通道必须穿过后续两次 `share()`
- `MessageProcessingDelegate` 用它修正本地累积内容
- UI 用它修正当前流式渲染状态

这就是当前约束下最小、最直接、也最符合“撤回”语义的方案。
