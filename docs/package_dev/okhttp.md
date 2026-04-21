# API 文档：`okhttp.d.ts`

`okhttp.d.ts` 描述的是全局 `OkHttp` 对象和一套更偏“客户端式”的 HTTP API。与 `Tools.Net.http()` 相比，它更适合链式构建请求、复用客户端配置和做拦截器处理。

## 全局对象

```ts
OkHttp
```

可以直接使用，无需 `import`。

## 核心对象关系

- `OkHttp.newClient()`：创建默认客户端。
- `OkHttp.newBuilder()`：创建客户端构建器。
- `OkHttpClientBuilder`：设置超时、重定向、重试、拦截器。
- `OkHttpClient`：执行请求。
- `RequestBuilder`：链式构造 `HttpRequest`。
- `OkHttpResponse`：包装响应结果。

## 主要类型

### `HttpRequest`

字段：

- `url`
- `method`
- `headers`
- `body?`
- `bodyType?`：`text | json | form | multipart`
- `formParams?`
- `multipartParams?`

方法：

- `execute()`：直接执行当前请求。

### `OkHttpResponse`

常用字段：

- `raw: HttpResponseData`
- `statusCode`
- `statusMessage`
- `headers`
- `content`
- `contentType`
- `size`

常用方法：

- `json()`
- `text()`
- `bodyAsBase64()`
- `isSuccessful()`

### `OkHttpClientBuilder`

支持以下链式配置：

- `connectTimeout(timeout)`
- `readTimeout(timeout)`
- `writeTimeout(timeout)`
- `followRedirects(follow)`
- `retryOnConnectionFailure(retry)`
- `addInterceptor(interceptor)`
- `build()`

### `OkHttpClient`

支持以下调用：

- `newRequest()`
- `execute(request)`
- `get(url, headers?)`
- `post(url, body, headers?)`
- `put(url, body, headers?)`
- `delete(url, headers?)`
- `OkHttpClient.newBuilder()`

### `RequestBuilder`

支持以下链式方法：

- `url(url)`
- `method(method)`
- `header(name, value)`
- `headers(headers)`
- `body(body, type?)`
- `jsonBody(data)`
- `formParam(name, value)`
- `multipartParam(name, value, contentType?)`
- `build()`

## 示例

### 最简单的 GET

```ts
const client = OkHttp.newClient();
const response = await client.get('https://example.com');
console.log(response.statusCode);
console.log(response.text());
```

### 使用构建器配置客户端

```ts
const client = OkHttp
  .newBuilder()
  .connectTimeout(10_000)
  .readTimeout(20_000)
  .followRedirects(true)
  .retryOnConnectionFailure(true)
  .build();
```

### 构建 JSON POST 请求

```ts
const client = OkHttp.newClient();
const request = client
  .newRequest()
  .url('https://example.com/api')
  .header('Authorization', 'Bearer token')
  .jsonBody({ hello: 'world' })
  .build();

const response = await client.execute(request);
const json = response.json();
```

### 添加拦截器

```ts
const client = OkHttp
  .newBuilder()
  .addInterceptor((request) => {
    request.headers['X-Trace-Id'] = String(Date.now());
    return request;
  })
  .build();
```

## 与 `Tools.Net` 的区别

- `Tools.Net` 更偏工具调用风格，适合快速请求。
- `OkHttp` 更偏客户端风格，适合链式配置和复用。
- 两者底层都以当前环境的桥接能力为准，但类型层面是两套不同封装。

## 相关文件

- `examples/types/okhttp.d.ts`
- `examples/types/network.d.ts`
- `examples/types/results.d.ts`
