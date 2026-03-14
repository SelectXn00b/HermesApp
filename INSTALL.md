# phoneforclaw 安装指南

本指南详细说明如何从源码构建和安装 phoneforclaw。

## 📋 前置要求

### 开发环境

- **JDK**: 17 或更高版本
- **Android Studio**: Arctic Fox (2020.3.1) 或更高版本
- **Android SDK**: API 34
- **NDK**: 如需构建 native 代码（可选）
- **Gradle**: 7.4+ (通过 wrapper 自动下载)

### 设备要求

- **Android 系统**: 5.0 (API 21) 或更高版本
- **推荐**: Android 8.0+ 以获得最佳体验
- **存储空间**: 至少 50MB 可用空间

### AI Provider

- **API Key**: Claude API (推荐) 或兼容 OpenAI 格式的 API
- **网络**: 需要稳定的互联网连接

---

## 🚀 快速安装

### 1. 克隆仓库

```bash
git clone https://github.com/xiaomo/phoneforclaw.git
cd phoneforclaw
```

### 2. 配置 API

复制配置模板：

```bash
mkdir -p config
cp config/models.json.example config/models.json
cp config/openclaw.json.default.txt config/openclaw.json
```

编辑 `config/models.json`，填入你的 API Key：

```json
{
  "mode": "merge",
  "providers": {
    "anthropic": {
      "baseUrl": "https://api.anthropic.com/v1",
      "apiKey": "your-api-key-here",
      "api": "anthropic",
      "models": [
        {
          "id": "claude-opus-4-6",
          "name": "Claude Opus 4.6",
          "reasoning": true,
          "input": ["text", "image"],
          "contextWindow": 200000,
          "maxTokens": 16384
        }
      ]
    }
  }
}
```

### 3. 构建 APK

**Debug 版本**（推荐用于开发和测试）：

```bash
./gradlew assembleDebug
```

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

**Release 版本**（需要签名配置）：

```bash
# 首先配置签名（见下文）
./gradlew assembleRelease
```

APK 位置：`app/build/outputs/apk/release/app-release.apk`

### 4. 安装到设备

通过 ADB 安装：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

或通过 Gradle：

```bash
./gradlew installDebug
```

### 5. 授予权限

安装后，打开应用并授予以下权限：

1. **Accessibility Service** (无障碍服务)
   - 设置 → 无障碍 → phoneforclaw → 启用

2. **Display Over Apps** (悬浮窗)
   - 设置 → 应用 → 特殊权限 → 显示在其他应用上层 → phoneforclaw → 允许

3. **Media Projection** (截屏)
   - 应用内首次使用时会弹出授权提示

---

## 🔑 签名配置（Release 版本）

### 方式 1: 使用现有 keystore

1. 创建 `keystore.properties` 文件：

```bash
cp keystore.properties.example keystore.properties
```

2. 编辑 `keystore.properties`：

```properties
storeFile=../your-keystore.jks
storePassword=your-store-password
keyPassword=your-key-password
keyAlias=your-key-alias
```

### 方式 2: 生成新 keystore

```bash
keytool -genkey -v \
  -keystore phoneforclaw-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias phoneforclaw
```

然后按照方式 1 配置 `keystore.properties`。

---

## 🧪 验证安装

### 运行测试

```bash
# 单元测试
./gradlew test

# Lint 检查
./gradlew lint
```

### 手动测试

1. 启动应用
2. 点击悬浮窗图标
3. 输入测试指令：
   ```
   截图并告诉我屏幕上有什么
   ```
4. 观察 Agent 执行过程

---

## 🛠️ Android Studio 构建

### 1. 导入项目

- 打开 Android Studio
- File → Open → 选择项目根目录
- 等待 Gradle 同步完成

### 2. 配置运行配置

- Run → Edit Configurations
- 添加 Android App 配置
- Module: app
- Install Flags: -r -t

### 3. 运行/调试

- Run → Run 'app' (Shift+F10)
- 或 Run → Debug 'app' (Shift+F9)

---

## 📦 预编译 APK 安装

如果你不想从源码构建，可以从 [Releases](https://github.com/xiaomo/phoneforclaw/releases) 下载预编译的 APK。

```bash
# 下载后安装
adb install phoneforclaw-v2.4.3.apk
```

---

## 🔧 高级配置

### 自定义 Skills

创建 Skills 目录：

```bash
adb shell mkdir -p /sdcard/.androidforclaw/workspace/skills/
```

上传自定义 Skill：

```bash
adb push my-skill.md /sdcard/.androidforclaw/workspace/skills/
```

### 模块化构建

phoneforclaw 支持模块化构建：

- **app**: 主应用模块
- **quickjs-executor**: JavaScript 执行器
- **feishu-channel**: 飞书集成（可选）

仅构建特定模块：

```bash
./gradlew :app:assembleDebug
./gradlew :quickjs-executor:build
```

---

## ❓ 常见问题

### 1. Gradle 同步失败

**问题**: Unable to resolve dependency

**解决**:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

### 2. 签名错误

**问题**: INSTALL_PARSE_FAILED_NO_CERTIFICATES

**解决**: 确保 `keystore.properties` 配置正确，或使用 debug 版本。

### 3. 权限无法授予

**问题**: Accessibility Service 无法启用

**解决**:
- 检查设备安全设置
- 部分国产 ROM 需要额外授权
- 尝试重启设备

### 4. 配置文件未生效

**问题**: API 调用失败

**解决**:
1. 确认配置文件存在：
   ```bash
   adb shell ls /sdcard/.androidforclaw/config/
   ```
2. 重新部署配置：
   ```bash
   adb push config/models.json /sdcard/.androidforclaw/config/
   ```
3. 重启应用

### 5. 构建速度慢

**解决**: 使用国内镜像源（已在 `build.gradle` 中配置）

---

## 📞 获取帮助

- **文档**: [完整文档](./docs/index.md)
- **Issues**: [GitHub Issues](https://github.com/xiaomo/phoneforclaw/issues)
- **Discussions**: [讨论区](https://github.com/xiaomo/phoneforclaw/discussions)

---

安装完成后，继续阅读 [快速开始指南](./QUICKSTART.md) 了解如何使用。
