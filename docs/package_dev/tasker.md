# API 文档：`tasker.d.ts`

`tasker.d.ts` 只定义了一组很小但很实用的 Tasker 集成 API：通过 `Tools.Tasker` 主动触发 Tasker 侧事件。

## 作用

- 让包内脚本把结果、状态或上下文发送给 Tasker。
- 适合把 Assistance 的工具链和既有的 Tasker 自动化流程串起来。

## 命名空间

类型命名空间：

```ts
Tasker
```

运行时入口：

```ts
Tools.Tasker
```

## 类型定义

### `Tasker.TriggerTaskerEventParams`

可用字段如下：

- `task_type: string`：Tasker 侧配置的事件标识，必填。
- `arg1?: string` ~ `arg5?: string`：最多 5 个简单字符串参数。
- `args_json?: string`：复杂数据、数组或对象时，使用 JSON 字符串传递。

## 运行时 API

### `Tools.Tasker.triggerEvent(params)`

```ts
triggerEvent(params: TriggerTaskerEventParams): Promise<string>
```

说明：

- 向 Tasker 发送一个事件。
- 返回值是原生层返回的状态字符串，不是 `results.d.ts` 里的结构化结果对象。

## 示例

### 发送简单事件

```ts
await Tools.Tasker.triggerEvent({
  task_type: "sync_notes",
  arg1: "daily",
  arg2: "force"
});
```

### 发送结构化参数

```ts
await Tools.Tasker.triggerEvent({
  task_type: "import_payload",
  args_json: JSON.stringify({
    source: "assistance",
    timestamp: Date.now(),
    items: ["a", "b", "c"]
  })
});
```

## 使用建议

- 简单参数优先使用 `arg1` ~ `arg5`。
- 复杂参数统一放进 `args_json`，避免在 Tasker 里做二次拼接。
- `task_type` 必须与 Tasker 中监听的事件名完全一致。

## 相关文件

- `examples/types/tasker.d.ts`
- `examples/types/index.d.ts`
- `docs/package_dev/index.md`
