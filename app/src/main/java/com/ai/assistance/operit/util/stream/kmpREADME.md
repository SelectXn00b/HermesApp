# StreamKmpGraph: 强大的流式模式匹配库

`StreamKmpGraph` 是一个使用 Kotlin 编写的高效、可扩展的流式模式匹配库。它结合了 Knuth-Morris-Pratt (KMP) 算法思想和灵活的条件匹配，提供了一个强大的 DSL（领域特定语言）来构建复杂的匹配模式。该库特别适用于需要逐字符处理数据流并实时识别模式的场景，同时支持捕获组以提取匹配内容。


## 核心特性
*   **流式处理:** 能够逐字符处理输入，非常适合处理大型文件或网络数据流，内存占用低。
*   **丰富的匹配条件:** 不仅仅是字符匹配，还支持字符范围、字符集、逻辑组合（与、或、非）以及自定义断言。
*   **强大的DSL:** 提供了一个直观、简洁的 DSL 来定义复杂的匹配模式。
*   **捕获组:** 支持类似正则表达式的捕获组，可以轻松提取匹配到的子字符串。
*   **混合动力:** 内部利用 KMP 算法的效率进行快速状态转移，在最终匹配时结合正则表达式的强大功能来提取捕获组。

---

## 核心概念

1.  **`KmpCondition`**: 匹配条件的核心接口。它定义了字符是否满足某个条件。库中提供了多种实现：
    *   `CharCondition`: 匹配单个特定字符。
    *   `CharRangeCondition`: 匹配指定范围内的任意字符。
    *   `CharSetCondition`: 匹配指定字符集中的任意字符。
    *   `PredicateCondition`: 使用自定义的 lambda 表达式进行匹配。
    *   `NotCondition`, `OrCondition`, `AndCondition`: 用于对其他条件进行逻辑组合。

2.  **`KmpPattern`**: 用于构建匹配模式的 DSL 入口。通过它，你可以链式调用各种方法来定义一个完整的匹配规则。

3.  **`StreamKmpGraph`**: 由 `KmpPattern` 构建的状态机。它接收字符流，并在内部根据 KMP 算法进行状态转移。

4.  **`StreamKmpMatchResult`**: `processChar` 方法的返回类型，表示每一步处理的结果：
    *   `NoMatch`: 当前字符不匹配任何模式，状态机已重置。
    *   `InProgress`: 当前字符是某个潜在匹配的一部分。
    *   `Match`: 当前字符完成了一个或多个模式的匹配。`Match` 对象包含捕获的组信息和是否为完整匹配的标志。

---

## 快速上手

### 1. 定义一个简单的模式

使用 `kmpPattern` DSL 可以轻松创建一个模式。例如，匹配字符串 "abc":

```kotlin
import com.ai.assistance.operit.util.stream.kmpPattern

val pattern = kmpPattern {
    literal("abc")
}
```

### 2. 构建图并匹配文本

使用 `StreamKmpGraphBuilder` 从模式构建图，然后使用 `processText` 方法在文本中查找所有匹配项的位置。

```kotlin
import com.ai.assistance.operit.util.stream.StreamKmpGraphBuilder

val builder = StreamKmpGraphBuilder()
val graph = builder.build(pattern)

val matches = graph.processText("ababcabc")
// matches 将会是 [5, 8]，表示 "abc" 在索引 5 和 8 处结束。
println(matches)
```

---

## DSL 详解

DSL 提供了丰富的方法来构建复杂的模式。

### 1. 基本条件

```kotlin
kmpPattern {
    // 匹配单个字符 'a'
    char('a')
    
    // 匹配字符串 "hello"
    literal("hello")
    
    // 匹配 'a' 到 'z' 之间的任意字符
    range('a', 'z')
    
    // 匹配 'a', 'e', 'i', 'o', 'u' 中的任意一个
    anyOf('a', 'e', 'i', 'o', 'u')

    // 匹配任意单个字符 (通配符)
    any()
}
```

### 2. 预定义字符类

为了方便，库预定义了一些常见的字符类。

```kotlin
kmpPattern {
    digit()           // 匹配任意数字 [0-9]
    letter()          // 匹配任意字母 [a-zA-Z]
    letterOrDigit()   // 匹配字母或数字
    whitespace()      // 匹配空白字符 (空格, tab 等)
}
```

### 3. 逻辑与否定

可以对条件进行逻辑组合。

```kotlin
kmpPattern {
    // 否定条件
    notChar('a')                  // 匹配非 'a' 的字符
    noneOf('x', 'y', 'z')         // 匹配非 x, y, z 的字符
    notInRange('0', '9')          // 匹配非数字字符
    notDigit()                    // 等同于 notInRange('0', '9')
    not(WHITESPACE)               // 匹配非空白字符

    // 组合条件 (DSL 也支持操作符 `+` for OR and `*` for AND)
    add(OrCondition(CharCondition('a'), CharCondition('b'))) // 匹配 'a' 或 'b'
    add(AndCondition(letter(), not(anyOf('x', 'y', 'z')))) // 匹配一个非x,y,z的字母
}
```

### 4. 重复

*   `repeat(count) { ... }`: 重复一个模式固定的次数。
*   `greedyStar { ... }`: "贪婪星号"，匹配零次或多次。它会尽可能多地匹配，但不会 "吃掉" 导致后续模式无法匹配的字符。

```kotlin
// 匹配 "aaa"
val pattern1 = kmpPattern {
    repeat(3) { char('a') }
}

// 匹配 "b"、"bb"、"bbb" 等任意多个 "b"，然后跟一个 "a"
// 例如，可以匹配 "bbba"
val pattern2 = kmpPattern {
    greedyStar { char('b') }
    char('a')
}
```

### 5. 捕获组

这是 `StreamKmpGraph` 的一个核心功能。使用 `group(id) { ... }` 来定义一个捕获组，`id` 是一个你指定的整数 `Int` ID。匹配成功后，可以根据 ID 提取内容。

```kotlin
// 匹配 "id:123" 并提取数字
val pattern = kmpPattern {
    literal("id:")
    group(1) {
        repeat(3) { digit() }
    }
}

val builder = StreamKmpGraphBuilder()
val graph = builder.build(pattern)

// 使用流式API
graph.reset()
graph.processChar('i')
graph.processChar('d')
graph.processChar(':')
graph.processChar('1')
graph.processChar('2')
val result = graph.processChar('3')

if (result is StreamKmpMatchResult.Match && result.isFullMatch) {
    val capturedNumber = result.groups[1] // "123"
    println("Captured: $capturedNumber")
}
```
#### 嵌套分组

分组可以嵌套，这对于解析结构化数据非常有用。

```kotlin
// 匹配 "(ax7)" 并分别捕获 "(ax7)" 和 "ax7"
val nestedGroupPattern = kmpPattern {
    group(1) { // 外部组
        char('(')
        group(2) { // 内部组
            letter()
            digit()
        }
        char(')')
    }
}
val graph = StreamKmpGraphBuilder().build(nestedGroupPattern)
graph.reset()
graph.processChar('(')
graph.processChar('a')
graph.processChar('7')
val result = graph.processChar(')')

if (result is StreamKmpMatchResult.Match) {
    // result.groups[1]会是"(a7)"
    // result.groups[2]会是"a7"
    println("Group 1: ${result.groups[1]}, Group 2: ${result.groups[2]}")
}

```

---

## 流式处理 API (`processChar`)

对于需要实时响应的场景，`processChar` 是首选方法。它一次处理一个字符并立即返回结果。

### 工作流程:

1.  **重置**：调用 `graph.reset()` 清空状态。
2.  **逐字符处理**: 在循环中将字符传入 `graph.processChar(c)`。
3.  **检查结果**:
    *   `InProgress`: 匹配正在进行中，继续提供下一个字符。
    *   `Match`: 找到了一个完整匹配。你可以从结果中提取捕获组。匹配完成后，KMP 的失败函数会自动处理，允许立即开始下一次匹配，无需手动重置。
    *   `NoMatch`: 当前字符导致匹配失败且无法回退，状态机已自动重置。

### 示例：解析日志流

假设我们有一个日志流，格式为 `[LEVEL] message`，我们想提取 `LEVEL` 和 `message`。

```kotlin
// 模式: [ (LEVEL) ] (MESSAGE)
val logPattern = kmpPattern {
    char('[')
    group(1) {
        // LEVEL: 匹配多个字母
        greedyStar { letter() }
    }
    char(']')
    char(' ')
    group(2) {
        // MESSAGE: 匹配到行尾
        greedyStar { any() }
    }
}

val graph = StreamKmpGraphBuilder().build(logPattern)

fun processLogStream(stream: Sequence<Char>) {
    graph.reset()
    var potentialMatchBuffer = ""
    stream.forEach { char ->
        // 当我们遇到换行符或流结束时，可以认为一个日志条目结束
        if(char == '\n') {
            val result = graph.processText(potentialMatchBuffer)
            // processText不返回分组，这里需要修改逻辑
            // 为了简单起见，我们假设每个日志条目都是独立处理的
            potentialMatchBuffer = ""
            graph.reset()
        } else {
             potentialMatchBuffer += char
        }

        val result = graph.processChar(char)
        if (result is StreamKmpMatchResult.Match && result.isFullMatch) {
            val level = result.groups[1]
            val message = result.groups[2]
            println("Level: $level, Message: $message")
            // 在流式场景中，匹配后可能需要重置或根据业务逻辑决定下一步
            graph.reset()
        }
    }
}

val logStream = "[INFO] User logged in.\n[DEBUG] Cache cleared.\n".asSequence()
// processLogStream(logStream) // 此处调用逻辑需要根据具体场景细化
```
*注意: 上述流处理示例逻辑需要根据具体应用场景细化，例如如何界定一条消息的结束。*

---

## 工作原理简介

`StreamKmpGraph` 采用一种混合策略以兼顾性能和功能：

1.  **KMP 状态机**: 模式首先被编译成一个 KMP 状态机。这个状态机非常高效，可以快速地对字符流进行状态转移，而不需要像传统正则表达式那样进行大量回溯。
2.  **二次正则匹配**: 当 KMP 状态机到达一个最终状态（`isFinal = true`）时，表明一个潜在的完整匹配已经找到。此时，库会将在缓冲区中累积的、可能匹配的文本片段，与一个由原始 `KmpPattern` 动态生成的、等效的**标准正则表达式**进行匹配。
3.  **提取捕获组**: 这次标准正则匹配的唯一目的是为了利用正则引擎成熟的**捕获组**功能。从 `MatchResult` 中提取出分组信息，然后封装到 `StreamKmpMatchResult` 中返回。

这种设计使得 `StreamKmpGraph` 既有 KMP 算法在流式处理上的高速优势，又能提供正则表达式强大的捕获组功能，是一种兼顾了性能和灵活性的解决方案。
