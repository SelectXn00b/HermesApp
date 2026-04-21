# API 文档：`memory.d.ts`

`memory.d.ts` 描述的是 `Tools.Memory` 命名空间，用于查询、创建、更新和组织记忆库内容，并管理记忆之间的链接关系。

## 作用

当前定义覆盖：

- 语义检索记忆。
- 通过标题读取具体记忆内容。
- 创建、更新、删除、移动记忆。
- 建立、查询、更新、删除记忆链接。

## 运行时入口

```ts
Tools.Memory
```

## 主要 API

### 记忆查询与读取

#### `query(query, folderPath?, limit?, startTime?, endTime?, snapshotId?, threshold?)`

```ts
query(
  query: string,
  folderPath?: string,
  limit?: number,
  startTime?: string,
  endTime?: string,
  snapshotId?: string,
  threshold?: number
): Promise<MemoryQueryResultData>
```

说明：

- `query` 支持自然语言问题、空格分隔短语，或使用 `|` 分隔多个关键词；在单个关键词内部，`*` 可作为模糊通配占位符，例如 `error*timeout`；传 `*` 时返回所有记忆。
- `limit` 为可选结果上限，取值 `>=1`，默认 `20`；当 `limit > 20` 时会进入截断结果模式。
- `startTime` / `endTime` 是本地时间字符串过滤条件，只支持 `YYYY-MM-DD` 和 `YYYY-MM-DD HH:mm` 两种格式。
- `startTime` 使用起始边界：按天时会从 `00:00:00.000` 开始，按分钟时会从该分钟的 `00` 秒开始。
- `endTime` 使用包含式结束边界：按天时会到 `23:59:59.999`，按分钟时会到该分钟的 `59.999` 秒。
- `snapshotId` 不传或传空时，会自动创建一个新的查询快照，并在返回值里带回 `snapshotId`。
- `snapshotId` 传入任意非空字符串时，会直接使用这个 id；如果该快照还不存在，就按这个 id 创建，而不是要求它必须已经存在。
- 后续串行或并发复用同一个 `snapshotId` 查询时，会自动排除该快照里已经返回过的记忆，并把本次新返回的记忆继续记入快照。
- `threshold` 是可选相关度阈值，要求 `>= 0`；只有得分不低于该阈值的记忆会被返回。`query_memory` 默认阈值为 `0`。
- 返回结构体中包含 `memories[]`，每项有 `title`、`content`、`source`、`tags`、`createdAt`，文档型记忆还可能带 `chunkInfo` 与 `chunkIndices`。
- 返回结构体还包含 `snapshotId`、`snapshotCreated`、`excludedBySnapshotCount`，用于分页式去重检索。

#### `getByTitle(title, chunkIndex?, chunkRange?, query?, limit?)`

通过精确标题读取记忆；当目标是文档型记忆时，可结合：

- `chunkIndex?`
- `chunkRange?`，例如 `"3-7"`
- `query?`，用于在文档内部进一步检索，支持自然语言、空格分隔短语、`|` 分隔多个关键词，以及在单个关键词内部使用 `*` 做模糊通配
- `limit?`，仅在使用 `query` 检索文档分块时生效，表示最多返回多少个分块，默认 `20`

返回值是 `Promise<string>`。

### 创建与更新

#### `create(title, content, contentType?, source?, folderPath?, tags?)`

创建新记忆，默认注释值包括：

- `contentType` 默认 `text/plain`
- `source` 默认 `ai_created`
- `folderPath` 默认空字符串

返回值是 `Promise<string>`。

#### `update(oldTitle, updates?)`

```ts
update(oldTitle: string, updates?: {
  newTitle?,
  content?,
  contentType?,
  source?,
  credibility?,
  importance?,
  folderPath?,
  tags?
}): Promise<string>
```

### 删除与移动

#### `deleteMemory(title)`

删除单个记忆，返回 `Promise<string>`。

#### `move(targetFolderPath, titles?, sourceFolderPath?)`

批量移动记忆：

- `titles` 可传字符串数组。
- 也可传逗号分隔字符串。
- `sourceFolderPath` 为空字符串时表示未分类目录。

## 记忆链接 API

### `link(sourceTitle, targetTitle, linkType?, weight?, description?)`

创建记忆链接，返回 `MemoryLinkResultData`。

### `queryLinks(linkId?, sourceTitle?, targetTitle?, linkType?, limit?)`

查询链接，返回 `MemoryLinkQueryResultData`。

### `updateLink(linkId?, sourceTitle?, targetTitle?, linkType?, newLinkType?, weight?, description?)`

更新已有链接，返回 `MemoryLinkResultData`。

### `deleteLink(linkId?, sourceTitle?, targetTitle?, linkType?)`

删除链接，返回 `Promise<string>`。

## 返回值特点

`memory.d.ts` 有两类返回风格：

- `query()` 返回结构化结果 `MemoryQueryResultData`。
- 记忆主体中的读取/写入操作多数返回 `Promise<string>`。
- 链接相关操作使用结构化结果：`MemoryLinkResultData`、`MemoryLinkQueryResultData`。

## 示例

### 语义查询

```ts
const result = await Tools.Memory.query(
  '最近关于网络请求的笔记',
  'dev/network',
  20,
  undefined,
  undefined,
  undefined,
  0
);
console.log(result.snapshotId);
console.log(result.memories.map(item => item.title));
```

### 带时间范围查询

```ts
const result = await Tools.Memory.query(
  '最近关于网络请求的笔记',
  'dev/network',
  5,
  '2026-03-01',
  '2026-03-27 18:30',
  undefined,
  0.1
);
console.log(result.memories.length);
```

### 使用快照排除已返回结果

```ts
const firstPage = await Tools.Memory.query('最近关于网络请求的笔记');
const secondPage = await Tools.Memory.query(
  '最近关于网络请求的笔记',
  undefined,
  5,
  undefined,
  undefined,
  firstPage.snapshotId || undefined,
  0
);
console.log(secondPage.excludedBySnapshotCount);
```

### 自定义快照 id 以支持并发查询

```ts
const snapshotId = 'network-audit-batch-1';
const threshold = 0.05;

const [recent, historical] = await Promise.all([
  Tools.Memory.query('最近关于网络请求的笔记', 'dev/network', 5, '2026-03-20', undefined, snapshotId, threshold),
  Tools.Memory.query('历史上的网络超时案例', 'dev/network', 5, undefined, '2026-03-19 23:59', snapshotId, threshold)
]);

console.log(snapshotId, recent.snapshotCreated, historical.snapshotCreated);
```

### 创建记忆

```ts
await Tools.Memory.create(
  'OkHttp 使用记录',
  '记录了常用请求写法',
  'text/plain',
  'manual',
  'dev/http',
  'android,http'
);
```

### 更新记忆

```ts
await Tools.Memory.update('OkHttp 使用记录', {
  content: '补充了拦截器与超时配置说明',
  importance: 0.9,
  tags: 'android,http,okhttp'
});
```

### 创建记忆链接

```ts
const link = await Tools.Memory.link(
  'OkHttp 使用记录',
  '网络请求排错',
  'related',
  0.8,
  '两者都与 Android 网络层有关'
);
complete(link);
```

## 相关文件

- `examples/types/memory.d.ts`
- `examples/types/results.d.ts`
- `docs/package_dev/results.md`
