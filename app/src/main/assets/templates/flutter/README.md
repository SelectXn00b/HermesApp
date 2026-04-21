# Operit Flutter 项目

这是一个基于 Flutter 官方稳定版应用模板整理的 Operit Flutter 项目，已包含 Android、iOS、Web、Windows、macOS、Linux 的基础结构，以及默认的计数器示例和 widget test。

## 快速开始

1. 先执行 `bash android/setup_android_env.sh`。如果当前环境还没有 Flutter，这个脚本会优先从 Flutter 官方 `storage.flutter-io.cn / storage.googleapis.com` 自动安装 Flutter stable，并继续完成 Flutter host artifacts、Java、Android SDK、Gradle、AAPT2 替换等初始化；同时会把 `FLUTTER_STORAGE_BASE_URL` 和 `PUB_HOSTED_URL` 持久化到 `~/.bashrc`，方便后续 `flutter pub get` 继续走同一套源。
2. 在项目根目录执行 `flutter pub get`。
3. 需要浏览器预览时，执行 `flutter run -d web-server --web-hostname 0.0.0.0 --web-port 5013`。
4. 需要 Android 安装包时，执行 `flutter build apk`。

## 模板说明

- `lib/main.dart` 保留了 Flutter 默认计数器示例，适合作为最小起点。
- `test/widget_test.dart` 保留了默认 widget smoke test。
- `android/local.properties` 是占位文件，若要直接运行 Android Gradle 任务，请先改成你本机的 Flutter SDK 和 Android SDK 路径。
- `android/setup_android_env.sh` 复制并适配了 Android 模板的初始化脚本，包含通过 Flutter 官方 `cn/com` storage 自动安装 Flutter、自动预拉取 Linux / Android 所需 artifacts、Java / Android SDK / Gradle 准备，以及 ARM64 `aapt2` 替换。
- Linux ARM64 也走 Flutter 官方源：脚本会先安装官方 Linux SDK 包，再通过 Flutter 自己的官方下载逻辑补齐 ARM64 的 Dart SDK 和 host artifacts。
- `.metadata`、`.idea` 这类强依赖本机环境的文件没有固化进模板；如果你希望按当前机器的 Flutter 环境补齐它们，可以在项目根目录执行 `flutter create .`。

## 常用命令

- `flutter doctor`
- `bash android/setup_android_env.sh`
- `flutter pub get`
- `flutter analyze`
- `flutter test`
- `flutter build apk`
- `flutter build web --no-tree-shake-icons`

## 关键目录

- `android/`
- `ios/`
- `macos/`
- `linux/`
- `windows/`
- `web/`
- `lib/`
- `test/`
