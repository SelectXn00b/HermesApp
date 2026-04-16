# PhoneForClaw Self-Control - Claude Code 使用指南

通过 ADB 从开发电脑远程控制 PhoneForClaw。

---

## 📋 快速开始

### 前置条件

1. **ADB 连接**：设备通过 USB 或网络连接
   ```bash
   adb devices  # 确认设备已连接
   ```

2. **PhoneForClaw 运行**：应用已安装并运行

### 基本用法

```bash
# 使用辅助脚本（推荐）
./self-control-adb.sh SKILL_NAME key=value

# 或直接使用 ADB
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method SKILL_NAME \
  --extra key:type:value
```

---

## 🛠️ 可用功能

### 1. 页面导航 (navigate_app)

打开应用内的指定页面。

**参数**：
- `page`: 页面 ID

**可用页面**：
- `main` - 主界面
- `config` - 配置页面
- `permissions` - 权限管理
- `chat_history` - 对话历史
- `chat_log` - 详细日志
- `feishu` - 飞书通道
- `channels` - 通道列表
- `result` - 结果展示

**示例**：
```bash
# 打开配置页面
./self-control-adb.sh navigate_app page=config

# 打开权限页面
./self-control-adb.sh navigate_app page=permissions
```

**ADB 命令**：
```bash
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method navigate_app \
  --extra page:s:config
```

---

### 2. 配置管理 (manage_config)

读取和修改应用配置。

**参数**：
- `operation`: 操作类型 (get/set/list/delete)
- `key`: 配置键名（get/set/delete 需要）
- `value`: 配置值（set 需要）
- `category`: 配置分类（list 可选：agent/api/ui/feature/perf）

**示例**：
```bash
# 读取配置
./self-control-adb.sh manage_config operation=get key=exploration_mode

# 设置配置
./self-control-adb.sh manage_config operation=set key=exploration_mode value=true

# 列出功能开关
./self-control-adb.sh manage_config operation=list category=feature

# 删除配置
./self-control-adb.sh manage_config operation=delete key=test_config
```

**ADB 命令**：
```bash
# 读取
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:get \
  --extra key:s:exploration_mode

# 设置
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:set \
  --extra key:s:exploration_mode \
  --extra value:s:true
```

---

### 3. 服务控制 (control_service)

控制悬浮窗和后台服务。

**参数**：
- `operation`: 操作类型
  - `show_float` - 显示悬浮窗
  - `hide_float` - 隐藏悬浮窗
  - `start_float` - 启动服务
  - `stop_float` - 停止服务
  - `check_status` - 检查状态
- `delay_ms`: 延迟时间（show_float 可选）

**示例**：
```bash
# 隐藏悬浮窗
./self-control-adb.sh control_service operation=hide_float

# 延迟显示悬浮窗
./self-control-adb.sh control_service operation=show_float delay_ms:i=500

# 检查服务状态
./self-control-adb.sh control_service operation=check_status
```

**ADB 命令**：
```bash
# 隐藏悬浮窗
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:hide_float

# 显示悬浮窗（延迟）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:show_float \
  --extra delay_ms:i:500
```

---

### 4. 日志查询 (query_logs)

查询应用运行日志。

**参数**：
- `level`: 日志级别（V/D/I/W/E/F，默认 I）
- `filter`: 过滤关键字
- `lines`: 返回行数（1-200，默认 100）
- `source`: 日志来源（logcat/file，默认 logcat）

**示例**：
```bash
# 查询错误日志
./self-control-adb.sh query_logs level=E lines:i=50

# 搜索特定 TAG
./self-control-adb.sh query_logs level=D filter=AgentLoop lines:i=100

# 查询所有日志
./self-control-adb.sh query_logs level=V lines:i=200
```

**ADB 命令**：
```bash
# 查询错误日志
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:E \
  --extra lines:i:50

# 过滤查询
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:D \
  --extra filter:s:AgentLoop \
  --extra lines:i:100
```

---

### 5. 健康检查 (health_check)

检查 Self-Control 服务状态。

**示例**：
```bash
./self-control-adb.sh health
```

**ADB 命令**：
```bash
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method health
```

---

### 6. 列出 Skills (list_skills)

列出所有可用的 Self-Control Skills。

**示例**：
```bash
./self-control-adb.sh list
```

**ADB 命令**：
```bash
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method list_skills
```

---

## 🔄 常见工作流

### 工作流 1: 远程配置

```bash
# 1. 检查健康状态
./self-control-adb.sh health

# 2. 查看当前配置
./self-control-adb.sh manage_config operation=list category=feature

# 3. 修改配置
./self-control-adb.sh manage_config operation=set key=exploration_mode value=true

# 4. 验证修改
./self-control-adb.sh manage_config operation=get key=exploration_mode

# 5. 打开配置页面让用户确认
./self-control-adb.sh navigate_app page=config
```

### 工作流 2: 故障诊断

```bash
# 1. 检查服务状态
./self-control-adb.sh control_service operation=check_status

# 2. 查看错误日志
./self-control-adb.sh query_logs level=E lines:i=50

# 3. 如果发现问题，调整配置
./self-control-adb.sh manage_config operation=set key=screenshot_delay value=200

# 4. 重启服务
./self-control-adb.sh control_service operation=stop_float
./self-control-adb.sh control_service operation=start_float
```

### 工作流 3: CI/CD 集成

```bash
#!/bin/bash
# ci-test.sh

# 配置测试环境
./self-control-adb.sh manage_config operation=set key=test_mode value=true
./self-control-adb.sh control_service operation=hide_float

# 运行测试...

# 收集日志
./self-control-adb.sh query_logs level=E lines:i=200 > error_logs.txt

# 清理
./self-control-adb.sh manage_config operation=set key=test_mode value=false
```

---

## 📊 参数类型

ADB 命令中的参数类型标记：

| 标记 | 类型 | 示例 |
|------|------|------|
| `:s` | String | `--extra page:s:config` |
| `:i` | Integer | `--extra lines:i:50` |
| `:l` | Long | `--extra timestamp:l:1234567890` |
| `:b` | Boolean | `--extra enabled:b:true` |
| `:f` | Float | `--extra ratio:f:3.14` |
| `:d` | Double | `--extra value:d:3.14159` |

---

## 🐛 故障排除

### 问题 1: 设备未连接

```bash
# 检查设备
adb devices

# 如果没有设备，确保：
# 1. USB 连接正常
# 2. 设备开启 USB 调试
# 3. 已授权电脑调试
```

### 问题 2: 命令无响应

```bash
# 检查应用是否运行
adb shell ps | grep com.xiaomo.hermes

# 检查 Provider 是否注册
adb shell dumpsys package com.xiaomo.hermes | grep Provider
```

### 问题 3: 权限被拒绝

```bash
# 检查应用权限
adb shell dumpsys package com.xiaomo.hermes | grep permission
```

---

## 📚 辅助脚本

项目提供了 `self-control-adb.sh` 脚本简化调用：

```bash
# 下载并设置执行权限
chmod +x self-control-adb.sh

# 查看帮助
./self-control-adb.sh help

# 列出所有 Skills
./self-control-adb.sh list

# 健康检查
./self-control-adb.sh health

# 执行 Skill
./self-control-adb.sh SKILL_NAME key1=value1 key2:type=value2
```

**脚本位置**: `self-control/self-control-adb.sh`

---

## 📖 相关文档

- [ADB_USAGE.md](../self-control/ADB_USAGE.md) - 完整 ADB 使用指南
- [DUAL_MODE.md](../self-control/DUAL_MODE.md) - 内部模式 vs ADB 模式
- [self-control/README.md](../self-control/README.md) - Self-Control Module 说明

---

**PhoneForClaw Self-Control** - 从 Claude Code 远程控制 Android 设备 🤖📱
