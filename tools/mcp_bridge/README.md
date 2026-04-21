# MCP TCP 桥接器

这个工具充当基于STDIO的MCP服务器与TCP客户端之间的桥梁，让无法直接访问命名管道(FIFO)的客户端(如Android应用)能够与MCP服务器通信。

## 功能特点

- 将STDIO型MCP服务器桥接到TCP端口
- 支持并发连接
- 自动重启崩溃的MCP进程
- 优雅地处理连接错误和进程终止
- 详细的日志记录

## 安装

```bash
# 进入bridge目录
cd bridge

# 安装依赖
npm install

# 编译TypeScript
npm run build
```

## 使用方法

### 基本用法

```bash
# 使用默认配置启动
npm start
```

这将在默认端口8752上启动桥接器，连接到`../your-mcp-server.js`。

### 高级用法

您可以通过命令行参数自定义桥接器：

```bash
# 格式
npm start -- [端口] [命令] [参数...]

# 示例：在端口9000上启动，连接到Python MCP服务器
npm start -- 9000 python ../python_mcp_server.py

# 示例：连接到Java MCP服务器
npm start -- 8752 java -jar ../java_mcp_server.jar
```

### 在代码中使用

您也可以在其他Node.js应用中使用这个桥接器：

```typescript
import McpBridge from './path/to/bridge';

const bridge = new McpBridge({
  port: 8752,
  host: '127.0.0.1',
  mcpCommand: 'node',
  mcpArgs: ['../your-mcp-server.js'],
  verbose: true
});

bridge.start();
```

## 在Termux中使用

在Termux环境中，可以按以下步骤设置：

```bash
# 安装Node.js
pkg install nodejs

# 克隆或下载此项目
git clone <项目URL>
cd <项目目录>/bridge

# 安装依赖
npm install

# 编译
npm run build

# 启动桥接器
npm start -- 8752 node ../your-mcp-server.js
```

## Android客户端示例

以下是Android客户端连接到MCP桥接器的示例代码：

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import org.json.JSONObject

class McpClient {
    private val host = "127.0.0.1"
    private val port = 8752
    
    suspend fun sendRequest(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        Socket(host, port).use { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = PrintWriter(socket.getOutputStream(), true)
            
            // 创建JSONRPC请求
            val requestId = System.currentTimeMillis().toString()
            val request = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("method", method)
                put("params", params)
            }
            
            // 发送请求
            output.println(request.toString())
            
            // 读取响应
            val response = input.readLine()
            JSONObject(response)
        }
    }
}
```

## 注意事项

- 确保MCP服务器程序已正确安装并可执行
- 桥接器应当与内部网络一起使用，不要暴露到公共互联网
- 对于生产环境，考虑添加认证和加密层
- 在Android环境中，确保应用有INTERNET权限 