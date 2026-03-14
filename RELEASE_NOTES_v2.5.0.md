# AndroidForClaw v2.5.0

发布日期: 2026-03-11

## ✨ 更新内容

### 🔧 修复的问题
1. ✅ **Skills metadata 解析失败** - Always Skills 从 0 恢复到 2
2. ✅ **飞书消息发送失败(表格超限)** - 自动降级为纯文本
3. ✅ **AgentLoop 缺少全局错误兜底** - 添加 try-catch 确保错误反馈
4. ✅ **修复 .gitignore 配置错误** - WorkspaceInitializer.kt 现在可以被提交

### 🎉 新增功能
- 飞书插件表格数量预检查 (maxTablesPerCard = 3)
- 发送失败自动降级重试机制
- AgentLoop 全局错误兜底
- 添加 Termux 集成支持

## 📦 下载

### 国内镜像加速 (推荐)
```bash
# 主应用 (必须, ~31MB)
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/v2.5.0/AndroidForClaw.apk"

# 无障碍服务 (推荐, ~4.4MB)
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/v2.5.0/ObserverService.apk"

# 浏览器 (可选, ~8.4MB)
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/v2.5.0/BrowserForClaw.apk"

# Termux (可选, ~33MB)
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/v2.5.0/Termux.apk"

# Termux API (可选, ~6.8MB)
curl -LO "https://ghproxy.com/https://github.com/xiaomochn/AndroidForClaw/releases/download/v2.5.0/Termux-API.apk"
```

### 直接下载 (GitHub)
从下方 **Assets** 区域下载 APK 文件。

## 🚀 安装说明

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

## 📋 要求

- Android 5.0+ (API 21+)
- 约 80MB 存储空间 (包含所有可选组件)

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

**查看日志**:
```bash
adb logcat | grep AndroidForClaw
```

---

**完整文档**: https://github.com/xiaomochn/AndroidForClaw

**问题反馈**: https://github.com/xiaomochn/AndroidForClaw/issues
