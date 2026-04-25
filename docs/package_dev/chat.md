# API 文档：`chat.d.ts`

`chat.d.ts` 描述的是 `Tools.Chat` 命名空间，用于管理会话、切换会话、发送消息以及读取消息记录。

## 作用

当前定义覆盖：

- 启动聊天服务。
- 创建、查询、切换、删除聊天会话。
- 发送普通消息与高级消息。
- 枚举角色卡与读取聊天消息。

## 运行时入口

```ts
Tools.Chat
```

## 主要 API

### `startService()`

```ts
startService(): Promise<ChatServiceStartResultData>
```

启动聊天服务或浮窗。

### `createNew(group?, setAsCurrentChat?, characterCardId?)`

```ts
createNew(group?: string, setAsCurrentChat?: boolean, characterCardId?: string): Promise<ChatCreationResultData>
```

创建新的聊天会话。

### `listAll()`

返回全部聊天列表。

### `listChats(params?)`

带过滤条件列出聊天：

- `query?`
- `match?: 'contains' | 'exact' | 'regex'`
- `limit?`
- `sort_by?: 'updatedAt' | 'createdAt' | 'messageCount'`
- `sort_order?: 'asc' | 'desc'`

### `findChat(params)`

```ts
findChat({ query, match?, index? }): Promise<ChatFindResultData>
```

根据标题或 ID 查找聊天。

### `agentStatus(chatId)`

查看某个聊天当前是否在处理中。

### `switchTo(chatId)`

切换当前聊天。

### `updateTitle(chatId, title)`

更新聊天标题。

### `deleteChat(chatId)`

删除聊天。

### `sendMessage(message, chatId?, roleCardId?, senderName?)`

发送普通消息。

### `sendMessageAdvanced(params)`

高级发送接口，支持：

- `chatId`
- `chatHistory`
- `workspacePath`
- `functionType`
- `promptFunctionType`
- `enableThinking`
- `thinkingGuidance`
- `enableMemoryQuery`
- `maxTokens`
- `tokenUsageThreshold`
- `customSystemPromptTemplate`
- `isSubTask`
- `stream`

### `listCharacterCards()`

列出可用角色卡。

### `getMessages(chatId, options?)`

```ts
getMessages(chatId: string, options?: { order?: 'asc' | 'desc'; limit?: number }): Promise<ChatMessagesResultData>
```

读取某个聊天的消息记录。

## 返回值

`chat.d.ts` 的返回值都定义在 `results.d.ts` 中，常见的有：

- `ChatServiceStartResultData`
- `ChatCreationResultData`
- `ChatListResultData`
- `ChatFindResultData`
- `AgentStatusResultData`
- `ChatSwitchResultData`
- `ChatTitleUpdateResultData`
- `ChatDeleteResultData`
- `MessageSendResultData`
- `ChatMessagesResultData`
- `CharacterCardListResultData`

## 示例

### 创建会话并发送消息

```ts
const created = await Tools.Chat.createNew('work', true);
const chatId = created.chatId;

await Tools.Chat.sendMessage('帮我总结今天的待办', chatId);
```

### 查找并切换聊天

```ts
const found = await Tools.Chat.findChat({
  query: '日报',
  match: 'contains'
});

if (found.chat) {
  await Tools.Chat.switchTo(found.chat.id);
}
```

### 读取最近消息

```ts
const messages = await Tools.Chat.getMessages('chat_123', {
  order: 'desc',
  limit: 20
});
console.log(messages.toString());
```

## 相关文件

- `examples/types/chat.d.ts`
- `examples/types/results.d.ts`
- `examples/types/index.d.ts`
