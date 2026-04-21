# API 文档：`toolpkg.d.ts`

`toolpkg.d.ts` 描述的是工具包插件注册系统。它的核心目标不是“调用工具”，而是**向宿主注册模块、钩子和插件**，让一个 tool package 可以在应用生命周期、消息处理、XML 渲染、输入菜单和提示词流水线中插入自己的行为。

## 作用

当前类型定义覆盖：

- 工具箱 UI 模块注册。
- 应用生命周期钩子。
- 消息处理插件。
- XML 渲染插件。
- 输入菜单开关插件。
- 工具执行生命周期钩子。
- Prompt 输入、历史、系统提示词、工具提示词、最终发送前的各类钩子。

## 类型命名空间与运行时对象

`toolpkg.d.ts` 里同时存在两个层面的 `ToolPkg`：

- `namespace ToolPkg`：承载类型定义。
- `const ToolPkg: ToolPkg.Registry`：全局运行时注册对象。

因此脚本里常见的实际写法是：

```ts
ToolPkg.registerAppLifecycleHook(...)
ToolPkg.registerMessageProcessingPlugin(...)
```

此外，全局还声明了一组辅助函数：

- `registerToolPkgToolboxUiModule(...)`
- `registerToolPkgAppLifecycleHook(...)`
- `registerToolPkgMessageProcessingPlugin(...)`
- `registerToolPkgXmlRenderPlugin(...)`
- `registerToolPkgInputMenuTogglePlugin(...)`
- `registerToolPkgToolLifecycleHook(...)`
- `registerToolPkgPromptInputHook(...)`
- `registerToolPkgPromptHistoryHook(...)`
- `registerToolPkgPromptEstimateHistoryHook(...)`
- `registerToolPkgSystemPromptComposeHook(...)`
- `registerToolPkgToolPromptComposeHook(...)`
- `registerToolPkgPromptFinalizeHook(...)`
- `registerToolPkgPromptEstimateFinalizeHook(...)`

## 基础类型

### `ToolPkg.LocalizedText`

```ts
type LocalizedText = string | { [lang: string]: string }
```

适合标题、描述等多语言文本。

### `ToolPkg.JsonPrimitive` / `ToolPkg.JsonValue` / `ToolPkg.JsonObject`

这一组类型用于约束所有插件返回值和事件载荷的 JSON 结构。

## 事件分类

### 应用生命周期事件：`AppLifecycleEvent`

支持：

- `application_on_create`
- `application_on_foreground`
- `application_on_background`
- `application_on_low_memory`
- `application_on_trim_memory`
- `application_on_terminate`
- `activity_on_create`
- `activity_on_start`
- `activity_on_resume`
- `activity_on_pause`
- `activity_on_stop`
- `activity_on_destroy`

### 通用事件名：`HookEventName`

这是全部 hook 事件的联合类型，除生命周期外还包括：

- `message_processing`
- `xml_render`
- `input_menu_toggle`
- 工具生命周期事件
- Prompt 输入 / 历史 / 系统提示词 / 工具提示词 / 最终发送事件

### Prompt 轮次类型：`PromptTurnKind` / `PromptTurn`

Prompt 相关 hook 和 `message_processing` 插件里的历史消息，统一使用结构化的 `PromptTurn`：

```ts
type PromptTurnKind =
  | 'SYSTEM'
  | 'USER'
  | 'ASSISTANT'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
  | 'SUMMARY'

interface PromptTurn {
  kind: PromptTurnKind
  content: string
  toolName?: string
  metadata?: JsonObject
}
```

注意：

- 这里不再使用旧的 `{ role, content }` 结构。
- `message_processing` 插件收到的 `chatHistory` 也是 `PromptTurn[]`。
- 如果你要复用旧的 role 语义，需要自己把 `kind` 映射成对应角色。

### 工具生命周期事件：`ToolLifecycleEventName`

- `tool_call_requested`
- `tool_permission_checked`
- `tool_execution_started`
- `tool_execution_result`
- `tool_execution_error`
- `tool_execution_finished`

### Prompt 流水线事件

#### `PromptInputEventName`

- `before_process`
- `after_process`

#### `PromptHistoryEventName`

- `before_prepare_history`
- `after_prepare_history`

#### `SystemPromptComposeEventName`

- `before_compose_system_prompt`
- `compose_system_prompt_sections`
- `after_compose_system_prompt`

#### `ToolPromptComposeEventName`

- `before_compose_tool_prompt`
- `filter_tool_prompt_items`
- `after_compose_tool_prompt`

#### `PromptFinalizeEventName`

- `before_finalize_prompt`
- `before_send_to_model`

## 事件对象

所有 hook 事件都继承自：

### `HookEventBase<TEventName, TPayload>`

公共字段包括：

- `event`
- `eventName`
- `eventPayload`
- `toolPkgId?`
- `containerPackageName?`
- `functionName?`
- `pluginId?`
- `hookId?`
- `timestampMs?`

## 各类 payload

### `MessageProcessingEventPayload`

字段包括：

- `messageContent?`
- `chatHistory?: PromptTurn[]`
- `workspacePath?`
- `maxTokens?`
- `tokenUsageThreshold?`
- `probeOnly?`
- `executionId?`

### `XmlRenderEventPayload`

字段包括：

- `xmlContent?`
- `tagName?`

### `InputMenuToggleEventPayload`

字段包括：

- `action?: 'create' | 'toggle' | string`
- `toggleId?`

### `ToolLifecycleEventPayload`

字段包括：

- `toolName`
- `parameters?`
- `description?`
- `granted?`
- `reason?`
- `success?`
- `errorMessage?`
- `resultText?`
- `resultJson?`

### `PromptHookEventPayload`

字段包括：

- `stage?`
- `functionType?`
- `promptFunctionType?`
- `useEnglish?`
- `rawInput?`
- `processedInput?`
- `chatHistory?: PromptTurn[]`
- `preparedHistory?: PromptTurn[]`
- `systemPrompt?`
- `toolPrompt?`
- `modelParameters?`
- `availableTools?`
- `metadata?`

## 返回值类型

### 消息处理插件返回：`MessageProcessingHookReturn`

允许返回：

- `boolean`
- `string`
- `MessageProcessingHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `MessageProcessingHookObjectResult` 可包含：

- `matched?`
- `text?`
- `content?`
- `chunks?`

### XML 渲染插件返回：`XmlRenderHookReturn`

允许返回：

- `string`
- `XmlRenderHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `XmlRenderHookObjectResult` 可包含：

- `handled?`
- `text?`
- `content?`
- `composeDsl?`

`composeDsl` 结构里可以返回：

- `screen: ComposeDslScreen`
- `state?`
- `memo?`
- `moduleSpec?`

### 输入菜单开关返回：`InputMenuToggleHookReturn`

允许返回：

- `InputMenuToggleDefinitionResult[]`
- `InputMenuToggleObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中单个开关定义包含：

- `id`
- `title`
- `description?`
- `isChecked?`

### Prompt 相关返回

- `PromptInputHookReturn`
- `PromptHistoryHookReturn`
- `SystemPromptComposeHookReturn`
- `ToolPromptComposeHookReturn`
- `PromptFinalizeHookReturn`

这几类返回允许在字符串、消息数组、结构化对象与空返回之间切换，具体以类型定义为准。
其中：

- `PromptHistoryHookReturn` 里的数组元素类型是 `PromptTurn`
- `PromptFinalizeHookReturn` 里的数组元素类型也是 `PromptTurn`
- 估算阶段的 `PromptEstimateHistoryHook` / `PromptEstimateFinalizeHook` 复用相同的 payload 和返回结构

## 注册定义对象

### `ToolboxUiModuleRegistration`

字段：

- `id`
- `runtime?`
- `screen: ComposeDslScreen`
- `params?`
- `title?`

### `AppLifecycleHookRegistration`

字段：

- `id`
- `event`
- `function`

### `MessageProcessingPluginRegistration`

字段：

- `id`
- `function`

### `XmlRenderPluginRegistration`

字段：

- `id`
- `tag`
- `function`

### `InputMenuTogglePluginRegistration`

字段：

- `id`
- `function`

### 其余注册对象

以下注册对象结构都很简单，字段都是：`id` + `function`：

- `ToolLifecycleHookRegistration`
- `PromptInputHookRegistration`
- `PromptHistoryHookRegistration`
- `PromptEstimateHistoryHookRegistration`
- `SystemPromptComposeHookRegistration`
- `ToolPromptComposeHookRegistration`
- `PromptFinalizeHookRegistration`
- `PromptEstimateFinalizeHookRegistration`

## `ToolPkg.Registry`

运行时 `ToolPkg` 对象实现了这个接口，提供以下方法：

- `registerToolboxUiModule(definition)`
- `registerAppLifecycleHook(definition)`
- `registerMessageProcessingPlugin(definition)`
- `registerXmlRenderPlugin(definition)`
- `registerInputMenuTogglePlugin(definition)`
- `registerToolLifecycleHook(definition)`
- `registerPromptInputHook(definition)`
- `registerPromptHistoryHook(definition)`
- `registerPromptEstimateHistoryHook(definition)`
- `registerSystemPromptComposeHook(definition)`
- `registerToolPromptComposeHook(definition)`
- `registerPromptFinalizeHook(definition)`
- `registerPromptEstimateFinalizeHook(definition)`
- `readResource(key, outputFileName?)`

### `ToolPkg.readResource(...)`

把当前 toolpkg `manifest.resources` 里声明的资源按 `key` 释放到宿主临时目录，并返回落盘后的绝对路径。

```ts
const jarPath = await ToolPkg.readResource('apktool_lib_jar', 'apktool-lib.jar');
```

说明：

- 这个方法不依赖 `compose_dsl` 的 `ctx`，普通子包工具函数、主入口 hook、UI 模块都可以直接调用。
- `key` 对应 `manifest.json` 里的 `resources[].key`。
- `outputFileName` 可选；不传时会使用清单资源原始文件名。
- 如果资源 `mime` 是目录类型（例如 `inode/directory`、`vnd.android.document/directory`），运行时会先把该目录压成 zip，再返回这个 zip 文件的绝对路径；默认文件名会自动补 `.zip`。

## 示例

### 注册工具箱 UI 模块

```ts
import toolboxUI from './index.ui.js';

ToolPkg.registerToolboxUiModule({
  id: 'demo_toolbox',
  runtime: 'compose_dsl',
  screen: toolboxUI,
  params: {},
  title: {
    zh: '示例模块',
    en: 'Demo Module'
  }
});
```

### 注册应用生命周期钩子

```ts
ToolPkg.registerAppLifecycleHook({
  id: 'demo_app_create',
  event: 'application_on_create',
  function(event) {
    console.log(JSON.stringify(event.eventPayload ?? {}));
    return { ok: true };
  }
});
```

### 注册消息处理插件

```ts
ToolPkg.registerMessageProcessingPlugin({
  id: 'demo_message_plugin',
  async function(event) {
    const message = String(event.eventPayload?.messageContent ?? '').trim();
    if (!message.startsWith('/demo')) {
      return { matched: false };
    }
    return {
      matched: true,
      text: '已命中 demo 插件'
    };
  }
});
```

### 注册 XML 渲染插件

```ts
ToolPkg.registerXmlRenderPlugin({
  id: 'demo_xml',
  tag: 'demo',
  function(event) {
    const xml = String(event.eventPayload?.xmlContent ?? '');
    if (!xml) {
      return { handled: false };
    }
    return {
      handled: true,
      text: 'XML 已处理'
    };
  }
});
```

### 注册输入菜单开关插件

```ts
ToolPkg.registerInputMenuTogglePlugin({
  id: 'demo_toggle',
  function(event) {
    if (event.eventPayload?.action === 'create') {
      return [
        {
          id: 'demo_feature',
          title: 'Demo Feature',
          description: '示例开关',
          isChecked: true
        }
      ];
    }
    return [];
  }
});
```

## 关于 `registerToolPkg()` 入口

从 `examples/linux_ssh/src/main.ts` 与 `examples/deepsearching/src/plugin/deep-search-plugin.ts` 可以看出，工具包通常会在入口文件中导出一个 `registerToolPkg()` 函数，并在里面集中调用上述注册方法。

这是一种**从仓库示例总结出的约定**；它不是 `toolpkg.d.ts` 本身直接声明的函数签名。

## 开发调试安装

`toolpkg.d.ts` 这里描述的是注册 API，本身不负责“如何调试安装到手机”。

如果你在开发 ToolPkg，需要注意：

- 普通 `.js` 包可以用 `tools/execute_js.bat` / `tools/execute_js.sh` 做单次执行调试
- `toolpkg` 不适合这样调试，因为它涉及 `manifest`、`main` 注册、ToolPkg cache、以及多类 hook/runtime 的重新同步
- 调试 ToolPkg 时，应使用 `tools/debug_toolpkg.bat` / `tools/debug_toolpkg.sh` / `tools/debug_toolpkg.py`

完整的打包、烧录、启用、刷新 hook/runtime 的工作流说明，见 [TOOLPKG_FORMAT_GUIDE.md](../TOOLPKG_FORMAT_GUIDE.md) 中的“10.3 使用调试安装脚本快速烧录到手机”。

## 相关文件

- `examples/types/toolpkg.d.ts`
- `examples/types/compose-dsl.d.ts`
- `docs/package_dev/core.md`
- `docs/TOOLPKG_FORMAT_GUIDE.md`
