# API 文档：`network.d.ts`

`network.d.ts` 描述的是 `Tools.Net` 命名空间。它不仅包含常规 HTTP 请求，还包含“访问网页并提取内容”和一整套持久化 Web 会话 API。

## 作用

当前定义覆盖三类能力：

- 普通 HTTP 请求。
- 网页访问与内容提取。
- 持久 Web 会话控制（导航、执行 JS、点击、填表、截图快照、文件上传等）。

## 运行时入口

```ts
Tools.Net
```

## 主要 API

### HTTP 请求

#### `httpGet(url, ignore_ssl?)`

```ts
httpGet(url: string, ignore_ssl?: boolean): Promise<HttpResponseData>
```

#### `httpPost(url, body, ignore_ssl?)`

```ts
httpPost(url: string, body: string | object, ignore_ssl?: boolean): Promise<HttpResponseData>
```

#### `http(options)`

```ts
http({
  url,
  method?,
  headers?,
  body?,
  connect_timeout?,
  read_timeout?,
  follow_redirects?,
  ignore_ssl?,
  responseType?,
  validateStatus?
}): Promise<HttpResponseData>
```

这是更通用的 HTTP 请求入口。

#### `uploadFile(options)`

通过 `multipart/form-data` 上传文件，支持：

- `url`
- `method?: 'POST' | 'PUT'`
- `headers?`
- `form_data?`
- `ignore_ssl?`
- `files[]`

其中 `files[]` 每项包含：

- `field_name`
- `file_path`
- `content_type?`
- `file_name?`

### 网页访问

#### `visit(urlOrParams)`

```ts
visit(
  string |
  {
    url?,
    visit_key?,
    link_number?,
    include_image_links?,
    headers?,
    user_agent_preset?,
    user_agent?
  }
): Promise<VisitWebResultData>
```

说明：

- 既支持直接访问 URL。
- 也支持基于已有 `visit_key` 继续访问某个链接。
- 这是“访问网页并提取可读内容”的工具，不是原始 HTTP GET/POST 的替代品。
- 如果你真正需要的是接口响应体、AJAX 返回值、精确 headers/status/body，应该使用 `httpGet()`、`httpPost()` 或 `http()`；误用 `visit()` 时可能出现空返回或内容不完整。
- 当正文过长时，`VisitWebResultData.content` 可能只保留预览，完整内容会写入 `contentSavedTo` 指向的本地文件。
- 遇到 `contentSavedTo` 时，优先对该路径使用 `read_file_part`、`read_file_full` 或 `grep_code`。

### 持久浏览器会话

#### `startBrowser(options?)`

启动一个持续存在的浏览器会话（浮窗 WebView），返回 `StringResultData`，其中 `value` 是 JSON 字符串。

#### `stopBrowser(sessionIdOrOptions?)`

关闭指定会话或全部会话。

#### `browserNavigate(sessionId, url, headers?)`

把某个会话导航到目标 URL。

#### `browserEval(sessionId, script, timeoutMs?)`

在会话内执行 JavaScript。

#### `browserClick(options)`

```ts
browserClick({
  session_id?,
  ref,
  element?,
  button?,
  modifiers?,
  doubleClick?
}): Promise<StringResultData>
```

这里的 `ref` 通常来自快照结果中的节点引用。

#### `browserFill(sessionId, selector, value)`

按 CSS 选择器填充内容。

#### `browserWaitFor(sessionId, selector?, timeoutMs?)`

等待页面就绪或某个选择器出现。

#### `browserSnapshot(sessionId, options?)`

获取当前页面的文本快照，可控制是否包含链接与图片。

#### `browserFileUpload(sessionId, paths?)`

处理页面文件选择器；未传 `paths` 时表示取消。

### Cookie 管理

通过 `Tools.Net.cookies` 使用：

- `get(domain)`
- `set(domain, cookies)`
- `clear(domain?)`

返回值均为 `HttpResponseData`。

## 示例

### 最简单的 GET 请求

```ts
const response = await Tools.Net.httpGet('https://example.com');
console.log(response.statusCode);
console.log(response.content);
```

### 通用 HTTP 请求

```ts
const response = await Tools.Net.http({
  url: 'https://example.com/api',
  method: 'POST',
  headers: {
    Authorization: 'Bearer token'
  },
  body: {
    hello: 'world'
  },
  follow_redirects: true
});
```

### 抓取网页内容

```ts
const page = await Tools.Net.visit({
  url: 'https://example.com',
  include_image_links: true,
  user_agent_preset: 'desktop'
});
console.log(page.title);
console.log(page.content);

// If you need raw API responses instead of webpage extraction,
// use httpGet/httpPost/http rather than visit().
```

### 持久会话中执行点击

```ts
const started = await Tools.Net.startBrowser({ url: 'https://example.com' });
const session = JSON.parse(started.value);

await Tools.Net.browserClick({
  session_id: session.sessionId,
  ref: 'node_12',
  element: '登录按钮'
});
```

## 返回值

- 常规网络请求使用 `HttpResponseData`。
- `visit()` 使用 `VisitWebResultData`。
- 浏览器会话控制相关 API 多数返回 `StringResultData`，实际有效载荷通常放在 `value` 字段中，且常为 JSON 字符串。

## 相关文件

- `examples/types/network.d.ts`
- `examples/types/results.d.ts`
- `docs/package_dev/results.md`
