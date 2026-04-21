# 允许的命名偏差（Exceptions）

以下情况允许偏离 Hermes 原始命名，不计入对齐度扣分。

## 1. Android 平台适配

| Hermes 原始 | Android 允许 | 原因 |
|-------------|-------------|------|
| `HermesCLI` (终端 UI) | 不移植 | Android 无终端 UI |
| `prompt_toolkit` 相关 | 不移植 | Android 用原生 UI |
| `rich` 显示相关 | 不移植 | Android 用原生 UI |
| `curses_ui.py` | 不移植 | Android 无 curses |
| `banner.py` | 不移植 | Android 无终端 banner |
| `setup.py` (CLI setup) | 不移植 | Android 用 App 初始化 |

## 2. 语言约定

| 说明 | 规则 |
|------|------|
| Python snake_case → Kotlin camelCase | 方法名/变量名自动转换 |
| Python UPPER_SNAKE_CASE 常量 | Kotlin 保持 UPPER_SNAKE_CASE |
| Python 私有 `_method` 前缀 | Kotlin 用 `private` 可见性修饰符，去掉下划线 |
| Python `__method` 双下划线 | Kotlin 用 `private` 可见性修饰符 |

## 3. 包/目录结构

| Python | Kotlin | 说明 |
|--------|--------|------|
| `gateway/platforms/` | `gateway/platforms/` | 保持相同子目录名 |
| `tools/browser_providers/` | `tools/browserProviders/` | Kotlin 用 camelCase 目录（可选）|
| `tools/environments/` | `tools/environments/` | 保持相同 |

## 4. 第三方依赖差异

| Python 功能 | Kotlin 替代 | 命名允许偏差 |
|-------------|------------|-------------|
| `subprocess.Popen` | `ProcessBuilder` | 方法名可适配 |
| `threading.Lock` | `ReentrantLock` 或 `Mutex` | 方法名可适配 |
| `logging.getLogger(__name__)` | `Timber.tag(TAG)` | 命名可适配 |
| `@dataclass` | `data class` | 保持字段名一致 |
| `asyncio.Queue` | `Channel` | 方法名可适配 |

## 5. Android 特有扩展（单独标记）

Android 特有的功能模块不计入对齐度，但必须标记为 Android 扩展：

- `AgentConfigActivity.kt` — Agent 配置 UI
- `TaskerTool.kt` — Tasker 集成
- `KeepAliveWorker.kt` — WorkManager 拉活
- `OpenClawConfig.kt` — 配置模型

标记方式：在文件头部加注释 `// ANDROID_EXTENSION: 不计入对齐度`
