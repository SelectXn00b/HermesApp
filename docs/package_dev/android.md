# API 文档：`android.d.ts`

`android.d.ts` 提供了一套比 `Tools.System` 更底层、更面向 Android 原生对象的 API。它的设计核心是几组类：`AdbExecutor`、`Intent`、`PackageManager`、`ContentProvider`、`SystemManager`、`DeviceController` 和总入口 `Android`。

## 全局入口

以下对象在 `index.d.ts` 中被声明为全局可用：

- `Intent`
- `IntentFlag`
- `IntentAction`
- `IntentCategory`
- `Android`

也可以从 `index.d.ts` 的导出里按类型使用。

## `AdbExecutor`

这是多个 Android 辅助类的基类。

### 方法

- `executeAdb(command, timeout?)`
- `executeShell(command, timeout?)`
- `parseKeyValueOutput(output, separator?)`
- `escapeShellArg(str)`

适合封装原始 ADB / shell 操作与结果解析。

## `IntentFlag` / `IntentAction` / `IntentCategory`

这三个常量枚举提供了 Intent 的常用标志、动作与分类。

### `IntentFlag`

覆盖了：

- Activity 启动标志，如 `ACTIVITY_NEW_TASK`、`ACTIVITY_CLEAR_TOP`
- URI 权限标志，如 `GRANT_READ_URI_PERMISSION`
- Receiver 标志，如 `RECEIVER_FOREGROUND`
- 其他系统标志

### `IntentAction`

覆盖了常见：

- 标准动作，如 `ACTION_VIEW`、`ACTION_SEND`、`ACTION_PICK`
- 系统设置动作，如 `ACTION_SETTINGS`
- 媒体动作，如 `ACTION_IMAGE_CAPTURE`
- 通信动作与广播动作

### `IntentCategory`

覆盖了常见分类，如：

- `CATEGORY_DEFAULT`
- `CATEGORY_BROWSABLE`
- `CATEGORY_LAUNCHER`
- `CATEGORY_HOME`
- 各类 `CATEGORY_APP_*`

完整枚举以 `examples/types/android.d.ts` 为准。

## `Intent` 类

### 构造函数

```ts
new Intent(action?: string | IntentAction)
```

### 主要属性

- `action`
- `packageName`
- `component`
- `extras`
- `flags`
- `categories`
- `executor`
- `uri`
- `type`

### 链式方法

- `setComponent(packageName, component)`
- `setPackage(packageName)`
- `setAction(action)`
- `setData(uri)`
- `setType(type)`
- `addCategory(category)`
- `removeCategory(category)`
- `hasCategory(category)`
- `getCategories()`
- `clearCategories()`
- `addFlag(flag)`
- `putExtra(key, value)`

### 执行动作

- `start()`：按 Activity 启动
- `sendBroadcast()`：发送广播
- `startService()`：启动 Service

## `PackageManager`

继承自 `AdbExecutor`，用于应用包管理。

### 方法

- `install(apkPath, replaceExisting?)`
- `uninstall(packageName, keepData?)`
- `getInfo(packageName)`
- `getList(includeSystem?)`
- `clearData(packageName)`
- `isInstalled(packageName)`

## `ContentProvider`

继承自 `AdbExecutor`，用于访问 Content Provider。

### 方法

- `setUri(uri)`
- `query(projection?, selection?, selectionArgs?, sortOrder?)`
- `insert(values)`
- `update(values, selection?, selectionArgs?)`
- `delete(selection?, selectionArgs?)`

## `SystemManager`

继承自 `AdbExecutor`，用于读取和修改系统属性、设置及屏幕信息。

### 方法

- `getProperty(prop)`
- `setProperty(prop, value)`
- `getAllProperties()`
- `getSetting(namespace, key)`
- `setSetting(namespace, key, value)`
- `listSettings(namespace)`
- `getScreenInfo()`

其中 `namespace` 受类型约束：

```ts
'system' | 'secure' | 'global'
```

## `DeviceController`

继承自 `AdbExecutor`，用于设备级控制。

### 方法

- `takeScreenshot(outputPath)`
- `recordScreen(outputPath, timeLimit?, bitRate?, size?)`
- `setBrightness(brightness)`
- `setVolume(stream, volume)`
- `setAirplaneMode(enable)`
- `setWiFi(enable)`
- `setBluetooth(enable)`
- `lock()`
- `unlock()`
- `reboot(mode?)`

`setVolume()` 的 `stream` 受类型约束：

```ts
'music' | 'call' | 'ring' | 'alarm' | 'notification'
```

## `Android` 总入口

### 构造函数

```ts
new Android()
```

### 成员

- `packageManager`
- `systemManager`
- `deviceController`

### 工厂方法

- `createIntent(action?)`
- `createContentProvider(uri)`

## 示例

### 构造并启动 Intent

```ts
const intent = new Intent(IntentAction.ACTION_VIEW)
  .setData('https://example.com')
  .addFlag(IntentFlag.ACTIVITY_NEW_TASK);

await intent.start();
```

### 使用 `Android` 总入口

```ts
const android = new Android();
const installed = await android.packageManager.isInstalled('com.android.settings');
console.log(installed);
```

### 读取系统设置

```ts
const android = new Android();
const value = await android.systemManager.getSetting('secure', 'enabled_accessibility_services');
console.log(value);
```

### 截图

```ts
const android = new Android();
await android.deviceController.takeScreenshot('/sdcard/screen.png');
```

## 相关文件

- `examples/types/android.d.ts`
- `examples/types/index.d.ts`
- `docs/package_dev/system.md`
