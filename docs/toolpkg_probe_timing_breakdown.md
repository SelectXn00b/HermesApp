# ToolPkg Probe Timing Breakdown

采样日志：2026-03-10 00:17:18  
场景：`deepsearching_message_plugin` 未命中，`probeOnly=true`，消息长度 `2`

## 说明

- `sendMessage.total`、`delegate.prepareResponseStream`、`toolpkg.messageProcessing.matchTotal` 都是父级埋点，已经包含各自内部子阶段。
- 看总耗时时，优先看父级埋点；看瓶颈细分时，再看子阶段。
- 本文档只整理首包前的本地前置耗时，不含模型持续流式输出时间。

## 主链路耗时

| 阶段 | 耗时 |
| --- | ---: |
| `delegate.loadModelConfig` | 4ms |
| `delegate.buildUserMessageContent` | 11ms |
| `delegate.addUserMessageToChat` | 10ms |
| `delegate.acquireService` | 0ms |
| `delegate.loadRoleInfo` | 1ms |
| `delegate.loadChatHistory` | 0ms |
| `sendMessage.buildMemory` | 2ms |
| `sendMessage.limitHistory` | 1ms |
| `sendMessage.matchPlugin` | 117ms |
| `sendMessage.readStreamSetting` | 1ms |
| `sendMessage.prepareRequest` | 4ms |
| `sendMessage.total` | 142ms |
| `delegate.prepareResponseStream` | 145ms |
| `delegate.shareResponseStream` | 1ms |
| `delegate.loadProviderModel` | 18ms |
| `delegate.firstResponseChunk` | 1895ms |

## ToolPkg Probe 内部拆分

| 阶段 | 耗时 |
| --- | ---: |
| `toolpkg.messageProcessing.loadHooks` | 9ms |
| `toolpkg.messageProcessing.buildPayload` | 0ms |
| `toolpkg.getMainScript.readBytes` | 2ms |
| `toolpkg.getMainScript.total` | 13ms |
| `toolpkg.runMainHook.getMainScript` | 16ms |
| `toolpkg.runMainHook.resolveFunctionSource` | 1ms |
| `toolpkg.runMainHook.getExecutionEngine` | 0ms |
| `toolpkg.jsEngine.initQuickJs` | 0ms |
| `toolpkg.jsEngine.buildExecutionScript` | 7ms |
| `deepsearching_probe.onMessageProcessing` | 28ms |
| `toolpkg.jsEngine.waitResult` | 41ms |
| `toolpkg.jsEngine.total` | 58ms |
| `toolpkg.runMainHook.executeScriptFunction` | 60ms |
| `toolpkg.runMainHook.total` | 86ms |
| `toolpkg.messageProcessing.runMainHook` | 88ms |
| `toolpkg.messageProcessing.decodeHookResult` | 1ms |
| `toolpkg.messageProcessing.hookTotal` | 92ms |
| `toolpkg.messageProcessing.parseProbeResult` | 1ms |
| `toolpkg.messageProcessing.probeHook` | 97ms |
| `toolpkg.messageProcessing.matchTotal` | 114ms |

## 汇总

| 指标 | 耗时 |
| --- | ---: |
| 本地前置到 `delegate.prepareResponseStream` | 145ms |
| 本地前置到请求已发出并完成流共享、模型信息读取 | 164ms |
| 插件探测总耗时 | 114ms |
| 从发送开始到首包 | 1895ms |
| 估算模型/网络首包等待 | 1731ms |

## 对比结论

| 采样阶段 | 耗时 |
| --- | ---: |
| 早期 probe | ~498ms |
| 解压缓存后 | ~385ms |
| 模块上下文复用后 | ~114ms |

