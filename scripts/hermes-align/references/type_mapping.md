# Python → Kotlin 类型映射速查

## 基础类型

| Python | Kotlin | 说明 |
|--------|--------|------|
| `str` | `String` | |
| `int` | `Int` | |
| `float` | `Double` | Kotlin 默认双精度 |
| `bool` | `Boolean` | |
| `bytes` | `ByteArray` | |
| `None` | `null` / `Unit` | 返回 None → 返回 Unit（无返回值）|
| `list[X]` | `List<X>` | Kotlin 用不可变 List，可变用 MutableList |
| `dict[K,V]` | `Map<K,V>` | 同上 |
| `tuple[X, Y]` | `Pair<X,Y>` 或 `data class` | 2 元组用 Pair，3+ 用 data class |
| `set[X]` | `Set<X>` | |
| `Optional[X]` | `X?` | 可空类型 |

## 标准库映射

| Python | Kotlin | 说明 |
|--------|--------|------|
| `pathlib.Path` | `java.io.File` / `java.nio.file.Path` | |
| `datetime.datetime` | `java.time.LocalDateTime` | |
| `json.dumps/dicts` | `kotlinx.serialization` / `org.json` | |
| `subprocess.Popen` | `ProcessBuilder` | |
| `threading.Thread` | `kotlinx.coroutines` | Kotlin 优先用协程 |
| `asyncio` | `kotlinx.coroutines` | |
| `logging` | `java.util.logging` / Timber | |
| `sqlite3` | Room Database | |
| `openai` (SDK) | Retrofit + OkHttp | 直接 HTTP 调用 |
| `pydantic` (model) | `data class` + `kotlinx.serialization` | |
| `requests/httpx` | OkHttp / Ktor | |
| `yaml` | SnakeYAML / kotlinx.serialization | |
| `fire` (CLI) | 无对应，用 CLI 参数解析库 | |
| `rich` | 无对应，Android 用原生 UI | |
| `prompt_toolkit` | 无对应，Android 用原生 UI | |

## Hermes 特有类型

| Python | Kotlin | 说明 |
|--------|--------|------|
| `AIAgent` (run_agent) | `AIAgent` | 保持原名 |
| `HermesCLI` | `HermesCLI` | Android 可能不存在（终端 UI 不适用）|
| `ToolResult` | `ToolResult` | |
| `EnvironmentInfo` | `EnvironmentInfo` | |
| `Message` | `Message` | |
| `ConversationTurn` | `ConversationTurn` | |

## 关键转换规则

### dataclass → Kotlin data class
```python
# Python
@dataclass
class EnvironmentInfo:
    name: str
    class_name: str
    file_path: str
    description: str = ""
```
```kotlin
// Kotlin
data class EnvironmentInfo(
    val name: String,
    val className: String,
    val filePath: String,
    val description: String = ""
)
```

### Enum → Kotlin enum class
```python
# Python
class PlatformType(str, Enum):
    TELEGRAM = "telegram"
    DISCORD = "discord"
```
```kotlin
// Kotlin
enum class PlatformType(val value: String) {
    TELEGRAM("telegram"),
    DISCORD("discord")
}
```

### TypedDict → Kotlin data class
```python
# Python
class ToolDefinition(TypedDict):
    name: str
    description: str
```
```kotlin
// Kotlin
data class ToolDefinition(
    val name: String,
    val description: String
)
```

### 异步函数
```python
# Python
async def handle_message(self, message: Message) -> str:
```
```kotlin
// Kotlin
suspend fun handleMessage(message: Message): String
```
