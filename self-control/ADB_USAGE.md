# Self-Control ADB 使用指南

通过 ADB（Android Debug Bridge）远程调用 PhoneForClaw 的 Self-Control 功能。

---

## 📋 概述

Self-Control Module 提供了 **3 种 ADB 调用方式**：

| 方式 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| **ContentProvider** | 返回结构化数据，易解析 | 需要 `content` 命令 | 自动化脚本、CI/CD |
| **Broadcast** | 兼容性好，Android 4.0+ | 异步执行，需查看日志 | 快速测试、手动调试 |
| **Shell Script** | 简单易用，封装细节 | 需要额外脚本文件 | 日常使用 |

---

## 🚀 快速开始

### 方法 1: 使用辅助脚本（推荐）

```bash
# 1. 下载脚本
chmod +x self-control-adb.sh

# 2. 查看帮助
./self-control-adb.sh help

# 3. 列出所有 Skills
./self-control-adb.sh list

# 4. 执行 Skill
./self-control-adb.sh navigate_app page=config
```

### 方法 2: 直接使用 ADB

```bash
# ContentProvider 方式
adb shell content call --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method navigate_app \
  --extra page:s:config

# Broadcast 方式
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill navigate_app \
  --es page config
```

---

## 📚 详细使用

### 1. ContentProvider 方式

#### 基本语法

```bash
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method SKILL_NAME \
  --extra key:type:value \
  --extra key2:type:value2
```

#### 参数类型

| 标记 | 类型 | 示例 |
|------|------|------|
| `:s` | String | `--extra page:s:config` |
| `:i` | Integer | `--extra lines:i:50` |
| `:l` | Long | `--extra timestamp:l:1234567890` |
| `:b` | Boolean | `--extra enabled:b:true` |
| `:f` | Float | `--extra ratio:f:3.14` |
| `:d` | Double | `--extra value:d:3.14159` |

#### 示例

##### 1.1 页面导航

```bash
# 打开配置页面
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method navigate_app \
  --extra page:s:config

# 打开权限页面
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method navigate_app \
  --extra page:s:permissions

# 打开对话历史（带参数）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method navigate_app \
  --extra page:s:chat_history \
  --extra session_id:s:abc123
```

##### 1.2 配置管理

```bash
# 读取配置
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:get \
  --extra key:s:exploration_mode

# 设置配置（Boolean）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:set \
  --extra key:s:exploration_mode \
  --extra value:s:true

# 设置配置（Integer）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:set \
  --extra key:s:screenshot_delay \
  --extra value:s:200

# 列出分类配置
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:list \
  --extra category:s:feature

# 删除配置
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method manage_config \
  --extra operation:s:delete \
  --extra key:s:test_config
```

##### 1.3 服务控制

```bash
# 检查服务状态
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:check_status

# 隐藏悬浮窗
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:hide_float

# 显示悬浮窗（立即）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:show_float

# 显示悬浮窗（延迟 500ms）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:show_float \
  --extra delay_ms:i:500

# 启动悬浮窗服务
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:start_float

# 停止悬浮窗服务
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method control_service \
  --extra operation:s:stop_float
```

##### 1.4 日志查询

```bash
# 查询错误日志（最近 50 行）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:E \
  --extra lines:i:50

# 查询调试日志（过滤 AgentLoop）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:D \
  --extra filter:s:AgentLoop \
  --extra lines:i:100

# 查询所有日志（Verbose，最近 200 行）
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:V \
  --extra lines:i:200

# 查询文件日志
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method query_logs \
  --extra level:s:I \
  --extra source:s:file \
  --extra lines:i:100
```

##### 1.5 列出 Skills 和健康检查

```bash
# 列出所有 Skills
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method list_skills

# 健康检查
adb shell content call \
  --uri content://com.xiaomo.hermes.selfcontrol/execute \
  --method health
```

---

### 2. Broadcast 方式

#### 基本语法

```bash
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill SKILL_NAME \
  --es/ei/el/ez/ef/ed key value
```

#### 参数类型

| 标记 | 类型 | 示例 |
|------|------|------|
| `--es` | String | `--es page config` |
| `--ei` | Integer | `--ei lines 50` |
| `--el` | Long | `--el timestamp 1234567890` |
| `--ez` | Boolean | `--ez enabled true` |
| `--ef` | Float | `--ef ratio 3.14` |
| `--ed` | Double | `--ed value 3.14159` |

#### 示例

##### 2.1 页面导航

```bash
# 打开配置页面
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill navigate_app \
  --es page config

# 打开权限页面
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill navigate_app \
  --es page permissions
```

##### 2.2 配置管理

```bash
# 读取配置
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill manage_config \
  --es operation get \
  --es key exploration_mode

# 设置配置
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill manage_config \
  --es operation set \
  --es key exploration_mode \
  --es value true

# 列出配置
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill manage_config \
  --es operation list \
  --es category feature
```

##### 2.3 服务控制

```bash
# 隐藏悬浮窗
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill control_service \
  --es operation hide_float

# 显示悬浮窗（延迟）
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill control_service \
  --es operation show_float \
  --ei delay_ms 500
```

##### 2.4 日志查询

```bash
# 查询错误日志
adb shell am broadcast \
  -a com.xiaomo.hermes.SELF_CONTROL \
  --es skill query_logs \
  --es level E \
  --ei lines 50
```

##### 2.5 查看结果

Broadcast 方式是异步的，结果通过 logcat 输出：

```bash
# 实时查看日志
adb logcat | grep SelfControlReceiver

# 查看最近的日志
adb logcat -d -t 50 | grep SelfControlReceiver
```

---

### 3. Shell 脚本方式

#### 安装

```bash
# 下载脚本
curl -o self-control-adb.sh https://raw.githubusercontent.com/.../self-control-adb.sh

# 或从项目中复制
cp self-control/self-control-adb.sh .

# 添加执行权限
chmod +x self-control-adb.sh
```

#### 使用

```bash
# 查看帮助
./self-control-adb.sh help

# 列出 Skills
./self-control-adb.sh list

# 健康检查
./self-control-adb.sh health

# 执行 Skill（默认使用 ContentProvider）
./self-control-adb.sh SKILL_NAME key1=value1 key2=value2

# 指定使用 Broadcast 方式
./self-control-adb.sh --method=broadcast SKILL_NAME key1=value1
```

#### 示例

```bash
# 页面导航
./self-control-adb.sh navigate_app page=config

# 配置管理
./self-control-adb.sh manage_config operation=get key=exploration_mode
./self-control-adb.sh manage_config operation=set key=exploration_mode value=true

# 服务控制
./self-control-adb.sh control_service operation=hide_float
./self-control-adb.sh control_service operation=show_float delay_ms:i=500

# 日志查询
./self-control-adb.sh query_logs level=E lines:i=50
```

---

## 🔄 完整工作流示例

### 示例 1: 截图前后处理

```bash
#!/bin/bash
# 截图前隐藏悬浮窗，截图后显示

# 1. 隐藏悬浮窗
./self-control-adb.sh control_service operation=hide_float

# 2. 等待 UI 更新
sleep 0.2

# 3. 执行截图（假设有截图命令）
adb shell screencap -p /sdcard/screenshot.png

# 4. 延迟显示悬浮窗
./self-control-adb.sh control_service operation=show_float delay_ms:i=500

echo "Screenshot saved to /sdcard/screenshot.png"
```

### 示例 2: 配置备份和恢复

```bash
#!/bin/bash
# 备份和恢复配置

# 备份配置
echo "Backing up configurations..."
./self-control-adb.sh manage_config operation=list category=feature > config_backup.txt

# 修改配置
echo "Modifying configuration..."
./self-control-adb.sh manage_config operation=set key=exploration_mode value=true

# 验证修改
echo "Verifying..."
./self-control-adb.sh manage_config operation=get key=exploration_mode

# 恢复配置（手动解析 config_backup.txt）
echo "Configuration backed up to config_backup.txt"
```

### 示例 3: 自动诊断

```bash
#!/bin/bash
# 自动检查应用状态并诊断问题

echo "=== Self-Control Health Check ==="
./self-control-adb.sh health

echo -e "\n=== Service Status ==="
./self-control-adb.sh control_service operation=check_status

echo -e "\n=== Recent Errors ==="
./self-control-adb.sh query_logs level=E lines:i=20

echo -e "\n=== Current Configuration ==="
./self-control-adb.sh manage_config operation=list category=feature
```

### 示例 4: CI/CD 集成

```yaml
# .github/workflows/test.yml
name: Android Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Start Android Emulator
        # ... 启动模拟器

      - name: Install APK
        run: adb install -r app-debug.apk

      - name: Health Check
        run: |
          ./self-control-adb.sh health
          if [ $? -ne 0 ]; then
            echo "Health check failed"
            exit 1
          fi

      - name: Configure App
        run: |
          ./self-control-adb.sh manage_config operation=set key=test_mode value=true

      - name: Run Tests
        run: |
          # 执行测试...

      - name: Collect Logs
        if: failure()
        run: |
          ./self-control-adb.sh query_logs level=E lines:i=200 > error_logs.txt
```

---

## 🔒 安全考虑

### 1. 权限保护

在 AndroidManifest.xml 中为 ContentProvider 和 BroadcastReceiver 添加权限：

```xml
<!-- 自定义权限 -->
<permission
    android:name="com.xiaomo.hermes.permission.SELF_CONTROL"
    android:protectionLevel="signature" />

<!-- ContentProvider 权限 -->
<provider
    android:name=".selfcontrol.SelfControlProvider"
    android:authorities="com.xiaomo.hermes.selfcontrol"
    android:exported="true"
    android:permission="com.xiaomo.hermes.permission.SELF_CONTROL" />

<!-- BroadcastReceiver 权限 -->
<receiver
    android:name=".selfcontrol.SelfControlReceiver"
    android:exported="true"
    android:permission="com.xiaomo.hermes.permission.SELF_CONTROL">
    <intent-filter>
        <action android:name="com.xiaomo.hermes.SELF_CONTROL" />
    </intent-filter>
</receiver>
```

### 2. 调试模式检查

仅在调试模式下启用 ADB 访问：

```kotlin
class SelfControlProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // 检查调试模式
        if (!BuildConfig.DEBUG && !isSystemUid()) {
            return errorBundle("Self-Control access denied in release mode")
        }

        // 执行操作...
    }

    private fun isSystemUid(): Boolean {
        return android.os.Process.myUid() == android.os.Process.SYSTEM_UID
    }
}
```

### 3. 操作白名单

限制可通过 ADB 执行的操作：

```kotlin
private val ALLOWED_SKILLS = setOf(
    "navigate_app",
    "control_service",
    "query_logs",
    "manage_config"  // 可选：限制敏感配置
)

override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    if (method !in ALLOWED_SKILLS) {
        return errorBundle("Skill not allowed: $method")
    }
    // ...
}
```

---

## 🐛 故障排除

### 问题 1: 命令无响应

```bash
# 检查应用是否运行
adb shell ps | grep com.xiaomo.hermes

# 检查 ContentProvider 是否注册
adb shell dumpsys package com.xiaomo.hermes | grep Provider

# 检查 BroadcastReceiver 是否注册
adb shell dumpsys package com.xiaomo.hermes | grep Receiver
```

### 问题 2: 权限被拒绝

```bash
# 检查权限
adb shell dumpsys package com.xiaomo.hermes | grep permission

# 临时授予权限（如果使用自定义权限）
adb shell pm grant com.xiaomo.hermes com.xiaomo.hermes.permission.SELF_CONTROL
```

### 问题 3: 结果解析失败

```bash
# ContentProvider 返回的是 Bundle 格式，使用 grep 提取关键信息
adb shell content call ... 2>&1 | grep -E "(success=|content=)"

# Broadcast 查看详细日志
adb logcat -c  # 清空日志
adb shell am broadcast ...  # 发送广播
adb logcat -d | grep -A10 "SelfControlReceiver"  # 查看结果
```

---

## 📚 参考

- **ContentProvider 文档**: [Android Developers - ContentProvider](https://developer.android.com/reference/android/content/ContentProvider)
- **Broadcast 文档**: [Android Developers - BroadcastReceiver](https://developer.android.com/reference/android/content/BroadcastReceiver)
- **ADB 命令**: [Android Developers - ADB](https://developer.android.com/studio/command-line/adb)

---

**Self-Control ADB** - 远程控制 PhoneForClaw 🛠️📱
