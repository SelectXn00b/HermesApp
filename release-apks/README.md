# AndroidForClaw Release APKs

最新构建时间: 2026-03-11

## 📦 包含的 APK

### 主要应用

#### 1. AndroidForClaw.apk (31 MB) ⭐
**主应用 - 必须安装**
- AI Agent 核心引擎
- AgentLoop 执行循环
- Skills 系统
- 飞书/Discord 集成
- Gateway WebSocket 服务

#### 2. ObserverService.apk (4.4 MB) ⭐
**无障碍服务扩展 - 推荐安装**
- UI 树观察能力
- 截图功能增强
- 手势操作支持
- 权限管理界面

#### 3. BrowserForClaw.apk (8.4 MB)
**浏览器扩展 - 可选安装**
- 基于 Unity WebView
- 支持网页操作
- 与主应用集成

### Termux 集成 (可选)

#### 4. Termux.apk (33 MB)
**Termux 终端模拟器 - 可选**
- Linux 终端环境
- Python/Node.js/Shell 支持
- 用于 termux_exec tool

#### 5. Termux-API.apk (6.8 MB)
**Termux API 插件 - 与 Termux 配套**
- 系统 API 访问
- 与主应用通信
- 需要先安装 Termux

## 🚀 安装顺序

### 基础安装 (必需)
```bash
# 1. 主应用 (必须)
adb install AndroidForClaw.apk

# 2. 无障碍服务 (推荐)
adb install ObserverService.apk
```

### 可选组件
```bash
# 3. 浏览器 (可选)
adb install BrowserForClaw.apk

# 4. Termux (可选 - 用于执行本地代码)
adb install Termux.apk
adb install Termux-API.apk
```

## 📝 更新说明

### 本次更新 (2026-03-11)

#### 修复的问题
1. ✅ Skills metadata 解析失败 → Always Skills 从 0 恢复到 2
2. ✅ 飞书消息发送失败(表格超限) → 自动降级为纯文本
3. ✅ AgentLoop 缺少全局错误兜底 → 添加 try-catch 确保错误反馈
4. ✅ 修复 .gitignore 配置错误 → WorkspaceInitializer.kt 现在可以被提交

#### 新增功能
- 飞书插件表格数量预检查 (maxTablesPerCard = 3)
- 发送失败自动降级重试机制
- AgentLoop 全局错误兜底
- 添加 Termux 集成支持

## ⚙️ 首次配置

### 1. 创建配置文件
```bash
adb push config/openclaw.json /sdcard/.androidforclaw/openclaw.json
```

或在手机上编辑: `/sdcard/.androidforclaw/openclaw.json`

### 2. 授予权限

**主应用 (AndroidForClaw)**:
- ✅ 存储权限 (访问配置文件)
- ✅ 悬浮窗权限 (显示 UI)
- ✅ 通知权限 (状态提醒)

**无障碍服务 (ObserverService)**:
- ✅ 无障碍服务权限 (UI 控制)
- ✅ 截图权限 (屏幕捕获)

**Termux (可选)**:
- ✅ 存储权限 (访问共享目录)
- 运行命令设置: `termux-setup-storage`

### 3. 配置 Termux Bridge (可选)

如果安装了 Termux,可以启用代码执行功能:

```bash
# 在 Termux 中执行
pkg install python nodejs termux-api
mkdir -p /sdcard/.androidforclaw/.ipc
python ~/phoneforclaw_server.py
```

详见: [docs/termux-integration/README.md](../../docs/termux-integration/README.md)

## 🔧 构建信息

- **Gradle**: 8.9
- **Kotlin**: 1.9.22
- **Target SDK**: 34 (Android 14)
- **Min SDK**: 21 (Android 5.0+)
- **Termux**: v0.118.3 (ARM64)
- **Termux API**: v0.50.1

## 🆘 问题排查

### 安装失败

**签名冲突**:
```bash
# 卸载旧版本
adb uninstall com.xiaomo.androidforclaw
adb install AndroidForClaw.apk
```

**解析失败**:
- 确认 APK 文件完整
- 检查设备架构 (需要 ARM64)

### 运行问题

**权限不足**:
- 检查无障碍服务是否启用
- 检查存储权限是否授予

**Termux 连接失败**:
- 确认 Termux 和 Termux:API 都已安装
- 检查 `/sdcard/.androidforclaw/.ipc/server.lock` 是否存在
- 运行 `bash ~/start_bridge.sh` 启动服务器

**查看日志**:
```bash
adb logcat | grep AndroidForClaw
```

## 📋 版本信息

| 应用 | 版本 | 大小 |
|------|------|------|
| AndroidForClaw | 1.0.0 | 31 MB |
| ObserverService | 1.0.0 | 4.4 MB |
| BrowserForClaw | 1.0.0 | 8.4 MB |
| Termux | 0.118.3 | 33 MB |
| Termux API | 0.50.1 | 6.8 MB |

---

**项目地址**: https://github.com/xiaomochn/AndroidForClaw
**文档**: https://github.com/xiaomochn/AndroidForClaw/blob/main/README.md
