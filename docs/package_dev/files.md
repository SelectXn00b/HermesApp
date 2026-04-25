# API 文档：`files.d.ts`

`files.d.ts` 描述的是 `Tools.Files` 命名空间。它是包内最常用的文件系统工具之一，负责在 `android` 与 `linux` 两种执行环境里读写、搜索、移动和下载文件。

## 作用

当前定义覆盖：

- 目录遍历与文件读取。
- 文本 / 二进制写入。
- 删除、移动、复制、建目录。
- 文件搜索、正则检索、上下文检索。
- 获取文件信息、应用 AI diff、压缩解压、打开分享、下载。

## 基本类型

### `FileEnvironment`

```ts
type FileEnvironment = 'android' | 'linux'
```

多数 API 都支持显式指定执行环境；默认环境以类型定义里的注释为准，通常是 `android`。

### `ApplyFileType`

```ts
type ApplyFileType = 'replace' | 'delete' | 'create'
```

用于 `Tools.Files.apply()`。

## 运行时入口

```ts
Tools.Files
```

## 主要 API

### 目录与读取

#### `list(path, environment?)`

列出目录内容，返回 `DirectoryListingData`。

#### `read(path)` / `read(options)`

```ts
read(path: string): Promise<FileContentData>
read({ path, environment?, intent?, direct_image? }): Promise<FileContentData>
```

第二个重载额外支持：

- `intent?`：读取意图说明。
- `direct_image?`：是否按图片直出方式处理。

#### `readPart(path, startLine?, endLine?, environment?)`

按行读取部分内容，返回 `FilePartContentData`。

#### `readBinary(path, environment?)`

读取二进制文件，返回 `BinaryFileContentData`，内容字段为 Base64。

### 写入与修改

#### `write(path, content, append?, environment?)`

写入文本，可选追加。

#### `writeBinary(path, base64Content, environment?)`

写入 Base64 编码的二进制内容。

#### `apply(path, type, old?, newContent?, environment?)`

```ts
apply(path, 'replace' | 'delete' | 'create', old?, newContent?, environment?)
```

说明：

- `replace` / `delete` 通常需要 `old` 精确匹配。
- `create` / `replace` 通常需要 `newContent`。
- 返回 `FileApplyResultData`，里面包含 `operation` 与 `aiDiffInstructions`。

### 删除、移动、复制

#### `deleteFile(path, recursive?, environment?)`

删除文件或目录。

#### `move(source, destination, environment?)`

移动文件。

#### `copy(source, destination, recursive?, sourceEnvironment?, destEnvironment?)`

支持跨环境复制，是 `files.d.ts` 里很重要的一点。

#### `mkdir(path, create_parents?, environment?)`

创建目录。

### 搜索与信息

#### `exists(path, environment?)`

检查路径是否存在，返回 `FileExistsData`。

#### `info(path, environment?)`

获取详细文件信息，返回 `FileInfoData`。

#### `find(path, pattern, options?, environment?)`

按文件名 / 模式搜索，返回 `FindFilesResultData`。

#### `grep(path, pattern, options?)`

```ts
grep(path, pattern, {
  file_pattern?,
  case_insensitive?,
  context_lines?,
  max_results?,
  environment?
})
```

做正则级内容检索，返回 `GrepResultData`。

#### `grepContext(path, intent, options?)`

按意图做语义相关内容检索，返回 `GrepResultData`。

### 压缩、打开、分享、下载

#### `zip(source, destination, environment?)`

压缩文件或目录。

#### `unzip(source, destination, environment?)`

解压归档文件。

#### `open(path, environment?)`

调用系统处理器打开文件。

#### `share(path, title?, environment?)`

分享文件给其他应用。

#### `download(url, destination, environment?, headers?)`

从 URL 下载文件。

#### `download(options)`

```ts
download({
  url?,
  visit_key?,
  link_number?,
  image_number?,
  destination,
  environment?,
  headers?
})
```

这个重载说明下载不仅可以直接给 URL，也可以配合 `visit_web` 结果里的 `visit_key` 与链接序号继续下载。

## 示例

### 读取文本文件

```ts
const file = await Tools.Files.read({
  path: '/sdcard/notes/todo.txt',
  environment: 'android'
});
console.log(file.content);
```

### 读取部分行号

```ts
const part = await Tools.Files.readPart('/sdcard/app.log', 1, 80);
console.log(part.content);
```

### 跨环境复制

```ts
await Tools.Files.copy(
  '/sdcard/input.txt',
  '/tmp/input.txt',
  false,
  'android',
  'linux'
);
```

### 搜索代码

```ts
const matches = await Tools.Files.grep('/workspace', 'toolCall\\(', {
  file_pattern: '*.ts',
  context_lines: 2,
  max_results: 20,
  environment: 'linux'
});
```

### 应用替换补丁

```ts
await Tools.Files.apply(
  '/sdcard/demo.txt',
  'replace',
  'old text',
  'new text'
);
```

## 返回值

本文件主要使用以下结果类型：

- `DirectoryListingData`
- `FileContentData`
- `BinaryFileContentData`
- `FilePartContentData`
- `FileOperationData`
- `FileExistsData`
- `FindFilesResultData`
- `FileInfoData`
- `FileApplyResultData`
- `GrepResultData`

## 相关文件

- `examples/types/files.d.ts`
- `examples/types/results.d.ts`
- `docs/package_dev/results.md`
