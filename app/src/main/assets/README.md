# 内置 Shizuku APK 说明

## 如何添加 Shizuku APK 到项目中

为了让应用能够内置 Shizuku APK，请按照以下步骤操作：

1. 下载最新版的 Shizuku APK：
   - 从 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases/latest) 下载最新版 Shizuku APK
   - 或者从 [Google Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) 获取 APK

2. 将下载的 APK 文件重命名为 `shizuku.apk`

3. 将 `shizuku.apk` 文件复制到此目录 (`app/src/main/assets/`)

4. 更新 `shizuku_version.txt` 文件中的版本号，使其与复制的 APK 版本一致

## 注意事项

- 确保使用最新的稳定版 Shizuku APK，以保持与最新 Android 版本的兼容性
- Shizuku 使用 Apache-2.0 许可证开源，包含一些限制条款。详细信息请参阅 [Shizuku 许可证](https://github.com/RikkaApps/Shizuku#license)
- 内置 Shizuku 仅用于没有应用商店访问权限的用户，正常情况仍应优先使用应用商店安装

## 版本信息

- 目前内置版本: 见 `shizuku_version.txt` 文件
- 最后更新时间: <!-- 更新时添加日期 -->

## Bundled Applications

This folder contains bundled applications that can be installed directly from the app:

1. **shizuku.apk** - Shizuku app for privileged API access without root
   * Version: See shizuku_version.txt
   * Used for advanced system operations

## Assets Folders

- **js/** - JavaScript files and shell scripts for various features
- **packages/** - Additional package files for the app 