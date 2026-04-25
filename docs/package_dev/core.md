# API 文档：`core.d.ts`

`core.d.ts` 是整个脚本运行环境的基础定义文件。它提供了工具调用、结果结构、底层桥接接口，以及若干全局工具对象。

## 作用

当前定义覆盖：

- 通用参数与结果类型。
- `toolCall()` 与 `complete()`。
- `NativeInterface` 原生桥接。
- 轻量工具对象 `_` 与 `dataUtils`。
- CommonJS 风格的 `exports`。

## 基础类型

### `ToolParams`

```ts
interface ToolParams {
  [key: string]: string | number | boolean | object;
}
```

用于描述工具调用参数。

### `ToolConfig`

```ts
interface ToolConfig {
  type?: string;
  name: string;
  params?: ToolParams;
}
```

用于对象形式的 `toolCall()`。

### `BaseResult`

```ts
interface BaseResult {
  success: boolean;
  error?: string;
}
```

### `StringResult` / `BooleanResult` / `NumberResult`

这三种结果都继承 `BaseResult`，并带有：

- `data`
- `toString()`

### `ToolResult`

```ts
type ToolResult = StringResult | BooleanResult | NumberResult | (BaseResult & { data: any })
```

### `ToolReturnType<T>`

它会根据 `tool-types.d.ts` 中的 `ToolResultMap` 为 `toolCall()` 推导返回类型。

## 全局函数

### `toolCall()`

`core.d.ts` 中一共定义了 4 个重载：

```ts
toolCall(toolType: string, toolName: T, toolParams?: ToolParams)
toolCall(toolName: T, toolParams?: ToolParams)
toolCall(config: ToolConfig & { name: T })
toolCall(toolName: string)
```

最常用的是后两种：

```ts
const result = await toolCall('read_file', { path: '/sdcard/a.txt' });
```

或者：

```ts
const result = await toolCall({
  name: 'http_request',
  params: { url: 'https://example.com' }
});
```

### `complete(result)`

```ts
complete<T>(result: T): void
```

结束脚本执行并返回结果。

## `NativeInterface`

`NativeInterface` 是更底层的原生桥接接口。多数业务代码优先使用 `Tools.*`、`toolCall()` 或全局对象；只有在需要桥接级能力时再直接用它。

### 工具调用与日志

- `callTool(toolType, toolName, paramsJson)`
- `callToolAsync(callbackId, toolType, toolName, paramsJson)`
- `setResult(result)`
- `setError(error)`
- `logInfo(message)`
- `logError(message)`
- `logDebug(message, data)`

### ToolPkg 注册相关

- `registerToolPkgToolboxUiModule(specJson)`
- `registerToolPkgAppLifecycleHook(specJson)`
- `registerToolPkgMessageProcessingPlugin(specJson)`
- `registerToolPkgXmlRenderPlugin(specJson)`
- `registerToolPkgInputMenuTogglePlugin(specJson)`

### 图片注册

- `registerImageFromBase64(base64, mimeType)`
- `registerImageFromPath(path)`

这两个方法都会返回一个可嵌入消息的 `<link type="image" id="...">` 字符串。

### 错误上报

- `reportError(errorType, errorMessage, errorLine, errorStack)`

### Java / Kotlin 桥接

- `javaClassExists(className)`
- `javaGetApplicationContext()`
- `javaGetCurrentActivity()`
- `javaNewInstance(className, argsJson)`
- `javaCallStatic(className, methodName, argsJson)`
- `javaCallInstance(instanceHandle, methodName, argsJson)`
- `javaGetStaticField(className, fieldName)`
- `javaSetStaticField(className, fieldName, valueJson)`
- `javaGetInstanceField(instanceHandle, fieldName)`
- `javaSetInstanceField(instanceHandle, fieldName, valueJson)`

这些方法普遍返回桥接 JSON 字符串，需要调用方自行解析。

补充说明：

- Java 实例句柄的解绑属于运行时内部生命周期管理，不再提供公开的 `release` / `releaseAll` 脚本接口。
- `Java.implement(...)` / `Java.proxy(...)` 产生的 JS 回调对象改为运行时自动解绑，旧的脚本侧手动释放接口已移除。

## 全局工具对象

### `_`

当前只声明了一个轻量 Lodash 风格子集：

- `isEmpty`
- `isString`
- `isNumber`
- `isBoolean`
- `isObject`
- `isArray`
- `forEach`
- `map`

### `dataUtils`

- `parseJson(jsonString)`
- `stringifyJson(obj)`
- `formatDate(date?)`

### `exports`

```ts
var exports: { [key: string]: any }
```

用于 CommonJS 风格导出。

## 示例

### 使用 `toolCall()`

```ts
const file = await toolCall('read_file', {
  path: '/sdcard/demo.txt'
});
complete(file);
```

### 直接记录日志

```ts
NativeInterface.logInfo('start');
NativeInterface.logDebug('payload', JSON.stringify({ ok: true }));
```

### 注册图片

```ts
const imageLink = NativeInterface.registerImageFromPath('/sdcard/demo.png');
complete({ imageLink });
```

## 与 `index.d.ts` 的关系

`core.d.ts` 定义的是“基础能力”；`index.d.ts` 会在此基础上把更多对象和辅助函数挂到全局作用域里，例如：

- `sendIntermediateResult`
- `getEnv`
- `Tools`
- `Java`
- `Kotlin`

这些补充内容请结合 `docs/package_dev/index.md` 一起看。

## 相关文件

- `examples/types/core.d.ts`
- `examples/types/tool-types.d.ts`
- `docs/package_dev/index.md`
