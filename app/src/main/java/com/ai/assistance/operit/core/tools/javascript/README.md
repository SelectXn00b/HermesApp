# Script 开发指南

本文只讨论当前脚本运行时真正重要的四件事：

1. 模块系统与 `import/export` 支持边界
2. Java / Kotlin Bridge 的用法
3. 脚本模块被宿主调用时的执行模型
4. `compose_dsl` 的编写方式

本文**不讨论 metadata、manifest、打包描述字段**。

---

## 1. 先建立正确心智模型

Hermes 当前脚本运行时不是 Node.js，也不是浏览器原生 ESM 运行时。

它更接近：

- 一个宿主控制的 **CommonJS 风格模块执行器**
- 一个按调用注入的 **局部运行时**
- 一个可直接访问 Android / Java / Kotlin 的 **桥接环境**
- 一个针对 UI 场景额外支持 `compose_dsl` 的 **声明式渲染运行时**

因此写脚本时，最重要的几条原则是：

- **把模块当成 CommonJS 写**：`require`、`exports`、`module.exports`
- **把一次调用当成一次独立会话**：不要依赖跨调用模块单例
- **把 stream 当成调用专属能力**：直接用宿主注入的 `sendIntermediateResult / emit / delta / log / update / done / complete`
- **把 Java/Kotlin 当成桥接对象系统**：不是 TypeScript 类型直连，而是代理对象 + handle 生命周期

---

## 2. 模块系统：实际支持什么

### 2.1 运行时支持的是 CommonJS，不是原生 ESM

运行时真正执行的 JS 模块形式是：

```js
const helper = require('./helper');

function run(params) {
  return helper.work(params);
}

exports.run = run;
```

或者：

```js
module.exports = {
  run(params) {
    return { ok: true, params };
  }
};
```

当前运行时**直接支持**：

- `require(...)`
- `exports.xxx = ...`
- `module.exports = ...`

当前运行时**不直接执行**原生 ESM：

```ts
import { foo } from './foo';
export function run() {}
```

如果你在 TypeScript 里想写 `import/export` 语法，可以，但前提是：

- 最终编译产物必须落成 CommonJS 风格 JS
- 不要把未转译的 ESM 直接丢给运行时

推荐的 TypeScript 输出目标是 CommonJS。

---

### 2.2 `require` 的解析规则

对于 toolpkg / 主脚本模块，`require` 支持以下几种形式。

#### 相对路径

```js
const plan = require('./plan/PlanModeManager');
const parser = require('../shared/parser');
```

#### 绝对包内路径

```js
const utils = require('/lib/utils');
```

#### JSON 模块

```js
const config = require('./config.json');
```

`.json` 会被自动解析为对象。

#### 目录入口回退

对于没有后缀的路径，运行时会按以下候选顺序尝试：

- `name`
- `name.js`
- `name.json`
- `name/index.js`
- `name/index.json`

也就是说，下面两种都可以：

```js
const mod = require('./foo');
const mod2 = require('./foo/index');
```

---

### 2.3 非相对模块：只支持运行时内建项

当前并不是完整 npm 生态。

运行时只内建了少数名字：

- `lodash` → `_`
- `uuid` → 提供 `v4()`
- `axios` → 一个面向宿主 `http_request` 的轻量包装，不是完整 Node axios

例如：

```js
const _ = require('lodash');
const { v4 } = require('uuid');
const axios = require('axios');
```

如果你写：

```js
require('fs');
require('path');
require('process');
```

这类 Node 内建模块**默认不支持**。

---

### 2.4 导出规则与推荐写法

宿主执行一个模块时，会去找目标函数名对应的导出。

推荐始终显式导出：

```js
async function onMessage(params) {
  return { ok: true };
}

exports.onMessage = onMessage;
```

或者：

```js
module.exports = {
  onMessage,
  onXmlRender,
  onAppStart,
};
```

虽然运行时还会尝试一些附加路径（例如在某些场景下解析到注入函数或 `window` 上的函数），但**文档层面推荐只把 `exports/module.exports` 当成正式接口**。

这样最稳定，也最利于拆分模块。

---

### 2.5 同一次调用内缓存，跨调用不缓存

这是当前架构的一个关键点。

**同一次调用内部**：

- `require('./foo')` 多次，只会执行一次
- 同调用内模块共享同一份 `exports`

**不同调用之间**：

- 主模块会重新求值
- 子模块也会重新按本次调用加载
- 不再保留跨调用 persistent module cache

因此你应该这样理解模块状态：

- **调用内可共享**
- **调用间不要持久化依赖模块级状态**

错误示例：

```js
let globalCounter = 0;

exports.run = function () {
  globalCounter += 1;
  return globalCounter;
};
```

不要假设它能跨多轮调用持续累加。

---

### 2.6 顶层代码与异步代码的边界

模块顶层代码在加载阶段执行，仍然是同步加载语义。

推荐把异步放进导出的函数里：

```js
async function run(params) {
  const data = await toolCall('http_request', {
    url: params.url,
    method: 'GET'
  });
  return data;
}

exports.run = run;
```

而不要把模块初始化设计成依赖“顶层异步完成后再导出”。

---

## 3. 脚本模块被调用时，到底发生了什么

### 3.1 宿主调用模型

宿主调用脚本时，本质上是：

- 建立一次新的调用会话
- 分配一个新的 `callId`
- 给这次调用注入一组**绑定好的局部运行时函数**
- 执行目标模块
- 查找指定导出函数
- 用一个 `params` 对象调用它
- 接收最终结果和中间流式结果

换句话说，你应当把导出函数理解成：

```ts
type RuntimeEntry = (params: Record<string, unknown>) => unknown | Promise<unknown>
```

不是：

- 多位置参数调用
- 依赖全局当前上下文
- 依赖宿主去猜这次 stream 属于谁

---

### 3.2 参数传递特点

宿主调用目标函数时传入的是**单个对象参数**。

例如：

```js
async function onMessage(params) {
  const event = params.event;
  const payload = params.eventPayload;
  const pluginId = params.pluginId;
  return { event, payload, pluginId };
}

exports.onMessage = onMessage;
```

因此推荐：

- 永远假设入口只有一个 `params`
- 所有扩展字段都挂在 `params` 上
- 模块内部自己做字段收敛和校验

---

### 3.3 返回值模型

你可以：

- 直接返回普通对象
- 返回字符串 / 数字 / 数组
- 返回 `Promise`
- 显式调用 `complete` / `done`

例如：

```js
exports.runSync = function (params) {
  return { ok: true, mode: 'sync', params };
};

exports.runAsync = async function (params) {
  const data = await toolCall('http_request', {
    url: params.url,
    method: 'GET'
  });
  return { ok: true, data };
};
```

如果你只是想“正常完成”，**直接 `return` 是最自然的写法**。

只有当你确实要手动控制结束时，才使用：

- `done(...)`
- `complete(...)`

---

### 3.4 Stream：当前推荐用法

对脚本开发者来说，最重要的结论只有一句：

**不要自己管理调用上下文，不要自己猜归属，直接使用注入的 emitter。**

当前每次调用都会注入一组**与本次调用绑定**的局部函数：

- `sendIntermediateResult(...)`
- `emit(...)`
- `delta(...)`
- `log(...)`
- `update(...)`
- `done(...)`
- `complete(...)`

它们都是**本次调用专属闭包**，所以可以安全跨过 `await`：

```js
exports.run = async function (params) {
  log({ stage: 'start' });

  const plan = await toolCall('http_request', {
    url: params.url,
    method: 'GET'
  });

  update({ stage: 'after-await', plan });

  return { ok: true, plan };
};
```

这套模型下，脚本作者需要遵守的规则是：

- **直接调用 emitter**
- **不要保存/推导/传递 callId**
- **不要自己补 Promise 上下文传播**
- **不要写“当前会话是谁”的全局逻辑**

这部分已经属于引擎职责，不应该回到插件层处理。

---

### 3.5 `console` 与错误上报

模块执行时，`console.log/info/warn/error` 也是按调用绑定的。

也就是说：

```js
console.log('loading...');
console.error('failed:', err);
```

会落到当前调用对应的日志通道，而不是依赖某个全局“当前活动调用”。

另外，运行时也提供了按调用绑定的：

```js
reportDetailedError(error, 'some context');
```

通常你自己只需要在业务代码里用 `try/catch` 做正常错误整理；底层桥接错误、Promise rejection 和 console 路径已经由运行时接管。

---

### 3.6 当前调用可用的上下文读取函数

在普通模块里，当前调用会注入这些便捷函数：

- `getEnv(key)`
- `getState()`
- `getLang()`
- `getCallerName()`
- `getChatId()`
- `getCallerCardId()`

例如：

```js
exports.run = async function () {
  const lang = getLang();
  const apiKey = getEnv('OPENAI_API_KEY');
  return { lang, hasApiKey: !!apiKey };
};
```

这些函数同样是**本次调用绑定**的，不需要任何全局上下文。

---

## 4. `toolCall`、`Tools` 与宿主能力

### 4.1 `toolCall` 是 Promise 风格

当前运行时中的 `toolCall(...)` 是异步的，返回 `Promise`。

支持几种常见写法：

```js
await toolCall('http_request', { url: 'https://example.com', method: 'GET' });
await toolCall('default', 'http_request', { url: 'https://example.com', method: 'GET' });
```

推荐统一写成 `await` 风格，不要把它当同步函数使用。

---

### 4.2 `Tools` 是宿主预定义桥

运行时也会注入 `Tools` 对象。它适合高层工具式调用；`toolCall` 更适合统一风格和动态调用。

如果你在写通用模块、可复用 helper，优先推荐：

- 对宿主工具能力用 `toolCall`
- 对 Java/Kotlin 系统能力用 `Java`

这样结构更清晰。

---

## 5. Java / Kotlin Bridge

### 5.1 入口对象

运行时会注入：

- `Java`
- `Kotlin`

它们是同一个桥的两个别名。

最常用的入口：

- `Java.type(className)`
- `Java.use(className)`
- `Java.importClass(className)`
- `Java.package(packageName)`
- `Java.newInstance(className, ...args)`
- `Java.callStatic(className, methodName, ...args)`
- `Java.callSuspend(className, methodName, ...args)`
- `Java.loadDex(path, options?)`
- `Java.loadJar(path, options?)`
- `Java.listLoadedCodePaths()`

---

### 5.2 基本示例

```js
const StringBuilder = Java.type('java.lang.StringBuilder');
const sb = new StringBuilder();
// 也可以：const sb = StringBuilder();
// 也可以：const sb = StringBuilder.newInstance();

sb.append('hello ');
sb.append('world');
const text = sb.toString();
```

类访问 / 嵌套类访问语法糖：

```js
const Integer = Java.java.lang.Integer;
const Build = Java.android.os.Build;
const Version = Java.android.os.Build.VERSION;
const AlertDialogBuilder = Java.android.app.AlertDialog.Builder;
```

静态字段 / 静态方法语法糖：

```js
const Integer = Java.java.lang.Integer;
const Build = Java.android.os.Build;

const maxValue = Integer.MAX_VALUE;
const parsed = Integer.parseInt('123');

const model = Build.MODEL;
const sdkInt = Build.VERSION.SDK_INT;

// 等价底层写法
const parsedByApi = Java.callStatic('java.lang.Integer', 'parseInt', '123');
```

挂起调用既可以走顶层 API，也可以走类代理语法糖：

```js
const SomeBridge = Java.com.ai.assistance.operit.SomeBridge;

const resultA = await SomeBridge.callSuspend('loadSomething', 'arg1');

const resultB = await Java.callSuspend(
  'com.ai.assistance.operit.SomeBridge',
  'loadSomething',
  'arg1'
);
```

如果你手里拿到的是实例代理，也同样支持：

```js
const stream = await enhancedAIService.callSuspend('sendMessage', options);
```

推荐统一写成 `await ...callSuspend(...)`，不要把 suspend 方法当同步函数使用。

---

### 5.3 Java 类、实例、包代理

桥里的对象不是 TypeScript 意义上的真实类实例，而是代理。

#### 类代理

```js
const File = Java.type('java.io.File');
const file = File.newInstance('/sdcard/test.txt');
```

#### 实例代理

```js
file.exists();
file.getName();
file.length();
```

#### 包代理

```js
const Runnable = Java.java.lang.Runnable;
```

这类代理支持：

- `new Cls(...args)` / `Cls(...args)` / `Cls.newInstance(...args)`
- `obj.method(...args)`，等价于 `obj.call('method', ...args)`
- `obj.field` / `obj.field = value`，等价于 `obj.get('field')` / `obj.set('field', value)`
- `Cls.STATIC_FIELD` / `Cls.STATIC_FIELD = value`，等价于 `Cls.getStatic('STATIC_FIELD')` / `Cls.setStatic('STATIC_FIELD', value)`
- `Cls.staticMethod(...args)`，等价于 `Cls.callStatic('staticMethod', ...args)`
- `Cls.InnerClass`，会按嵌套类解析成 `Outer$Inner`

例如：

```js
const System = Java.java.lang.System;
const now = System.currentTimeMillis();

const ActivityLifecycleManager = Java.com.ai.assistance.operit.core.application.ActivityLifecycleManager;
const activity = ActivityLifecycleManager.INSTANCE.getCurrentActivity();
```

对于 Kotlin 类代理上的静态方法调用，运行时还会自动尝试 `Companion` 回退；
因此很多 `companion object` 方法也可以直接写成 `SomeKotlinClass.someMethod()`。

#### 动态加载 dex / jar

如果脚本需要访问宿主 APK 之外的类，可以先把外部代码挂进 Java bridge 的
`ClassLoader` 链，再正常使用 `Java.type(...)` / `Java.xxx.yyy`。

加载 `.dex`：

```js
Java.loadDex('/data/user/<current_user_id>/com.ai.assistance.operit/files/plugins/demo.dex');

const DemoEntry = Java.type('com.example.demo.Entry');
const message = DemoEntry.callStatic('hello');
```

加载 `.jar`：

```js
Java.loadJar('/data/user/<current_user_id>/com.ai.assistance.operit/files/plugins/demo.jar');

const DemoEntry = Java.type('com.example.demo.Entry');
const instance = new DemoEntry();
```

如果外部代码还依赖 `.so`，可以额外指定原生库目录：

```js
Java.loadDex('/data/user/<current_user_id>/com.ai.assistance.operit/files/plugins/demo.dex', {
  nativeLibraryDir: '/data/user/<current_user_id>/com.ai.assistance.operit/files/plugins/lib'
});
```

如果外部 jar 和宿主 APK 存在同包名依赖冲突，可以显式指定子优先加载的包前缀：

```js
Java.loadJar('/data/user/<current_user_id>/com.ai.assistance.operit/files/plugins/demo.jar', {
  childFirstPrefixes: [
    'com.example.plugin.',
    'org.apache.commons.'
  ]
});
```

查看当前会话里已经挂载过哪些外部代码：

```js
const loaded = Java.listLoadedCodePaths();
console.log(JSON.stringify(loaded, null, 2));
```

约束说明：

- `loadDex(...)` 只接受 `.dex` 文件。
- `loadJar(...)` 只接受 `.jar` 文件，并且 **jar 内必须包含 `classes.dex`**。
- 传统 JVM `.class` jar 不能直接在 Android 里通过这个桥执行。
- 调用顺序要先 `loadDex/loadJar`，再 `Java.type(...)` 或包代理访问类。
- `childFirstPrefixes` 只会对子匹配包名前缀启用子优先查找，其余类仍保持默认父优先。
- 同一路径重复加载会复用当前会话里已经创建的加载记录，不会重复挂载。

---

### 5.4 接口实现与回调

你可以在 JS 里实现 Java 接口：

```js
const Runnable = Java.type('java.lang.Runnable');

const runnable = Java.implement(Runnable, {
  run() {
    console.log('Runnable called from Java');
  }
});
```

或者 SAM 风格：

```js
const runnable = Java.implement('java.lang.Runnable', () => {
  console.log('run');
});
```

如果调用位置本身能推断目标接口，也支持更短的写法：

```js
const runnable = Java.implement(() => {
  console.log('run');
});
```

`Java.proxy(...)` 只是 `Java.implement(...)` 的别名：

```js
const runnable = Java.proxy(Runnable, {
  run() {
    console.log('run via proxy alias');
  }
});
```

多接口实现也支持：

```js
const impl = Java.implement([
  'java.lang.Runnable',
  'java.io.Closeable'
], {
  run() {},
  close() {}
});
```

除了显式 `Java.implement(...)` / `Java.proxy(...)`，**回调位置**还支持对象字面量语法糖：

```js
const ViewOnClickListener = Java.android.view.View.OnClickListener;

button.setOnClickListener({
  onClick(view) {
    console.log('clicked', view);
  }
});

const listener = ViewOnClickListener({
  onClick(view) {
    console.log('clicked from class proxy', view);
  }
});
```

这里的核心规则是：

- 当 Java / Kotlin 方法或构造器的目标参数类型本身就是接口时，plain object 会自动适配成接口代理
- 对象上的同名方法会映射到接口方法
- `getX()` / `isX()` / `setX(v)` 这类 accessor，也可以映射到对象上的普通属性 `x`

例如：

```js
someApi.setListener({
  enabled: true,
  onChanged(value) {
    console.log('changed', value);
  }
});
```

如果接口方法是 `isEnabled()` / `getEnabled()` / `setEnabled(value)`，上面的 `enabled` 属性也可以被当成对应实现使用。

回调参数和返回值仍然走普通桥接转换：

- 参数会按桥接规则传回 JS
- 非 `void` / 非 `Unit` 方法可以直接 `return`
- `void` / `Unit` 回调可以不返回值

桥内部会给这些 JS 对象分配对象 ID，并通过宿主回调回 JS。

建议：

- 单一 SAM 接口位置，可以优先用 `Java.implement(() => {})` 这种简写
- 只是在某个方法调用点临时传 listener 时，优先用对象字面量回调，更短更直观
- 需要多接口、方法名更明确、或者文档可读性更强时，优先显式写接口名 / 类代理
- 不要把裸 JS 函数直接当普通桥接参数传给 Java；函数回调请走 `Java.implement(...)` / `Java.proxy(...)`

---

### 5.5 句柄生命周期：实例与 JS 回调自动解绑

桥接实例背后对应 handle。

Java 实例代理的 handle 解绑由运行时自动处理：

```js
const obj = Java.newInstance('java.lang.StringBuilder');
obj.append('abc');
return obj.toString();
```

建议：

- JS 接口回调标记不再需要手动释放；对应 Java 代理被 GC 后，运行时会自动解除当前 JS 回调注册
- Java 实例代理不要再写 `obj.release()` / `Java.release(...)` / `Java.releaseAll()`
- 运行时会在代理对象被 GC 后尝试解绑对应 handle，也会在引擎销毁时统一清理剩余句柄

---

### 5.6 桥接参数与返回值边界

这一节最重要的结论只有一句：

- **桥接会主动做类型归一化，不会把 Java 容器原样搬到 JS。**

最容易踩坑的是：

- Java `List` / `Set` / 其他 `Iterable`
- Java 数组
- `Map` / `JSONObject` / `JSONArray`

这些值一旦过桥到 JS，通常都会被归一成普通 JS 结构，而不是保留 Java 容器方法。

例如：

```js
const result = someJavaApi.listSomething();

Array.isArray(result); // 大概率是 true
result.length;         // 对
result[0];             // 对
result.size();         // 错，JS 里已经不是 java.util.List 了
```

#### Java / Kotlin -> JS

桥接返回到 JS 时，当前实际转换规则是：

| Java / Kotlin 侧值 | JS 侧表现 |
|------|------|
| `null` / Kotlin `Unit` | `null` |
| `String` / `CharSequence` / `char` | `string` |
| `boolean` / `Boolean` | `boolean` |
| `int` / `long` / `double` / 其他 `Number` | `number` |
| `float` | `number`（按 double 语义传） |
| `Enum` | 枚举名字符串 |
| `Class<?>` | 类名字符串 |
| `Map` / `JSONObject` | 普通 JS 对象 |
| `Iterable`（含 `List` / `Set` 等） | 普通 JS 数组 |
| Java 数组 | 普通 JS 数组 |
| `JSONArray` | 普通 JS 数组 |
| 其他普通 Java/Kotlin 对象 | Java 实例代理（handle proxy） |

这意味着：

- 从 Java 回来的 `List<File>`，在 JS 里要按数组用：`length`、索引、`map/filter`
- 从 Java 回来的 `Map<String, Object>`，在 JS 里要按 plain object 用
- 如果返回的是普通对象代理，才会有 `obj.method()`、`obj.field` 这一类桥接能力

#### JS -> Java / Kotlin

JS 传参给 Java / Kotlin 时，桥接会按目标参数类型自动尝试转换：

| JS 侧值 | 可自动适配到的 Java / Kotlin 目标 |
|------|------|
| `null` | 所有非 primitive 参数 |
| `string` | `String`，也可尝试转 `char` / `enum` / `Class<?>` / `JSONObject` / `JSONArray` |
| `number` | 各种数字类型：`byte` / `short` / `int` / `long` / `float` / `double` |
| `boolean` | `boolean` / `Boolean`，也可参与部分数字转换 |
| JS 数组 | Java 数组、`Collection`、`JSONArray` |
| plain object | `Map`、`JSONObject`，或在目标是接口时转成接口实现代理 |
| Java 实例代理 | 还原成原始 Java 对象 |
| `Java.implement(...)` / `Java.proxy(...)` 返回值 | Java 接口代理 |

额外几条当前已支持的自动转换：

- 目标参数是接口时，plain object 可以直接适配成接口代理
- 对象属性 `enabled` 可以映射到 `getEnabled()` / `isEnabled()` / `setEnabled(...)`
- JS 数组传给 Java varargs 时，会逐项按目标组件类型转换
- 字符串传给 `Class<?>` 参数时，会按类名尝试 `Class.forName(...)`
- `List` 传给 `JSONArray`、plain object 传给 `JSONObject` 也会自动包装

几个实用例子：

```js
// 1. JS 数组 -> Java 数组 / Collection
someApi.acceptStrings(['a', 'b', 'c']);

// 2. plain object -> Java Map / JSONObject
someApi.acceptConfig({
  enabled: true,
  retries: 3
});

// 3. plain object -> Java interface
button.setOnClickListener({
  onClick(view) {
    console.log('clicked');
  }
});

// 4. Java List -> JS array
const files = someApi.listFiles();
const names = files.map((file) => file.getName());
```

实际开发建议：

- 简单参数直接传原始值
- 集合返回值进 JS 后默认按 plain object / array 处理，不要假设还保留 Java 容器方法
- 需要对象行为时，确认拿到的是 Java 实例代理，而不是已经被归一化后的结构
- 需要高保真调用时，优先把复杂逻辑放在 Java/Kotlin 侧，然后从 JS 调一个干净的桥接方法

---

## 6. `compose_dsl`

`compose_dsl` 是一类特殊的运行时模块：它不是“返回普通 JSON 结果”，而是“返回一棵声明式 UI 树”。

---

### 6.1 入口函数长什么样

`compose_dsl` 模块的入口应导出：

- `default`
- 或 `Screen`

它们的签名都是：

```ts
type ComposeDslScreen = (ctx: ComposeDslContext) => ComposeNode | Promise<ComposeNode>
```

推荐写法：

```js
function Screen(ctx) {
  const { UI, Modifier } = ctx;
  return UI.Column(
    {
      modifier: Modifier.padding(16)
    },
    [
      UI.Text({ text: 'Hello compose_dsl' })
    ]
  );
}

exports.default = Screen;
```

或者：

```js
exports.Screen = function Screen(ctx) {
  return ctx.UI.Text({ text: 'Hello' });
};
```

对于 UI 模块，**`default` / `Screen` 才是正式入口**。

---

### 6.2 `ctx` 里有什么

`ComposeDslContext` 里最重要的是：

#### 状态能力

- `useState(key, initialValue)`
- `useMutable(key, initialValue)`
- `useRef(key, initialValue)`
- `useMemo(key, factory, deps?)`

#### UI 能力

- `UI.*`
- `Modifier`
- `MaterialTheme`
- `h(type, props, children)`

#### 宿主能力

- `callTool(...)`
- `toolCall?(...)`
- `getEnv(key)`
- `setEnv(key, value)`
- `navigate(route, args?)`
- `showToast(message)`
- `reportError(error)`

#### 包 / 模块身份

- `getModuleSpec()`
- `getCurrentPackageName()`
- `getCurrentToolPkgId()`
- `getCurrentUiModuleId()`

#### 包管理能力（可选）

- `isPackageImported(packageName)`
- `importPackage(packageName)`
- `removePackage(packageName)`
- `usePackage(packageName)`
- `listImportedPackages()`
- `resolveToolName(request)`

---

### 6.3 一个最小的 `compose_dsl` 示例

```js
function Screen(ctx) {
  const { UI, Modifier, useState } = ctx;
  const [count, setCount] = useState('count', 0);

  return UI.Column(
    {
      modifier: Modifier.padding(16),
      verticalArrangement: 'center'
    },
    [
      UI.Text({ text: `count = ${count}` }),
      UI.Button(
        {
          text: '加一',
          onClick: () => setCount(count + 1)
        }
      )
    ]
  );
}

exports.default = Screen;
```

这里有两个关键点：

- 事件处理函数可以直接写 JS 函数
- 状态变化后，宿主会驱动重新渲染

---

### 6.4 `compose_dsl` 的调用特点

与普通模块不同，`compose_dsl` 更像“宿主控制的渲染循环”。

你需要知道：

- 首次进入时，宿主会调用 screen，拿到一棵 UI 树
- 用户触发按钮、输入框、手势等动作后，宿主会分发 action
- action 内如果有异步逻辑，宿主会在状态变化后自动推动中间渲染
- 你不需要手动给 UI 自己发 `sendIntermediateResult` 来刷新界面

也就是说：

- **普通脚本的 stream** 由你自己在业务阶段调用 emitter
- **compose_dsl 的界面更新** 主要通过状态变化驱动，运行时自动处理中间渲染

这是两种不同层级的“流”。

---

### 6.5 `compose_dsl` 里的 `getEnv`

普通模块里的 `getEnv` 是按调用注入的。

在 `compose_dsl` 里，应该优先通过：

```js
ctx.getEnv('KEY')
```

而不是依赖某个全局 helper。

这是因为当前 DSL 上下文已经会把本次调用的运行时能力显式挂进 `ctx`。

---

### 6.6 `compose_dsl` 里的工具调用

```js
async function Screen(ctx) {
  const { UI, useState } = ctx;
  const [text, setText] = useState('text', 'loading...');

  return UI.Column({}, [
    UI.Text({ text }),
    UI.Button({
      text: '加载',
      onClick: async () => {
        const data = await ctx.callTool('http_request', {
          url: 'https://example.com',
          method: 'GET'
        });
        setText(JSON.stringify(data));
      }
    })
  ]);
}

exports.default = Screen;
```

推荐在 DSL 中统一走 `ctx.callTool(...)` 或 `ctx.toolCall(...)`，而不是把普通脚本里的 helper 直接硬搬过来。

---

### 6.7 资源与包操作

#### 读取资源

```js
const path = await ToolPkg.readResource('icon');
```

宿主返回的通常是一个可读路径或二进制内容。

#### 包管理

```js
const imported = await ctx.isPackageImported('com.operit.xxx');
if (!imported) {
  await ctx.importPackage('com.operit.xxx');
}
```

#### 解析工具名

```js
const toolName = await ctx.resolveToolName({
  packageName: 'com.operit.xxx',
  toolName: 'do_work'
});
```

---

## 7. 推荐的模块写法

### 7.1 普通脚本 / hook 模块

```js
const planner = require('./planner');
const render = require('./render');

async function onMessage(params) {
  log({ stage: 'start' });

  const plan = await planner.build(params);
  update({ stage: 'planned', plan });

  const result = await render.run(plan, params);
  return result;
}

exports.onMessage = onMessage;
```

### 7.2 helper 模块

```js
async function build(params) {
  return { tasks: [], raw: params };
}

exports.build = build;
```

### 7.3 `compose_dsl` 模块

```js
function Screen(ctx) {
  const { UI } = ctx;
  return UI.Text({ text: 'compose' });
}

exports.default = Screen;
```

---

## 8. 强烈建议避免的写法

### 8.1 不要依赖全局当前调用

错误思路：

```js
// 不要自己猜当前调用是谁
// 不要自己维护 callId
```

当前 runtime 已经把 emitter、日志、错误上报、上下文读取全部做成按调用绑定的局部能力。

---

### 8.2 不要把未转译的 ESM 直接交给运行时

错误：

```ts
import { foo } from './foo';
export const run = () => {};
```

如果没有编译到 CommonJS，就不要直接运行。

---

### 8.3 不要假设模块跨调用持久化

错误：

```js
let cache = null;
```

不要依赖它跨调用保存状态。

---

### 8.4 不要在插件里手动处理 stream 归属

错误方向：

- 手动抓 `callId`
- 手动写“当前活动调用”全局变量
- 手动 patch Promise/微任务归属

这些都属于引擎层，不属于脚本层。

---

## 9. 一页总结

如果你只记四件事，就记这四件：

1. **模块按 CommonJS 写**：`require`、`exports`、`module.exports`
2. **一次调用就是一次新会话**：不要依赖跨调用模块状态
3. **stream 直接用注入的 emitter**：`sendIntermediateResult / emit / delta / log / update / done / complete`
4. **UI 用 `compose_dsl` 时导出 `default` 或 `Screen`**，业务能力都从 `ctx` 走

---

## 10. 相关类型定义

建议同时参考：

- `examples/types/java-bridge.d.ts`
- `examples/types/compose-dsl.d.ts`

这两份定义是理解当前脚本运行时边界最直接的入口。
