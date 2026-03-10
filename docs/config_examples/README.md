# 配置文件说明

## 配置文件位置

**设备路径**: `/sdcard/.androidforclaw/openclaw.json`

应用启动时会自动从 `app/src/main/assets/openclaw.json.default` 复制默认配置到此位置。

## 默认配置

参考文件: [app/src/main/assets/openclaw.json.default](../../app/src/main/assets/openclaw.json.default)

```json
// 完整配置请查看上述文件，以下为关键配置说明
```

**主要配置块**:

#### 1. models (模型配置)
```json
{
  "mode": "merge",
  "providers": {
    "anthropic": {
      "baseUrl": "https://api.anthropic.com/v1",
      "apiKey": "${ANTHROPIC_API_KEY}",
      "api": "anthropic",
      "models": [
        {
          "id": "claude-opus-4-6",
          "name": "Claude Opus 4.6",
          "reasoning": true,
          "contextWindow": 200000,
          "maxTokens": 16384
        }
      ]
    }
  }
}
```

**配置项**:
- `baseUrl`: API 端点
- `apiKey`: API 密钥（支持环境变量 `${VAR_NAME}`）
- `api`: API 类型 (openai-completions, anthropic)
- `models`: 模型列表（id, name, reasoning, cost, contextWindow, maxTokens）

#### 2. thinking (推理配置)
```json
{
  "enabled": true,              // 启用 Extended Thinking
  "budgetTokens": 10000,        // 思考 token 预算
  "showInUI": true,             // UI 显示思考内容
  "logToFile": false            // 记录到文件
}
```

#### 3. agent (Agent 配置)
```json
{
  "maxIterations": 20,                      // 最大迭代次数
  "defaultModel": "ppio/pa/claude-opus-4-6", // 默认模型
  "timeout": 300000,                        // 超时时间 (ms)
  "retryOnError": true,                     // 错误重试
  "maxRetries": 3,                          // 最大重试次数
  "mode": "exploration"                     // 模式 (exploration/planning)
}
```

#### 4. skills (技能系统)
```json
{
  "bundledPath": "assets/skills",                        // 内置技能
  "workspacePath": "/sdcard/AndroidForClaw/workspace/skills", // 用户技能
  "managedPath": "/sdcard/AndroidForClaw/.skills",      // 管理技能
  "autoLoad": ["mobile-operations"],                     // 自动加载
  "disabled": [],                                        // 禁用列表
  "onDemand": true,                                      // 按需加载
  "cacheEnabled": true                                   // 缓存
}
```

#### 5. tools (工具配置)
- **screenshot**: 截图工具 (质量、分辨率、格式)
- **accessibility**: 无障碍服务 (手势、UI 树)
- **exec**: 命令执行 (超时、黑名单)
- **browser**: 浏览器工具

#### 6. gateway (网关配置)
```json
{
  "enabled": true,
  "port": 8080,
  "host": "0.0.0.0",
  "channels": ["app", "webui", "adb"],
  "feishu": {
    "enabled": true,
    "appId": "...",
    "appSecret": "...",
    // ... 飞书集成配置
  }
}
```

#### 7. ui (界面配置)
- **floatingWindow**: 悬浮窗设置
- **theme**: 主题 (auto/light/dark)
- **language**: 语言

#### 8. logging (日志配置)
- **level**: 日志级别
- **logToFile**: 记录到文件
- **logPath**: 日志路径
- **logLLMCalls**: 记录 LLM 调用
- **logToolCalls**: 记录工具调用

#### 9. memory (记忆配置)
- **enabled**: 启用记忆
- **path**: 记忆存储路径
- **autoSave**: 自动保存
- **maxEntries**: 最大条目数

#### 10. session (会话配置)
- **storagePath**: 会话存储路径
- **autoSave**: 自动保存
- **maxMessages**: 最大消息数
- **compression**: 压缩

## 使用方法

### 1. 复制配置到设备

```bash
# 创建配置目录
adb shell mkdir -p /sdcard/AndroidForClaw/config

# 推送配置文件
adb push openclaw.json /sdcard/AndroidForClaw/config/
```

### 2. 修改配置

根据实际需求修改配置文件：

- API 密钥
- 模型选择
- Agent 参数
- 功能开关

### 3. 重启应用

配置会在应用启动时加载，修改后需要重启应用生效。

## 环境变量

配置文件支持环境变量引用：

```json
{
  "apiKey": "${ANTHROPIC_API_KEY}"
}
```

应用会从以下位置读取环境变量：
1. 系统环境变量
2. `.env` 文件（如果存在）

## 注意事项

⚠️ **安全提醒**:
- 不要将包含真实 API 密钥的配置文件提交到 Git
- 使用环境变量保护敏感信息
- 示例配置中的密钥已脱敏或使用占位符

⚠️ **路径说明**:
- `/sdcard/.androidforclaw/`: 应用外部存储根目录（用户可访问）
- `/sdcard/.androidforclaw/openclaw.json`: 主配置文件（从 assets/openclaw.json.default 复制）
- `/sdcard/.androidforclaw/workspace/`: 工作区目录（sessions、skills、memory 等）

## 更多信息

详见项目文档：
- [CLAUDE.md](../CLAUDE.md) - 项目架构和开发指南
- [模型配置指南](../模型配置指南.md) - 模型配置详细说明
- [OpenClaw架构深度分析](../OpenClaw架构深度分析.md) - 架构对齐说明
