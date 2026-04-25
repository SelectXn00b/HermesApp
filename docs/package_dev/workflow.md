# API 文档：`workflow.d.ts`

`workflow.d.ts` 描述的是 `Workflow` 类型命名空间，以及运行时入口 `Tools.Workflow`。它围绕工作流的节点、连接、增删改查与触发执行展开。

## 作用

当前定义覆盖：

- 工作流节点与连接类型。
- 创建、读取、更新、局部补丁、删除、触发。
- 工作流详情和列表结果类型。

## 类型命名空间与运行时入口

类型命名空间：

```ts
Workflow
```

运行时入口：

```ts
Tools.Workflow
```

注意：`Tools.Workflow` 暴露的是 `Workflow.Runtime` 接口里的方法。

## 核心类型

### 节点类型

`Workflow.Node` 是以下联合类型之一：

- `Workflow.Trigger`
- `Workflow.Execute`
- `Workflow.Condition`
- `Workflow.Logic`
- `Workflow.Extract`

### `Workflow.NodeInput`

这是创建或更新工作流时最重要的输入类型。公共字段包括：

- `id?`
- `type: 'trigger' | 'execute' | 'condition' | 'logic' | 'extract'`
- `name?`
- `description?`
- `position?`

按节点类型还可带上不同字段：

- 触发器：`triggerType`、`triggerConfig`
- 执行节点：`actionType`、`actionConfig`、`jsCode`
- 条件节点：`left`、`operator`、`right`
- 提取节点：`source`、`mode`、`expression`、`group`、`defaultValue` 等
- 逻辑节点：通过 `type: 'logic'` 与操作符表达

### `Workflow.ParameterValueInput`

输入值既可以是：

- 直接字面量：`string | number | boolean | null`
- 引用对象：`{ value?, nodeId?, ref?, refNodeId? }`

### 连接类型

#### `Workflow.ConnectionInput`

- `id?`
- `sourceNodeId?`
- `targetNodeId?`
- `condition?: ConnectionCondition | null`

#### `Workflow.ConnectionCondition`

支持关键字：

- `true`
- `false`
- `on_success`
- `success`
- `ok`
- `on_error`
- `error`
- `failed`

也支持正则条件字符串。

### Patch 类型

局部更新支持：

- `PatchOperation = 'add' | 'update' | 'remove'`
- `NodePatch`
- `ConnectionPatch`
- `PatchParams`

## 运行时 API

### `Tools.Workflow.getAll()`

获取全部工作流，返回 `WorkflowListResultData`。

### `Tools.Workflow.create(name, description?, nodes?, connections?, enabled?)`

创建工作流，返回 `WorkflowDetailResultData`。

### `Tools.Workflow.get(workflowId)`

按 ID 读取工作流详情。

### `Tools.Workflow.update(workflowId, updates?)`

整体更新工作流，可传：

- `name?`
- `description?`
- `nodes?`
- `connections?`
- `enabled?`

### `Tools.Workflow.patch(workflowId, patch?)`

按 patch 操作局部更新工作流，可传：

- `name?`
- `description?`
- `enabled?`
- `node_patches?`
- `connection_patches?`

### `Tools.Workflow.delete(workflowId)`

删除工作流，返回 `StringResultData`。

类型定义里该成员写作 `'delete'(...)`，脚本里既可以写 `Tools.Workflow.delete(id)`，也可以更稳妥地写成 `Tools.Workflow['delete'](id)`。

### `Tools.Workflow.trigger(workflowId)`

手动触发工作流，返回 `StringResultData`。

## 示例

### 创建最小工作流

```ts
const created = await Tools.Workflow.create(
  'demo-workflow',
  '文档同步示例',
  [
    {
      id: 'trigger_1',
      type: 'trigger',
      name: '手动触发',
      triggerType: 'manual',
      position: { x: 80, y: 80 }
    },
    {
      id: 'exec_1',
      type: 'execute',
      name: '发送通知',
      actionType: 'send_notification',
      actionConfig: {
        message: '工作流已执行'
      },
      position: { x: 320, y: 80 }
    }
  ],
  [
    {
      sourceNodeId: 'trigger_1',
      targetNodeId: 'exec_1',
      condition: 'on_success'
    }
  ],
  true
);
```

### 读取详情

```ts
const detail = await Tools.Workflow.get(created.id);
console.log(detail.nodes.length);
console.log(detail.connections.length);
```

### 局部追加节点

```ts
await Tools.Workflow.patch(created.id, {
  node_patches: [
    {
      op: 'add',
      node: {
        id: 'exec_2',
        type: 'execute',
        name: '写日志',
        actionType: 'write_file',
        actionConfig: {
          path: '/sdcard/workflow.log',
          content: 'workflow finished'
        },
        position: { x: 560, y: 80 }
      }
    }
  ]
});
```

### 触发与删除

```ts
await Tools.Workflow.trigger(created.id);
await Tools.Workflow['delete'](created.id);
```

## 返回值

本文件主要使用以下结果类型：

- `WorkflowResultData`
- `WorkflowListResultData`
- `WorkflowDetailResultData`
- `StringResultData`

## 相关文件

- `examples/types/workflow.d.ts`
- `examples/types/results.d.ts`
- `docs/package_dev/results.md`
