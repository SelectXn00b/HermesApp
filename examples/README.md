# VSCode中运行JavaScript到Android设备的使用说明

本项目配置了通过VSCode直接运行JavaScript文件到Android设备的功能。这让您可以快速开发和测试JavaScript脚本，无需手动执行命令行工具。

## 准备工作

1. 确保安装了VSCode
2. 确保Android设备已连接并已启用USB调试
3. 确保ADB工具可用（已添加到系统路径）
4. 确保Android设备上安装了兼容的应用程序

## 使用方法

### 方法一：通过调试菜单（推荐）

1. 在VSCode中打开要运行的JavaScript文件
2. 按下`F5`键或点击调试菜单中的"开始调试"
3. 在弹出的输入框中输入以下信息：
   - 要执行的JavaScript函数名（如：`showMessage`）
   - 函数参数，使用JSON格式（如：`{"name": "张三", "message": "你好"}`）
4. 执行结果将在终端窗口显示

### 方法二：通过任务菜单

1. 在VSCode中打开要运行的JavaScript文件
2. 按下`Ctrl+Shift+B`（Windows/Linux）或`Cmd+Shift+B`（macOS）
3. 在弹出的输入框中输入函数名和参数，同上

## 示例

项目中包含了示例脚本 `examples/demo_script.js`，其中定义了以下函数：

### `main(params)`

默认函数，简单返回提供的参数。

示例参数：`{"message": "测试"}`

### `showMessage(params)`

显示消息和当前时间。

示例参数：`{"name": "张三", "message": "欢迎使用"}`

### `calculate(params)`

执行简单的计算操作。

示例参数：`{"a": 10, "b": 5, "operation": "add"}`

可用的操作类型：
- `add`：加法
- `subtract`：减法
- `multiply`：乘法
- `divide`：除法

## 注意事项

1. 确保JavaScript函数中使用`complete()`函数返回结果，这是必须的
2. 参数必须是有效的JSON格式
3. 脚本执行可能需要一些时间，请耐心等待结果
4. 如果遇到问题，请检查VSCode的终端输出和Android设备的日志

## 高级用法

如果需要更复杂的执行选项，可以直接在命令行使用以下命令：

Windows:
```
.\tools\execute_js.bat <JS文件路径> <函数名> <JSON参数或@参数文件>
```

Linux/macOS:
```
./tools/execute_js.sh <JS文件路径> <函数名> <JSON参数或@参数文件>
```

例如：
```
.\tools\execute_js.bat examples\demo_script.js calculate @params.json
```

补充说明：
- `@params.json` 是当前最稳的传参方式，尤其适合 Windows / PowerShell
- 如果使用 `run_sandbox_script.*` 或包内的 `debug_run_sandbox_script` 并传 `source_code`，代码会按“顶层脚本”执行
- 这类内联脚本里应使用 `console.log(...)`、`emit(...)`、`complete(...)`
- 不要直接写 `params.xxx` 或 `intermediate(...)`


# Assistance Tool Packages

This directory contains tool packages that can be loaded by the AI assistant. These packages provide additional functionality to the assistant, allowing it to perform specialized tasks.

## Package Formats

Packages can be defined in two formats:

1. **JavaScript (.js)** - Preferred format with full support for Promise-based async execution
2. **HJSON (.hjson)** - Legacy format with limited functionality (maintained for backward compatibility)

## JavaScript Package Structure

A JavaScript package consists of a single JavaScript file with:

1. A **metadata block** at the top of the file
2. **Tool function definitions** that implement the functionality
3. Optional **exports** statements to make the functions available

### Example JavaScript Package:

```javascript
/*
METADATA
{
    "name": "example_package",
    "description": "An example package showing how to create tools",
    "tools": [
        {
            "name": "hello_world",
            "description": "A simple hello world tool",
            "parameters": [
                {
                    "name": "name",
                    "description": "Name to greet",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "FILE_READ"
}
*/

// Tool implementation
async function hello_world(params) {
    // Access parameters
    const name = params.name || "World";
    
    // Use async/await with other tools
    await Tools.System.sleep(1);
    
    // Return a result
    complete(`Hello, ${name}!`);
}

// Export the function
exports.hello_world = hello_world;
```

## TypeScript Support

You can also write your packages in TypeScript (.ts) with full type checking for all APIs. TypeScript packages are automatically compiled to JavaScript at runtime.

### Parameter Typing Rule

For tool packages under `examples`, Kotlin now converts tool params to strong types before invoking the TypeScript/JavaScript runtime, based on each tool's metadata.

- In TypeScript, parameter types should match metadata directly.
- Fields with `"required": false` should be modeled as optional `?`.
- Do not keep legacy TS-layer coercion such as `string -> number`, `string -> boolean`, `JSON string -> array/object`, or duplicate "required parameter" checks that only existed to compensate for old permissive passthrough behavior.
- Keep only validations that metadata cannot express well, such as enum/domain rules, cross-field constraints, and nested object/array shape checks.

### TypeScript Parameter Behavior

Parameters passed to `.ts` example packages rely on Kotlin’s metadata-driven conversion. The Kotlin bridge reads each tool’s `type`/`required` flags and converts values _before_ they arrive in JavaScript/TypeScript, which means the TS layer should trust the metadata typing and focus on higher-level business rules. Please do not add string→number/boolean/array coercion or re-check `required` fields that Kotlin already enforces, and treat `required: false` parameters as optional (`?`) with their actual primitive types.

### Example TypeScript Package:

```typescript
/*
METADATA
{
    "name": "typescript_example",
    "description": "A TypeScript example package",
    "tools": [
        {
            "name": "hello_world",
            "description": "A simple hello world tool with TypeScript",
            "parameters": [
                {
                    "name": "name",
                    "description": "Name to greet",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "FILE_READ"
}
*/

/**
 * Simple hello world function with TypeScript
 * @param params Tool parameters with name property
 */
async function hello_world(params: { name: string }): Promise<void> {
    // Access parameters with type checking
    const name = params.name || "World";
    
    // Use async/await with proper typing
    await Tools.System.sleep(1);
    
    // Return a result
    complete(`Hello, ${name}!`);
}

// Export the function
exports.hello_world = hello_world;
```

## Promise-Based Async API

All tool calls now return Promises, allowing for async/await pattern usage:

```javascript
async function chain_example(params) {
    try {
        // Chain multiple tool calls with await
        const fileList = await Tools.Files.list("/some/path");
        const fileContents = await Tools.Files.read("/some/path/file.txt");
        
        // Do work with the results
        const result = `Found ${fileList.length} files. First file contents: ${fileContents}`;
        
        complete(result);
    } catch (error) {
        complete(`Error: ${error.message}`);
    }
}
```

## Available Tools API

The following utility namespaces are available in all package scripts:

### Tools.Files
- `list(path)` - List files in a directory
- `read(path)` - Read file contents
- `write(path, content)` - Write content to file
- `deleteFile(path)` - Delete a file or directory
- `exists(path)` - Check if file exists
- `move(source, target)` - Move file from source to target
- `copy(source, target)` - Copy file from source to target
- `mkdir(path)` - Create a directory
- `find(path, pattern)` - Find files matching a pattern
- `info(path)` - Get information about a file
- `zip(source, target)` - Zip files/directories
- `unzip(source, target)` - Unzip an archive
- `open(path)` - Open a file with system handler

### Tools.Net
- `httpGet(url)` - Perform HTTP GET request
- `httpPost(url, data)` - Perform HTTP POST request
- `visit(query)` - Perform web search

### Tools.System
- `exec(command)` - Execute a system command
- `sleep(seconds)` - Sleep for specified seconds

### Other Utilities
- `Tools.calc(expression)` - Calculate mathematical expression
- `_` - Lodash-like utility library
- `dataUtils` - Data processing utilities

## Creating a New Package

1. Create a new `.js` or `.ts` file in this directory
2. Add a METADATA block at the top with package details
3. Define your tool functions
4. Export the functions using the CommonJS pattern
5. Use the Promise-based API for any asynchronous operations

## Testing Your Package

You can test your package by using the `use_package` tool in the assistant chat:

```
<tool name="use_package">
  <param name="package_name">your_package_name</param>
</tool>
```

After the package is loaded, you can use any of its tools:

```
<tool name="your_package_name:your_tool_name">
  <param name="param1">value1</param>
</tool>
```

## Function Name Duplication Issue

Currently, these example scripts have a common issue: they contain duplicate function names across different files, even though the scripts are designed to operate independently. This can cause problems if:

1. Multiple scripts are imported into the same project
2. The scripts are executed in an environment where name collisions matter
3. Future integration of these scripts is planned

### Recommended Solutions:

#### 1. Namespace Pattern

Wrap all functions in a namespace object to avoid global namespace pollution:

```javascript
// Instead of:
function get_history(params) { /* ... */ }

// Use:
const QQTools = {
  get_history: function(params) { /* ... */ },
  reply: function(params) { /* ... */ }
};

// Then call as:
QQTools.get_history(params);
```

#### 2. Module Pattern (IIFE)

Use the module pattern to encapsulate functions:

```javascript
const QQIntelligent = (function() {
  // Private functions
  function close_keyboard() { /* ... */ }
  
  // Public API
  return {
    get_history: function(params) { /* ... */ },
    reply: function(params) { /* ... */ }
  };
})();
```

#### 3. ES6 Modules (For TypeScript files)

Convert the scripts to proper ES6 modules:

```typescript
// In qq_intelligent.ts
export function get_history(params) { /* ... */ }
export function reply(params) { /* ... */ }

// In another file that needs these functions
import { get_history, reply } from './qq_intelligent';
```

#### 4. Function Prefix

If modifying the code structure is difficult, prefix all functions with a unique identifier:

```javascript
function qq_get_history(params) { /* ... */ }
function qq_reply(params) { /* ... */ }
```

Adopting one of these approaches would resolve the function name duplication issue while maintaining the independence of each script. 
