---
always: false
skillKey: install-app
primaryEnv: android
emoji: 📦
---

# Install App Skill

安装 APK 文件到 Android 设备。

## 工具

### install_app

安装指定路径的 APK 文件。

**参数：**
- `apk_path`（必填）：APK 文件路径，支持：
  - 绝对路径：`/sdcard/Download/app.apk`
  - 相对路径会在常用目录搜索（/sdcard/、/sdcard/Download/、/sdcard/.androidforclaw/）
  - `content://` URI
- `allow_downgrade`（可选，默认 false）：是否允许降级安装

**返回信息：**
- 包名、版本号、安装类型（全新安装/升级/降级/重装）
- APK 大小

## 使用场景

1. **安装下载的 APK**：用户下载了 APK 文件后要求安装
2. **升级应用**：安装新版本的 APK
3. **批量安装**：多个 APK 需要依次安装
4. **Skill 安装**：安装 skill 依赖的 companion app（如 BrowserForClaw、ScreenForClaw）

## 示例

```
用户：帮我安装 /sdcard/Download/wechat.apk
→ install_app(apk_path="/sdcard/Download/wechat.apk")

用户：降级安装这个旧版本
→ install_app(apk_path="/sdcard/Download/app-old.apk", allow_downgrade=true)
```

## 注意事项

- 如果设备没有授予"安装未知来源应用"权限，系统会弹出确认对话框
- 弹出确认对话框时，可配合 `screenshot` + `tap` 工具自动点击确认
- 降级安装默认禁止，需要用户明确指定 `allow_downgrade=true`
- APK 文件必须是有效的 Android 安装包（签名完整）
